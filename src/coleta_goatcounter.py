"""Coleta de estatísticas de acesso via API do GoatCounter.

Cada site monitorado tem, opcionalmente, um `goatcounter_code` (o subdomínio
`<code>.goatcounter.com`) e um token de API correspondente, lido do JSON na
variável de ambiente GOATCOUNTER_TOKENS (formato {"<code>": "<token>"} —
nunca fica hardcoded nem exposto no front, só o Action server-side lê).

Site sem código configurado, ou sem token disponível, simplesmente não tem
bloco de acesso no status.json — o painel mostra "sem dados de acesso ainda"
em vez de quebrar.

Referência da API: https://www.goatcounter.com/api (v0). Os campos exatos de
resposta foram conferidos contra a doc pública; se o formato mudar, as funções
abaixo devem ser ajustadas — elas nunca lançam exceção para fora, só retornam
None em caso de erro, pra uma falha de um site não derrubar os outros.
"""

import json
import os
import urllib.request
import urllib.error


def _tokens():
    bruto = os.environ.get("GOATCOUNTER_TOKENS", "")
    if not bruto.strip():
        return {}
    try:
        return json.loads(bruto)
    except json.JSONDecodeError:
        return {}


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


def estatisticas(goatcounter_code):
    """Total de pageviews/visitantes + série diária dos últimos 30 dias."""
    if not goatcounter_code:
        return None
    token = _tokens().get(goatcounter_code)
    if not token:
        return None

    base = f"https://{goatcounter_code}.goatcounter.com/api/v0"
    try:
        total = _get_json(f"{base}/stats/total", token)
        hits = _get_json(f"{base}/stats/hits?daily=true", token)
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError):
        return None

    serie_diaria = []
    for pagina in hits.get("hits", []):
        for dia in pagina.get("stats", []):
            serie_diaria.append({"data": dia.get("day"), "visitas": dia.get("daily", dia.get("count"))})

    return {
        "total_pageviews": total.get("total"),
        "total_eventos": total.get("total_events"),
        "serie_diaria": serie_diaria,
    }
