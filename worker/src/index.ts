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

const RE_HISTORICO = /^historico\/([A-Za-z0-9_-]+)\.json$/;
const PREFIXO_DADOS = "/dados/";
const PREFIXO_ENVIO = "/api/envio/";
const LIMITE_BYTES = 5 * 1024 * 1024;

export default {
  async fetch(request, env): Promise<Response> {
    const { pathname } = new URL(request.url);
    if (pathname.startsWith(PREFIXO_DADOS)) {
      const chave = chaveDoArquivo(pathname.slice(PREFIXO_DADOS.length));
      if (chave === null || !["GET", "HEAD"].includes(request.method)) {
        return env.ASSETS.fetch(request);
      }
      return servir(request, env, chave);
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

function erro(status: number, mensagem: string): Response {
  return Response.json({ ok: false, erro: mensagem }, { status });
}
