// Painel Status na Cloudflare: serve a página (docs/, arquivos estáticos) e os dados.
//
// A coleta continua em Python (src/, no GitHub Actions). O Actions envia o resultado
// para cá (PUT /api/envio/...), e o Worker guarda no R2 exatamente os bytes recebidos —
// o formato do status.json e do histórico é o de src/publicacao.py e src/historico.py,
// sem nenhuma tradução no meio. Caminhos servidos (os mesmos do GitHub Pages):
//
//   GET /dados/status.json              R2 "status.json"
//   GET /dados/historico/<slug>.json    R2 "historico/<slug>.json"
//   PUT /api/envio/status.json          grava (Authorization: Bearer TOKEN_ENVIO)
//   PUT /api/envio/historico/<slug>.json
//
// Sem cópia no R2, /dados/* cai para o arquivo estático de docs/dados/ (reserva da
// transição). Cada status recebido também vira um retrato em snapshots/AAAA-MM-DD/,
// apagado pelo R2 depois de 30 dias (regra de ciclo de vida do bucket).
//
// Agenda (Cron Trigger "*/30 * * * *", no lugar do `schedule` do Actions, que atrasa e
// é desligado após 60 dias sem atividade): nas horas múltiplas de 3 (UTC), no minuto 0,
// dispara o workflow atualizar-status.yml pela API do GitHub. Nas outras meias horas é
// vigia: se o status publicado passou de ATRASO_MAXIMO_MS, dispara de novo (no máximo
// uma vez a cada INTERVALO_REDISPARO_MS). O último disparo fica em R2
// "controle/agenda.json", legível em GET /api/agenda.
//
// App Android (o repositório é privado: o APK não sai mais do GitHub Releases). O job
// "publicar" do android.yml manda o APK assinado e depois o versao.json, com o mesmo token:
//
//   GET /app/versao.json                     R2 "app/versao.json" — o app consulta para o aviso de versão
//   GET /app/painel-status-vX.Y.Z-buildN.apk R2 "app/<nome>"
//   PUT /api/app/<nome do apk>               grava o APK
//   PUT /api/app/versao.json                 só aceita se o APK citado já estiver no R2

const RE_HISTORICO = /^historico\/([A-Za-z0-9_-]+)\.json$/;
const PREFIXO_DADOS = "/dados/";
const PREFIXO_ENVIO = "/api/envio/";
const LIMITE_BYTES = 5 * 1024 * 1024;

const PREFIXO_APP = "/app/";
const PREFIXO_ENVIO_APP = "/api/app/";
const VERSAO = "versao.json";
/** Mesmo padrão que o app lê (VerificadorDeAtualizacao) e o android.yml gera. */
const RE_APK = /^painel-status-v(\d+\.\d+\.\d+)-build(\d+)\.apk$/;
const LIMITE_APK = 50 * 1024 * 1024;
const TIPO_APK = "application/vnd.android.package-archive";

const WORKFLOW_DISPATCH =
  "https://api.github.com/repos/gfvdata-web/painel-status/actions/workflows/atualizar-status.yml/dispatches";
const HORAS_ENTRE_RODADAS = 3;
const MINUTO = 60 * 1000;
const ATRASO_MAXIMO_MS = (HORAS_ENTRE_RODADAS * 60 + 20) * MINUTO;
const INTERVALO_REDISPARO_MS = 60 * MINUTO;
const CHAVE_AGENDA = "controle/agenda.json";

export default {
  async scheduled(controller, env): Promise<void> {
    await agendar(new Date(controller.scheduledTime), env);
  },

  async fetch(request, env): Promise<Response> {
    const { pathname } = new URL(request.url);
    if (pathname === "/api/agenda") {
      const agenda = await env.DADOS.get(CHAVE_AGENDA);
      return new Response(agenda ? agenda.body : "{}", { headers: cabecalhosJson() });
    }
    if (pathname.startsWith(PREFIXO_DADOS)) {
      const chave = chaveDoArquivo(pathname.slice(PREFIXO_DADOS.length));
      if (chave === null || !["GET", "HEAD"].includes(request.method)) {
        return env.ASSETS.fetch(request);
      }
      return servir(request, env, chave);
    }
    if (pathname.startsWith(PREFIXO_APP)) {
      const nome = pathname.slice(PREFIXO_APP.length);
      if (!(nome === VERSAO || RE_APK.test(nome)) || !["GET", "HEAD"].includes(request.method)) {
        return erro(404, "não encontrado");
      }
      return servirDoApp(request, env, nome);
    }
    if (pathname.startsWith(PREFIXO_ENVIO_APP)) {
      const nome = pathname.slice(PREFIXO_ENVIO_APP.length);
      if (!(nome === VERSAO || RE_APK.test(nome))) return erro(404, "caminho desconhecido");
      if (request.method !== "PUT") return erro(405, "use PUT");
      if (!(await autorizado(request, env))) return erro(401, "token inválido");
      return nome === VERSAO ? receberVersao(request, env) : receberApk(request, env, nome);
    }
    if (pathname.startsWith(PREFIXO_ENVIO)) {
      const chave = chaveDoArquivo(pathname.slice(PREFIXO_ENVIO.length));
      if (chave === null) return erro(404, "caminho desconhecido");
      if (request.method !== "PUT") return erro(405, "use PUT");
      if (!(await autorizado(request, env))) return erro(401, "token inválido");
      return receber(request, env, chave);
    }
    return env.ASSETS.fetch(request);
  },
} satisfies ExportedHandler<Env>;

/** "status.json" ou "historico/<slug>.json" (o caminho já sem o prefixo); null fora disso. */
function chaveDoArquivo(resto: string): string | null {
  if (resto === "status.json" || RE_HISTORICO.test(resto)) return resto;
  return null;
}

function cabecalhosJson(): Headers {
  return new Headers({
    "content-type": "application/json; charset=utf-8",
    // Pode guardar, mas confere sempre (ETag): a recarga de 1 min precisa do mais novo.
    "cache-control": "no-cache",
    // Igual ao GitHub Pages: dado público, legível de qualquer origem.
    "access-control-allow-origin": "*",
  });
}

async function servir(request: Request, env: Env, chave: string): Promise<Response> {
  const objeto = await env.DADOS.get(chave, { onlyIf: request.headers });
  if (objeto === null) return env.ASSETS.fetch(request);
  const headers = cabecalhosJson();
  headers.set("etag", objeto.httpEtag);
  if (!("body" in objeto)) return new Response(null, { status: 304, headers });
  return new Response(request.method === "HEAD" ? null : objeto.body, { headers });
}

async function servirDoApp(request: Request, env: Env, nome: string): Promise<Response> {
  const objeto = await env.DADOS.get(`app/${nome}`, { onlyIf: request.headers });
  if (objeto === null) return erro(404, "não encontrado");
  const headers = nome === VERSAO ? cabecalhosJson() : cabecalhosApk(nome);
  headers.set("etag", objeto.httpEtag);
  if (!("body" in objeto)) return new Response(null, { status: 304, headers });
  return new Response(request.method === "HEAD" ? null : objeto.body, { headers });
}

function cabecalhosApk(nome: string): Headers {
  return new Headers({
    "content-type": TIPO_APK,
    "content-disposition": `attachment; filename="${nome}"`,
    // O nome traz versão e build: um arquivo nunca muda depois de publicado.
    "cache-control": "public, max-age=31536000, immutable",
  });
}

async function receberApk(request: Request, env: Env, nome: string): Promise<Response> {
  const corpo = await request.arrayBuffer();
  if (corpo.byteLength < 4) return erro(422, "não é um APK");
  if (corpo.byteLength > LIMITE_APK) return erro(413, "APK grande demais");
  // Todo APK é um zip: começa com os bytes 50 4B 03 04 ("PK").
  const assinatura = new Uint8Array(corpo, 0, 4);
  if (assinatura[0] !== 0x50 || assinatura[1] !== 0x4b || assinatura[2] !== 0x03 || assinatura[3] !== 0x04) {
    return erro(422, "não é um APK");
  }
  await env.DADOS.put(`app/${nome}`, corpo, { httpMetadata: { contentType: TIPO_APK } });
  return Response.json({ ok: true, chave: `app/${nome}`, bytes: corpo.byteLength });
}

interface Versao {
  versao: string;
  build: number;
  apk: string;
  notas: string;
  publicado_em: string;
}

/** versao.json: versao e build têm de bater com o nome do APK, e o APK já tem de estar no R2. */
async function receberVersao(request: Request, env: Env): Promise<Response> {
  const texto = await request.text();
  if (texto.length > 64 * 1024) return erro(413, "versao.json grande demais");
  let dados: Partial<Versao>;
  try {
    dados = JSON.parse(texto);
  } catch {
    return erro(400, "JSON inválido");
  }
  const achado = typeof dados?.apk === "string" ? RE_APK.exec(dados.apk) : null;
  if (!achado) return erro(422, "apk ausente ou fora do padrão");
  if (dados.versao !== achado[1] || dados.build !== Number(achado[2])) {
    return erro(422, "versao/build não batem com o nome do APK");
  }
  if (!(await env.DADOS.head(`app/${dados.apk}`))) return erro(409, `o APK ${dados.apk} ainda não foi enviado`);
  await env.DADOS.put(`app/${VERSAO}`, texto, { httpMetadata: { contentType: "application/json; charset=utf-8" } });
  return Response.json({ ok: true, chave: `app/${VERSAO}`, apk: dados.apk });
}

async function autorizado(request: Request, env: Env): Promise<boolean> {
  const recebido = request.headers.get("authorization") ?? "";
  if (!env.TOKEN_ENVIO) return false;
  // Compara os hashes em tempo constante (os dois têm o mesmo tamanho).
  const codificar = (texto: string) => crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto));
  const [a, b] = await Promise.all([codificar(recebido), codificar(`Bearer ${env.TOKEN_ENVIO}`)]);
  return crypto.subtle.timingSafeEqual(a, b);
}

async function receber(request: Request, env: Env, chave: string): Promise<Response> {
  const texto = await request.text();
  if (texto.length > LIMITE_BYTES) return erro(413, "arquivo grande demais");
  let dados: unknown;
  try {
    dados = JSON.parse(texto);
  } catch {
    return erro(400, "JSON inválido");
  }
  const httpMetadata = { contentType: "application/json; charset=utf-8" };

  if (chave === "status.json") {
    const problema = problemaNoStatus(dados);
    if (problema) return erro(422, problema);
    const geradoEm = (dados as Status).meta.gerado_em;
    // Nunca volta no tempo: um envio atrasado não sobrescreve um status mais novo.
    const atual = await env.DADOS.head(chave);
    const geradoAtual = atual?.customMetadata?.gerado_em;
    if (geradoAtual && Date.parse(geradoEm) < Date.parse(geradoAtual)) {
      return erro(409, `status mais antigo (${geradoEm}) que o publicado (${geradoAtual})`);
    }
    const customMetadata = { gerado_em: geradoEm };
    await env.DADOS.put(chave, texto, { httpMetadata, customMetadata });
    await env.DADOS.put(chaveDoRetrato(geradoEm), texto, { httpMetadata, customMetadata });
    return Response.json({ ok: true, chave, gerado_em: geradoEm });
  }

  const slug = RE_HISTORICO.exec(chave)![1];
  const problema = problemaNoHistorico(dados, slug);
  if (problema) return erro(422, problema);
  await env.DADOS.put(chave, texto, { httpMetadata });
  return Response.json({ ok: true, chave });
}

interface Status {
  meta: { gerado_em: string };
  sites: { slug: string; nome: string }[];
  forms_avulsos: unknown[];
}

/** O mínimo que a página e o app precisam para desenhar; texto do problema, ou null. */
function problemaNoStatus(dados: unknown): string | null {
  const status = dados as Partial<Status> | null;
  if (typeof status !== "object" || status === null) return "status não é um objeto";
  if (typeof status.meta?.gerado_em !== "string" || Number.isNaN(Date.parse(status.meta.gerado_em))) {
    return "meta.gerado_em ausente ou inválido";
  }
  if (!Array.isArray(status.sites) || status.sites.length === 0) return "sites vazio";
  if (!status.sites.every((s) => typeof s?.slug === "string" && typeof s?.nome === "string")) {
    return "site sem slug ou nome";
  }
  if (!Array.isArray(status.forms_avulsos)) return "forms_avulsos ausente";
  return null;
}

function problemaNoHistorico(dados: unknown, slug: string): string | null {
  const historico = dados as { slug?: unknown; execucoes?: unknown } | null;
  if (typeof historico !== "object" || historico === null) return "histórico não é um objeto";
  if (historico.slug !== slug) return "slug do histórico não bate com o caminho";
  if (!Array.isArray(historico.execucoes)) return "execucoes ausente";
  return null;
}

/** snapshots/2026-10-12/16-56-10.json (UTC) */
function chaveDoRetrato(geradoEm: string): string {
  const [dia, hora] = new Date(geradoEm).toISOString().split("T");
  return `snapshots/${dia}/${hora.slice(0, 8).replaceAll(":", "-")}.json`;
}

interface Agenda {
  ultimo_disparo: string | null; // ISO; o último disparo tentado
  motivo: "rotina" | "atraso" | null;
  resultado: string | null; // "ok" ou a descrição da falha
  ultima_verificacao: string; // ISO; a última execução do Cron
  status_gerado_em: string | null; // o que estava publicado na última verificação
}

/** Decide, a cada meia hora, se dispara a coleta; grava o que fez em CHAVE_AGENDA. */
export async function agendar(agora: Date, env: Env, disparar = dispararWorkflow): Promise<Agenda> {
  const [cabecalho, anterior] = await Promise.all([
    env.DADOS.head("status.json"),
    env.DADOS.get(CHAVE_AGENDA).then((o) => (o ? (o.json() as Promise<Agenda>) : null)),
  ]);
  const geradoEm = cabecalho?.customMetadata?.gerado_em ?? null;
  const agenda: Agenda = {
    ultimo_disparo: anterior?.ultimo_disparo ?? null,
    motivo: anterior?.motivo ?? null,
    resultado: anterior?.resultado ?? null,
    ultima_verificacao: agora.toISOString(),
    status_gerado_em: geradoEm,
  };

  const rotina = agora.getUTCMinutes() < 30 && agora.getUTCHours() % HORAS_ENTRE_RODADAS === 0;
  const atrasado = geradoEm === null || agora.getTime() - Date.parse(geradoEm) > ATRASO_MAXIMO_MS;
  const disparouHaPouco =
    agenda.ultimo_disparo !== null && agora.getTime() - Date.parse(agenda.ultimo_disparo) < INTERVALO_REDISPARO_MS;
  const motivo = rotina ? "rotina" : atrasado && !disparouHaPouco ? "atraso" : null;

  if (motivo) {
    agenda.ultimo_disparo = agora.toISOString();
    agenda.motivo = motivo;
    agenda.resultado = await disparar(env);
    console.log(`disparo (${motivo}): ${agenda.resultado}`);
  }
  await env.DADOS.put(CHAVE_AGENDA, JSON.stringify(agenda, null, 1), {
    httpMetadata: { contentType: "application/json; charset=utf-8" },
  });
  return agenda;
}

/** workflow_dispatch do atualizar-status.yml; "ok" ou a descrição da falha. */
async function dispararWorkflow(env: Env): Promise<string> {
  if (!env.GITHUB_TOKEN_DISPARO) return "sem GITHUB_TOKEN_DISPARO";
  try {
    const resposta = await fetch(WORKFLOW_DISPATCH, {
      method: "POST",
      headers: {
        authorization: `Bearer ${env.GITHUB_TOKEN_DISPARO}`,
        accept: "application/vnd.github+json",
        "x-github-api-version": "2022-11-28",
        "user-agent": "painel-status-worker",
      },
      body: JSON.stringify({ ref: "main" }),
    });
    if (resposta.ok) return "ok";
    return `HTTP ${resposta.status}: ${(await resposta.text()).slice(0, 200)}`;
  } catch (falha) {
    return `falha de rede: ${falha}`;
  }
}

function erro(status: number, mensagem: string): Response {
  return Response.json({ ok: false, erro: mensagem }, { status });
}
