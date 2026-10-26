// Roda docs/js/lista.js (página principal) e docs/js/app.js (V1, /v1) no Node, sem navegador:
// um DOM mínimo de mentira (só os ids que existem no HTML de verdade) e fetch devolvendo o
// status.json de exemplo (tests/fixtures, gerado pela coleta com a API falsa) e o publicado.
// Confere o que a página desenha: nomes, alertas, etapas, pop-up, histórico, escape de HTML.
import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const docs = path.join(raiz, "docs");
const lerTexto = (...partes) => fs.readFileSync(path.join(...partes), "utf-8");
const lerJson = (...partes) => JSON.parse(lerTexto(...partes));

const exemplo = lerJson(raiz, "tests", "fixtures", "status_exemplo.json");
const historicoExemplo = lerJson(raiz, "tests", "fixtures", "historico_exemplo.json");
const publicado = lerJson(docs, "dados", "status.json");
const copia = (valor) => JSON.parse(JSON.stringify(valor));

/** Carrega a página `html` com o script `js` num contexto isolado. */
async function abrir(html, js, status, { historicos = {}, sessao, ordem, statusPut = 200 } = {}) {
  const ids = new Set([...lerTexto(docs, html).matchAll(/\bid="([^"]+)"/g)].map((m) => m[1]));
  const elementos = new Map();
  const intervalos = [];
  const erros = [];
  const pedidos = [];
  const requisicoes = []; // {url, method, body} de cada fetch (sessão/ordem usam método e corpo)

  const criar = (id) => {
    const ouvintes = {};
    return {
      id, innerHTML: "", textContent: "", hidden: false, open: false, dataset: {}, scrollTop: 0, ouvintes,
      classList: { toggle() {}, add() {}, remove() {} },
      setAttribute() {}, getAttribute: () => null,
      addEventListener(tipo, f) { (ouvintes[tipo] ??= []).push(f); },
      querySelectorAll: () => [], querySelector: () => null, closest: () => null, matches: () => false,
      showModal() { this.open = true; }, close() { this.open = false; }, focus() {}, value: "",
    };
  };
  const el = (id) => {
    if (!ids.has(id)) return null; // como no navegador: id que não está no HTML dá null
    if (!elementos.has(id)) elementos.set(id, criar(id));
    return elementos.get(id);
  };
  const resposta = (corpo, ok = true, codigo = 200) => ({ ok, status: codigo, json: async () => copia(corpo) });

  const sandbox = {
    document: {
      hidden: false,
      documentElement: { attrs: {}, getAttribute(n) { return this.attrs[n] ?? null; }, setAttribute(n, v) { this.attrs[n] = v; } },
      getElementById: el,
      querySelectorAll: () => [],
      querySelector: () => null,
    },
    console: { log() {}, warn() {}, error: (...a) => erros.push(a.join(" ")) },
    localStorage: { getItem: () => null, setItem() {} },
    getComputedStyle: () => ({ getPropertyValue: () => "#336699" }),
    setInterval: (f, ms) => { intervalos.push(ms); return intervalos.length; },
    setTimeout: () => 0,
    clearTimeout() {},
    // `sessao`/`ordem` indefinidos = API ausente (404), como na cópia antiga do GitHub Pages.
    fetch: async (url, opcoes = {}) => {
      pedidos.push(url);
      requisicoes.push({ url, method: opcoes.method || "GET", body: opcoes.body ? JSON.parse(opcoes.body) : null });
      if (url === "api/sessao") return sessao === undefined ? resposta({}, false, 404) : resposta({ logado: sessao });
      if (url === "api/ordem?alvo=site") {
        if ((opcoes.method || "GET") === "PUT") return resposta({ ok: statusPut === 200 }, statusPut === 200, statusPut);
        return ordem === undefined ? resposta({}, false, 404) : resposta({ slugs: ordem, atualizado_em: "2026-10-26T00:00:00Z" });
      }
      if (url === "dados/status.json") return status === null ? resposta({}, false, 404) : resposta(status);
      if (url in historicos) return historicos[url] === null ? resposta({}, false, 404) : resposta(historicos[url]);
      return resposta({}, false, 404);
    },
  };
  sandbox.window = sandbox;
  sandbox.matchMedia = () => ({ matches: false, addEventListener() {} });
  vm.createContext(sandbox);
  vm.runInContext(lerTexto(docs, "js", js), sandbox, { filename: js });

  const pagina = {
    el, erros, intervalos, pedidos, requisicoes,
    ctx: (expressao) => vm.runInContext(expressao, sandbox),
    texto: (id) => el(id).innerHTML + el(id).textContent,
    async esperar(condicao) {
      for (let i = 0; i < 200 && !condicao(); i++) await new Promise((r) => setTimeout(r, 5));
      assert.ok(condicao(), "a página não terminou de carregar");
    },
  };
  return pagina;
}

const abrirLista = async (status, opcoes) => {
  const p = await abrir("index.html", "lista.js", status, opcoes);
  await p.esperar(() => p.el("lista-resumo").textContent !== "");
  return p;
};
const abrirV1 = async (status, opcoes) => {
  const p = await abrir("v1.html", "app.js", status, opcoes);
  await p.esperar(() => p.el("meta-gerado").textContent !== "");
  return p;
};
const semLixo = (texto, onde) => {
  for (const ruim of ["undefined", "NaN", "[object", "null"]) assert.ok(!texto.includes(ruim), `${onde}: aparece "${ruim}"`);
};
const naLista = (status) => status.sites.filter((s) => s.na_lista !== false);
const site = (status, slug) => status.sites.find((s) => s.slug === slug);

// ---------------------------------------------------------------- lista.js (página principal)

test("lista: um cartão por site, destaque primeiro, resumo e outros forms", async () => {
  const p = await abrirLista(exemplo);
  const html = p.el("lista-sites").innerHTML;
  assert.equal((html.match(/<article/g) || []).length, naLista(exemplo).length);
  for (const s of naLista(exemplo)) assert.ok(html.includes(s.nome), s.nome);
  assert.ok(html.indexOf("Bolão F1") < html.indexOf("Meios de pagamento"), "destaque deve vir primeiro");
  assert.equal(p.el("lista-resumo").textContent, `${exemplo.sites.length} sites · todos no ar`);
  assert.match(p.el("lista-outros").innerHTML, /Notas fiscais ↗/);
  assert.match(html, /href="https:\/\/forms\.gle\/7yZAx1WThPf51bv67"[^>]*>Enviar palpites \(Google Forms\)/);
  assert.match(html, /Abrir Google Forms|Enviar fotos/);
  semLixo(html, "lista-sites");
  assert.deepEqual(p.erros, []);
});

// na_lista=false (hoje o Painel de IA): sem cartão, mas monitorado e com botão no topo (nav#atalhos-topo).
const atalhoIa = site(exemplo, "painel_ia");

test("lista: site com na_lista=false não tem cartão e vira atalho no topo, ainda contado no resumo", async () => {
  assert.equal(atalhoIa.na_lista, false);
  const p = await abrirLista(exemplo);
  assert.ok(!p.el("lista-sites").innerHTML.includes(atalhoIa.nome), "cartão do Painel de IA não deve existir");
  const nav = p.el("atalhos-topo");
  assert.equal(nav.hidden, false);
  assert.ok(nav.innerHTML.includes(`href="${atalhoIa.pages_url}"`));
  assert.match(nav.innerHTML, /🤖 Painel IA/);
  assert.doesNotMatch(nav.innerHTML, /versao-switch__ponto/);
  assert.equal(p.el("lista-resumo").textContent, `${exemplo.sites.length} sites · todos no ar`);
  semLixo(nav.innerHTML, "atalhos-topo");
});

test("lista: atalho com site fora do ar ganha bolinha vermelha e conta no resumo", async () => {
  const status = copia(exemplo);
  site(status, "painel_ia").site_no_ar = { ok: false, status_http: 503 };
  const p = await abrirLista(status);
  assert.match(p.el("atalhos-topo").innerHTML, /versao-switch__ponto[^>]*title="fora do ar"/);
  assert.equal(p.el("lista-resumo").textContent, `${status.sites.length} sites · 1 com problema de acesso`);
});

test("lista: atalho só aceita http(s); sem na_lista=false o nav fica oculto; JSON antigo mostra tudo", async () => {
  const status = copia(exemplo);
  site(status, "painel_ia").pages_url = "javascript:alert(1)";
  const p = await abrirLista(status);
  assert.equal(p.el("atalhos-topo").hidden, true);
  assert.equal(p.el("atalhos-topo").innerHTML, "");

  const antigo = copia(exemplo);
  for (const s of antigo.sites) delete s.na_lista;
  const q = await abrirLista(antigo);
  assert.equal((q.el("lista-sites").innerHTML.match(/<article/g) || []).length, antigo.sites.length);
  assert.equal(q.el("atalhos-topo").hidden, true);
});

test("lista: recarrega a cada minuto", async () => {
  const p = await abrirLista(exemplo);
  assert.deepEqual(p.intervalos, [60000]);
});

test("lista: o status publicado de verdade também desenha", async () => {
  const p = await abrirLista(publicado);
  assert.equal((p.el("lista-sites").innerHTML.match(/<article/g) || []).length, naLista(publicado).length);
  semLixo(p.el("lista-sites").innerHTML, "status publicado");
});

test("lista: site fora do ar, alerta e visitas", async () => {
  const status = copia(exemplo);
  site(status, "chess_tracking").site_no_ar = { ok: false, status_http: 503 };
  site(status, "var_vendo").alertas = [{ nivel: "aviso", texto: "A última coleta falhou." }];
  const p = await abrirLista(status);
  const html = p.el("lista-sites").innerHTML;
  assert.match(html, /bolinha--erro/);
  assert.match(html, /Site pode estar fora do ar \(HTTP 503\)/);
  assert.match(html, /bolinha--aviso/);
  assert.match(html, /⚠ A última coleta falhou\./);
  assert.equal(p.el("lista-resumo").textContent, `${status.sites.length} sites · 1 com problema de acesso`);
});

test("lista: faixa = categoria; bolinha = situação; legenda só com categoria atribuída", async () => {
  const semCategoria = await abrirLista(exemplo);
  assert.match(semCategoria.el("lista-sites").innerHTML, /cartao--cat-neutra/);
  assert.equal(semCategoria.el("lista-legenda").hidden, true);

  const status = copia(exemplo);
  site(status, "meios_pagamento_mensal").categoria = "dados_abertos";
  site(status, "plants_care").categoria = "hobby";
  const p = await abrirLista(status);
  const html = p.el("lista-sites").innerHTML;
  assert.match(html, /cartao--cat-dados_abertos/);
  assert.match(html, /cartao--cat-hobby/);
  assert.equal(p.el("lista-legenda").hidden, false);
  assert.match(p.el("lista-legenda").innerHTML, /Dados abertos/);
  assert.match(p.el("lista-legenda").innerHTML, /Hobby/);
  assert.doesNotMatch(p.el("lista-legenda").innerHTML, /Curiosidade/);
  assert.match(html, /<span class="bolinha bolinha--ok"/);
  semLixo(html, "lista com categoria");
});

test("lista: pipeline (Bolão F1 e PlantsCare) começa recolhido, ícone ao lado do nome, expande/oculta ao clicar", async () => {
  const p = await abrirLista(exemplo);
  const html = p.el("lista-sites").innerHTML;
  // Ícone na mesma linha do nome (dentro de cartao__titulo), sem texto: o cartão recolhido não cresce.
  assert.match(html, /<div class="cartao__titulo">\s*<a class="cartao__nome"[^>]*>Bolão F1<\/a>\s*<button type="button" class="cartao__alternar" data-slug="bolao_f1" aria-expanded="false" aria-label="Mostrar pipeline" title="Mostrar pipeline">▸<\/button>/);
  assert.match(html, /<div class="cartao__pipeline" hidden>/);
  assert.match(html, /cartao__alternar" data-slug="plants_care"/, "PlantsCare mostra o envio do Forms (pipeline_forms)");
  assert.match(html, /Último envio do Forms/);
  assert.equal((html.match(/class="cartao__alternar"/g) || []).length, 2, "só Bolão e PlantsCare têm pipeline");

  // Clique (delegação em #lista-sites): alvo = botão do Bolão, dentro do cartão dele.
  const pipeline = { hidden: true };
  const atributos = {};
  const botao = { dataset: { slug: "bolao_f1" }, textContent: "", setAttribute(k, v) { atributos[k] = v; }, closest: (sel) => (sel === ".cartao" ? cartaoFake : null) };
  const cartaoFake = { querySelector: (sel) => (sel === ".cartao__pipeline" ? pipeline : null) };
  const clicar = () => p.el("lista-sites").ouvintes.click[0]({ target: { closest: (sel) => (sel === ".cartao__alternar" ? botao : null) } });
  clicar();
  assert.equal(pipeline.hidden, false, "expande");
  assert.equal(botao.textContent, "▾");
  assert.equal(atributos["aria-label"], "Ocultar pipeline");
  clicar();
  assert.equal(pipeline.hidden, true, "oculta de novo");
  assert.equal(botao.textContent, "▸");
  assert.deepEqual(p.erros, []);
});

test("lista: pipeline_forms com erro na execução não mostra ícone de pipeline", async () => {
  const status = copia(exemplo);
  site(status, "plants_care").pipeline_forms = { workflow: "pipeline.yml", execucao: { erro: "HTTP 404" }, etapas: null };
  const p = await abrirLista(status);
  assert.doesNotMatch(p.el("lista-sites").innerHTML, /cartao__alternar" data-slug="plants_care"/);
});

test("lista: visitas = soma dos últimos 7 dias da série; sem rastreio = traço", async () => {
  const status = copia(exemplo);
  const chess = site(status, "chess_tracking");
  chess.acesso.serie_diaria = Array.from({ length: 10 }, (_, i) => ({ data: `2026-10-${String(i + 1).padStart(2, "0")}`, visitantes: i + 1 }));
  const p = await abrirLista(status);
  assert.equal(p.ctx("visitasRecentes")(chess), 4 + 5 + 6 + 7 + 8 + 9 + 10);
  assert.equal(p.ctx("visitasRecentes")(site(status, "meios_pagamento_mensal")), null);
  assert.match(p.el("lista-sites").innerHTML, /<strong>—<\/strong>/);
});

test("lista: linha de downloads só nos sites com o bloco (7 dias = soma dos últimos 7)", async () => {
  const status = copia(exemplo);
  const meios = site(status, "meios_pagamento_mensal");
  meios.downloads.serie_diaria = Array.from({ length: 10 }, (_, i) => ({ data: `2026-10-${String(i + 1).padStart(2, "0")}`, downloads: i + 1 }));
  const p = await abrirLista(status);
  assert.equal(p.ctx("downloadsRecentes")(meios), 4 + 5 + 6 + 7 + 8 + 9 + 10);
  assert.equal(p.ctx("downloadsRecentes")(site(status, "chess_tracking")), null);
  const html = p.el("lista-sites").innerHTML;
  const { total, skill, csv } = meios.downloads;
  assert.ok(html.includes(`Downloads (7 dias): <strong>49</strong> · total ${total} · skill ${skill} · CSV ${csv}`));
  assert.match(html, /title="Total sem robôs: 8 · por navegador: 3 · por script \(ex\.: skill do Claude\): 5 — Contando desde 08\/10\/2026"/);
  // 3 sites de dados abertos têm o bloco; os demais cartões não mostram a linha (nem "0")
  assert.equal((html.match(/cartao__downloads/g) || []).length, 3);
  semLixo(html, "lista-sites");
});

test("lista: texto vindo do status é escapado (sem HTML injetado)", async () => {
  const status = copia(exemplo);
  const s = site(status, "chess_tracking");
  s.nome = '<img src=x onerror="alert(1)">';
  s.form_url = 'https://forms.gle/x"onmouseover="alert(2)';
  s.form_rotulo = "<b>negrito</b>";
  status.forms_avulsos = [{ nome: "<script>x</script>", url: "https://forms.gle/a" }];
  const p = await abrirLista(status);
  const html = p.el("lista-sites").innerHTML + p.el("lista-outros").innerHTML;
  assert.ok(!/<img|<b>|<script/.test(html), "HTML cru no cartão");
  assert.ok(html.includes("&lt;img"));
  assert.ok(!html.includes('"onmouseover="'));
});

test("lista: sem forms_avulsos nem acesso não quebra", async () => {
  const status = copia(exemplo);
  delete status.forms_avulsos;
  for (const s of status.sites) delete s.acesso;
  const p = await abrirLista(status);
  assert.equal(p.el("lista-outros").innerHTML, "");
  assert.deepEqual(p.erros, []);
});

test("lista: status.json indisponível mostra a mensagem de erro", async () => {
  const p = await abrir("index.html", "lista.js", null);
  await p.esperar(() => p.el("lista-resumo").textContent !== "");
  assert.equal(p.el("lista-resumo").textContent, "Erro ao carregar status.json");
});


// ---------------------------------------------------------------- lista.js: login e ordem dos cartões

const slugsDaLista = (p) => [...p.el("lista-sites").innerHTML.matchAll(/<article[^>]*data-slug="([^"]+)"/g)].map((m) => m[1]);

test("lista: ordem salva é aplicada e site novo vai para o fim", async () => {
  const cadastro = naLista(exemplo).map((s) => s.slug);
  const salva = ["var_vendo", "chess_tracking", "slug_que_saiu"]; // o último não existe mais
  const p = await abrirLista(exemplo, { ordem: salva });
  const resto = cadastro.filter((s) => !salva.includes(s));
  assert.deepEqual(slugsDaLista(p), ["var_vendo", "chess_tracking", ...resto]);
  assert.ok(p.pedidos.includes("api/ordem?alvo=site"));
  // site novo no cadastro (fora da ordem salva) aparece depois dos salvos, na ordem do cadastro
  const status = copia(exemplo);
  status.sites.push({ ...copia(status.sites[0]), slug: "site_novo", nome: "Site novo", destaque: false });
  const q = await abrirLista(status, { ordem: cadastro });
  assert.equal(slugsDaLista(q).at(-1), "site_novo");
});

test("lista: sem ordem salva (ou API fora) mantém destaque primeiro e ordem do cadastro", async () => {
  const esperado = ["bolao_f1", ...naLista(exemplo).map((s) => s.slug).filter((s) => s !== "bolao_f1")];
  assert.deepEqual(slugsDaLista(await abrirLista(exemplo)), esperado);
  assert.deepEqual(slugsDaLista(await abrirLista(exemplo, { ordem: [] })), esperado);
});

test("lista: ordenar() — salvos primeiro, novos no fim, duplicados e desconhecidos ignorados", async () => {
  const p = await abrirLista(exemplo);
  const ordenar = (sites, slugs) => Array.from(p.ctx("ordenar")(sites, slugs), (s) => s.slug);
  const sites = [{ slug: "a" }, { slug: "b", destaque: true }, { slug: "c" }, { slug: "d" }];
  assert.deepEqual(ordenar(sites, []), ["b", "a", "c", "d"]);
  assert.deepEqual(ordenar(sites, undefined), ["b", "a", "c", "d"]);
  assert.deepEqual(ordenar(sites, ["d", "a"]), ["d", "a", "b", "c"]);
  assert.deepEqual(ordenar(sites, ["c", "x", "c", "b"]), ["c", "b", "a", "d"]);
  assert.deepEqual(sites.map((s) => s.slug), ["a", "b", "c", "d"], "não altera a lista original");
});

test("lista: Entrar some se api/sessao falha e aparece com logado:false", async () => {
  const sem = await abrirLista(exemplo); // api/sessao -> 404
  await sem.esperar(() => sem.pedidos.includes("api/sessao"));
  await new Promise((r) => setTimeout(r, 20));
  for (const id of ["sessao-controles", "btn-entrar", "btn-editar", "btn-sair", "btn-salvar", "btn-cancelar"]) {
    assert.equal(sem.el(id).hidden, true, `${id} deve ficar oculto sem API de sessão`);
  }
  const fora = await abrirLista(exemplo, { sessao: false });
  await fora.esperar(() => fora.el("btn-entrar").hidden === false);
  assert.equal(fora.el("sessao-controles").hidden, false);
  assert.equal(fora.el("btn-editar").hidden, true);
  assert.equal(fora.el("btn-sair").hidden, true);
  const dentro = await abrirLista(exemplo, { sessao: true });
  await dentro.esperar(() => dentro.el("btn-editar").hidden === false);
  assert.equal(dentro.el("btn-entrar").hidden, true);
  assert.equal(dentro.el("btn-sair").hidden, false);
});

test("lista: login abre o pop-up e erro de envio aparece dentro dele", async () => {
  const p = await abrirLista(exemplo, { sessao: false });
  await p.esperar(() => p.el("btn-entrar").hidden === false);
  p.el("btn-entrar").ouvintes.click[0]();
  assert.equal(p.el("dialogo-login").open, true);
  // O fetch falso não conhece api/login (404): erro genérico, pop-up continua aberto.
  await p.el("form-login").ouvintes.submit[0]({ preventDefault() {} });
  assert.equal(p.el("login-erro").textContent, "não foi possível entrar");
  assert.equal(p.el("dialogo-login").open, true);
});

test("lista: Editar liga o modo de edição; Salvar envia PUT com os slugs na ordem do DOM", async () => {
  const p = await abrirLista(exemplo, { sessao: true });
  await p.esperar(() => p.el("btn-editar").hidden === false);
  p.el("btn-editar").ouvintes.click[0]();
  assert.equal(p.el("btn-editar").hidden, true);
  assert.equal(p.el("btn-salvar").hidden, false);
  assert.equal(p.el("btn-cancelar").hidden, false);
  // DOM de mentira: a ordem atual dos cartões depois do arrasto
  const nova = ["var_vendo", "bolao_f1", "chess_tracking"];
  p.el("lista-sites").querySelectorAll = () => nova.map((slug) => ({ dataset: { slug } }));
  await p.el("btn-salvar").ouvintes.click[0]();
  const put = p.requisicoes.find((r) => r.method === "PUT");
  assert.equal(put.url, "api/ordem?alvo=site");
  assert.deepEqual(put.body, { slugs: nova });
  assert.equal(p.el("aviso-ordem").textContent, "Ordem salva");
  assert.equal(p.el("btn-editar").hidden, false, "saiu do modo de edição");
  assert.equal(p.el("btn-salvar").hidden, true);
  assert.deepEqual(p.erros, []);
});

test("lista: Salvar com 401 abre o login; com outro erro avisa que não salvou", async () => {
  const sem = await abrirLista(exemplo, { sessao: true, statusPut: 401 });
  await sem.esperar(() => sem.el("btn-editar").hidden === false);
  sem.el("btn-editar").ouvintes.click[0]();
  await sem.el("btn-salvar").ouvintes.click[0]();
  assert.equal(sem.el("dialogo-login").open, true);
  assert.equal(sem.el("btn-salvar").hidden, false, "continua editando");

  const falha = await abrirLista(exemplo, { sessao: true, statusPut: 500 });
  await falha.esperar(() => falha.el("btn-editar").hidden === false);
  falha.el("btn-editar").ouvintes.click[0]();
  await falha.el("btn-salvar").ouvintes.click[0]();
  assert.equal(falha.el("aviso-ordem").textContent, "não foi possível salvar");
  assert.equal(falha.el("btn-salvar").hidden, false);
});

test("lista: a recarga automática não redesenha a lista durante a edição", async () => {
  const p = await abrirLista(exemplo, { sessao: true });
  await p.esperar(() => p.el("btn-editar").hidden === false);
  p.el("btn-editar").ouvintes.click[0]();
  p.el("lista-sites").innerHTML = "arrastando";
  await p.ctx("carregar()");
  assert.equal(p.el("lista-sites").innerHTML, "arrastando");
  p.el("btn-cancelar").ouvintes.click[0](); // Cancelar restaura a ordem anterior
  assert.match(p.el("lista-sites").innerHTML, /<article/);
});

// ---------------------------------------------------------------- app.js (V1, /v1)

test("v1: card de destaque do Bolão com rodada, etapas e links", async () => {
  const p = await abrirV1(exemplo);
  const html = p.el("secao-destaque").innerHTML;
  assert.match(p.el("meta-gerado").textContent, /^Atualizado em /);
  assert.match(html, /<h2>Bolão F1<\/h2>/);
  assert.match(html, /Rodada mais recente<\/p>\s*<p class="valor">21</);
  assert.equal((html.match(/class="etapa etapa--/g) || []).length, 5);
  for (const titulo of ["Palpites enviados", "Palpites lidos", "Resultado oficial", "Pontuação calculada", "Página atualizada"]) {
    assert.ok(html.includes(titulo), titulo);
  }
  assert.match(html, /Enviar palpites \(Google Forms\) →/);
  assert.match(html, /data-slug-historico="bolao_f1"/);
  assert.match(html, /Último commit em dados: "Atualiza dados da rodada 21"/);
  assert.match(html, /Site no ar/);
  semLixo(html, "secao-destaque");
});

test("v1: Bolão em vigília e sem rastreio de acesso", async () => {
  const status = copia(exemplo);
  const bolao = site(status, "bolao_f1");
  bolao.em_vigilia = true;
  delete bolao.acesso;
  const p = await abrirV1(status);
  const html = p.el("secao-destaque").innerHTML;
  assert.match(html, /Em vigília — aguardando resultado/);
  assert.match(html, /Rastreio de acesso ainda não configurado/);
});

test("v1: Painel de IA sem card, atalho no topo com o href certo e bolinha só quando fora do ar", async () => {
  const p = await abrirV1(exemplo);
  assert.ok(!p.el("grade-sites").innerHTML.includes(atalhoIa.nome));
  assert.ok(!p.el("secao-destaque").innerHTML.includes(atalhoIa.nome));
  assert.equal(p.el("atalhos-topo").hidden, false);
  assert.ok(p.el("atalhos-topo").innerHTML.includes(`href="${atalhoIa.pages_url}"`));
  assert.match(p.el("atalhos-topo").innerHTML, /🤖 Painel IA/);
  assert.doesNotMatch(p.el("atalhos-topo").innerHTML, /versao-switch__ponto/);

  const status = copia(exemplo);
  site(status, "painel_ia").site_no_ar = { ok: false, status_http: 503 };
  const fora = await abrirV1(status);
  assert.match(fora.el("atalhos-topo").innerHTML, /versao-switch__ponto[^>]*title="fora do ar"/);
});

test("v1: grade com os demais sites, passo a passo do Forms e card de outros forms", async () => {
  const p = await abrirV1(exemplo);
  const html = p.el("grade-sites").innerHTML;
  const outros = naLista(exemplo).filter((s) => !s.destaque);
  assert.equal((html.match(/class="site-card[ "]/g) || []).length, outros.length + 1); // + "Outros forms"
  for (const s of outros) assert.ok(html.includes(s.nome), s.nome);
  assert.match(html, /Último envio do Forms/);
  assert.equal((html.match(/class="etapa etapa--/g) || []).length, 4);
  assert.match(html, /Outros forms/);
  assert.match(html, /Notas fiscais \(Google Forms\) →/);
  assert.match(html, /Período coberto<\/span><strong>2020-01 → 2026-08/);
  semLixo(html, "grade-sites");
  assert.deepEqual(p.erros, []);
});

test("v1: o status publicado de verdade também desenha, inclusive todos os pop-ups", async () => {
  const p = await abrirV1(publicado);
  semLixo(p.el("grade-sites").innerHTML, "grade publicada");
  for (const s of publicado.sites.filter((x) => x.detalhes)) {
    p.ctx(`abrirDetalhes(${JSON.stringify(s.slug)})`);
    const html = p.el("dialogo-conteudo").innerHTML;
    assert.ok(html.includes(s.nome), s.slug);
    semLixo(html, `pop-up ${s.slug}`);
  }
});

test("v1: pop-up de detalhes mostra rotina, falha, dados e publicação", async () => {
  const p = await abrirV1(exemplo);
  p.ctx('abrirDetalhes("chess_tracking")');
  const html = p.el("dialogo-conteudo").innerHTML;
  assert.equal(p.el("dialogo-site").open, true);
  assert.equal(p.el("dialogo-site").dataset.slug, "chess_tracking");
  for (const trecho of ["Rotina de atualização de dados", "de segunda a sexta às 18:00 (Brasília)", "Último sucesso",
    "Última falha", "Motivo da falha", "passo “Coletar dados”", "503 Server Error", "Commits recentes na pasta de dados",
    "Publicação e acesso", "Último deploy (Pages)", "Passos da execução mais recente", "Repositório →",
    'data-historico="dados/historico/chess_tracking.json"']) {
    assert.ok(html.includes(trecho), trecho);
  }
  semLixo(html, "pop-up chess_tracking");
  // site sem rotina automática
  p.ctx('abrirDetalhes("eleicoes_2026")');
  assert.match(p.el("dialogo-conteudo").innerHTML, /Sem rotina automática cadastrada/);
});

test("v1: pop-up mostra downloads da skill, do CSV, uso por script e desde quando; sem bloco, nada", async () => {
  const p = await abrirV1(exemplo);
  p.ctx('abrirDetalhes("meios_pagamento_mensal")');
  const html = p.el("dialogo-conteudo").innerHTML;
  for (const trecho of ["Downloads da skill", "Downloads do CSV", "Uso por script", "Contando desde", "08/10/2026"]) {
    assert.ok(html.includes(trecho), trecho);
  }
  semLixo(html, "pop-up meios_pagamento_mensal");
  p.ctx('abrirDetalhes("chess_tracking")');
  assert.doesNotMatch(p.el("dialogo-conteudo").innerHTML, /Downloads da skill|Contando desde/);
  // desde nulo (coleta sem data) não quebra
  const status = copia(exemplo);
  site(status, "meios_pagamento_mensal").downloads.desde = null;
  const q = await abrirV1(status);
  q.ctx('abrirDetalhes("meios_pagamento_mensal")');
  assert.match(q.el("dialogo-conteudo").innerHTML, /Contando desde[\s\S]*sem registro/);
});

test("v1: abrirDetalhes ignora slug desconhecido e o Bolão (sem detalhes)", async () => {
  const p = await abrirV1(exemplo);
  p.ctx('abrirDetalhes("nao_existe")');
  p.ctx('abrirDetalhes("bolao_f1")');
  assert.equal(p.el("dialogo-site").open, false);
});

test("v1: cliques ligam card, histórico do Bolão, voltar e fundo do pop-up", async () => {
  const historicos = { "dados/historico/bolao_f1.json": historicoExemplo, "dados/historico/chess_tracking.json": historicoExemplo };
  const p = await abrirV1(exemplo, { historicos });
  const clicar = (id, evento) => p.el(id).ouvintes.click.forEach((f) => f(evento));
  const alvo = (seletores) => ({ closest: (sel) => seletores[sel] ?? null });

  clicar("grade-sites", { target: alvo({ a: null, ".site-card--clicavel": { dataset: { slug: "chess_tracking" } } }) });
  assert.match(p.el("dialogo-conteudo").innerHTML, /Rotina de atualização de dados/);

  clicar("grade-sites", { target: alvo({ a: {} }) }); // clique num link do card: não abre nada novo
  assert.match(p.el("dialogo-conteudo").innerHTML, /chess_tracking|Chess Tracking/);

  clicar("dialogo-site", { target: alvo({ "[data-historico]": { dataset: { historico: "dados/historico/chess_tracking.json" } } }) });
  await p.esperar(() => p.el("dialogo-conteudo").innerHTML.includes("Histórico de execuções"));
  await p.esperar(() => p.el("dialogo-conteudo").innerHTML.includes("Resumo"));
  assert.match(p.el("dialogo-conteudo").innerHTML, /data-voltar="chess_tracking"/);

  clicar("dialogo-site", { target: alvo({ "[data-voltar]": { dataset: { voltar: "chess_tracking" } } }) });
  assert.match(p.el("dialogo-conteudo").innerHTML, /Rotina de atualização de dados/);

  p.el("dialogo-site").ouvintes.click[0]({ target: p.el("dialogo-site") }); // fundo escurecido fecha
  assert.equal(p.el("dialogo-site").open, false);

  clicar("secao-destaque", { target: alvo({ "[data-slug-historico]": { dataset: { slugHistorico: "bolao_f1", historico: "dados/historico/bolao_f1.json" } } }) });
  await p.esperar(() => p.el("dialogo-conteudo").innerHTML.includes("Resumo"));
  assert.equal(p.el("dialogo-site").open, true);
  assert.match(p.el("dialogo-conteudo").innerHTML, /Histórico de execuções — Bolão F1/);
  assert.ok(!p.el("dialogo-conteudo").innerHTML.includes("botao-voltar"), "Bolão não tem pop-up para voltar");
});

test("v1: histórico completo — resumo, execuções, motivo, avisos recorrentes", async () => {
  const hist = copia(historicoExemplo);
  const aviso = { nivel: "warning", mensagem: "Node.js 20 is deprecated." };
  hist.execucoes = Array.from({ length: 4 }, (_, i) => ({
    id: i + 1, criado_em: `2026-10-0${4 - i}T10:00:00Z`, evento: "schedule", conclusao: i === 1 ? "failure" : "success",
    duracao_s: 60 * (i + 1), url: `https://github.com/x/${i}`, avisos: [aviso],
    ...(i === 1 ? { motivo: { job: "j", passo: "Coletar", mensagem: "<b>quebrou</b>" } } : {}),
  }));
  const p = await abrirV1(exemplo, { historicos: { "dados/historico/chess_tracking.json": hist } });
  await p.ctx('abrirHistorico("chess_tracking", "dados/historico/chess_tracking.json")');
  const html = p.el("dialogo-conteudo").innerHTML;
  assert.match(html, /Execuções<\/span><div>4/);
  assert.match(html, /Com sucesso<\/span><div>3 <span class="suave">\(75%\)/);
  assert.match(html, /Avisos recorrentes \(1\)/);
  assert.equal((html.match(/class="hist-item"/g) || []).length, 4);
  assert.ok(html.includes("&lt;b&gt;quebrou&lt;/b&gt;") && !html.includes("<b>quebrou"));
  semLixo(html, "histórico");
  p.ctx("filtrarHistorico(true)"); // sem DOM de verdade só não pode lançar
});

test("v1: histórico que não carrega mostra o erro dentro do pop-up", async () => {
  const p = await abrirV1(exemplo, { historicos: { "dados/historico/chess_tracking.json": null } });
  await p.ctx('abrirHistorico("chess_tracking", "dados/historico/chess_tracking.json")');
  assert.match(p.el("dialogo-conteudo").innerHTML, /Não foi possível carregar o histórico \(HTTP 404\)/);
  assert.equal(p.el("dialogo-site").open, true);
});

test("v1: sem destaque, sem acesso e com erro de coleta não quebra", async () => {
  const status = copia(exemplo);
  for (const s of status.sites) { s.destaque = false; delete s.acesso; s.ultimo_commit_dados = { erro: "HTTP 404 ao consultar commits" }; }
  const p = await abrirV1(status);
  assert.match(p.el("grade-sites").innerHTML, /sem registro/);
  assert.deepEqual(p.erros, []);
});

test("v1: texto do status é escapado nos cartões", async () => {
  const status = copia(exemplo);
  site(status, "chess_tracking").nome = "<img src=x onerror=alert(1)>";
  site(status, "chess_tracking").alertas = [{ nivel: "erro", texto: '"><script>1</script>' }];
  site(status, "chess_tracking").ultimo_commit_dados.mensagem = "<u>x</u>";
  const p = await abrirV1(status);
  p.ctx('abrirDetalhes("chess_tracking")');
  const html = p.el("grade-sites").innerHTML + p.el("dialogo-conteudo").innerHTML;
  assert.ok(!/<img|<script|<u>/.test(html));
});

test("v1: recarrega a cada minuto e status.json fora do ar mostra erro", async () => {
  const p = await abrirV1(exemplo);
  assert.deepEqual(p.intervalos, [60000]);
  const falha = await abrir("v1.html", "app.js", null);
  await falha.esperar(() => falha.el("meta-gerado").textContent !== "");
  assert.equal(falha.el("meta-gerado").textContent, "Erro ao carregar status.json");
});

test("v1: formatadores", async () => {
  const p = await abrirV1(exemplo);
  const f = (expr) => p.ctx(expr);
  assert.equal(f("formatarDuracao(3725)"), "1 h 2 min");
  assert.equal(f("formatarDuracao(125)"), "2 min 5 s");
  assert.equal(f("formatarDuracao(9)"), "9 s");
  assert.equal(f("formatarDuracao(null)"), "—");
  assert.equal(f('formatarDataCurta("2026-10-13")'), "13/10/2026");
  assert.equal(f("formatarRelativo(null)"), "sem registro");
  assert.equal(f("formatarRelativo(new Date().toISOString())"), "hoje");
  assert.equal(f("formatarRelativo(new Date(Date.now() - 86400000 * 1.5).toISOString())"), "há 1 dia");
  assert.equal(f("formatarRelativo(new Date(Date.now() - 86400000 * 3.5).toISOString())"), "há 3 dias");
  assert.equal(f('tomConclusao("success")'), "ok");
  assert.equal(f('tomConclusao("failure")'), "erro");
  assert.equal(f('tomConclusao("cancelled")'), "aviso");
  assert.equal(f('tomConclusao("skipped")'), "neutro");
  assert.equal(f("mediana([3, 1, 2, null])"), 2);
  assert.equal(f("mediana([])"), null);
  assert.equal(f(`escapar('<a href="x">&\\'')`), "&lt;a href=&quot;x&quot;&gt;&amp;&#39;");
  assert.equal(f("escapar(undefined)"), "");
});

test("v1: badges de execução", async () => {
  const p = await abrirV1(exemplo);
  const badge = (e) => p.ctx(`badgeExecucao(${JSON.stringify(e)})`);
  assert.match(badge({ conclusao: "success", status: "completed" }), /Última execução OK/);
  assert.match(badge({ conclusao: "failure", status: "completed", url: "https://x" }), /Última execução falhou/);
  assert.match(badge({ conclusao: null, status: "in_progress" }), /Execução em andamento/);
  assert.match(badge({ erro: "x" }), /Sem execução registrada/);
  assert.match(badge({ conclusao: "cancelled", status: "completed" }), /Última execução: cancelled/);
});
