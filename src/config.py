"""Registro dos sites monitorados pelo painel-status.

Cada entrada descreve um site publicado via GitHub Pages sob a conta gfvdata-web.
Nada aqui exige mudança nos repositórios monitorados: o painel só lê informação
pública já exposta pela API do GitHub (commits, execuções de Actions) e, quando
configurado, pela API do GoatCounter.

Rastreio de acesso: um único site GoatCounter (`gfvdata`, ver coleta_goatcounter.py)
recebe o snippet padrão em todos os sites monitorados — a separação entre eles é
feita pelo prefixo do caminho (o nome do repositório), já que todos vivem sob
gfvdata-web.github.io/<repo>/. Não há campo por-site aqui para isso.
"""

OWNER = "gfvdata-web"

SITES = [
    {
        "slug": "bolao_f1",
        "nome": "Bolão F1",
        "repo": "page-bolao-formula1",
        "pages_url": "https://gfvdata-web.github.io/page-bolao-formula1/",
        "caminho_dados": "docs/data",
        "workflow_arquivo": "pipeline.yml",
        "destaque": True,
    },
    {
        "slug": "meios_pagamento_mensal",
        "nome": "Meios de pagamento",
        "repo": "fonte-meios-pagamento",
        "pages_url": "https://gfvdata-web.github.io/fonte-meios-pagamento/",
        "caminho_dados": "docs/dados",
        "json_meta_url": "https://gfvdata-web.github.io/fonte-meios-pagamento/dados/meios_pagamento_mensal.json",
        "workflow_arquivo": None,
        "destaque": False,
    },
    {
        "slug": "arrecadacao_federal",
        "nome": "Arrecadação federal",
        "repo": "fonte-arrecadacao-federal",
        "pages_url": "https://gfvdata-web.github.io/fonte-arrecadacao-federal/",
        "caminho_dados": "docs/dados",
        "json_meta_url": "https://gfvdata-web.github.io/fonte-arrecadacao-federal/dados/arrecadacao_federal.json",
        "workflow_arquivo": None,
        "destaque": False,
    },
    {
        "slug": "credito_modalidade",
        "nome": "Crédito por modalidade",
        "repo": "fonte-credito-modalidade",
        "pages_url": "https://gfvdata-web.github.io/fonte-credito-modalidade/",
        "caminho_dados": "docs/dados",
        "json_meta_url": "https://gfvdata-web.github.io/fonte-credito-modalidade/dados/credito_modalidade.json",
        "workflow_arquivo": None,
        "destaque": False,
    },
    {
        "slug": "simulador_investimentos",
        "nome": "Simulador de investimentos",
        "repo": "simulador-investimentos",
        "pages_url": "https://gfvdata-web.github.io/simulador-investimentos/",
        "caminho_dados": "dados",
        "workflow_arquivo": "atualizar-dados.yml",
        "destaque": False,
    },
    {
        "slug": "cruzeiro_indata",
        "nome": "Cruzeiro Indata",
        "repo": "cruzeiro-indata-publico",
        "pages_url": "https://gfvdata-web.github.io/cruzeiro-indata-publico/",
        "caminho_dados": "data",
        "workflow_arquivo": None,
        "destaque": False,
    },
]
