// Layout em lista (lista.html) — teste do molde B: um cartão por site, empilhados.
// Lê o mesmo docs/dados/status.json do index.html; nada é cadastrado aqui.

let statusAtual = null;

// Mesmo intervalo de recarga do app.js (INTERVALO_RECARGA_MS) — mudou lá, mudar aqui.
const INTERVALO_RECARGA_MS = 60 * 1000;
const DIAS_VISITAS = 7;

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

// Situação do cartão: cor da faixa + texto embaixo do nome (o endereço, se estiver tudo bem).
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
      nivel: "destaque",
      texto: site.em_vigilia ? "● Em vigília — aguardando resultado" : null,
    };
  }
  return { nivel: "ok", texto: null };
}

function enderecoCurto(url) {
  return String(url || "").replace(/^https?:\/\//, "").replace(/\/$/, "");
}

function cartao(site) {
  const { nivel, texto } = situacao(site);
  const visitas = visitasRecentes(site);
  const sub = texto
    ? `<p class="cartao__sub cartao__sub--${nivel}">${escapar(texto)}</p>`
    : `<p class="cartao__sub">${escapar(enderecoCurto(site.pages_url))}</p>`;
  const form = site.form_url
    ? `<a class="cartao__form" href="${escapar(site.form_url)}" target="_blank" rel="noopener">${escapar(site.form_rotulo || "Abrir Google Forms")}</a>`
    : "";
  return `
    <article class="cartao${nivel === "ok" ? "" : ` cartao--${nivel}`}">
      <div class="cartao__principal">
        <a class="cartao__nome" href="${escapar(site.pages_url)}" target="_blank" rel="noopener">${escapar(site.nome)}</a>
        ${sub}
      </div>
      ${form}
      <div class="cartao__visitas" title="${visitas === null ? "Sem rastreio de visitas" : `Visitantes únicos nos últimos ${DIAS_VISITAS} dias`}">
        <strong>${visitas === null ? "—" : visitas}</strong><small>visitas · ${DIAS_VISITAS}d</small>
      </div>
    </article>`;
}

function renderizar(status) {
  // Destaque (Bolão F1) primeiro; o resto na ordem do cadastro.
  const sites = [...status.sites].sort((a, b) => Number(Boolean(b.destaque)) - Number(Boolean(a.destaque)));
  document.getElementById("lista-sites").innerHTML = sites.map(cartao).join("");

  const foraDoAr = sites.filter((s) => !(s.site_no_ar && s.site_no_ar.ok)).length;
  document.getElementById("lista-resumo").textContent =
    `${sites.length} sites · ${foraDoAr ? `${foraDoAr} com problema de acesso` : "todos no ar"}`;

  const forms = status.forms_avulsos || [];
  document.getElementById("lista-outros").innerHTML = forms.length
    ? `Outros forms:${forms.map((f) =>
        `<a href="${escapar(f.url)}" target="_blank" rel="noopener">${escapar(f.nome)} ↗</a>`).join("")}`
    : "";
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
carregar().catch((erro) => {
  console.error("Falha ao carregar status.json", erro);
  document.getElementById("lista-resumo").textContent = "Erro ao carregar status.json";
});
setInterval(() => {
  if (document.hidden) return;
  carregar().catch((erro) => console.warn("Recarga do status.json falhou", erro));
}, INTERVALO_RECARGA_MS);
