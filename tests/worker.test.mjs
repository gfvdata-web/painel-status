// Worker da Cloudflare (worker/src/index.ts) rodando no Node, sem Cloudflare: o Node 24 lê o .ts
// direto (só apaga os tipos) e o ambiente do Worker é de mentira — D1, KV e ASSETS em memória.
// Cobre rotas, validação do envio (problemaNoStatus/problemaNoHistorico), versao.json do app,
// guarda dos APKs, a agenda do Cron e o login/ordem dos cards. O tsc (scripts/check) cobre os tipos.
import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import nodeCrypto from "node:crypto";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const lerJson = (...p) => JSON.parse(fs.readFileSync(path.join(raiz, ...p), "utf-8"));
const copia = (v) => JSON.parse(JSON.stringify(v));

// Só o runtime do Workers tem crypto.subtle.timingSafeEqual; no Node usamos o equivalente.
if (!crypto.subtle.timingSafeEqual) {
  crypto.subtle.timingSafeEqual = (a, b) => nodeCrypto.timingSafeEqual(new Uint8Array(a), new Uint8Array(b));
}
const worker = await import(pathToFileURL(path.join(raiz, "worker", "src", "index.ts")).href);

const TOKEN = "segredo-de-teste";

/** D1 + KV + ASSETS em memória; só entende as consultas que o Worker faz (outra = falha o teste). */
function criarEnv({ token = TOKEN, usuario = "gfvdata", senha = "admin" } = {}) {
  const arquivos = new Map(); // chave -> {conteudo, etag, gerado_em}
  const retratos = new Map();
  const kv = new Map();
  const consultas = [];
  const BANCO = {
    prepare(sql) {
      const s = sql.replace(/\s+/g, " ").trim();
      consultas.push(s);
      const pronto = (args) => ({
        async first(coluna) {
          let linha = null;
          if (/^SELECT conteudo, etag, gerado_em FROM arquivos WHERE chave = \?/.test(s)) linha = arquivos.get(args[0]) ?? null;
          else if (/^SELECT gerado_em FROM arquivos WHERE chave = \?/.test(s)) linha = arquivos.get(args[0]) ?? null;
          else if (/^SELECT gerado_em FROM arquivos WHERE chave = 'status.json'/.test(s)) linha = arquivos.get("status.json") ?? null;
          else if (/^SELECT MAX\(gerado_em\) AS ultimo FROM retratos/.test(s)) {
            const todos = [...retratos.keys()].sort();
            linha = { ultimo: todos.length ? todos[todos.length - 1] : null };
          } else throw new Error(`consulta não prevista no teste: ${s}`);
          if (linha === null) return null;
          return coluna ? linha[coluna] : linha;
        },
        async run() {
          if (/^INSERT INTO arquivos/.test(s)) {
            const [chave, conteudo, etag, gerado_em] = args;
            arquivos.set(chave, { conteudo, etag, gerado_em });
          } else if (/^INSERT OR IGNORE INTO retratos/.test(s)) {
            if (!retratos.has(args[0])) retratos.set(args[0], args[1]);
          } else if (/^DELETE FROM retratos WHERE gerado_em < \?/.test(s)) {
            for (const k of [...retratos.keys()]) if (k < args[0]) retratos.delete(k);
          } else throw new Error(`escrita não prevista no teste: ${s}`);
          return { success: true };
        },
      });
      return { bind: (...args) => pronto(args), ...pronto([]) };
    },
  };
  const APKS = {
    async get(nome) {
      if (!kv.has(nome)) return null;
      return new Blob([kv.get(nome)]).stream(); // o KV devolve um ReadableStream
    },
    async put(nome, bytes) { kv.set(nome, new Uint8Array(bytes)); },
    async delete(nome) { kv.delete(nome); },
    async list() { return { keys: [...kv.keys()].map((name) => ({ name })) }; },
  };
  const ASSETS = { fetch: async (req) => new Response(`asset:${new URL(req.url).pathname}`, { status: 200 }) };
  return { env: { BANCO, APKS, ASSETS, TOKEN_ENVIO: token, PAINEL_USUARIO: usuario, PAINEL_SENHA: senha }, arquivos, retratos, kv, consultas };
}

const chamar = (env, caminho, { metodo = "GET", corpo, token, cabecalhos = {} } = {}) => {
  const headers = { ...cabecalhos };
  if (token !== undefined) headers.authorization = `Bearer ${token}`;
  return worker.default.fetch(
    new Request(`https://painel.teste${caminho}`, { method: metodo, body: corpo, headers }), env);
};
const enviar = (env, caminho, dados, token = TOKEN) =>
  chamar(env, caminho, { metodo: "PUT", corpo: typeof dados === "string" || dados instanceof Uint8Array ? dados : JSON.stringify(dados), token });
const json = async (resposta) => resposta.json();

const statusExemplo = lerJson("tests", "fixtures", "status_exemplo.json");
const statusPublicado = lerJson("docs", "dados", "status.json");

// ------------------------------------------------------------------------------- dados

test("envio: o status gerado pela coleta e o publicado passam na validação do Worker", async () => {
  for (const status of [statusExemplo, statusPublicado]) {
    const { env } = criarEnv();
    const r = await enviar(env, "/api/envio/status.json", status);
    assert.equal(r.status, 200, JSON.stringify(await r.clone().json()));
    assert.equal((await json(r)).gerado_em, status.meta.gerado_em);
  }
});

test("envio: os históricos gerados e publicados passam na validação", async () => {
  const { env } = criarEnv();
  const historico = lerJson("tests", "fixtures", "historico_exemplo.json");
  assert.equal((await enviar(env, `/api/envio/historico/${historico.slug}.json`, historico)).status, 200);
  const pasta = path.join(raiz, "docs", "dados", "historico");
  for (const nome of fs.readdirSync(pasta)) {
    const dados = lerJson("docs", "dados", "historico", nome);
    assert.equal((await enviar(env, `/api/envio/historico/${nome}`, dados)).status, 200, nome);
  }
});

test("envio: sem token, token errado ou Worker sem segredo = 401", async () => {
  const { env } = criarEnv();
  assert.equal((await chamar(env, "/api/envio/status.json", { metodo: "PUT", corpo: "{}" })).status, 401);
  assert.equal((await enviar(env, "/api/envio/status.json", statusExemplo, "errado")).status, 401);
  const semSegredo = criarEnv({ token: "" }).env;
  assert.equal((await enviar(semSegredo, "/api/envio/status.json", statusExemplo, "")).status, 401);
});

test("envio: método e caminho", async () => {
  const { env } = criarEnv();
  assert.equal((await chamar(env, "/api/envio/status.json", { token: TOKEN })).status, 405);
  assert.equal((await enviar(env, "/api/envio/qualquer.json", statusExemplo)).status, 404);
  assert.equal((await enviar(env, "/api/envio/historico/../x.json", statusExemplo)).status, 404);
  assert.equal((await enviar(env, "/api/envio/historico/a b.json", {})).status, 404);
});

test("envio: validação do status (422/400)", async () => {
  const { env } = criarEnv();
  const status = () => copia(statusExemplo);
  const casos = {
    "não é JSON": ["{quebrado", 400, "JSON inválido"],
    "UTF-8 inválido": [new Uint8Array([0x7b, 0xff, 0x7d]), 400, "JSON inválido"],
    "array": [[], 422, "meta.gerado_em"],
    "null": ["null", 422, "status não é um objeto"],
    "sem gerado_em": [(() => { const s = status(); delete s.meta.gerado_em; return s; })(), 422, "meta.gerado_em"],
    "gerado_em inválido": [(() => { const s = status(); s.meta.gerado_em = "ontem"; return s; })(), 422, "meta.gerado_em"],
    "sites vazio": [{ ...status(), sites: [] }, 422, "sites vazio"],
    "site sem slug": [(() => { const s = status(); delete s.sites[0].slug; return s; })(), 422, "site sem slug ou nome"],
    "site sem nome": [(() => { const s = status(); delete s.sites[1].nome; return s; })(), 422, "site sem slug ou nome"],
    "sem forms_avulsos": [(() => { const s = status(); delete s.forms_avulsos; return s; })(), 422, "forms_avulsos"],
  };
  for (const [nome, [corpo, codigo, trecho]] of Object.entries(casos)) {
    const r = await enviar(env, "/api/envio/status.json", corpo);
    assert.equal(r.status, codigo, nome);
    assert.match((await json(r)).erro, new RegExp(trecho), nome);
  }
});

test("envio: arquivo grande demais (413)", async () => {
  const { env } = criarEnv();
  const r = await enviar(env, "/api/envio/status.json", "x".repeat(1_900_001));
  assert.equal(r.status, 413);
});

test("envio: status mais antigo que o publicado não sobrescreve (409)", async () => {
  const { env, arquivos } = criarEnv();
  const novo = copia(statusExemplo);
  novo.meta.gerado_em = "2026-10-13T12:00:00+00:00";
  const velho = copia(statusExemplo);
  velho.meta.gerado_em = "2026-10-13T11:00:00+00:00";
  assert.equal((await enviar(env, "/api/envio/status.json", novo)).status, 200);
  const r = await enviar(env, "/api/envio/status.json", velho);
  assert.equal(r.status, 409);
  assert.equal(JSON.parse(arquivos.get("status.json").conteudo).meta.gerado_em, novo.meta.gerado_em);
  assert.equal((await enviar(env, "/api/envio/status.json", novo)).status, 200); // mesmo instante pode repetir
});

test("envio: retrato do status só a cada 25 min", async () => {
  const { env, retratos } = criarEnv();
  const em = (iso) => ({ ...copia(statusExemplo), meta: { ...statusExemplo.meta, gerado_em: iso } });
  await enviar(env, "/api/envio/status.json", em("2026-10-13T12:00:00+00:00"));
  await enviar(env, "/api/envio/status.json", em("2026-10-13T12:10:00+00:00"));
  assert.equal(retratos.size, 1);
  await enviar(env, "/api/envio/status.json", em("2026-10-13T12:26:00+00:00"));
  assert.equal(retratos.size, 2);
});

test("envio: histórico com slug diferente do caminho ou sem execucoes (422)", async () => {
  const { env } = criarEnv();
  const historico = lerJson("tests", "fixtures", "historico_exemplo.json");
  const r = await enviar(env, "/api/envio/historico/outro_slug.json", historico);
  assert.equal(r.status, 422);
  assert.match((await json(r)).erro, /slug do histórico/);
  assert.equal((await enviar(env, `/api/envio/historico/${historico.slug}.json`, { slug: historico.slug })).status, 422);
  assert.equal((await enviar(env, `/api/envio/historico/${historico.slug}.json`, "[]")).status, 422);
});

test("leitura: /dados serve o que o D1 guarda, com ETag e 304", async () => {
  const { env } = criarEnv();
  await enviar(env, "/api/envio/status.json", statusExemplo);
  const r = await chamar(env, "/dados/status.json");
  assert.equal(r.status, 200);
  assert.match(r.headers.get("content-type"), /application\/json/);
  assert.equal(r.headers.get("access-control-allow-origin"), "*");
  assert.equal(r.headers.get("cache-control"), "no-cache");
  assert.deepEqual(await json(r), statusExemplo);
  const etag = r.headers.get("etag");
  assert.match(etag, /^"[0-9a-f]{64}"$/);
  const condicional = await chamar(env, "/dados/status.json", { cabecalhos: { "if-none-match": etag } });
  assert.equal(condicional.status, 304);
  const head = await chamar(env, "/dados/status.json", { metodo: "HEAD" });
  assert.equal(head.status, 200);
  assert.equal(await head.text(), "");
});

test("leitura: sem cópia no D1 (ou outro caminho) cai para os arquivos estáticos", async () => {
  const { env } = criarEnv();
  assert.equal(await (await chamar(env, "/dados/status.json")).text(), "asset:/dados/status.json");
  assert.equal(await (await chamar(env, "/dados/historico/x.json")).text(), "asset:/dados/historico/x.json");
  assert.equal(await (await chamar(env, "/dados/outro.json")).text(), "asset:/dados/outro.json");
  assert.equal(await (await chamar(env, "/")).text(), "asset:/");
  assert.equal(await (await chamar(env, "/v1")).text(), "asset:/v1");
});

// ------------------------------------------------------------------------------- app Android

const UM_APK = (extra = 0) => new Uint8Array([0x50, 0x4b, 0x03, 0x04, ...new Array(10 + extra).fill(7)]);
const versao = (v = "1.2.0", build = 12) => ({
  versao: v, build, apk: `painel-status-v${v}-build${build}.apk`, notas: "n", publicado_em: "2026-10-13T12:00:00Z",
});

test("app: versao.json só entra com o APK já enviado e batendo com o nome", async () => {
  const { env } = criarEnv();
  const meta = versao();
  assert.equal((await enviar(env, "/api/app/versao.json", meta)).status, 409); // APK ainda não enviado
  assert.equal((await enviar(env, `/api/app/${meta.apk}`, UM_APK())).status, 200);
  assert.equal((await enviar(env, "/api/app/versao.json", { ...meta, build: 13 })).status, 422);
  assert.equal((await enviar(env, "/api/app/versao.json", { ...meta, versao: "9.9.9" })).status, 422);
  assert.equal((await enviar(env, "/api/app/versao.json", { ...meta, apk: "qualquer.apk" })).status, 422);
  assert.equal((await enviar(env, "/api/app/versao.json", "{quebrado")).status, 400);
  assert.equal((await enviar(env, "/api/app/versao.json", meta)).status, 200);
  const lido = await chamar(env, "/app/versao.json");
  assert.equal(lido.status, 200);
  assert.deepEqual(await json(lido), meta);
});

test("app: envio exige token e o nome do APK segue o padrão que o app lê", async () => {
  const { env } = criarEnv();
  assert.equal((await enviar(env, "/api/app/painel-status-v1.0.0-build1.apk", UM_APK(), "errado")).status, 401);
  assert.equal((await enviar(env, "/api/app/painel-status.apk", UM_APK())).status, 404);
  assert.equal((await enviar(env, "/api/app/painel-status-v1.0-build1.apk", UM_APK())).status, 404);
  assert.equal((await chamar(env, "/api/app/versao.json", { token: TOKEN })).status, 405);
});

test("app: só aceita zip (começa com PK) e limita o tamanho", async () => {
  const { env } = criarEnv();
  const nome = "painel-status-v1.0.0-build1.apk";
  assert.equal((await enviar(env, `/api/app/${nome}`, "texto qualquer")).status, 422);
  assert.equal((await enviar(env, `/api/app/${nome}`, new Uint8Array([0x50, 0x4b]))).status, 422);
  assert.equal((await enviar(env, `/api/app/${nome}`, new Uint8Array(25 * 1024 * 1024 + 1))).status, 413);
  assert.equal((await enviar(env, `/api/app/${nome}`, UM_APK())).status, 200);
});

test("app: guarda só os 3 APKs mais novos (pelo build) e serve o download", async () => {
  const { env, kv } = criarEnv();
  for (const build of [1, 2, 3, 4]) {
    const r = await enviar(env, `/api/app/painel-status-v1.0.0-build${build}.apk`, UM_APK(build));
    assert.equal(r.status, 200);
  }
  assert.deepEqual([...kv.keys()].sort(), [2, 3, 4].map((b) => `painel-status-v1.0.0-build${b}.apk`));
  const apk = await chamar(env, "/app/painel-status-v1.0.0-build4.apk");
  assert.equal(apk.status, 200);
  assert.equal(apk.headers.get("content-type"), "application/vnd.android.package-archive");
  assert.match(apk.headers.get("content-disposition"), /attachment; filename="painel-status-v1\.0\.0-build4\.apk"/);
  assert.match(apk.headers.get("cache-control"), /immutable/);
  assert.equal((await apk.arrayBuffer()).byteLength, 4 + 14);
  assert.equal((await chamar(env, "/app/painel-status-v1.0.0-build1.apk")).status, 404); // já apagado
});

test("app: /app/ só serve versao.json e APK com nome válido, só por GET/HEAD", async () => {
  const { env } = criarEnv();
  assert.equal((await chamar(env, "/app/versao.json")).status, 404); // nada publicado ainda
  assert.equal((await chamar(env, "/app/outra-coisa")).status, 404);
  assert.equal((await chamar(env, "/app/versao.json", { metodo: "POST", corpo: "{}" })).status, 404);
});

// ------------------------------------------------------------------------------- agenda (Cron)

const quando = (iso) => new Date(iso);
function agendaDe(env) {
  return JSON.parse(env.arquivos.get("controle/agenda.json").conteudo);
}

test("agenda: nas horas múltiplas de 3 (UTC), até o minuto 29, dispara por rotina", async () => {
  for (const [hora, minuto, dispara] of [[9, 0, true], [9, 29, true], [9, 30, false], [10, 0, false], [0, 0, true], [21, 0, true], [22, 0, false]]) {
    const ctx = criarEnv();
    // status publicado há 10 min: não está atrasado, só a rotina decide
    const agora = quando(`2026-10-13T${String(hora).padStart(2, "0")}:${String(minuto).padStart(2, "0")}:00Z`);
    ctx.arquivos.set("status.json", { conteudo: "{}", etag: "e", gerado_em: new Date(agora - 10 * 60000).toISOString() });
    let chamadas = 0;
    const agenda = await worker.agendar(agora, ctx.env, async () => { chamadas++; return "ok"; });
    assert.equal(chamadas, dispara ? 1 : 0, `${hora}:${minuto}`);
    assert.equal(agenda.motivo, dispara ? "rotina" : null);
    assert.equal(agendaDe(ctx).ultima_verificacao, agora.toISOString());
  }
});

test("agenda: status atrasado (> 3 h 20 min) dispara por atraso, no máximo 1 vez por hora", async () => {
  const ctx = criarEnv();
  const t0 = quando("2026-10-13T10:30:00Z");
  ctx.arquivos.set("status.json", { conteudo: "{}", etag: "e", gerado_em: "2026-10-13T06:00:00Z" });
  let chamadas = 0;
  const disparar = async () => { chamadas++; return "HTTP 500: falhou"; };
  const a1 = await worker.agendar(t0, ctx.env, disparar);
  assert.equal(a1.motivo, "atraso");
  assert.equal(a1.resultado, "HTTP 500: falhou");
  await worker.agendar(quando("2026-10-13T11:00:00Z"), ctx.env, disparar); // 30 min depois: espera
  assert.equal(chamadas, 1);
  const a3 = await worker.agendar(quando("2026-10-13T11:30:00Z"), ctx.env, disparar); // 1 h depois: de novo
  assert.equal(chamadas, 2);
  assert.equal(a3.ultimo_disparo, "2026-10-13T11:30:00.000Z");
});

test("agenda: sem nenhum status publicado conta como atrasado; status recente não dispara", async () => {
  const vazio = criarEnv();
  let chamadas = 0;
  await worker.agendar(quando("2026-10-13T10:30:00Z"), vazio.env, async () => { chamadas++; return "ok"; });
  assert.equal(chamadas, 1);
  const recente = criarEnv();
  recente.arquivos.set("status.json", { conteudo: "{}", etag: "e", gerado_em: "2026-10-13T09:00:00Z" });
  await worker.agendar(quando("2026-10-13T10:30:00Z"), recente.env, async () => { chamadas++; return "ok"; });
  assert.equal(chamadas, 1);
});

test("agenda: GET /api/agenda devolve o último registro e scheduled() apaga retratos velhos", async () => {
  const ctx = criarEnv();
  assert.deepEqual(await json(await chamar(ctx.env, "/api/agenda")), {});
  ctx.retratos.set("2026-08-01T00:00:00+00:00", "{}");
  ctx.retratos.set("2026-10-12T00:00:00+00:00", "{}");
  const agora = quando("2026-10-13T10:30:00Z");
  ctx.arquivos.set("status.json", { conteudo: "{}", etag: "e", gerado_em: "2026-10-13T10:00:00Z" });
  await worker.default.scheduled({ scheduledTime: agora.getTime() }, ctx.env);
  assert.deepEqual([...ctx.retratos.keys()], ["2026-10-12T00:00:00+00:00"]); // > 30 dias saiu
  const agenda = await json(await chamar(ctx.env, "/api/agenda"));
  assert.equal(agenda.ultima_verificacao, agora.toISOString());
  assert.equal(agenda.status_gerado_em, "2026-10-13T10:00:00Z");
});

test("agenda: sem GITHUB_TOKEN_DISPARO o disparo diz o motivo em vez de falhar", async () => {
  const ctx = criarEnv();
  const agenda = await worker.agendar(quando("2026-10-13T09:00:00Z"), ctx.env); // disparo padrão
  assert.equal(agenda.resultado, "sem GITHUB_TOKEN_DISPARO");
});

test("agenda: o disparo padrão chama o workflow certo do GitHub", async () => {
  const ctx = criarEnv();
  ctx.env.GITHUB_TOKEN_DISPARO = "gh-token";
  const pedidos = [];
  const original = globalThis.fetch;
  globalThis.fetch = async (url, opcoes) => { pedidos.push([url, opcoes]); return new Response(null, { status: 204 }); };
  try {
    const agenda = await worker.agendar(quando("2026-10-13T09:00:00Z"), ctx.env);
    assert.equal(agenda.resultado, "ok");
  } finally {
    globalThis.fetch = original;
  }
  const [url, opcoes] = pedidos[0];
  assert.equal(url, "https://api.github.com/repos/gfvdata-web/painel-status/actions/workflows/atualizar-status.yml/dispatches");
  assert.equal(opcoes.method, "POST");
  assert.equal(opcoes.headers.authorization, "Bearer gh-token");
  assert.deepEqual(JSON.parse(opcoes.body), { ref: "main" });
});

// ------------------------------------------------------------------------ login e ordem

const entrar = (env, usuario = "gfvdata", senha = "admin") =>
  chamar(env, "/api/login", { metodo: "POST", corpo: JSON.stringify({ usuario, senha }) });
const sessaoDe = (resposta) => /sessao=([^;]+)/.exec(resposta.headers.get("set-cookie"))[1];

test("login: credencial certa dá cookie + token; errada ou sem secrets não", async () => {
  const { env } = criarEnv();
  const ok = await entrar(env);
  assert.equal(ok.status, 200);
  const cookie = ok.headers.get("set-cookie");
  assert.match(cookie, /HttpOnly/);
  assert.match(cookie, /SameSite=Strict/);
  assert.equal((await json(ok)).token, sessaoDe(ok));
  assert.equal((await entrar(env, "gfvdata", "errada")).status, 401);
  assert.equal((await entrar(env, "outro", "admin")).status, 401);
  assert.equal((await chamar(env, "/api/login", { metodo: "POST", corpo: "{quebrado" })).status, 401);
  assert.equal((await chamar(env, "/api/login")).status, 405);
  assert.equal((await entrar(criarEnv({ usuario: "", senha: "" }).env)).status, 503);
});

test("sessão: /api/sessao diz se está logado; logout apaga o cookie", async () => {
  const { env } = criarEnv();
  assert.deepEqual(await json(await chamar(env, "/api/sessao")), { logado: false });
  const token = sessaoDe(await entrar(env));
  assert.deepEqual(await json(await chamar(env, "/api/sessao", { cabecalhos: { cookie: `x=1; sessao=${token}` } })), { logado: true });
  assert.deepEqual(await json(await chamar(env, "/api/sessao", { token })), { logado: true });
  const saiu = await chamar(env, "/api/logout", { metodo: "POST" });
  assert.match(saiu.headers.get("set-cookie"), /Max-Age=0/);
});

test("sessão: adulterada, expirada ou de outra senha é recusada", async () => {
  const { env } = criarEnv();
  const token = sessaoDe(await entrar(env));
  const [expira, assinatura] = token.split(".");
  const logado = async (t, e = env) => (await json(await chamar(e, "/api/sessao", { token: t }))).logado;
  assert.equal(await logado(`${Number(expira) + 1}.${assinatura}`), false);
  assert.equal(await logado(`${expira}.${assinatura.slice(0, -1)}${assinatura.endsWith("0") ? "1" : "0"}`), false);
  assert.equal(await logado("lixo"), false);
  assert.equal(await logado(token, criarEnv({ senha: "nova" }).env), false);
  const velha = Date.now() - 1000;
  const chave = await crypto.subtle.importKey("raw", new TextEncoder().encode("admin"), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const ass = Buffer.from(await crypto.subtle.sign("HMAC", chave, new TextEncoder().encode(String(velha)))).toString("hex");
  assert.equal(await logado(`${velha}.${ass}`), false);
});

test("ordem: GET público (vazio no começo), PUT exige sessão; site e app separados", async () => {
  const ctx = criarEnv();
  const { env } = ctx;
  assert.deepEqual(await json(await chamar(env, "/api/ordem?alvo=site")), { slugs: [], atualizado_em: null });
  const corpo = JSON.stringify({ slugs: ["b", "a"] });
  assert.equal((await chamar(env, "/api/ordem?alvo=site", { metodo: "PUT", corpo })).status, 401);
  assert.equal((await chamar(env, "/api/ordem?alvo=site", { metodo: "PUT", corpo, token: TOKEN })).status, 401);
  const token = sessaoDe(await entrar(env));
  const r = await chamar(env, "/api/ordem?alvo=site", { metodo: "PUT", corpo, cabecalhos: { cookie: `sessao=${token}` } });
  assert.equal(r.status, 200);
  assert.ok(ctx.arquivos.has("controle/ordem-site.json"));
  assert.equal((await chamar(env, "/api/ordem?alvo=app", { metodo: "PUT", corpo: JSON.stringify({ slugs: ["c"] }), token })).status, 200);
  assert.deepEqual((await json(await chamar(env, "/api/ordem?alvo=site"))).slugs, ["b", "a"]);
  assert.deepEqual((await json(await chamar(env, "/api/ordem?alvo=app"))).slugs, ["c"]);
  assert.equal((await chamar(env, "/api/ordem?alvo=outro")).status, 400);
  assert.equal((await chamar(env, "/api/ordem?alvo=site", { metodo: "POST", token })).status, 405);
});

test("ordem: corpo inválido = 400", async () => {
  const { env } = criarEnv();
  const token = sessaoDe(await entrar(env));
  for (const corpo of ["{quebrado", "{}", '{"slugs":"a"}', '{"slugs":["a","a"]}', '{"slugs":["a b"]}', '{"slugs":[1]}',
    JSON.stringify({ slugs: Array.from({ length: 101 }, (_, i) => `s${i}`) })]) {
    assert.equal((await chamar(env, "/api/ordem?alvo=site", { metodo: "PUT", corpo, token })).status, 400, corpo);
  }
});
