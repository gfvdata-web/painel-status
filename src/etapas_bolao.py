"""Traduz a última execução do pipeline do Bolão F1 em etapas legíveis.

O fluxo real (ver page-bolao-formula1/.github/workflows/pipeline.yml):

  1. Palpites enviados — alguém cola o bloco do WhatsApp no Google Forms e o
     Apps Script dispara um `repository_dispatch` (evento `novo_palpite`).
     Uma execução `workflow_dispatch` é re-tentativa manual, sem Forms.
  2. Palpites lidos — passo "Roda pipeline (...)": lê o texto, identifica a
     rodada e grava os palpites. Sai com 0 (já pontuou) ou 2 (quali ainda sem
     resultado); qualquer outro código é erro e derruba o passo.
  3. Resultado oficial — consulta à Jolpica. Se já estava lá, sai no passo 2
     e o "Verificador" é pulado; se não, o Verificador fica de vigília
     (a cada 30 min, até 5 h) até o resultado sair.
  4. Pontuação calculada — acontece no mesmo comando que obtém o resultado,
     então acompanha a etapa 3.
  5. Página atualizada — o commit dos dados dispara o deploy do GitHub Pages
     ("pages build and deployment"); conta o primeiro deploy iniciado depois
     da pontuação.

Cada etapa sai como {"chave", "titulo", "estado", "detalhe", "quando"}, com
estado em: ok | andamento | aguardando | erro | pendente | pulado.
"""

PASSO_NOVO_PALPITE = "Roda pipeline (novo palpite)"
PASSO_RETRY_MANUAL = "Roda pipeline (retry manual)"
PASSO_VERIFICADOR = "Verificador"  # prefixo: o nome completo tem um travessão


def _passo(passos, prefixo):
    for passo in passos:
        if passo["nome"].startswith(prefixo):
            return passo
    return None


def _executou(passo):
    return passo is not None and passo["conclusao"] != "skipped"


def _etapa(chave, titulo, estado, detalhe="", quando=None):
    return {"chave": chave, "titulo": titulo, "estado": estado, "detalhe": detalhe, "quando": quando}


def montar_etapas(execucao, passos, deploys):
    if not execucao or execucao.get("erro"):
        return None

    etapas = []
    manual = execucao.get("evento") == "workflow_dispatch"

    # 1. Envio pelo Forms
    if manual:
        etapas.append(_etapa(
            "forms", "Palpites enviados (Google Forms)", "pulado",
            "Re-tentativa manual no GitHub — não houve envio novo pelo Forms.",
            execucao.get("criado_em"),
        ))
    else:
        etapas.append(_etapa(
            "forms", "Palpites enviados (Google Forms)", "ok",
            "Forms recebido e Apps Script disparou o pipeline no GitHub Actions.",
            execucao.get("criado_em"),
        ))

    if passos is None:
        etapas.append(_etapa("leitura", "Palpites lidos e rodada identificada", "pendente",
                             "Não foi possível ler os passos da execução na API do GitHub."))
        return etapas

    # 2. Leitura dos palpites
    leitura = _passo(passos, PASSO_RETRY_MANUAL if manual else PASSO_NOVO_PALPITE)
    verificador = _passo(passos, PASSO_VERIFICADOR)
    titulo_leitura = "Palpites lidos e rodada identificada"
    if leitura is None or leitura["status"] != "completed":
        etapas.append(_etapa("leitura", titulo_leitura, "andamento", "Processando o texto enviado…"))
        leitura_ok = False
    elif leitura["conclusao"] == "success":
        etapas.append(_etapa("leitura", titulo_leitura, "ok", "Palpites gravados no repositório.", leitura["fim"]))
        leitura_ok = True
    else:
        etapas.append(_etapa(
            "leitura", titulo_leitura, "erro",
            "Falha ao ler o texto ou identificar a corrida — ver o log da execução.", leitura["fim"],
        ))
        leitura_ok = False

    # 3 e 4. Resultado oficial + pontuação
    titulo_resultado = "Resultado oficial do quali (Jolpica)"
    titulo_pontos = "Pontuação calculada"
    pontuado_em = None
    if not leitura_ok:
        estado = "pendente"
        etapas.append(_etapa("resultado", titulo_resultado, estado))
        etapas.append(_etapa("pontuacao", titulo_pontos, estado))
    elif not _executou(verificador):
        pontuado_em = leitura["fim"]
        etapas.append(_etapa("resultado", titulo_resultado, "ok",
                             "Já estava publicado no momento do envio.", pontuado_em))
        etapas.append(_etapa("pontuacao", titulo_pontos, "ok", "", pontuado_em))
    elif verificador["status"] != "completed":
        etapas.append(_etapa(
            "resultado", titulo_resultado, "aguardando",
            "Em vigília: consultando a Jolpica a cada 30 min (até 5 h) até o resultado sair.",
            verificador["inicio"],
        ))
        etapas.append(_etapa("pontuacao", titulo_pontos, "pendente", "Assim que o resultado sair."))
    elif verificador["conclusao"] == "success":
        pontuado_em = verificador["fim"]
        etapas.append(_etapa("resultado", titulo_resultado, "ok",
                             "Saiu depois do envio — obtido pela vigília.", pontuado_em))
        etapas.append(_etapa("pontuacao", titulo_pontos, "ok", "", pontuado_em))
    else:
        etapas.append(_etapa(
            "resultado", titulo_resultado, "erro",
            "Não saiu em 5 h de vigília. Palpites estão salvos: rodar o workflow manualmente.",
            verificador["fim"],
        ))
        etapas.append(_etapa("pontuacao", titulo_pontos, "pendente"))

    # 5. Deploy do GitHub Pages
    titulo_pagina = "Página atualizada (GitHub Pages)"
    if pontuado_em is None:
        # Sem pontuação nova a página não muda de placar — mesmo que os
        # palpites já tenham sido publicados.
        etapas.append(_etapa("pagina", titulo_pagina, "pendente"))
        return etapas

    posteriores = [d for d in deploys or [] if d["criado_em"] >= pontuado_em]
    deploy = posteriores[-1] if posteriores else None  # o mais antigo depois da pontuação
    if deploy is None:
        etapas.append(_etapa("pagina", titulo_pagina, "andamento", "Aguardando o deploy começar."))
    elif deploy["status"] != "completed":
        etapas.append(_etapa("pagina", titulo_pagina, "andamento", "Deploy em andamento.", deploy["criado_em"]))
    elif deploy["conclusao"] == "success":
        etapas.append(_etapa("pagina", titulo_pagina, "ok", "Placar novo no ar.", deploy["atualizado_em"]))
    else:
        etapas.append(_etapa("pagina", titulo_pagina, "erro", "O deploy do GitHub Pages falhou.",
                             deploy["atualizado_em"]))
    return etapas
