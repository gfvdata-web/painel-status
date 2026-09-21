"""Monta docs/dados/status.json a partir da coleta de GitHub + GoatCounter.

Uso: python -m src.publicacao
"""

import json
import re
from pathlib import Path

from . import coleta_github as gh
from . import coleta_goatcounter as gc
from .config import OWNER, SITES

SAIDA = Path(__file__).resolve().parent.parent / "docs" / "dados" / "status.json"

RE_RODADA = re.compile(r"rodada\s+(\d+)", re.IGNORECASE)


def coletar_site(site, acesso_por_repo, acesso_anterior, falha_total):
    bloco = {
        "slug": site["slug"],
        "nome": site["nome"],
        "repo": site["repo"],
        "pages_url": site["pages_url"],
        "destaque": site.get("destaque", False),
    }

    bloco["ultimo_commit_dados"] = gh.ultimo_commit_no_caminho(
        OWNER, site["repo"], site["caminho_dados"]
    )

    if site.get("json_meta_url"):
        bloco["meta_publicada"] = gh.meta_json(site["json_meta_url"])

    if site.get("workflow_arquivo"):
        execucao = gh.ultima_execucao_workflow(OWNER, site["repo"], site["workflow_arquivo"])
        bloco["ultima_execucao_pipeline"] = execucao
        if site["slug"] == "bolao_f1":
            match = RE_RODADA.search(bloco["ultimo_commit_dados"].get("mensagem", ""))
            bloco["rodada_mais_recente"] = int(match.group(1)) if match else None
            bloco["em_vigilia"] = execucao.get("status") == "in_progress"

    bloco["site_no_ar"] = gh.site_no_ar(site["pages_url"])

    acesso = acesso_por_repo.get(site["repo"])
    if not acesso and falha_total:
        acesso = acesso_anterior.get(site["slug"])
    if acesso:
        bloco["acesso"] = acesso

    return bloco


def _acesso_anterior_por_slug():
    """Lê o último status.json publicado para reaproveitar 'acesso' numa falha total da coleta."""
    if not SAIDA.exists():
        return {}
    try:
        anterior = json.loads(SAIDA.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}
    return {site["slug"]: site["acesso"] for site in anterior.get("sites", []) if site.get("acesso")}


def montar():
    acesso_por_repo = gc.estatisticas_por_repo([site["repo"] for site in SITES])
    falha_total = acesso_por_repo is None
    acesso_anterior = _acesso_anterior_por_slug() if falha_total else {}
    if falha_total:
        acesso_por_repo = {}
    return {
        "meta": {
            "gerado_em": gh.agora_iso(),
            "descricao": "Status de atualização de dados e acesso dos sites gfvdata-web",
        },
        "sites": [
            coletar_site(site, acesso_por_repo, acesso_anterior, falha_total) for site in SITES
        ],
    }


def main():
    status = montar()
    SAIDA.parent.mkdir(parents=True, exist_ok=True)
    SAIDA.write_text(json.dumps(status, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"status.json gerado em {SAIDA}")


if __name__ == "__main__":
    main()
