"""Detalhes de um site da grade (todos menos o Bolão F1) para o pop-up do card, e alertas.

Sai no status.json como `detalhes` e `alertas` de cada site:

  detalhes.coleta        só com workflow_arquivo no cadastro:
      workflow, url_workflow   arquivo e página do workflow no GitHub
      agenda                   [{cron, descricao}] lido do próprio arquivo do workflow
      historico                execuções recentes (mais recente primeiro), com duracao_s
      ultimo_sucesso           execução, ou None se não houver no histórico
      ultima_falha             execução + motivo {job, passo, mensagem}, ou None
      passos_ultima            passos da execução mais recente [{nome, conclusao}]
  detalhes.commits_dados  últimos commits na pasta de dados
  detalhes.deploy_pages   último deploy do GitHub Pages, ou None

  alertas                 [{nivel: "aviso" | "erro", texto}] — o card mostra ⚠ quando há algum

A mensagem de erro vem do log do job que falhou (linha antes do "##[error]");
sem acesso ao log, o motivo fica só com o nome do passo que falhou.
"""

from datetime import datetime, timezone

from . import coleta_github as gh

CONCLUSOES_FALHA = {"failure", "timed_out", "startup_failure", "cancelled"}
DIAS_SEM_SUCESSO_ALERTA = 4  # cobre um fim de semana numa coleta de dias úteis
LIMITE_ACTIONS_S = 6 * 3600  # o GitHub cancela jobs que passam de 6 h
FUSO_BRASILIA_H = -3  # sem horário de verão desde 2019

DIAS_SEMANA = ["domingo", "segunda", "terça", "quarta", "quinta", "sexta", "sábado"]


def _data(iso):
    return datetime.fromisoformat(iso.replace("Z", "+00:00"))


def duracao_s(execucao):
    return int((_data(execucao["atualizado_em"]) - _data(execucao["criado_em"])).total_seconds())


def _dias_semana(campo):
    """Campo "dia da semana" do cron -> conjunto de 0..6, ou None se não souber ler."""
    if campo == "*":
        return set(range(7))
    dias = set()
    for parte in campo.split(","):
        if "-" in parte:
            inicio, fim = parte.split("-")
            if not (inicio.isdigit() and fim.isdigit()):
                return None
            dias.update(range(int(inicio), int(fim) + 1))
        elif parte.isdigit():
            dias.add(int(parte))
        else:
            return None
    return {dia % 7 for dia in dias}


def _descrever_dias(dias):
    if dias == set(range(7)):
        return "todo dia"
    if dias == {1, 2, 3, 4, 5}:
        return "de segunda a sexta"
    return ", ".join(DIAS_SEMANA[dia] for dia in sorted(dias))


def descrever_cron(expressao):
    """'0 21 * * 1-5' -> 'de segunda a sexta às 18:00 (Brasília)'. Formas que não
    reconhece voltam como a expressão crua, com "(UTC)"."""
    crua = f"{expressao} (UTC)"
    campos = expressao.split()
    if len(campos) != 5:
        return crua
    minuto, hora, dia_mes, mes, dia_semana = campos
    if dia_mes != "*" or mes != "*" or not minuto.isdigit():
        return crua
    dias = _dias_semana(dia_semana)
    if dias is None:
        return crua
    if hora.startswith("*/") and hora[2:].isdigit():
        return f"{_descrever_dias(dias)}, a cada {hora[2:]} h (minuto {int(minuto):02d})"
    if not hora.isdigit():
        return crua
    hora_local = int(hora) + FUSO_BRASILIA_H
    if hora_local < 0:  # em Brasília ainda é o dia anterior
        hora_local += 24
        dias = {(dia - 1) % 7 for dia in dias}
    return f"{_descrever_dias(dias)} às {hora_local:02d}:{int(minuto):02d} (Brasília)"


def _estourou_limite(execucao):
    return execucao["conclusao"] == "cancelled" and duracao_s(execucao) >= LIMITE_ACTIONS_S - 300


def _relevantes(execucoes):
    """Execuções concluídas, sem os cancelamentos que só abriram espaço para uma execução
    mais nova (disparo manual em cima de outro) — esses não indicam problema."""
    return [
        e for indice, e in enumerate(execucoes)
        if e["status"] == "completed"
        and not (e["conclusao"] == "cancelled" and indice > 0 and not _estourou_limite(e))
    ]


def motivo_falha(owner, repo, execucao, jobs=None):
    """{job, passo, mensagem} de uma execução que falhou; `jobs` evita buscar de novo."""
    if _estourou_limite(execucao):
        return {"job": None, "passo": None,
                "mensagem": "Cancelada pelo GitHub: passou do limite de 6 h (a coleta travou)."}
    if jobs is None:
        jobs = gh.jobs_execucao(owner, repo, execucao["id"]) or []
    for job in jobs:
        if job["conclusao"] not in CONCLUSOES_FALHA:
            continue
        passo = next((p for p in job["passos"] if p["conclusao"] in CONCLUSOES_FALHA), None)
        return {
            "job": job["nome"],
            "passo": passo["nome"] if passo else None,
            "mensagem": gh.linha_de_erro_do_log(owner, repo, job["id"]),
        }
    return {"job": None, "passo": None, "mensagem": None}


def _coleta(owner, site, execucoes):
    repo, arquivo = site["repo"], site["workflow_arquivo"]
    coleta = {
        "workflow": arquivo,
        "url_workflow": f"https://github.com/{owner}/{repo}/actions/workflows/{arquivo}",
        "agenda": [
            {"cron": cron, "descricao": descrever_cron(cron)}
            for cron in gh.crons_do_workflow(owner, repo, arquivo) or []
        ],
        "historico": [],
        "ultimo_sucesso": None,
        "ultima_falha": None,
        "passos_ultima": [],
    }
    if isinstance(execucoes, dict):  # {"erro": ...}
        coleta["erro"] = execucoes["erro"]
        return coleta

    concluidas = _relevantes(execucoes)
    coleta["historico"] = [
        {**e, "duracao_s": duracao_s(e) if e["status"] == "completed" else None} for e in execucoes
    ]
    coleta["ultimo_sucesso"] = next((e for e in concluidas if e["conclusao"] == "success"), None)
    falha = next((e for e in concluidas if e["conclusao"] in CONCLUSOES_FALHA), None)
    if falha:
        coleta["ultima_falha"] = {**falha, "motivo": motivo_falha(owner, repo, falha)}
    passos = gh.passos_execucao(owner, repo, execucoes[0]["id"]) or []
    coleta["passos_ultima"] = [{"nome": p["nome"], "conclusao": p["conclusao"] or p["status"]} for p in passos]
    return coleta


def _alertas(coleta, deploy_pages, site_no_ar):
    alertas = []
    if not (site_no_ar or {}).get("ok"):
        alertas.append({"nivel": "erro", "texto": "O site não respondeu na última checagem."})
    if coleta:
        concluidas = _relevantes(coleta["historico"])
        ultima = concluidas[0] if concluidas else None
        if ultima and ultima["conclusao"] == "cancelled":
            alertas.append({"nivel": "aviso", "texto": "A última coleta foi cancelada."})
        elif ultima and ultima["conclusao"] in CONCLUSOES_FALHA:
            alertas.append({"nivel": "aviso", "texto": "A última coleta falhou."})
        sucesso = coleta["ultimo_sucesso"]
        if coleta["historico"] and sucesso is None:
            alertas.append({"nivel": "aviso", "texto": "Nenhuma coleta com sucesso nas execuções recentes."})
        elif sucesso:
            dias = (datetime.now(timezone.utc) - _data(sucesso["criado_em"])).days
            if dias >= DIAS_SEM_SUCESSO_ALERTA:
                alertas.append({"nivel": "aviso", "texto": f"Sem coleta com sucesso há {dias} dias."})
    if deploy_pages and deploy_pages["status"] == "completed" and deploy_pages["conclusao"] != "success":
        alertas.append({"nivel": "aviso", "texto": "O último deploy do GitHub Pages falhou."})
    return alertas


def montar_detalhes(owner, site, commits, execucoes, site_no_ar):
    """`commits` e `execucoes` já coletados por publicacao.py (listas ou {"erro"}); devolve
    (detalhes, alertas)."""
    coleta = _coleta(owner, site, execucoes) if site["workflow_arquivo"] else None
    deploys = gh.execucoes_pages(owner, site["repo"], quantidade=5)
    # Deploy cancelado = substituído por outro disparado junto; não conta como o último.
    validos = [d for d in deploys or [] if d["conclusao"] != "cancelled"]
    deploy_pages = (validos or deploys or [None])[0]
    detalhes = {
        "coleta": coleta,
        "commits_dados": commits if isinstance(commits, list) else [],
        "deploy_pages": deploy_pages,
    }
    return detalhes, _alertas(coleta, deploy_pages, site_no_ar)
