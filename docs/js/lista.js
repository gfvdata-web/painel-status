// Página principal (index.html), layout em lista — molde B: um cartão por site, empilhados.
// Lê o mesmo docs/dados/status.json do v1.html (painel completo); nada é cadastrado aqui.

let statusAtual = null;
// Sites com o pipeline expandido (slugs). Fica só na memória: a recarga de 1 min
// redesenha a lista, e a lembrança evita que o pipeline feche sozinho.
const pipelinesAbertos = new Set();

// Mesmo intervalo de recarga do app.js (INTERVALO_RECARGA_MS) — mudou lá, mudar aqui.
const INTERVALO_RECARGA_MS = 60 * 1000;
const DIAS_VISITAS = 7;

// Rótulos das categorias (config.py: CATEGORIAS). Cor da faixa = classe cartao--cat-<valor>.
const NOME_CATEGORIA = {
  dados_abertos: "Dados abertos",
  hobby: "Hobby",
  curiosidade: "Curiosidade",
};

// Mapas do passo a passo, iguais aos de app.js (a lista não carrega app.js). Mudou um, mudar o outro.
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

async function carregar() {
  const resp = await fetch("dados/status.json", { cache: "no-store" });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  const novo = await resp.json();
  if (statusAtual && novo.meta.gerado_em === statusAtual.meta.gerado_em) return;
  statusAtual = novo;
  renderizar(statusAtual);
}

// Todo texto vindo do status.json passa por escapar() antes de ir para o innerHTML.
function escapar(valor) {
  return String(valor ?? "")
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

// Soma dos visitantes únicos dos últimos 7 dias da série; null = site sem rastreio.
function visitasRecentes(site) {
  const serie = site.acesso && site.acesso.serie_diaria;
  if (!serie || !serie.length) return null;
  return serie.slice(-DIAS_VISITAS).reduce((total, dia) => total + (dia.visitantes || 0), 0);
}

// Situação do cartão: nível (bolinha à direita) + texto embaixo do nome (o endereço, se estiver tudo bem).
function situacao(site) {
  const noAr = site.site_no_ar || {};
  if (!noAr.ok) {
    const http = noAr.status_http ? ` (HTTP ${noAr.status_http})` : "";
    return { nivel: "erro", texto: `Site pode estar fora do ar${http}` };
  }
  const alertas = site.alertas || [];
  if (alertas.length) {
    const nivel = alertas.some((a) => a.nivel === "erro") ? "erro" : "aviso";
    return { nivel, texto: `⚠ ${alertas.map((a) => a.texto).join(" ")}` };
  }
  if (site.destaque) {
    return {
      nivel: "ok",
      texto: site.em_vigilia ? "● Em vigília — aguardando resultado" : null,
    };
  }
  return { nivel: "ok", texto: null };
}

// Bolinha: verde no ar, âmbar alerta, vermelho fora do ar. Rótulo para leitor de tela e dica ao passar o mouse.
const TEXTO_BOLINHA = {
  ok: "No ar",
  aviso: "Com aviso",
  erro: "Com problema",
};

function enderecoCurto(url) {
  return String(url || "").replace(/^https?:\/\//, "").replace(/\/$/, "");
}

function formatarDataHora(iso) {
  const data = new Date(iso);
  if (Number.isNaN(data.getTime())) return "";
  return data.toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });
}

// Passo a passo do pipeline (só o Bolão F1 tem etapas_pipeline): trilha horizontal, rolável no celular.
function pipeline(site) {
  const etapas = site.etapas_pipeline;
  if (!etapas || !etapas.length) return "";
  const execucao = site.ultima_execucao_pipeline || {};
  const aberto = pipelinesAbertos.has(site.slug);
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
      </li>`;
  }).join("");
  const link = execucao.url
    ? ` <a href="${escapar(execucao.url)}" target="_blank" rel="noopener">ver execução ↗</a>`
    : "";
  return `
    <button type="button" class="cartao__alternar" data-slug="${escapar(site.slug)}" aria-expanded="${aberto}">
      ${aberto ? "▾ Ocultar pipeline" : "▸ Pipeline de atualização"}
    </button>
    <div class="cartao__pipeline" ${aberto ? "" : "hidden"}>
      <p class="cartao__pipeline-titulo">Última atualização de dados${link}</p>
      <ol class="etapas__lista">${itens}</ol>
    </div>`;
}

function cartao(site) {
  const { nivel, texto } = situacao(site);
  const visitas = visitasRecentes(site);
  const categoria = site.categoria && NOME_CATEGORIA[site.categoria] ? site.categoria : "neutra";
  const sub = texto
    ? `<p class="cartao__sub cartao__sub--${nivel}">${escapar(texto)}</p>`
    : `<p class="cartao__sub">${escapar(enderecoCurto(site.pages_url))}</p>`;
  const form = site.form_url
    ? `<a class="cartao__form" href="${escapar(site.form_url)}" target="_blank" rel="noopener">${escapar(site.form_rotulo || "Abrir Google Forms")}</a>`
    : "";
  const bolinhaNivel = nivel === "aviso" || nivel === "erro" ? nivel : "ok";
  const rotuloCategoria = NOME_CATEGORIA[site.categoria] || "sem categoria";
  return `
    <article class="cartao cartao--cat-${categoria}">
      <div class="cartao__linha">
        <div class="cartao__principal">
          <a class="cartao__nome" href="${escapar(site.pages_url)}" target="_blank" rel="noopener">${escapar(site.nome)}</a>
          ${sub}
        </div>
        ${form}
        <div class="cartao__visitas" title="${visitas === null ? "Sem rastreio de visitas" : `Visitantes únicos nos últimos ${DIAS_VISITAS} dias`}">
          <strong>${visitas === null ? "—" : visitas}</strong><small>visitas · ${DIAS_VISITAS}d</small>
        </div>
        <span class="bolinha bolinha--${bolinhaNivel}" role="img" aria-label="${TEXTO_BOLINHA[bolinhaNivel]}" title="${TEXTO_BOLINHA[bolinhaNivel]} · ${escapar(rotuloCategoria)}"></span>
      </div>
      ${pipeline(site)}
    </article>`;
}

function renderizar(status) {
  // Destaque (Bolão F1) primeiro; o resto na ordem do cadastro.
  const sites = [...status.sites].sort((a, b) => Number(Boolean(b.destaque)) - Number(Boolean(a.destaque)));
  document.getElementById("lista-sites").innerHTML = sites.map(cartao).join("");

  // Legenda das categorias: só aparece depois que alguma categoria estiver atribuída.
  const usadas = [...new Set(sites.map((s) => s.categoria).filter((c) => NOME_CATEGORIA[c]))];
  const legenda = document.getElementById("lista-legenda");
  legenda.hidden = usadas.length === 0;
  legenda.innerHTML = usadas.map((c) =>
    `<span class="legenda__item"><span class="legenda__cor legenda__cor--${c}" aria-hidden="true"></span>${escapar(NOME_CATEGORIA[c])}</span>`).join("");

  const foraDoAr = sites.filter((s) => !(s.site_no_ar && s.site_no_ar.ok)).length;
  document.getElementById("lista-resumo").textContent =
    `${sites.length} sites · ${foraDoAr ? `${foraDoAr} com problema de acesso` : "todos no ar"}`;

  const forms = status.forms_avulsos || [];
  document.getElementById("lista-outros").innerHTML = forms.length
    ? `Outros forms:${forms.map((f) =>
        `<a href="${escapar(f.url)}" target="_blank" rel="noopener">${escapar(f.nome)} ↗</a>`).join("")}`
    : "";
}

// Expandir/ocultar o pipeline (delegação: o conteúdo é redesenhado a cada recarga).
function configurarPipeline() {
  document.getElementById("lista-sites").addEventListener("click", (evento) => {
    const botao = evento.target.closest(".cartao__alternar");
    if (!botao) return;
    const slug = botao.dataset.slug;
    const cartaoDoSite = botao.closest(".cartao");
    const aberto = !pipelinesAbertos.has(slug);
    if (aberto) pipelinesAbertos.add(slug); else pipelinesAbertos.delete(slug);
    botao.setAttribute("aria-expanded", String(aberto));
    botao.textContent = aberto ? "▾ Ocultar pipeline" : "▸ Pipeline de atualização";
    cartaoDoSite.querySelector(".cartao__pipeline").hidden = !aberto;
  });
}

// ---------- Tema claro/escuro (mesma lógica e mesma chave "tema" do app.js) ----------
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

function configurarTema() {
  sincronizarSwitchTema();
  document.querySelectorAll("#tema-switch .tema-switch__btn").forEach((botao) => {
    botao.addEventListener("click", () => {
      document.documentElement.setAttribute("data-theme", botao.dataset.tema);
      try { localStorage.setItem("tema", botao.dataset.tema); } catch (e) { /* storage bloqueado */ }
      sincronizarSwitchTema();
    });
  });
  // Sem escolha explícita salva, acompanha a mudança de tema do sistema.
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", sincronizarSwitchTema);
}

configurarTema();
configurarPipeline();
carregar().catch((erro) => {
  console.error("Falha ao carregar status.json", erro);
  document.getElementById("lista-resumo").textContent = "Erro ao carregar status.json";
});
setInterval(() => {
  if (document.hidden) return;
  carregar().catch((erro) => console.warn("Recarga do status.json falhou", erro));
}, INTERVALO_RECARGA_MS);
