"""Monta docs/dados/status.json a partir da coleta de GitHub + GoatCounter.

Uso: python -m src.publicacao

Formato de saída (o único arquivo que docs/js/app.js lê):
  meta           {gerado_em, descricao}
  sites[]        slug, nome, repo, pages_url, destaque, ultimo_commit_dados,
                 site_no_ar e, conforme o cadastro em config.py: form_url,
                 form_rotulo,
                 periodo_publicado, ultima_execucao_pipeline, acesso e — só no
                 site com pipeline_bolao — rodada_mais_recente, em_vigilia,
                 etapas_pipeline; nos demais, detalhes e alertas (pop-up do card,
                 formato em detalhes_site.py)
  forms_avulsos  [{nome, url}] copiado de config.FORMS_AVULSOS
"""

import json
import re
from pathlib import Path

from . import coleta_github as gh
from . import coleta_goatcounter as gc
from .detalhes_site import montar_detalhes
from .etapas_bolao import montar_etapas
from .config import FORMS_AVULSOS, OWNER, SITES, validar

SAIDA = Path(__file__).resolve().parent.parent / "docs" / "dados" / "status.json"

RE_RODADA = re.compile(r"rodada\s+(\d+)", re.IGNORECASE)


def coletar_site(site, acesso_por_repo, acesso_anterior, falha_total):
    bloco = {
        "slug": site["slug"],
        "nome": site["nome"],
        "repo": site["repo"],
        "pages_url": site["pages_url"],
        "destaque": site["destaque"],
    }
    if site["form_url"]:
        bloco["form_url"] = site["form_url"]
        if site["form_rotulo"]:
            bloco["form_rotulo"] = site["form_rotulo"]

    commits = gh.commits_no_caminho(OWNER, site["repo"], site["caminho_dados"])
    bloco["ultimo_commit_dados"] = commits[0] if isinstance(commits, list) else commits

    if site["json_meta_url"]:
        bloco["periodo_publicado"] = gh.periodo_publicado(site["json_meta_url"])

    execucoes = None
    if site["workflow_arquivo"]:
        execucoes = gh.execucoes_workflow(OWNER, site["repo"], site["workflow_arquivo"])
        execucao = execucoes[0] if isinstance(execucoes, list) else execucoes
        bloco["ultima_execucao_pipeline"] = execucao
        if site["pipeline_bolao"]:
            match = RE_RODADA.search(bloco["ultimo_commit_dados"].get("mensagem", ""))
            bloco["rodada_mais_recente"] = int(match.group(1)) if match else None
            bloco["em_vigilia"] = execucao.get("status") == "in_progress"
            if not execucao.get("erro"):
                passos = gh.passos_execucao(OWNER, site["repo"], execucao["id"])
                deploys = gh.execucoes_pages(OWNER, site["repo"])
                bloco["etapas_pipeline"] = montar_etapas(execucao, passos, deploys)

    bloco["site_no_ar"] = gh.site_no_ar(site["pages_url"])

    if not site["pipeline_bolao"]:
        bloco["detalhes"], bloco["alertas"] = montar_detalhes(
            OWNER, site, commits, execucoes, bloco["site_no_ar"]
        )

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
    validar()
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
        "forms_avulsos": FORMS_AVULSOS,
    }


def main():
    status = montar()
    SAIDA.parent.mkdir(parents=True, exist_ok=True)
    SAIDA.write_text(json.dumps(status, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"status.json gerado em {SAIDA}")


if __name__ == "__main__":
    main()
