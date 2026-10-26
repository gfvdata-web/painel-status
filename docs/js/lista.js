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

// Ordem salva dos cartões (slugs, vinda de api/ordem) e estado do login/edição.
// sessao: null = API de sessão indisponível (ex.: cópia antiga no GitHub Pages) -> nenhum botão aparece.
let ordemSalva = [];
let sessao = null;
let editando = false;
let arrastando = null;
let temporizadorAviso = null;
const URL_ORDEM = "api/ordem?alvo=site";

// Ordem salva é opcional: qualquer falha vale como "sem ordem salva".
async function carregarOrdem() {
  try {
    const resp = await fetch(URL_ORDEM, { cache: "no-store", credentials: "same-origin" });
    if (!resp.ok) return [];
    const dados = await resp.json();
    return Array.isArray(dados.slugs) ? dados.slugs : [];
  } catch (erro) {
    return [];
  }
}

async function carregar() {
  // Durante a edição a lista não é redesenhada (o arrasto se perderia).
  if (editando) return;
  const [resp, slugs] = await Promise.all([fetch("dados/status.json", { cache: "no-store" }), carregarOrdem()]);
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  const novo = await resp.json();
  if (editando) return;
  const mudouOrdem = JSON.stringify(slugs) !== JSON.stringify(ordemSalva);
  if (statusAtual && !mudouOrdem && novo.meta.gerado_em === statusAtual.meta.gerado_em) return;
  statusAtual = novo;
  ordemSalva = slugs;
  renderizar(statusAtual);
}

// Ordem dos cartões: slugs salvos primeiro, na ordem salva; sites fora da lista (novos) vão para o
// fim, na ordem do cadastro. Sem ordem salva, o destaque (Bolão F1) primeiro e o resto no cadastro.
function ordenar(sites, slugs) {
  if (!slugs || !slugs.length) {
    return [...sites].sort((a, b) => Number(Boolean(b.destaque)) - Number(Boolean(a.destaque)));
  }
  const porSlug = new Map(sites.map((s) => [s.slug, s]));
  const vistos = new Set();
  const salvos = [];
  for (const slug of slugs) {
    if (porSlug.has(slug) && !vistos.has(slug)) { vistos.add(slug); salvos.push(porSlug.get(slug)); }
  }
  return [...salvos, ...sites.filter((s) => !vistos.has(s.slug))];
}

// Todo texto vindo do status.json passa por escapar() antes de ir para o innerHTML.
function escapar(valor) {
  return String(valor ?? "")
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

// Sites com na_lista === false não viram cartão: entram na barra do topo como botão (nav#atalhos-topo).
// JSON antigo, sem o campo, mostra tudo na lista. Só http(s); bolinha vermelha se o site estiver fora do ar.
// Mesma função em app.js e lista.js (as páginas não compartilham script) — mudou uma, mudar a outra.
function naLista(site) {
  return site.na_lista !== false;
}

function renderizarAtalhos(sites) {
  const nav = document.getElementById("atalhos-topo");
  if (!nav) return;
  const links = sites.filter((s) => !naLista(s) && /^https?:\/\//.test(s.pages_url || "")).map((s) => {
    const foraDoAr = !(s.site_no_ar && s.site_no_ar.ok);
    const ponto = foraDoAr ? `<span class="versao-switch__ponto" role="img" aria-label="fora do ar" title="fora do ar"></span>` : "";
    return `<a class="versao-switch__link" href="${escapar(s.pages_url)}" title="${escapar(s.nome)}">🤖 ${escapar(s.nome.replace(/ de /g, " "))}${ponto}</a>`;
  });
  nav.innerHTML = links.join("");
  nav.hidden = links.length === 0;
}

// Soma dos visitantes únicos dos últimos 7 dias da série; null = site sem rastreio.
function visitasRecentes(site) {
  const serie = site.acesso && site.acesso.serie_diaria;
  if (!serie || !serie.length) return null;
  return serie.slice(-DIAS_VISITAS).reduce((total, dia) => total + (dia.visitantes || 0), 0);
}

// Downloads dos últimos 7 dias da série (mesma janela das visitas); null = site sem o bloco `downloads`.
function downloadsRecentes(site) {
  const bloco = site.downloads;
  if (!bloco) return null;
  return (bloco.serie_diaria || []).slice(-DIAS_VISITAS).reduce((total, dia) => total + (dia.downloads || 0), 0);
}

// Linha discreta do cartão: 7 dias em destaque; total, skill e CSV (acumulados) ao lado; o resto na dica.
function linhaDownloads(site) {
  const recentes = downloadsRecentes(site);
  if (recentes === null) return "";
  const b = site.downloads;
  const dica = [
    `Total sem robôs: ${b.total} · por navegador: ${b.por_navegador} · por script (ex.: skill do Claude): ${b.por_script}`,
    b.desde ? `Contando desde ${b.desde.split("-").reverse().join("/")}` : "",
  ].filter(Boolean).join(" — ");
  return `<p class="cartao__downloads" title="${escapar(dica)}">Downloads (${DIAS_VISITAS} dias): <strong>${escapar(recentes)}</strong> · total ${escapar(b.total)} · skill ${escapar(b.skill)} · CSV ${escapar(b.csv)}</p>`;
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

// Passo a passo a mostrar no cartão: etapas_pipeline (Bolão F1) ou o último envio do Forms
// (pipeline_forms, hoje o PlantsCare — mesmo formato). null = cartão sem pipeline.
function dadosPipeline(site) {
  if (site.etapas_pipeline && site.etapas_pipeline.length) {
    return { etapas: site.etapas_pipeline, execucao: site.ultima_execucao_pipeline || {}, titulo: "Última atualização de dados" };
  }
  const bloco = site.pipeline_forms;
  const execucao = (bloco && bloco.execucao) || {};
  if (bloco && !execucao.erro && bloco.etapas && bloco.etapas.length) {
    return { etapas: bloco.etapas, execucao, titulo: "Último envio do Forms" };
  }
  return null;
}

// Ícone ao lado do nome que abre/fecha o pipeline (o cartão recolhido fica com a altura dos outros).
function rotuloAlternar(aberto) {
  return aberto ? "Ocultar pipeline" : "Mostrar pipeline";
}

function botaoPipeline(site) {
  if (!dadosPipeline(site)) return "";
  const aberto = pipelinesAbertos.has(site.slug);
  return `<button type="button" class="cartao__alternar" data-slug="${escapar(site.slug)}" aria-expanded="${aberto}" aria-label="${rotuloAlternar(aberto)}" title="${rotuloAlternar(aberto)}">${aberto ? "▾" : "▸"}</button>`;
}

// Trilha horizontal, centralizada, rolável no celular.
function painelPipeline(site) {
  const dados = dadosPipeline(site);
  if (!dados) return "";
  const { etapas, execucao, titulo } = dados;
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
    <div class="cartao__pipeline" ${aberto ? "" : "hidden"}>
      <p class="cartao__pipeline-titulo">${titulo}${link}</p>
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
    <article class="cartao cartao--cat-${categoria}" data-slug="${escapar(site.slug)}">
      <div class="cartao__linha">
        <button type="button" class="cartao__arrastar" aria-label="Arrastar para mudar a ordem" title="Arrastar para mudar a ordem">⠿</button>
        <div class="cartao__principal">
          <div class="cartao__titulo">
            <a class="cartao__nome" href="${escapar(site.pages_url)}" target="_blank" rel="noopener">${escapar(site.nome)}</a>
            ${botaoPipeline(site)}
          </div>
          ${sub}
          ${linhaDownloads(site)}
        </div>
        ${form}
        <div class="cartao__visitas" title="${visitas === null ? "Sem rastreio de visitas" : `Visitantes únicos nos últimos ${DIAS_VISITAS} dias`}">
          <strong>${visitas === null ? "—" : visitas}</strong><small>visitas · ${DIAS_VISITAS}d</small>
        </div>
        <span class="bolinha bolinha--${bolinhaNivel}" role="img" aria-label="${TEXTO_BOLINHA[bolinhaNivel]}" title="${TEXTO_BOLINHA[bolinhaNivel]} · ${escapar(rotuloCategoria)}"></span>
      </div>
      ${painelPipeline(site)}
    </article>`;
}

function renderizar(status) {
  const sites = ordenar(status.sites.filter(naLista), ordemSalva);
  document.getElementById("lista-sites").innerHTML = sites.map(cartao).join("");
  renderizarAtalhos(status.sites);

  // Legenda das categorias: só aparece depois que alguma categoria estiver atribuída.
  const usadas = [...new Set(sites.map((s) => s.categoria).filter((c) => NOME_CATEGORIA[c]))];
  const legenda = document.getElementById("lista-legenda");
  legenda.hidden = usadas.length === 0;
  legenda.innerHTML = usadas.map((c) =>
    `<span class="legenda__item"><span class="legenda__cor legenda__cor--${c}" aria-hidden="true"></span>${escapar(NOME_CATEGORIA[c])}</span>`).join("");

  // O resumo conta todos os sites monitorados, inclusive os que ficam só como botão no topo.
  const foraDoAr = status.sites.filter((s) => !(s.site_no_ar && s.site_no_ar.ok)).length;
  document.getElementById("lista-resumo").textContent =
    `${status.sites.length} sites · ${foraDoAr ? `${foraDoAr} com problema de acesso` : "todos no ar"}`;

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
    botao.setAttribute("aria-label", rotuloAlternar(aberto));
    botao.setAttribute("title", rotuloAlternar(aberto));
    botao.textContent = aberto ? "▾" : "▸";
    cartaoDoSite.querySelector(".cartao__pipeline").hidden = !aberto;
  });
}

// ---------- Login e edição da ordem dos cartões ----------
function mostrarAviso(texto, erro) {
  const aviso = document.getElementById("aviso-ordem");
  aviso.textContent = texto;
  aviso.hidden = false;
  aviso.classList.toggle("aviso-ordem--erro", Boolean(erro));
  clearTimeout(temporizadorAviso);
  temporizadorAviso = setTimeout(() => { aviso.hidden = true; }, erro ? 5000 : 2500);
}

// Quais botões da barra do topo aparecem, conforme sessão (null/false/true) e modo de edição.
function atualizarBotoes() {
  document.getElementById("sessao-controles").hidden = sessao === null;
  document.getElementById("btn-entrar").hidden = sessao !== false;
  document.getElementById("btn-editar").hidden = !(sessao === true && !editando);
  document.getElementById("btn-sair").hidden = !(sessao === true && !editando);
  document.getElementById("btn-salvar").hidden = !editando;
  document.getElementById("btn-cancelar").hidden = !editando;
}

async function carregarSessao() {
  try {
    const resp = await fetch("api/sessao", { cache: "no-store", credentials: "same-origin" });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const dados = await resp.json();
    sessao = Boolean(dados.logado);
  } catch (erro) {
    sessao = null; // sem API (cópia antiga): a página segue só de leitura
  }
  atualizarBotoes();
}

function abrirLogin() {
  document.getElementById("login-erro").textContent = "";
  document.getElementById("login-senha").value = "";
  document.getElementById("dialogo-login").showModal();
  document.getElementById("login-usuario").focus();
}

async function enviarLogin(evento) {
  evento.preventDefault();
  const erro = document.getElementById("login-erro");
  erro.textContent = "";
  try {
    const resp = await fetch("api/login", {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        usuario: document.getElementById("login-usuario").value,
        senha: document.getElementById("login-senha").value,
      }),
    });
    if (resp.status === 401) { erro.textContent = "usuário ou senha incorretos"; return; }
    if (!resp.ok) { erro.textContent = "não foi possível entrar"; return; }
  } catch (falha) {
    erro.textContent = "não foi possível entrar";
    return;
  }
  document.getElementById("login-senha").value = "";
  document.getElementById("dialogo-login").close();
  sessao = true;
  atualizarBotoes();
}

async function sair() {
  try {
    await fetch("api/logout", { method: "POST", credentials: "same-origin" });
  } catch (erro) { /* sem rede: sai só na tela */ }
  sessao = false;
  atualizarBotoes();
}

function slugsNoDom() {
  return [...document.getElementById("lista-sites").querySelectorAll(".cartao")].map((c) => c.dataset.slug);
}

function entrarNaEdicao() {
  editando = true;
  document.getElementById("lista-sites").classList.add("lista--editando");
  atualizarBotoes();
}

// Sai da edição e redesenha a lista (ordem salva ou, ao cancelar, a de antes).
function sairDaEdicao() {
  editando = false;
  arrastando = null;
  document.getElementById("lista-sites").classList.remove("lista--editando");
  atualizarBotoes();
  if (statusAtual) renderizar(statusAtual);
}

async function salvarOrdem() {
  const slugs = slugsNoDom();
  let resp;
  try {
    resp = await fetch(URL_ORDEM, {
      method: "PUT",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ slugs }),
    });
  } catch (erro) {
    mostrarAviso("não foi possível salvar", true);
    return;
  }
  if (resp.status === 401) {
    sessao = false;
    atualizarBotoes();
    abrirLogin();
    return;
  }
  if (!resp.ok) { mostrarAviso("não foi possível salvar", true); return; }
  ordemSalva = slugs;
  sairDaEdicao();
  mostrarAviso("Ordem salva", false);
}

// Arrasto com Pointer Events (mouse e toque) pela alça; setas movem o cartão com a alça em foco.
// O cartão arrastado sobe/desce no DOM conforme o ponteiro cruza o meio dos vizinhos.
function moverCartaoPara(y) {
  const lista = document.getElementById("lista-sites");
  const cartaoMovido = arrastando.cartao;
  const proximo = [...lista.children].find((outro) => {
    if (outro === cartaoMovido) return false;
    const caixa = outro.getBoundingClientRect();
    return y < caixa.top + caixa.height / 2;
  });
  if (proximo) { if (proximo !== cartaoMovido.nextElementSibling) lista.insertBefore(cartaoMovido, proximo); }
  else if (lista.lastElementChild !== cartaoMovido) lista.appendChild(cartaoMovido);
}

function configurarEdicao() {
  const lista = document.getElementById("lista-sites");
  lista.addEventListener("pointerdown", (evento) => {
    const alca = editando && evento.target.closest(".cartao__arrastar");
    if (!alca) return;
    evento.preventDefault();
    const cartaoDaAlca = alca.closest(".cartao");
    alca.setPointerCapture(evento.pointerId);
    cartaoDaAlca.classList.add("cartao--arrastando");
    arrastando = { cartao: cartaoDaAlca, alca, id: evento.pointerId };
  });
  lista.addEventListener("pointermove", (evento) => {
    if (!arrastando || evento.pointerId !== arrastando.id) return;
    evento.preventDefault();
    moverCartaoPara(evento.clientY);
    // Perto da borda da tela, rola a página para alcançar o resto da lista.
    if (evento.clientY < 60) window.scrollBy(0, -12);
    else if (evento.clientY > window.innerHeight - 60) window.scrollBy(0, 12);
  });
  const soltar = (evento) => {
    if (!arrastando || evento.pointerId !== arrastando.id) return;
    arrastando.cartao.classList.remove("cartao--arrastando");
    try { arrastando.alca.releasePointerCapture(arrastando.id); } catch (erro) { /* já liberado */ }
    arrastando = null;
  };
  lista.addEventListener("pointerup", soltar);
  lista.addEventListener("pointercancel", soltar);
  lista.addEventListener("keydown", (evento) => {
    const alca = editando && evento.target.closest(".cartao__arrastar");
    if (!alca || (evento.key !== "ArrowUp" && evento.key !== "ArrowDown")) return;
    evento.preventDefault();
    const cartaoDaAlca = alca.closest(".cartao");
    if (evento.key === "ArrowUp") {
      const anterior = cartaoDaAlca.previousElementSibling;
      if (anterior) lista.insertBefore(cartaoDaAlca, anterior);
    } else {
      const seguinte = cartaoDaAlca.nextElementSibling;
      if (seguinte) lista.insertBefore(cartaoDaAlca, seguinte.nextElementSibling);
    }
    alca.focus();
  });

  document.getElementById("btn-entrar").addEventListener("click", abrirLogin);
  document.getElementById("btn-sair").addEventListener("click", sair);
  document.getElementById("btn-editar").addEventListener("click", entrarNaEdicao);
  document.getElementById("btn-cancelar").addEventListener("click", sairDaEdicao);
  document.getElementById("btn-salvar").addEventListener("click", salvarOrdem);
  document.getElementById("form-login").addEventListener("submit", enviarLogin);
  document.getElementById("login-cancelar").addEventListener("click", () => document.getElementById("dialogo-login").close());
  atualizarBotoes();
  carregarSessao();
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
configurarEdicao();
carregar().catch((erro) => {
  console.error("Falha ao carregar status.json", erro);
  document.getElementById("lista-resumo").textContent = "Erro ao carregar status.json";
});
setInterval(() => {
  if (document.hidden) return;
  carregar().catch((erro) => console.warn("Recarga do status.json falhou", erro));
}, INTERVALO_RECARGA_MS);
