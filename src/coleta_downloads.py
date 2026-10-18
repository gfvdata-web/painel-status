"""Coleta da contagem de downloads dos sites de dados abertos.

Cada site publica um JSON público (sem token) em <site>/contagem.json, gerado pelo
contador próprio dele na Cloudflare (D1 `downloads`, sem IP; classifica cada download em
navegador, script ou robô). Formato lido:
  {site, gerado_em, desde, arquivos: [{arquivo, tipo_arquivo ("csv"|"skill"|...),
   total, por_tipo {navegador, script, robo}, serie_30d [{dia, total}]}]}
"total" já exclui robôs; serie_30d só tem os dias com download.

Devolve para o status.json o bloco "downloads" do site:
  {total, skill, csv, por_navegador, por_script, desde, serie_diaria [{data, downloads}]}
serie_diaria = soma de todos os arquivos por dia, nos últimos 30 dias contínuos
terminando hoje em Brasília (UTC-3), com zeros nos dias sem download.

Falha de rede ou JSON fora do formato não derruba a rodada: coletar() devolve None
(aviso em stderr) e publicacao.py reaproveita o "downloads" do status.json anterior.
"""

import sys
from datetime import datetime, timedelta, timezone

from .rede import ERROS_REDE, get_json

DIAS_DA_SERIE = 30
BRASILIA = timezone(timedelta(hours=-3))


def _serie(arquivos, hoje):
    por_dia = {}
    for arquivo in arquivos:
        for ponto in arquivo.get("serie_30d", []):
            por_dia[ponto["dia"]] = por_dia.get(ponto["dia"], 0) + int(ponto["total"])
    dias = [hoje - timedelta(days=n) for n in range(DIAS_DA_SERIE - 1, -1, -1)]
    return [{"data": d.isoformat(), "downloads": por_dia.get(d.isoformat(), 0)} for d in dias]


def montar_bloco(dados, agora=None):
    """Bloco "downloads" a partir do JSON do site. Levanta KeyError/TypeError/ValueError se
    o JSON não tiver o formato esperado."""
    hoje = (agora or datetime.now(timezone.utc)).astimezone(BRASILIA).date()
    arquivos = dados["arquivos"]
    if not isinstance(arquivos, list):
        raise TypeError("arquivos não é lista")
    por_tipo = {"skill": 0, "csv": 0}
    total = navegador = script = 0
    for arquivo in arquivos:
        n = int(arquivo["total"])
        total += n
        if arquivo.get("tipo_arquivo") in por_tipo:
            por_tipo[arquivo["tipo_arquivo"]] += n
        navegador += int(arquivo["por_tipo"]["navegador"])
        script += int(arquivo["por_tipo"]["script"])
    desde = dados.get("desde")
    return {
        "total": total,
        "skill": por_tipo["skill"],
        "csv": por_tipo["csv"],
        "por_navegador": navegador,
        "por_script": script,
        "desde": desde if isinstance(desde, str) else None,
        "serie_diaria": _serie(arquivos, hoje),
    }


def coletar(url):
    """Bloco "downloads" lido de `url`, ou None se a coleta falhou."""
    try:
        return montar_bloco(get_json(url))
    except ERROS_REDE as erro:
        print(f"[downloads] falha ao ler {url}: {erro}", file=sys.stderr)
    except (KeyError, TypeError, ValueError, AttributeError) as erro:
        print(f"[downloads] JSON fora do formato em {url}: {erro!r}", file=sys.stderr)
    return None
