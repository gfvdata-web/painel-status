// Painel Status na Cloudflare: serve a página (docs/, arquivos estáticos), os dados e o APK.
//
// A coleta continua em Python (src/, no GitHub Actions). O Actions envia o resultado
// para cá (PUT /api/envio/...), e o Worker guarda no D1 exatamente o texto recebido —
// o formato do status.json e do histórico é o de src/publicacao.py e src/historico.py,
// sem nenhuma tradução no meio. Caminhos servidos (os mesmos do GitHub Pages):
//
//   GET /dados/status.json              D1 arquivos["status.json"]
//   GET /dados/historico/<slug>.json    D1 arquivos["historico/<slug>.json"]
//   PUT /api/envio/status.json          grava (Authorization: Bearer TOKEN_ENVIO)
//   PUT /api/envio/historico/<slug>.json
//
// Sem cópia no D1, /dados/* cai para o arquivo estático de docs/dados/ (reserva da
// transição). Cada status recebido também vira um retrato (tabela retratos, no máximo
// 1 a cada INTERVALO_RETRATO_MS), apagado pelo Cron depois de RETENCAO_RETRATOS_MS.
//
// Custo: só D1, KV e Workers do plano grátis, que dão erro no limite diário em vez de
// cobrar (o R2 cobraria o excedente). Ver a seção "Cloudflare" do README.md.
//
// Agenda (Cron Trigger "*/30 * * * *", no lugar do `schedule` do Actions, que atrasa e
// é desligado após 60 dias sem atividade): nas horas múltiplas de 3 (UTC), no minuto 0,
// dispara o workflow atualizar-status.yml pela API do GitHub. Nas outras meias horas é
// vigia: se o status publicado passou de ATRASO_MAXIMO_MS, dispara de novo (no máximo
// uma vez a cada INTERVALO_REDISPARO_MS). O último disparo fica em
// arquivos["controle/agenda.json"], legível em GET /api/agenda.
//
// App Android (o repositório é privado: o APK não sai mais do GitHub Releases). O job
// "publicar" do android.yml manda o APK assinado e depois o versao.json, com o mesmo token:
//
//   GET /app/versao.json                     D1 arquivos["app/versao.json"] — o aviso de versão do app
//   GET /app/painel-status-vX.Y.Z-buildN.apk KV APKS[<nome>] (ficam só os APKS_GUARDADOS mais novos)
//   PUT /api/app/<nome do apk>               grava o APK
//   PUT /api/app/versao.json                 só aceita se o APK citado já estiver no KV

const RE_HISTORICO = /^historico\/([A-Za-z0-9_-]+)\.json$/;
const PREFIXO_DADOS = "/dados/";
const PREFIXO_ENVIO = "/api/envio/";
/** O D1 guarda até 2 MB por linha; o maior arquivo hoje tem ~70 KB. */
const LIMITE_BYTES = 1_900_000;

const PREFIXO_APP = "/app/";
const PREFIXO_ENVIO_APP = "/api/app/";
const VERSAO = "versao.json";
/** Mesmo padrão que o app lê (VerificadorDeAtualizacao) e o android.yml gera. */
const RE_APK = /^painel-status-v(\d+\.\d+\.\d+)-build(\d+)\.apk$/;
/** Limite de valor do KV. */
const LIMITE_APK = 25 * 1024 * 1024;
const TIPO_APK = "application/vnd.android.package-archive";
const APKS_GUARDADOS = 3;

const WORKFLOW_DISPATCH =
  "https://api.github.com/repos/gfvdata-web/painel-status/actions/workflows/atualizar-status.yml/dispatches";
const HORAS_ENTRE_RODADAS = 3;
const MINUTO = 60 * 1000;
const DIA = 24 * 60 * MINUTO;
const ATRASO_MAXIMO_MS = (HORAS_ENTRE_RODADAS * 60 + 20) * MINUTO;
const INTERVALO_REDISPARO_MS = 60 * MINUTO;
const CHAVE_AGENDA = "controle/agenda.json";
const INTERVALO_RETRATO_MS = 25 * MINUTO;
const RETENCAO_RETRATOS_MS = 30 * DIA;

export default {
  async scheduled(controller, env): Promise<void> {
    const agora = new Date(controller.scheduledTime);
    await agendar(agora, env);
    await env.BANCO.prepare("DELETE FROM retratos WHERE gerado_em < ?")
      .bind(new Date(agora.getTime() - RETENCAO_RETRATOS_MS).toISOString())
      .run();
  },

  async fetch(request, env): Promise<Response> {
    const { pathname } = new URL(request.url);
    if (pathname === "/api/agenda") {
      const agenda = await lerArquivo(env, CHAVE_AGENDA);
      return new Response(agenda?.conteudo ?? "{}", { headers: cabecalhosJson() });
    }
    if (pathname.startsWith(PREFIXO_DADOS)) {
      const chave = chaveDoArquivo(pathname.slice(PREFIXO_DADOS.length));
      if (chave === null || !["GET", "HEAD"].includes(request.method)) {
        return env.ASSETS.fetch(request);
      }
      const arquivo = await lerArquivo(env, chave);
      return arquivo ? servirArquivo(request, arquivo) : env.ASSETS.fetch(request);
    }
    if (pathname.startsWith(PREFIXO_APP)) {
      const nome = pathname.slice(PREFIXO_APP.length);
      if (!(nome === VERSAO || RE_APK.test(nome)) || !["GET", "HEAD"].includes(request.method)) {
        return erro(404, "não encontrado");
      }
      if (nome === VERSAO) {
        const arquivo = await lerArquivo(env, `app/${VERSAO}`);
        return arquivo ? servirArquivo(request, arquivo) : erro(404, "não encontrado");
      }
      return servirApk(request, env, nome);
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

// --- Armazenamento (D1) -------------------------------------------------------------

interface Arquivo {
  conteudo: string;
  etag: string;
  gerado_em: string | null;
}

function lerArquivo(env: Env, chave: string): Promise<Arquivo | null> {
  return env.BANCO.prepare("SELECT conteudo, etag, gerado_em FROM arquivos WHERE chave = ?")
    .bind(chave)
    .first<Arquivo>();
}

async function gravarArquivo(env: Env, chave: string, conteudo: string, geradoEm: string | null = null) {
  await env.BANCO.prepare(
    `INSERT INTO arquivos (chave, conteudo, etag, gerado_em, atualizado_em) VALUES (?, ?, ?, ?, ?)
     ON CONFLICT (chave) DO UPDATE SET conteudo = excluded.conteudo, etag = excluded.etag,
       gerado_em = excluded.gerado_em, atualizado_em = excluded.atualizado_em`,
  )
    .bind(chave, conteudo, await sha256(conteudo), geradoEm, new Date().toISOString())
    .run();
}

async function sha256(texto: string): Promise<string> {
  const resumo = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto));
  return [...new Uint8Array(resumo)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// --- Leitura -------------------------------------------------------------------------

function cabecalhosJson(): Headers {
  return new Headers({
    "content-type": "application/json; charset=utf-8",
    // Pode guardar, mas confere sempre (ETag): a recarga de 1 min precisa do mais novo.
    "cache-control": "no-cache",
    // Igual ao GitHub Pages: dado público, legível de qualquer origem.
    "access-control-allow-origin": "*",
  });
}

function servirArquivo(request: Request, arquivo: Arquivo): Response {
  const headers = cabecalhosJson();
  const etag = `"${arquivo.etag}"`;
  headers.set("etag", etag);
  if (request.headers.get("if-none-match") === etag) return new Response(null, { status: 304, headers });
  return new Response(request.method === "HEAD" ? null : arquivo.conteudo, { headers });
}

async function servirApk(request: Request, env: Env, nome: string): Promise<Response> {
  const apk = await env.APKS.get(nome, "stream");
  if (apk === null) return erro(404, "não encontrado");
  const headers = new Headers({
    "content-type": TIPO_APK,
    "content-disposition": `attachment; filename="${nome}"`,
    // O nome traz versão e build: um arquivo nunca muda depois de publicado.
    "cache-control": "public, max-age=31536000, immutable",
  });
  if (request.method === "HEAD") {
    await apk.cancel();
    return new Response(null, { headers });
  }
  return new Response(apk, { headers });
}

// --- Envio ---------------------------------------------------------------------------

async function autorizado(request: Request, env: Env): Promise<boolean> {
  const recebido = request.headers.get("authorization") ?? "";
  if (!env.TOKEN_ENVIO) return false;
  // Compara os hashes em tempo constante (os dois têm o mesmo tamanho).
  const codificar = (texto: string) => crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto));
  const [a, b] = await Promise.all([codificar(recebido), codificar(`Bearer ${env.TOKEN_ENVIO}`)]);
  return crypto.subtle.timingSafeEqual(a, b);
}

async function receber(request: Request, env: Env, chave: string): Promise<Response> {
  const corpo = await request.arrayBuffer();
  if (corpo.byteLength > LIMITE_BYTES) return erro(413, "arquivo grande demais");
  let texto: string;
  let dados: unknown;
  try {
    texto = new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(corpo);
    dados = JSON.parse(texto);
  } catch {
    return erro(400, "JSON inválido");
  }

  if (chave === "status.json") {
    const problema = problemaNoStatus(dados);
    if (problema) return erro(422, problema);
    const geradoEm = (dados as Status).meta.gerado_em;
    // Nunca volta no tempo: um envio atrasado não sobrescreve um status mais novo.
    const atual = await env.BANCO.prepare("SELECT gerado_em FROM arquivos WHERE chave = ?")
      .bind(chave)
      .first<string>("gerado_em");
    if (atual && Date.parse(geradoEm) < Date.parse(atual)) {
      return erro(409, `status mais antigo (${geradoEm}) que o publicado (${atual})`);
    }
    await gravarArquivo(env, chave, texto, geradoEm);
    await guardarRetrato(env, geradoEm, texto);
    return Response.json({ ok: true, chave, gerado_em: geradoEm });
  }

  const slug = RE_HISTORICO.exec(chave)![1];
  const problema = problemaNoHistorico(dados, slug);
  if (problema) return erro(422, problema);
  await gravarArquivo(env, chave, texto);
  return Response.json({ ok: true, chave });
}

/** Grava o retrato se o último tiver mais de INTERVALO_RETRATO_MS (a vigília do Bolão
 * manda status a cada minuto; um retrato por envio encheria o banco à toa). */
async function guardarRetrato(env: Env, geradoEm: string, texto: string) {
  const ultimo = await env.BANCO.prepare("SELECT MAX(gerado_em) AS ultimo FROM retratos").first<string>("ultimo");
  if (ultimo && Date.parse(geradoEm) - Date.parse(ultimo) < INTERVALO_RETRATO_MS) return;
  await env.BANCO.prepare("INSERT OR IGNORE INTO retratos (gerado_em, conteudo) VALUES (?, ?)")
    .bind(geradoEm, texto)
    .run();
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

async function receberApk(request: Request, env: Env, nome: string): Promise<Response> {
  const corpo = await request.arrayBuffer();
  if (corpo.byteLength > LIMITE_APK) return erro(413, "APK grande demais");
  // Todo APK é um zip: começa com os bytes 50 4B 03 04 ("PK").
  const inicio = new Uint8Array(corpo.slice(0, 4));
  if (inicio.length < 4 || inicio[0] !== 0x50 || inicio[1] !== 0x4b || inicio[2] !== 0x03 || inicio[3] !== 0x04) {
    return erro(422, "não é um APK");
  }
  await env.APKS.put(nome, corpo);
  // Guarda só os mais novos (pelo build): o KV grátis tem 1 GB.
  const { keys } = await env.APKS.list();
  const antigos = keys
    .map((k) => ({ nome: k.name, build: Number(RE_APK.exec(k.name)?.[2] ?? -1) }))
    .sort((a, b) => b.build - a.build)
    .slice(APKS_GUARDADOS);
  await Promise.all(antigos.map((a) => env.APKS.delete(a.nome)));
  return Response.json({ ok: true, apk: nome, bytes: corpo.byteLength, apagados: antigos.map((a) => a.nome) });
}

interface Versao {
  versao: string;
  build: number;
  apk: string;
  notas: string;
  publicado_em: string;
}

/** versao.json: versao e build têm de bater com o nome do APK, e o APK já tem de estar no KV. */
async function receberVersao(request: Request, env: Env): Promise<Response> {
  const texto = await request.text();
  if (texto.length > 64 * 1024) return erro(413, "versao.json grande demais");
  let dados: Partial<Versao>;
  try {
    dados = JSON.parse(texto);
  } catch {
    return erro(400, "JSON inválido");
  }
  const nomeDoApk = typeof dados?.apk === "string" ? dados.apk : "";
  const achado = RE_APK.exec(nomeDoApk);
  if (!achado) return erro(422, "apk ausente ou fora do padrão");
  if (dados.versao !== achado[1] || dados.build !== Number(achado[2])) {
    return erro(422, "versao/build não batem com o nome do APK");
  }
  // Leitura direta (a listagem do KV pode demorar a mostrar uma chave recém-gravada).
  const apk = await env.APKS.get(nomeDoApk, "stream");
  if (apk === null) return erro(409, `o APK ${nomeDoApk} ainda não foi enviado`);
  await apk.cancel();
  await gravarArquivo(env, `app/${VERSAO}`, texto);
  return Response.json({ ok: true, chave: `app/${VERSAO}`, apk: nomeDoApk });
}

// --- Agenda --------------------------------------------------------------------------

interface Agenda {
  ultimo_disparo: string | null; // ISO; o último disparo tentado
  motivo: "rotina" | "atraso" | null;
  resultado: string | null; // "ok" ou a descrição da falha
  ultima_verificacao: string; // ISO; a última execução do Cron
  status_gerado_em: string | null; // o que estava publicado na última verificação
}

/** Decide, a cada meia hora, se dispara a coleta; grava o que fez em CHAVE_AGENDA. */
export async function agendar(agora: Date, env: Env, disparar = dispararWorkflow): Promise<Agenda> {
  const [status, anterior] = await Promise.all([
    env.BANCO.prepare("SELECT gerado_em FROM arquivos WHERE chave = 'status.json'").first<string>("gerado_em"),
    lerArquivo(env, CHAVE_AGENDA).then((a) => (a ? (JSON.parse(a.conteudo) as Agenda) : null)),
  ]);
  const geradoEm = status ?? null;
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
  await gravarArquivo(env, CHAVE_AGENDA, JSON.stringify(agenda, null, 1));
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
