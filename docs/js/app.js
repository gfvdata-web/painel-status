// Painel de Status — lê docs/dados/status.json e monta a página.
// Sem build step: é só fetch + template strings, igual aos outros painéis da família.
// Nada é cadastrado aqui: sites e forms vêm de src/config.py via status.json.

let statusAtual = null;
let graficos = []; // instâncias do Chart.js ativas, destruídas a cada redesenho

async function carregar() {
  const resp = await fetch("dados/status.json", { cache: "no-store" });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  statusAtual = await resp.json();

  document.getElementById("meta-gerado").textContent =
    `Atualizado em ${formatarDataHora(statusAtual.meta.gerado_em)}`;

  renderizarTudo(statusAtual);
}

function renderizarTudo(status) {
  graficos.forEach((grafico) => grafico.destroy());
  graficos = [];

  const destaque = status.sites.find((s) => s.destaque);
  const outros = status.sites.filter((s) => !s.destaque);

  if (destaque) renderizarDestaque(destaque);
  renderizarGrade(outros, status.forms_avulsos || []);
}

// ---------- Utilitários ----------
// Todo texto vindo do status.json passa por escapar() antes de ir para o innerHTML
// (mensagens de commit, por exemplo, podem ter < > & ").
function escapar(valor) {
  return String(valor ?? "")
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

function linkExterno(url, conteudoHtml, classe = "") {
  const attrClasse = classe ? ` class="${classe}"` : "";
  return `<a${attrClasse} href="${escapar(url)}" target="_blank" rel="noopener">${conteudoHtml}</a>`;
}

// Link do Google Forms do site (vazio se não houver); rótulo vem de form_rotulo.
function linkForm(site, classe) {
  if (!site.form_url) return "";
  return linkExterno(site.form_url, `${escapar(site.form_rotulo || "Abrir Google Forms")} →`, classe);
}

function corCss(variavel) {
  return getComputedStyle(document.documentElement).getPropertyValue(variavel).trim();
}

function temSerie(site) {
  return Boolean(site.acesso && site.acesso.serie_diaria && site.acesso.serie_diaria.length);
}

function formatarDataHora(iso) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

function formatarDataCurta(iso) {
  const [ano, mes, dia] = iso.split("-");
  return `${dia}/${mes}/${ano}`;
}

function formatarRelativo(iso) {
  if (!iso) return "sem registro";
  const dias = Math.floor((Date.now() - new Date(iso).getTime()) / 86400000);
  if (dias <= 0) return "hoje";
  if (dias === 1) return "há 1 dia";
  return `há ${dias} dias`;
}

// ---------- Tema claro/escuro ----------
function temaEfetivo() {
  const attr = document.documentElement.getAttribute("data-theme");
  if (attr === "dark" || attr === "light") return attr;
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function sincronizarSwitchTema() {
  const efetivo = temaEfetivo();
  document.querySelectorAll("#tema-switch .tema-switch__btn").forEach((botao) => {
    const ativo = botao.dataset.tema === efetivo;
    botao.classList.toggle("tema-switch__btn--ativo", ativo);
    botao.setAttribute("aria-pressed", ativo ? "true" : "false");
  });
}

function aplicarTema(tema) {
  document.documentElement.setAttribute("data-theme", tema);
  try { localStorage.setItem("tema", tema); } catch (e) { /* modo privado / storage bloqueado */ }
  sincronizarSwitchTema();
  // Canvas (Chart.js) não reage sozinho à troca de tema via CSS — redesenha com as cores novas.
  if (statusAtual) renderizarTudo(statusAtual);
}

function configurarTema() {
  sincronizarSwitchTema();
  document.querySelectorAll("#tema-switch .tema-switch__btn").forEach((botao) => {
    botao.addEventListener("click", () => aplicarTema(botao.dataset.tema));
  });
  // Sem escolha explícita salva, acompanha a mudança de tema do sistema.
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
    if (!document.documentElement.getAttribute("data-theme")) {
      sincronizarSwitchTema();
      if (statusAtual) renderizarTudo(statusAtual);
    }
  });
}

// ---------- Gráfico de acesso ----------
// `variavelCor` é uma variável CSS (--f1, --acento) em hex, para acompanhar o tema.
function renderizarGraficoAcesso(canvasId, serieDiaria, variavelCor) {
  const ctx = document.getElementById(canvasId);
  if (!ctx || !window.Chart) return;
  const cor = corCss(variavelCor);
  Chart.defaults.color = corCss("--texto-suave");
  Chart.defaults.borderColor = corCss("--borda");
  graficos.push(new Chart(ctx, {
    type: "line",
    data: {
      labels: serieDiaria.map((d) => d.data),
      datasets: [
        {
          label: "Visitantes únicos por dia",
          data: serieDiaria.map((d) => d.visitantes),
          borderColor: cor,
          backgroundColor: cor + "1f",
          tension: 0.3,
          fill: true,
          pointRadius: 0,
          pointHoverRadius: 4,
          pointHoverBackgroundColor: cor,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: "index", intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            title: (itens) => formatarDataCurta(itens[0].label),
            label: (item) => `${item.formattedValue} visitantes únicos`,
          },
        },
      },
      scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
    },
  }));
}

// ---------- Badges ----------
function badgeNoAr(site) {
  const ok = site.site_no_ar && site.site_no_ar.ok;
  return ok
    ? `<span class="badge ok">Site no ar</span>`
    : `<span class="badge erro">Site pode estar fora do ar</span>`;
}

// Última execução do workflow cadastrado em `workflow_arquivo` (coleta ou pipeline).
function badgeExecucao(execucao) {
  if (!execucao || execucao.erro) {
    return `<span class="badge neutro">Sem execução registrada</span>`;
  }
  if (execucao.conclusao === "success") {
    return `<span class="badge ok">Última execução OK</span>`;
  }
  if (execucao.conclusao === "failure") {
    return linkExterno(execucao.url, `<span class="badge erro">Última execução falhou</span>`, "link-badge");
  }
  if (execucao.status !== "completed") {
    return `<span class="badge aviso">Execução em andamento</span>`;
  }
  return `<span class="badge neutro">Última execução: ${escapar(execucao.conclusao)}</span>`;
}

function badgeStatusPipelineBolao(site) {
  if (site.em_vigilia) {
    return `<span class="badge aviso" title="Aguardando o resultado oficial sair na Jolpica">Em vigília — aguardando resultado</span>`;
  }
  return badgeExecucao(site.ultima_execucao_pipeline);
}

// ---------- Etapas do pipeline do Bolão F1 (ver src/etapas_bolao.py) ----------
const ROTULO_ESTADO_ETAPA = {
  ok: "concluída",
  andamento: "em andamento",
  aguardando: "aguardando",
  erro: "falhou",
  pendente: "não iniciada",
  pulado: "não se aplica",
};

const ICONE_ESTADO_ETAPA = {
  ok: "✓",
  andamento: "…",
  aguardando: "⏳",
  erro: "✕",
  pendente: "",
  pulado: "–",
};

function renderizarEtapas(site) {
  const etapas = site.etapas_pipeline;
  if (!etapas || !etapas.length) return "";
  const execucao = site.ultima_execucao_pipeline || {};
  const itens = etapas.map((etapa, indice) => {
    const estadoTexto = `${ROTULO_ESTADO_ETAPA[etapa.estado] || etapa.estado}${etapa.quando ? ` · ${formatarDataHora(etapa.quando)}` : ""}`;
    const titleAttr = etapa.detalhe ? ` title="${escapar(etapa.detalhe)}"` : "";
    return `
    <li class="etapa etapa--${escapar(etapa.estado)}"${titleAttr}>
      <span class="etapa__marcador" aria-hidden="true">${ICONE_ESTADO_ETAPA[etapa.estado] || indice + 1}</span>
      <div class="etapa__texto">
        <p class="etapa__titulo">${escapar(etapa.titulo)}</p>
        <p class="etapa__estado">${escapar(estadoTexto)}</p>
      </div>
    </li>
  `;
  }).join("");
  return `
    <div class="etapas">
      <p class="etapas__titulo">
        Última atualização de dados — passo a passo
        ${execucao.url ? linkExterno(execucao.url, "ver execução") : ""}
      </p>
      <ol class="etapas__lista">${itens}</ol>
    </div>
  `;
}

// ---------- Card de destaque ----------
function renderizarDestaque(site) {
  const secao = document.getElementById("secao-destaque");
  const commit = site.ultimo_commit_dados || {};
  const acesso = site.acesso;

  const idGrafico = "grafico-destaque";
  const graficoHtml = temSerie(site)
    ? `<canvas id="${idGrafico}"></canvas>`
    : `<div class="sem-dados">Rastreio de acesso ainda não configurado para este site.</div>`;

  secao.innerHTML = `
    <div class="card-destaque">
      <div class="card-destaque__topo">
        <div>
          <div class="titulo-destaque">
            <h2>${escapar(site.nome)}</h2>
            <span class="selo-destaque">Destaque</span>
          </div>
          <div>
            ${badgeNoAr(site)}
            ${site.ultima_execucao_pipeline ? badgeStatusPipelineBolao(site) : ""}
          </div>
          <div class="kpis-destaque">
            <div class="kpi">
              <p class="rotulo">Rodada mais recente</p>
              <p class="valor">${escapar(site.rodada_mais_recente ?? "—")}</p>
            </div>
            <div class="kpi">
              <p class="rotulo">Visitantes (únicos)</p>
              <p class="valor">${escapar(acesso ? acesso.visitantes_unicos ?? "—" : "—")}</p>
            </div>
            <div class="kpi">
              <p class="rotulo">Dados atualizados</p>
              <p class="valor valor--texto">${formatarRelativo(commit.data)}</p>
            </div>
          </div>
        </div>
        <div class="grafico-caixa">${graficoHtml}</div>
      </div>
      <div class="card-destaque__rodape">
        ${renderizarEtapas(site)}
        <p class="commit-info">
          Último commit em dados: "${escapar(commit.mensagem ?? "—")}"
          ${commit.url ? `(${linkExterno(commit.url, escapar(commit.sha))})` : ""}
        </p>
        <div class="links-destaque">
          ${linkExterno(site.pages_url, `Abrir ${escapar(site.nome)} →`, "link-site")}
          ${linkForm(site, "link-site")}
          ${site.historico_arquivo ? `<button type="button" class="link-site botao-link" data-historico="${escapar(site.historico_arquivo)}" data-slug-historico="${escapar(site.slug)}">Histórico de execuções →</button>` : ""}
        </div>
      </div>
    </div>
  `;

  if (temSerie(site)) renderizarGraficoAcesso(idGrafico, acesso.serie_diaria, "--f1");
}

// ---------- Grade: outros sites + card "Outros forms" ----------
// Alertas (gerados em src/detalhes_site.py): o card mostra ⚠ na cor do mais grave.
function iconeAlerta(site) {
  const alertas = site.alertas || [];
  if (!alertas.length) return "";
  const nivel = alertas.some((a) => a.nivel === "erro") ? "erro" : "aviso";
  const textos = alertas.map((a) => a.texto).join(" ");
  return `<span class="alerta-icone alerta-icone--${nivel}" title="${escapar(textos)}" aria-label="Alerta: ${escapar(textos)}">⚠</span>`;
}

function linhasAlerta(site) {
  return (site.alertas || [])
    .map((a) => `<p class="alerta-linha alerta-linha--${escapar(a.nivel)}">${escapar(a.texto)}</p>`)
    .join("");
}

function renderizarCardSite(site, idGrafico) {
  const commit = site.ultimo_commit_dados || {};
  const acesso = site.acesso;
  const periodo = site.periodo_publicado;
  const clicavel = Boolean(site.detalhes);
  const atributosClique = clicavel
    ? ` data-slug="${escapar(site.slug)}" tabindex="0" role="button" aria-haspopup="dialog" aria-label="Detalhes de ${escapar(site.nome)}"`
    : "";
  return `
    <div class="site-card${clicavel ? " site-card--clicavel" : ""}"${atributosClique}>
      <h3>${escapar(site.nome)} ${iconeAlerta(site)}</h3>
      <div>
        ${badgeNoAr(site)}
        ${site.ultima_execucao_pipeline ? badgeExecucao(site.ultima_execucao_pipeline) : ""}
      </div>
      ${linhasAlerta(site)}
      <div class="linha"><span>Dados atualizados</span><strong>${formatarRelativo(commit.data)}</strong></div>
      ${periodo ? `<div class="linha"><span>Período coberto</span><strong>${escapar(periodo.inicio)} → ${escapar(periodo.fim)}</strong></div>` : ""}
      <div class="linha"><span>Visitantes (únicos)</span><strong>${escapar(acesso ? acesso.visitantes_unicos ?? "—" : "sem rastreio")}</strong></div>
      ${temSerie(site) ? `<div class="grafico-mini"><canvas id="${idGrafico}"></canvas></div>` : ""}
      <div class="site-card__links">
        ${linkExterno(site.pages_url, "Abrir site →", "link-site")}
        ${linkForm(site, "link-site")}
        ${clicavel ? `<span class="site-card__dica">Ver detalhes</span>` : ""}
      </div>
    </div>
  `;
}

function renderizarCardOutrosForms(forms) {
  if (!forms.length) return "";
  return `
    <div class="site-card">
      <h3>Outros forms</h3>
      ${forms.map((form) => linkExterno(form.url, `${escapar(form.nome)} (Google Forms) →`, "link-site link-form")).join("")}
    </div>
  `;
}

function renderizarGrade(sites, formsAvulsos) {
  const idGrafico = (site) => `grafico-${site.slug}`;
  document.getElementById("grade-sites").innerHTML =
    sites.map((site) => renderizarCardSite(site, idGrafico(site))).join("") +
    renderizarCardOutrosForms(formsAvulsos);

  sites.filter(temSerie).forEach((site) => {
    renderizarGraficoAcesso(idGrafico(site), site.acesso.serie_diaria, "--acento");
  });
}

// ---------- Pop-up de detalhes (formato em src/detalhes_site.py) ----------
const ROTULO_CONCLUSAO = {
  success: "sucesso",
  failure: "falhou",
  cancelled: "cancelada",
  timed_out: "tempo esgotado",
  startup_failure: "falhou ao iniciar",
  skipped: "pulado",
  in_progress: "em andamento",
  queued: "na fila",
};

const ROTULO_EVENTO = {
  schedule: "agendada",
  workflow_dispatch: "manual",
  repository_dispatch: "disparo externo",
  push: "push",
};

// Tom de cor de um resultado: ok | erro | aviso | neutro.
function tomConclusao(conclusao) {
  if (conclusao === "success") return "ok";
  if (["failure", "timed_out", "startup_failure"].includes(conclusao)) return "erro";
  if (["in_progress", "queued", "cancelled"].includes(conclusao)) return "aviso";
  return "neutro";
}

function formatarDuracao(segundos) {
  if (segundos == null) return "—";
  const h = Math.floor(segundos / 3600);
  const min = Math.floor((segundos % 3600) / 60);
  const s = segundos % 60;
  if (h) return `${h} h ${min} min`;
  if (min) return `${min} min ${s} s`;
  return `${s} s`;
}

function dataComRelativo(iso) {
  return `${formatarDataHora(iso)} <span class="suave">(${formatarRelativo(iso)})</span>`;
}

function linhaDetalhe(rotulo, valorHtml) {
  return `<div class="det-linha"><span>${rotulo}</span><div>${valorHtml}</div></div>`;
}

function secaoColeta(coleta, historicoArquivo) {
  if (!coleta) {
    return `
      <section class="det-secao">
        <h3>Rotina de atualização de dados</h3>
        <p class="suave">Sem rotina automática cadastrada: os dados deste site são atualizados por commit manual.</p>
      </section>`;
  }
  const agenda = coleta.agenda.length
    ? coleta.agenda.map((a) => `${escapar(a.descricao)} <code>${escapar(a.cron)}</code>`).join("<br>")
    : `<span class="suave">sem agendamento (só disparo manual ou externo)</span>`;
  const sucesso = coleta.ultimo_sucesso;
  const falha = coleta.ultima_falha;
  const motivo = falha && falha.motivo;
  const total = coleta.historico.length;

  const historico = coleta.historico.slice().reverse(); // mais antiga à esquerda
  const concluidas = coleta.historico.filter((e) => e.status === "completed");
  const bolinhas = historico.map((e) => {
    const resultado = e.status === "completed" ? e.conclusao : e.status;
    const titulo = `${formatarDataHora(e.criado_em)} · ${ROTULO_CONCLUSAO[resultado] || resultado} · ${ROTULO_EVENTO[e.evento] || e.evento} · ${formatarDuracao(e.duracao_s)}`;
    return `<a class="bolinha bolinha--${tomConclusao(resultado)}" href="${escapar(e.url)}" target="_blank" rel="noopener" title="${escapar(titulo)}" aria-label="${escapar(titulo)}"></a>`;
  }).join("");

  const passos = coleta.passos_ultima.map((p) =>
    `<li class="passo passo--${tomConclusao(p.conclusao)}">${escapar(p.nome)} <span class="suave">· ${escapar(ROTULO_CONCLUSAO[p.conclusao] || p.conclusao)}</span></li>`
  ).join("");

  return `
    <section class="det-secao">
      <h3>Rotina de atualização de dados</h3>
      ${coleta.erro ? `<p class="alerta-linha alerta-linha--erro">${escapar(coleta.erro)}</p>` : ""}
      ${linhaDetalhe("Workflow", linkExterno(coleta.url_workflow, `<code>${escapar(coleta.workflow)}</code>`))}
      ${linhaDetalhe("Agenda", agenda)}
      ${linhaDetalhe("Último sucesso", sucesso
        ? linkExterno(sucesso.url, dataComRelativo(sucesso.criado_em))
        : `<span class="texto-erro">nenhum nas últimas ${total} execuções</span>`)}
      ${linhaDetalhe("Última falha", falha
        ? `${linkExterno(falha.url, dataComRelativo(falha.criado_em))} <span class="suave">· ${escapar(ROTULO_CONCLUSAO[falha.conclusao] || falha.conclusao)}</span>`
        : `<span class="suave">nenhuma nas últimas ${total} execuções</span>`)}
      ${motivo ? `
        <div class="motivo-falha">
          <p class="motivo-falha__titulo">Motivo da falha${motivo.passo ? ` — passo “${escapar(motivo.passo)}”` : ""}</p>
          ${motivo.mensagem ? `<pre>${escapar(motivo.mensagem)}</pre>` : `<p class="suave">Mensagem de erro indisponível — ver o log da execução.</p>`}
        </div>` : ""}
      ${historico.length ? linhaDetalhe(
        "Histórico",
        `<div class="bolinhas">${bolinhas}</div>
         <span class="suave">${concluidas.filter((e) => e.conclusao === "success").length} de ${concluidas.length} concluídas com sucesso · mais recente à direita</span>
         ${historicoArquivo ? `<br><button type="button" class="botao-historico" data-historico="${escapar(historicoArquivo)}">Ver histórico completo →</button>` : ""}`
      ) : ""}
      ${passos ? `
        <details class="det-passos">
          <summary>Passos da execução mais recente</summary>
          <ol>${passos}</ol>
        </details>` : ""}
    </section>`;
}

function secaoDados(site) {
  const commits = site.detalhes.commits_dados || [];
  const ultimo = site.ultimo_commit_dados || {};
  const periodo = site.periodo_publicado;
  const lista = commits.map((c) => `
    <li>${linkExterno(c.url, `<code>${escapar(c.sha)}</code>`)} ${escapar(c.mensagem)}
      <span class="suave">· ${formatarDataHora(c.data)}</span></li>`).join("");
  return `
    <section class="det-secao">
      <h3>Dados</h3>
      ${linhaDetalhe("Última atualização", ultimo.data
        ? dataComRelativo(ultimo.data)
        : `<span class="suave">${escapar(ultimo.erro || "sem registro")}</span>`)}
      ${periodo ? linhaDetalhe("Período coberto", `${escapar(periodo.inicio)} → ${escapar(periodo.fim)}`) : ""}
      ${lista ? `<p class="det-subtitulo">Commits recentes na pasta de dados</p><ul class="det-commits">${lista}</ul>` : ""}
    </section>`;
}

function secaoPublicacao(site) {
  const noAr = site.site_no_ar || {};
  const deploy = site.detalhes.deploy_pages;
  const acesso = site.acesso;
  return `
    <section class="det-secao">
      <h3>Publicação e acesso</h3>
      ${linhaDetalhe("Site", noAr.ok
        ? `<span class="texto-ok">no ar</span> <span class="suave">· HTTP ${escapar(noAr.status_http)}</span>`
        : `<span class="texto-erro">não respondeu</span> <span class="suave">· ${escapar(noAr.status_http || noAr.erro || "")}</span>`)}
      ${linhaDetalhe("Último deploy (Pages)", deploy
        ? `${linkExterno(deploy.url, dataComRelativo(deploy.criado_em))} <span class="suave">· ${escapar(ROTULO_CONCLUSAO[deploy.conclusao || deploy.status] || deploy.status)}</span>`
        : `<span class="suave">sem deploy automático do Pages registrado (publicado por workflow próprio)</span>`)}
      ${linhaDetalhe("Visitantes (únicos)", escapar(acesso ? acesso.visitantes_unicos ?? "—" : "sem rastreio"))}
    </section>`;
}

// ---------- Histórico completo de execuções (formato em src/historico.py) ----------
const FRACAO_RECORRENTE = 0.5; // aviso presente em pelo menos metade das execuções

function mediana(valores) {
  const validos = valores.filter((v) => v != null).sort((a, b) => a - b);
  return validos.length ? validos[Math.floor(validos.length / 2)] : null;
}

// Avisos que se repetem na maioria das execuções (em geral do próprio GitHub, como o
// de versão do Node.js) saem uma vez só no resumo, sem poluir cada execução.
function avisosRecorrentes(execucoes) {
  const lidas = execucoes.filter((e) => Array.isArray(e.avisos));
  const contagem = new Map();
  lidas.forEach((e) => new Set(e.avisos.map((a) => a.mensagem)).forEach((m) => contagem.set(m, (contagem.get(m) || 0) + 1)));
  const minimo = Math.max(3, Math.ceil(lidas.length * FRACAO_RECORRENTE));
  return new Map([...contagem].filter(([, n]) => n >= minimo));
}

function itemHistorico(e, recorrentes) {
  const avisos = (e.avisos || []).filter((a) => !recorrentes.has(a.mensagem));
  const motivo = e.motivo;
  const limpo = e.conclusao === "success" && !avisos.length;
  const resultado = ROTULO_CONCLUSAO[e.conclusao] || e.conclusao;
  return `
    <li class="hist-item" data-limpo="${limpo ? 1 : 0}">
      <div class="hist-item__topo">
        <span class="bolinha bolinha--${tomConclusao(e.conclusao)}" aria-hidden="true"></span>
        ${linkExterno(e.url, formatarDataHora(e.criado_em))}
        <span class="hist-item__resultado hist-item__resultado--${tomConclusao(e.conclusao)}">${escapar(resultado)}</span>
        <span class="suave">· ${escapar(ROTULO_EVENTO[e.evento] || e.evento)} · ${formatarDuracao(e.duracao_s)}</span>
      </div>
      ${motivo ? `
        <div class="motivo-falha">
          <p class="motivo-falha__titulo">Motivo${motivo.passo ? ` — passo “${escapar(motivo.passo)}”` : ""}</p>
          ${motivo.mensagem ? `<pre>${escapar(motivo.mensagem)}</pre>` : `<p class="suave">Mensagem indisponível (log expirado ou ilegível).</p>`}
        </div>` : ""}
      ${avisos.length ? `<ul class="hist-avisos">${avisos.map((a) =>
        `<li class="hist-aviso hist-aviso--${escapar(a.nivel)}">${escapar(a.mensagem)}</li>`).join("")}</ul>` : ""}
      ${e.avisos === null ? `<p class="suave hist-nota">Avisos não lidos nesta execução.</p>` : ""}
    </li>`;
}

function renderizarHistorico(historico) {
  const execucoes = historico.execucoes || [];
  const sucessos = execucoes.filter((e) => e.conclusao === "success");
  const recorrentes = avisosRecorrentes(execucoes);
  const lidas = execucoes.filter((e) => Array.isArray(e.avisos)).length;
  const pct = execucoes.length ? Math.round((sucessos.length / execucoes.length) * 100) : 0;
  const maisAntiga = execucoes.length ? execucoes[execucoes.length - 1].criado_em : null;
  return `
    <section class="det-secao">
      <h3>Resumo</h3>
      ${linhaDetalhe("Execuções", `${execucoes.length}${maisAntiga ? ` <span class="suave">· desde ${formatarDataHora(maisAntiga)}</span>` : ""}`)}
      ${linhaDetalhe("Com sucesso", `${sucessos.length} <span class="suave">(${pct}%)</span>`)}
      ${linhaDetalhe("Duração típica", `${formatarDuracao(mediana(sucessos.map((e) => e.duracao_s)))} <span class="suave">· mediana das que deram certo</span>`)}
      ${linhaDetalhe("Workflow", linkExterno(historico.url_workflow, `<code>${escapar(historico.workflow)}</code>`))}
      ${recorrentes.size ? `
        <details class="det-passos">
          <summary>Avisos recorrentes (${recorrentes.size}) — em quase toda execução, omitidos abaixo</summary>
          <ul class="hist-avisos">${[...recorrentes].map(([m, n]) =>
            `<li class="hist-aviso">${escapar(m)} <span class="suave">· ${n} de ${lidas}</span></li>`).join("")}</ul>
        </details>` : ""}
    </section>
    <section class="det-secao">
      <h3>Execuções · mais recente primeiro</h3>
      <label class="hist-filtro"><input type="checkbox" data-filtro-historico> Só com falha ou aviso</label>
      <ol class="hist-lista">${execucoes.map((e) => itemHistorico(e, recorrentes)).join("")}</ol>
      <p class="suave hist-vazio" hidden>Nenhuma execução com falha ou aviso.</p>
    </section>`;
}

async function abrirHistorico(slug, arquivo) {
  const site = statusAtual && statusAtual.sites.find((s) => s.slug === slug);
  if (!site) return;
  const conteudo = document.getElementById("dialogo-conteudo");
  const topo = `
    <header class="dialogo__topo">
      ${site.detalhes ? `<button type="button" class="botao-voltar" data-voltar="${escapar(slug)}">← Voltar</button>` : "<span></span>"}
      <form method="dialog"><button class="dialogo__fechar" aria-label="Fechar">✕</button></form>
    </header>
    <h2 id="dialogo-titulo" class="hist-titulo">Histórico de execuções — ${escapar(site.nome)}</h2>`;
  conteudo.innerHTML = `${topo}<p class="suave">Carregando…</p>`;
  try {
    const resp = await fetch(arquivo, { cache: "no-store" });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    conteudo.innerHTML = topo + renderizarHistorico(await resp.json());
  } catch (erro) {
    conteudo.innerHTML = `${topo}<p class="alerta-linha alerta-linha--erro">Não foi possível carregar o histórico (${escapar(erro.message)}).</p>`;
  }
  const dialogo = document.getElementById("dialogo-site");
  dialogo.dataset.slug = slug;
  dialogo.scrollTop = 0;
  if (!dialogo.open) dialogo.showModal(); // aberto direto do card do Bolão F1
}

function filtrarHistorico(somenteProblemas) {
  let visiveis = 0;
  document.querySelectorAll("#dialogo-conteudo .hist-item").forEach((item) => {
    item.hidden = somenteProblemas && item.dataset.limpo === "1";
    if (!item.hidden) visiveis += 1;
  });
  const vazio = document.querySelector("#dialogo-conteudo .hist-vazio");
  if (vazio) vazio.hidden = visiveis > 0;
}

function abrirDetalhes(slug) {
  const site = statusAtual && statusAtual.sites.find((s) => s.slug === slug);
  if (!site || !site.detalhes) return;
  document.getElementById("dialogo-conteudo").innerHTML = `
    <header class="dialogo__topo">
      <h2 id="dialogo-titulo">${escapar(site.nome)} ${iconeAlerta(site)}</h2>
      <form method="dialog"><button class="dialogo__fechar" aria-label="Fechar">✕</button></form>
    </header>
    ${linhasAlerta(site)}
    ${secaoColeta(site.detalhes.coleta, site.historico_arquivo)}
    ${secaoDados(site)}
    ${secaoPublicacao(site)}
    <div class="dialogo__links">
      ${linkExterno(site.pages_url, "Abrir site →", "link-site")}
      ${linkExterno(`https://github.com/gfvdata-web/${site.repo}`, "Repositório →", "link-site")}
      ${linkForm(site, "link-site")}
    </div>
  `;
  const dialogo = document.getElementById("dialogo-site");
  dialogo.dataset.slug = slug;
  dialogo.scrollTop = 0;
  if (!dialogo.open) dialogo.showModal(); // ao voltar do histórico ele já está aberto
}

function configurarDetalhes() {
  const grade = document.getElementById("grade-sites");
  const abrirDoEvento = (evento) => {
    if (evento.target.closest("a")) return; // links do card seguem normais
    const card = evento.target.closest(".site-card--clicavel");
    if (card) abrirDetalhes(card.dataset.slug);
  };
  grade.addEventListener("click", abrirDoEvento);
  grade.addEventListener("keydown", (evento) => {
    if ((evento.key === "Enter" || evento.key === " ") && evento.target.matches(".site-card--clicavel")) {
      evento.preventDefault();
      abrirDoEvento(evento);
    }
  });
  // Card do Bolão F1 (sem pop-up de detalhes): o link abre o histórico direto.
  document.getElementById("secao-destaque").addEventListener("click", (evento) => {
    const botao = evento.target.closest("[data-slug-historico]");
    if (botao) abrirHistorico(botao.dataset.slugHistorico, botao.dataset.historico);
  });
  const dialogo = document.getElementById("dialogo-site");
  // Clique fora da caixa (no fundo escurecido) fecha; Esc já fecha nativamente.
  dialogo.addEventListener("click", (evento) => {
    if (evento.target === dialogo) { dialogo.close(); return; }
    const botaoHistorico = evento.target.closest("[data-historico]");
    if (botaoHistorico) abrirHistorico(dialogo.dataset.slug, botaoHistorico.dataset.historico);
    const botaoVoltar = evento.target.closest("[data-voltar]");
    if (botaoVoltar) abrirDetalhes(botaoVoltar.dataset.voltar);
  });
  dialogo.addEventListener("change", (evento) => {
    if (evento.target.matches("[data-filtro-historico]")) filtrarHistorico(evento.target.checked);
  });
}

configurarTema();
configurarDetalhes();
carregar().catch((erro) => {
  console.error("Falha ao carregar status.json", erro);
  document.getElementById("meta-gerado").textContent = "Erro ao carregar status.json";
});
