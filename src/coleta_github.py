"""Coleta de sinais públicos da API do GitHub para cada site monitorado.

Usa GITHUB_TOKEN (variável de ambiente) quando disponível, só para elevar o
limite de taxa — todos os dados lidos são públicos, nenhuma permissão
privilegiada é exercida aqui.

Toda função devolve um valor mesmo com a API fora do ar: dict com "erro" (ou
None, onde indicado), nunca exceção — um site com problema não derruba a coleta
dos outros.
"""

import os
import urllib.error
from datetime import datetime, timezone

from .rede import ERROS_REDE, get_json, get_status

API = "https://api.github.com"


def _get_json(url):
    headers = {"Accept": "application/vnd.github+json"}
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return get_json(url, headers)


def _descrever_erro(exc, o_que):
    if isinstance(exc, urllib.error.HTTPError):
        return f"HTTP {exc.code} ao consultar {o_que}"
    return f"falha de rede ao consultar {o_que}: {exc}"


def ultimo_commit_no_caminho(owner, repo, caminho):
    """Data e mensagem do commit mais recente que tocou `caminho` no repositório."""
    url = f"{API}/repos/{owner}/{repo}/commits?path={caminho}&per_page=1"
    try:
        commits = _get_json(url)
    except ERROS_REDE as exc:
        return {"erro": _descrever_erro(exc, "commits")}
    if not commits:
        return {"erro": "nenhum commit encontrado nesse caminho"}
    commit = commits[0]
    return {
        "data": commit["commit"]["author"]["date"],
        "mensagem": commit["commit"]["message"].splitlines()[0],
        "sha": commit["sha"][:7],
        "url": commit["html_url"],
    }


def ultima_execucao_workflow(owner, repo, arquivo_workflow):
    """Status/conclusão da execução mais recente de um workflow do Actions."""
    url = f"{API}/repos/{owner}/{repo}/actions/workflows/{arquivo_workflow}/runs?per_page=1"
    try:
        dados = _get_json(url)
    except ERROS_REDE as exc:
        return {"erro": _descrever_erro(exc, "execuções")}
    runs = dados.get("workflow_runs") or []
    if not runs:
        return {"erro": "nenhuma execução encontrada"}
    run = runs[0]
    return {
        "id": run["id"],
        "evento": run["event"],  # repository_dispatch (Forms) | workflow_dispatch (manual) | ...
        "status": run["status"],  # queued | in_progress | completed
        "conclusao": run.get("conclusion"),  # success | failure | ... | None
        "criado_em": run["created_at"],
        "atualizado_em": run["updated_at"],
        "url": run["html_url"],
    }


def passos_execucao(owner, repo, run_id):
    """Passos (steps) de todos os jobs de uma execução, na ordem em que rodam. None em falha."""
    url = f"{API}/repos/{owner}/{repo}/actions/runs/{run_id}/jobs"
    try:
        dados = _get_json(url)
    except ERROS_REDE:
        return None
    return [
        {
            "nome": passo["name"],
            "status": passo["status"],
            "conclusao": passo.get("conclusion"),
            "inicio": passo.get("started_at"),
            "fim": passo.get("completed_at"),
        }
        for job in dados.get("jobs") or []
        for passo in job.get("steps") or []
    ]


def execucoes_pages(owner, repo, quantidade=30):
    """Execuções recentes do deploy automático do GitHub Pages (mais recente primeiro). None em falha."""
    url = f"{API}/repos/{owner}/{repo}/actions/runs?event=dynamic&per_page={quantidade}"
    try:
        dados = _get_json(url)
    except ERROS_REDE:
        return None
    return [
        {
            "status": run["status"],
            "conclusao": run.get("conclusion"),
            "criado_em": run["created_at"],
            "atualizado_em": run["updated_at"],
            "url": run["html_url"],
        }
        for run in dados.get("workflow_runs") or []
        if run.get("name") == "pages build and deployment"
    ]


def site_no_ar(pages_url):
    """Checagem HTTP simples: o site publicado responde?"""
    try:
        status = get_status(pages_url)
    except urllib.error.HTTPError as exc:
        return {"ok": False, "status_http": exc.code}
    except ERROS_REDE as exc:  # DNS/timeout/etc.
        return {"ok": False, "erro": str(exc)}
    return {"ok": 200 <= status < 300, "status_http": status}


def periodo_publicado(url):
    """`meta.periodo` ({inicio, fim}) de um JSON de dados publicado, quando existir."""
    try:
        dados = _get_json(url)
    except ERROS_REDE:
        return None
    return (dados.get("meta") or {}).get("periodo")


def agora_iso():
    return datetime.now(timezone.utc).isoformat()
