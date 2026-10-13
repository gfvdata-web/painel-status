"""Traduz a última execução do pipeline disparado pelo Google Forms de um site
(campo `pipeline_forms` em config.py) em etapas legíveis. Hoje só o PlantsCare.

O fluxo real (ver PlantsCare/.github/workflows/pipeline.yml e
PlantsCare/google-apps-script/Code.gs):

  1. Fotos enviadas — o dono manda fotos + comentário pelo Forms; o Apps Script
     arquiva no Drive e grava o envio em inbox/<planta>/<carimbo>/ no GitHub. O
     meta.json, que vai por último, dispara o pipeline (evento `push`). Uma
     execução `workflow_dispatch` é manual: reprocessa inbox/ ou refaz só o
     relatório de plantas escolhidas.
  2. Ficha atualizada — passo "Estágio 1 …": copia as fotos e escreve a entrada
     nova na ficha. Pulado quando a execução manual só refaz o relatório.
  3. Relatório — passo "Estágio 2-4 …": síntese do Gemini, PDF e e-mail. Pulado
     quando não havia envio pendente em inbox/.
  4. Salvo no repositório — passo "Commitar …": ficha, fotos, PDF e a limpeza de
     inbox/. Roda mesmo com o relatório falhando, para a ficha não se perder.

Os passos são achados pelo começo do nome: renomeou lá, mudar PASSO_* aqui.

Cada etapa sai como {"chave", "titulo", "estado", "detalhe", "quando"}, com
estado em: ok | andamento | erro | pendente | pulado (o mesmo formato de
etapas_bolao.py, desenhado pelo mesmo código da página).
"""

PASSO_FICHA = "Estágio 1"
PASSO_RELATORIO = "Estágio 2-4"
PASSO_COMMIT = "Commitar"


def _passo(passos, prefixo):
    return next((p for p in passos if p["nome"].startswith(prefixo)), None)


def _etapa(chave, titulo, estado, detalhe="", quando=None):
    return {"chave": chave, "titulo": titulo, "estado": estado, "detalhe": detalhe, "quando": quando}


def _estado(passo, execucao):
    """(estado, quando) de um passo. Passo que ainda não apareceu: pendente, ou
    pulado se a execução já terminou sem chegar nele."""
    if passo is None:
        return ("pendente" if execucao.get("status") != "completed" else "pulado"), None
    if passo["status"] != "completed":
        return ("andamento" if passo["status"] == "in_progress" else "pendente"), passo.get("inicio")
    if passo["conclusao"] == "success":
        return "ok", passo["fim"]
    if passo["conclusao"] == "skipped":
        return "pulado", None
    return "erro", passo["fim"]


def montar_etapas(execucao, passos):
    if not execucao or execucao.get("erro"):
        return None

    etapas = []
    manual = execucao.get("evento") == "workflow_dispatch"

    # 1. Envio pelo Forms
    if manual:
        etapas.append(_etapa(
            "forms", "Fotos enviadas (Google Forms)", "pulado",
            "Execução manual no GitHub — não houve envio novo pelo Forms.",
            execucao.get("criado_em"),
        ))
    else:
        etapas.append(_etapa(
            "forms", "Fotos enviadas (Google Forms)", "ok",
            "Apps Script gravou o envio em inbox/ no GitHub e disparou o pipeline.",
            execucao.get("criado_em"),
        ))

    titulo_ficha = "Ficha da planta atualizada"
    titulo_relatorio = "Relatório da IA (Gemini) → PDF → e-mail"
    titulo_commit = "Salvo no repositório"
    if passos is None:
        etapas.append(_etapa("ficha", titulo_ficha, "pendente",
                             "Não foi possível ler os passos da execução na API do GitHub."))
        return etapas
    if not passos:
        etapas.append(_etapa("ficha", titulo_ficha, "andamento", "Execução na fila do GitHub Actions…"))
        etapas.append(_etapa("relatorio", titulo_relatorio, "pendente"))
        etapas.append(_etapa("commit", titulo_commit, "pendente"))
        return etapas

    # 2. Ficha
    estado, quando = _estado(_passo(passos, PASSO_FICHA), execucao)
    detalhe = {
        "ok": "Fotos copiadas e entrada nova escrita na ficha.",
        "andamento": "Lendo o envio de inbox/…",
        "erro": "Algum envio não pôde ser processado e ficou em inbox/ — ver o log da execução.",
        "pulado": "Execução manual só para refazer o relatório.",
    }.get(estado, "")
    etapas.append(_etapa("ficha", titulo_ficha, estado, detalhe, quando))

    # 3. Relatório. Pulado depois da ficha = nenhuma planta tocada.
    estado, quando = _estado(_passo(passos, PASSO_RELATORIO), execucao)
    detalhe = {
        "ok": "Síntese do Gemini, PDF e e-mail enviados.",
        "andamento": "Gemini sintetizando, depois PDF e e-mail…",
        "erro": "Falhou (Gemini, PDF ou e-mail). A ficha foi salva; refazer com "
                "`gh workflow run pipeline.yml -f plantas=<planta>`.",
        "pulado": "Nenhum envio pendente em inbox/ nesta execução.",
    }.get(estado, "")
    etapas.append(_etapa("relatorio", titulo_relatorio, estado, detalhe, quando))

    # 4. Commit
    estado, quando = _estado(_passo(passos, PASSO_COMMIT), execucao)
    detalhe = {
        "ok": "Ficha, fotos e PDF commitados; envio retirado de inbox/.",
        "erro": "O commit/push falhou — o envio continua em inbox/ e entra na próxima execução.",
        "pulado": "Nada a salvar.",
    }.get(estado, "")
    etapas.append(_etapa("commit", titulo_commit, estado, detalhe, quando))
    return etapas
