"""Coleta de estatísticas de acesso via API do GoatCounter.

Um único site GoatCounter (`gfvdata`) recebe o rastreio de todos os sites
monitorados — todos vivem sob o mesmo domínio `gfvdata-web.github.io/<repo>/`,
então o caminho registrado já vem prefixado com o nome do repositório sem
precisar de nenhum JavaScript especial no snippet. A separação por site aqui
é feita filtrando os caminhos por esse prefixo.

Token de API lido de GOATCOUNTER_TOKEN (variável de ambiente, nunca hardcoded
nem exposto no front — só o Action server-side lê). Sem token configurado,
todo site fica sem bloco de acesso no status.json em vez de quebrar o resto.

Uma falha na chamada de /paths (rede instável, GoatCounter fora do ar,
token expirado) é diferente de "site nunca teve visita": nesse caso
estatisticas_por_repo devolve None (em vez de um dict por repo), e é
publicacao.py quem decide reaproveitar o último status.json publicado em
vez de apagar os números da rodada anterior. Erros vão para stderr para
aparecerem no log do Action — antes eram engolidos em silêncio.

Referência da API: https://www.goatcounter.com/help/api — endpoints usados:
GET /api/v0/paths (lista de caminhos conhecidos, para achar os IDs de cada
site) e GET /api/v0/stats/total?include_paths=... (total + série diária já
filtrados pelos IDs de caminho do site).
"""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

GOATCOUNTER_CODE = "gfvdata"
INICIO_HISTORICO = "2026-01-01T00:00:00Z"  # antes de qualquer site ter tracking


def _token():
    return os.environ.get("GOATCOUNTER_TOKEN", "").strip() or None


def _get_json(url, token):
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            "User-Agent": "painel-status",
        },
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _todos_os_caminhos(base, token):
    """Pagina /api/v0/paths e devolve a lista completa de {id, path}."""
    caminhos = []
    apos = None
    while True:
        params = {"Limit": 200}
        if apos is not None:
            params["After"] = apos
        pagina = _get_json(f"{base}/paths?{urllib.parse.urlencode(params)}", token)
        caminhos.extend(pagina.get("paths", []))
        if not pagina.get("more") or not pagina.get("paths"):
            break
        apos = pagina["paths"][-1]["id"]
    return caminhos


def _ids_do_site(caminhos, repo):
    prefixo = f"/{repo}/"
    exato = f"/{repo}"
    return [c["id"] for c in caminhos if c["path"] == exato or c["path"].startswith(prefixo)]


def estatisticas_por_repo(repos):
    """Para uma lista de nomes de repositório, devolve {repo: {total, serie_diaria} | None}.

    Devolve None (não um dict) quando a chamada de /paths falhou por completo —
    sinal para o chamador de que isto é uma falha transitória, não "sem dado".
    """
    token = _token()
    if not token:
        return {repo: None for repo in repos}

    base = f"https://{GOATCOUNTER_CODE}.goatcounter.com/api/v0"
    try:
        caminhos = _todos_os_caminhos(base, token)
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as erro:
        print(f"[goatcounter] falha ao buscar /paths: {erro}", file=sys.stderr)
        return None

    resultado = {}
    fim = datetime.now(timezone.utc).isoformat()
    for repo in repos:
        ids = _ids_do_site(caminhos, repo)
        if not ids:
            resultado[repo] = None
            continue
        query = urllib.parse.urlencode(
            {"start": INICIO_HISTORICO, "end": fim, "include_paths": ids}, doseq=True
        )
        try:
            dados = _get_json(f"{base}/stats/total?{query}", token)
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as erro:
            print(f"[goatcounter] falha ao buscar stats de {repo}: {erro}", file=sys.stderr)
            resultado[repo] = None
            continue
        serie_completa = [
            {"data": dia.get("day"), "visitas": dia.get("daily", 0)}
            for dia in dados.get("stats", [])
        ]
        corte = datetime.now(timezone.utc) - timedelta(days=30)
        serie_30d = [d for d in serie_completa if d["data"] and d["data"] >= corte.strftime("%Y-%m-%d")]
        resultado[repo] = {
            "total_pageviews": dados.get("total"),
            "serie_diaria": serie_30d,
        }
    return resultado
