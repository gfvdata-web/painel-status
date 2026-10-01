"""Coleta de sinais públicos da API do GitHub para cada site monitorado.

Usa GITHUB_TOKEN (variável de ambiente) quando disponível, para elevar o
limite de taxa e para ler logs de execução (a API de logs exige token mesmo
em repositório público) — nenhuma permissão de escrita é exercida aqui.

Toda função devolve um valor mesmo com a API fora do ar: dict com "erro" (ou
None, onde indicado), nunca exceção — um site com problema não derruba a coleta
dos outros.
"""

import base64
import os
import re
import urllib.error
from datetime import datetime, timezone

from .rede import ERROS_REDE, get_json, get_status, get_texto

API = "https://api.github.com"


def _headers():
    headers = {"Accept": "application/vnd.github+json"}
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def _get_json(url):
    return get_json(url, _headers())


def _descrever_erro(exc, o_que):
    if isinstance(exc, urllib.error.HTTPError):
        return f"HTTP {exc.code} ao consultar {o_que}"
    return f"falha de rede ao consultar {o_que}: {exc}"


def commits_no_caminho(owner, repo, caminho, quantidade=5):
    """Commits mais recentes que tocaram `caminho` (mais recente primeiro), ou {"erro"}."""
    url = f"{API}/repos/{owner}/{repo}/commits?path={caminho}&per_page={quantidade}"
    try:
        commits = _get_json(url)
    except ERROS_REDE as exc:
        return {"erro": _descrever_erro(exc, "commits")}
    if not commits:
        return {"erro": "nenhum commit encontrado nesse caminho"}
    return [
        {
            "data": commit["commit"]["author"]["date"],
            "mensagem": commit["commit"]["message"].splitlines()[0],
            "sha": commit["sha"][:7],
            "url": commit["html_url"],
        }
        for commit in commits
    ]


def execucoes_workflow(owner, repo, arquivo_workflow, quantidade=15):
    """Execuções mais recentes de um workflow do Actions (mais recente primeiro), ou {"erro"}."""
    url = f"{API}/repos/{owner}/{repo}/actions/workflows/{arquivo_workflow}/runs?per_page={quantidade}"
    try:
        dados = _get_json(url)
    except ERROS_REDE as exc:
        return {"erro": _descrever_erro(exc, "execuções")}
    runs = dados.get("workflow_runs") or []
    if not runs:
        return {"erro": "nenhuma execução encontrada"}
    return [
        {
            "id": run["id"],
            "evento": run["event"],  # schedule | repository_dispatch (Forms) | workflow_dispatch (manual) | ...
            "status": run["status"],  # queued | in_progress | completed
            "conclusao": run.get("conclusion"),  # success | failure | cancelled | timed_out | ... | None
            "criado_em": run["created_at"],
            "atualizado_em": run["updated_at"],
            "url": run["html_url"],
        }
        for run in runs
    ]


def jobs_execucao(owner, repo, run_id):
    """Jobs de uma execução com seus passos, na ordem em que rodam. None em falha."""
    url = f"{API}/repos/{owner}/{repo}/actions/runs/{run_id}/jobs"
    try:
        dados = _get_json(url)
    except ERROS_REDE:
        return None
    return [
        {
            "id": job["id"],
            "nome": job["name"],
            "conclusao": job.get("conclusion"),
            "passos": [
                {
                    "nome": passo["name"],
                    "status": passo["status"],
                    "conclusao": passo.get("conclusion"),
                    "inicio": passo.get("started_at"),
                    "fim": passo.get("completed_at"),
                }
                for passo in job.get("steps") or []
            ],
        }
        for job in dados.get("jobs") or []
    ]


def passos_execucao(owner, repo, run_id):
    """Passos (steps) de todos os jobs de uma execução, na ordem em que rodam. None em falha."""
    jobs = jobs_execucao(owner, repo, run_id)
    if jobs is None:
        return None
    return [passo for job in jobs for passo in job["passos"]]


# Linha de log do Actions: "2026-09-25T23:44:56.6519466Z <texto>"
RE_PREFIXO_LOG = re.compile(r"^\S+Z ")
RE_CRON = re.compile(r"""cron:\s*['"]([^'"]+)['"]""")


def linha_de_erro_do_log(owner, repo, job_id):
    """Última linha útil antes do primeiro "##[error]" do log de um job (numa exceção
    Python, a própria exceção). None se o log não puder ser lido."""
    try:
        log = get_texto(f"{API}/repos/{owner}/{repo}/actions/jobs/{job_id}/logs", _headers())
    except ERROS_REDE:
        return None
    linhas = [RE_PREFIXO_LOG.sub("", linha).strip() for linha in log.splitlines()]
    for indice, linha in enumerate(linhas):
        if not linha.startswith("##[error]"):
            continue
        mensagem = linha.removeprefix("##[error]")
        if not mensagem.startswith("Process completed with exit code"):
            return mensagem[:400]
        anteriores = [anterior for anterior in linhas[:indice] if anterior and not anterior.startswith("##[")]
        return anteriores[-1][:400] if anteriores else mensagem
    return None


def crons_do_workflow(owner, repo, arquivo_workflow):
    """Expressões cron declaradas em `on.schedule` do arquivo do workflow. None em falha."""
    url = f"{API}/repos/{owner}/{repo}/contents/.github/workflows/{arquivo_workflow}"
    try:
        dados = _get_json(url)
    except ERROS_REDE:
        return None
    texto = base64.b64decode(dados.get("content", "")).decode("utf-8", errors="replace")
    return RE_CRON.findall(texto)


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
