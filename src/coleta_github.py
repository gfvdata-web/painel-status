"""Coleta de sinais públicos da API do GitHub para cada site monitorado.

Usa GITHUB_TOKEN (variável de ambiente) quando disponível, só para elevar o
limite de taxa — todos os dados lidos são públicos, nenhuma permissão
privilegiada é exercida aqui.
"""

import os
import urllib.request
import urllib.error
import json
from datetime import datetime, timezone

API = "https://api.github.com"


def _headers():
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "painel-status"}
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def _get_json(url):
    req = urllib.request.Request(url, headers=_headers())
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def ultimo_commit_no_caminho(owner, repo, caminho):
    """Data e mensagem do commit mais recente que tocou `caminho` no repositório."""
    url = f"{API}/repos/{owner}/{repo}/commits?path={caminho}&per_page=1"
    try:
        commits = _get_json(url)
    except urllib.error.HTTPError as exc:
        return {"erro": f"HTTP {exc.code} ao consultar commits"}
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
    except urllib.error.HTTPError as exc:
        return {"erro": f"HTTP {exc.code} ao consultar execuções"}
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
    """Passos (steps) de todos os jobs de uma execução, na ordem em que rodam."""
    url = f"{API}/repos/{owner}/{repo}/actions/runs/{run_id}/jobs"
    try:
        dados = _get_json(url)
    except urllib.error.HTTPError:
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
    """Execuções recentes do deploy automático do GitHub Pages (mais recente primeiro)."""
    url = f"{API}/repos/{owner}/{repo}/actions/runs?event=dynamic&per_page={quantidade}"
    try:
        dados = _get_json(url)
    except urllib.error.HTTPError:
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
    req = urllib.request.Request(pages_url, headers={"User-Agent": "painel-status"}, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return {"ok": 200 <= resp.status < 300, "status_http": resp.status}
    except urllib.error.HTTPError as exc:
        return {"ok": False, "status_http": exc.code}
    except Exception as exc:  # DNS/timeout/etc.
        return {"ok": False, "erro": str(exc)}


def meta_json(url):
    """Lê o bloco `meta` de um JSON de dados publicado, quando existir."""
    try:
        dados = _get_json(url)
    except Exception:
        return None
    return dados.get("meta")


def agora_iso():
    return datetime.now(timezone.utc).isoformat()
