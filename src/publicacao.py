"""Monta docs/dados/status.json a partir da coleta de GitHub + GoatCounter.

Uso: python -m src.publicacao              rodada completa (todos os sites)
     python -m src.publicacao --so-bolao   só o card do Bolão F1, sobre o status.json
                                           atual (usado pelo vigia_bolao.py, que
                                           roda a cada minuto e não pode gastar
                                           a cota da API com os outros sites)
     python -m src.publicacao --so-forms   idem, só os sites com pipeline_forms
                                           (job vigiar-forms, ver vigia_forms.py)

Formato de saída (o único arquivo que docs/js/app.js lê — e o app Android em android/,
que espelha estes campos em data/modelo/Modelos.kt; mudanças só aditivas):
  meta           {gerado_em, descricao}
  sites[]        slug, nome, repo, pages_url, destaque, ultimo_commit_dados,
                 site_no_ar e, conforme o cadastro em config.py: form_url,
                 form_rotulo,
                 periodo_publicado, ultima_execucao_pipeline, acesso e — só no
                 site com pipeline_bolao — rodada_mais_recente, em_vigilia,
                 etapas_pipeline; nos demais, detalhes e alertas (pop-up do card,
                 formato em detalhes_site.py); só nos sites com pipeline_forms,
                 pipeline_forms {workflow, execucao, etapas} — execucao no
                 formato de ultima_execucao_pipeline, etapas no de
                 etapas_pipeline (etapas_forms.py); em todo site com workflow,
                 historico_arquivo (caminho do histórico acumulado, formato
                 em historico.py)
  forms_avulsos  [{nome, url}] copiado de config.FORMS_AVULSOS
"""

import json
import re
import sys
from pathlib import Path

from . import coleta_github as gh
from . import coleta_goatcounter as gc
from . import historico
from .detalhes_site import montar_detalhes
from .etapas_bolao import montar_etapas
from .etapas_forms import montar_etapas as montar_etapas_forms
from .config import FORMS_AVULSOS, OWNER, SITES, validar

SAIDA = Path(__file__).resolve().parent.parent / "docs" / "dados" / "status.json"

RE_RODADA = re.compile(r"rodada\s+(\d+)", re.IGNORECASE)
EXECUCOES_NO_POPUP = 15
EXECUCOES_RECENTES = 100  # uma página da API; o histórico acumulado só acrescenta


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

    commits = gh.commits_no_caminho(
        OWNER, site["repo"], site["caminho_dados"], branch=site["branch_dados"]
    )
    bloco["ultimo_commit_dados"] = commits[0] if isinstance(commits, list) else commits

    if site["json_meta_url"]:
        bloco["periodo_publicado"] = gh.periodo_publicado(site["json_meta_url"])

    execucoes = None
    if site["workflow_arquivo"]:
        execucoes = gh.execucoes_workflow(OWNER, site["repo"], site["workflow_arquivo"], EXECUCOES_RECENTES)
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
        recentes = execucoes[:EXECUCOES_NO_POPUP] if isinstance(execucoes, list) else execucoes
        bloco["detalhes"], bloco["alertas"] = montar_detalhes(
            OWNER, site, commits, recentes, bloco["site_no_ar"]
        )

    if site["pipeline_forms"]:
        bloco["pipeline_forms"] = coletar_pipeline_forms(site)
        execucao = bloco["pipeline_forms"]["execucao"]
        if execucao.get("conclusao") in ("failure", "timed_out"):
            bloco["alertas"].append({"nivel": "aviso", "texto": "O último envio pelo Forms falhou no pipeline."})

    if site["workflow_arquivo"]:
        bloco["historico_arquivo"] = historico.atualizar(OWNER, site, execucoes)

    acesso = acesso_por_repo.get(site["repo"])
    if not acesso and falha_total:
        acesso = acesso_anterior.get(site["slug"])
    if acesso:
        bloco["acesso"] = acesso

    return bloco


def coletar_pipeline_forms(site):
    """Última execução do pipeline do Forms e o passo a passo dela."""
    execucoes = gh.execucoes_workflow(OWNER, site["repo"], site["pipeline_forms"], 1)
    execucao = execucoes[0] if isinstance(execucoes, list) else execucoes
    bloco = {"workflow": site["pipeline_forms"], "execucao": execucao, "etapas": None}
    if not execucao.get("erro"):
        passos = gh.passos_execucao(OWNER, site["repo"], execucao["id"])
        bloco["etapas"] = montar_etapas_forms(execucao, passos)
    return bloco


def _status_anterior():
    """Último status.json publicado, ou None."""
    if not SAIDA.exists():
        return None
    try:
        return json.loads(SAIDA.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return None


def _acesso_anterior_por_slug(anterior):
    """'acesso' de cada site no status.json anterior — reaproveitado quando não há coleta nova."""
    if not anterior:
        return {}
    return {site["slug"]: site["acesso"] for site in anterior.get("sites", []) if site.get("acesso")}


def _meta():
    return {
        "gerado_em": gh.agora_iso(),
        "descricao": "Status de atualização de dados e acesso dos sites gfvdata-web",
    }


def montar():
    validar()
    acesso_por_repo = gc.estatisticas_por_repo([site["repo"] for site in SITES])
    falha_total = acesso_por_repo is None
    acesso_anterior = _acesso_anterior_por_slug(_status_anterior()) if falha_total else {}
    if falha_total:
        acesso_por_repo = {}
    return {
        "meta": _meta(),
        "sites": [
            coletar_site(site, acesso_por_repo, acesso_anterior, falha_total) for site in SITES
        ],
        "forms_avulsos": FORMS_AVULSOS,
    }


def montar_so(campo):
    """Recoleta só os sites com `campo` (pipeline_bolao, pipeline_forms) ligado e
    mantém os outros como estão no status.json atual. Sem status.json anterior, cai
    na rodada completa."""
    anterior = _status_anterior()
    if not anterior:
        return montar()
    validar()
    sites = list(anterior["sites"])
    for site in (s for s in SITES if s[campo]):
        bloco = coletar_site(site, {}, _acesso_anterior_por_slug(anterior), falha_total=True)
        indice = next((i for i, s in enumerate(sites) if s["slug"] == site["slug"]), None)
        if indice is None:
            sites.insert(0 if site["destaque"] else len(sites), bloco)
        else:
            sites[indice] = bloco
    return {**anterior, "meta": _meta(), "sites": sites}


def main():
    argumentos = sys.argv[1:]
    if "--so-bolao" in argumentos:
        status = montar_so("pipeline_bolao")
    elif "--so-forms" in argumentos:
        status = montar_so("pipeline_forms")
    else:
        status = montar()
    SAIDA.parent.mkdir(parents=True, exist_ok=True)
    SAIDA.write_text(json.dumps(status, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"status.json gerado em {SAIDA}")


if __name__ == "__main__":
    main()
