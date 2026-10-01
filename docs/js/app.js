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
        </div>
      </div>
    </div>
  `;

  if (temSerie(site)) renderizarGraficoAcesso(idGrafico, acesso.serie_diaria, "--f1");
}

// ---------- Grade: outros sites + card "Outros forms" ----------
function renderizarCardSite(site, idGrafico) {
  const commit = site.ultimo_commit_dados || {};
  const acesso = site.acesso;
  const periodo = site.periodo_publicado;
  return `
    <div class="site-card">
      <h3>${escapar(site.nome)}</h3>
      <div>
        ${badgeNoAr(site)}
        ${site.ultima_execucao_pipeline ? badgeExecucao(site.ultima_execucao_pipeline) : ""}
      </div>
      <div class="linha"><span>Dados atualizados</span><strong>${formatarRelativo(commit.data)}</strong></div>
      ${periodo ? `<div class="linha"><span>Período coberto</span><strong>${escapar(periodo.inicio)} → ${escapar(periodo.fim)}</strong></div>` : ""}
      <div class="linha"><span>Visitantes (únicos)</span><strong>${escapar(acesso ? acesso.visitantes_unicos ?? "—" : "sem rastreio")}</strong></div>
      ${temSerie(site) ? `<div class="grafico-mini"><canvas id="${idGrafico}"></canvas></div>` : ""}
      ${linkExterno(site.pages_url, "Abrir site →", "link-site")}
      ${linkForm(site, "link-site link-form")}
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

configurarTema();
carregar().catch((erro) => {
  console.error("Falha ao carregar status.json", erro);
  document.getElementById("meta-gerado").textContent = "Erro ao carregar status.json";
});
