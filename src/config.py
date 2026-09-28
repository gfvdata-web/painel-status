"""Cadastro único do painel-status: sites monitorados e forms avulsos.

Tudo o que aparece no painel sai daqui (via docs/dados/status.json, gerado por
publicacao.py). Mudou algo aqui? Atualizar também as tabelas do README.md e
rodar o workflow `atualizar-status.yml` — ver checklist no CLAUDE.md.

Nada aqui exige mudança nos repositórios monitorados: o painel só lê informação
pública já exposta pela API do GitHub (commits, execuções de Actions) e, quando
configurado, pela API do GoatCounter.

Campos de cada site (todos presentes em toda entrada, para servir de molde):
  slug             identificador único, snake_case
  nome             nome exibido no card
  repo             repositório em github.com/gfvdata-web
  pages_url        endereço publicado (checagem "site no ar" e link "Abrir")
  caminho_dados    pasta do repo onde os dados moram; o último commit nela é
                   o "Dados atualizados"
  workflow_arquivo arquivo em .github/workflows/ da coleta/pipeline, ou None;
                   com valor, o card mostra se a última execução passou
  json_meta_url    JSON publicado com meta.periodo {inicio, fim}, ou None;
                   com valor, o card mostra "Período coberto"
  form_url         Google Forms ligado ao site, ou None (link no card)
  destaque         True em no máximo um site: vira o card grande do topo
  pipeline_bolao   True só no Bolão F1: monta rodada, vigília e o passo a passo
                   (etapas_bolao.py); exige workflow_arquivo

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
        "json_meta_url": None,
        # Onde se cola o bloco de palpites do WhatsApp — o envio dispara o
        # pipeline via Apps Script.
        "form_url": "https://forms.gle/7yZAx1WThPf51bv67",
        "destaque": True,
        "pipeline_bolao": True,
    },
    {
        "slug": "meios_pagamento_mensal",
        "nome": "Meios de pagamento",
        "repo": "fonte-meios-pagamento",
        "pages_url": "https://gfvdata-web.github.io/fonte-meios-pagamento/",
        "caminho_dados": "docs/dados",
        "workflow_arquivo": None,
        "json_meta_url": "https://gfvdata-web.github.io/fonte-meios-pagamento/dados/meios_pagamento_mensal.json",
        "form_url": None,
        "destaque": False,
        "pipeline_bolao": False,
    },
    {
        "slug": "arrecadacao_federal",
        "nome": "Arrecadação federal",
        "repo": "fonte-arrecadacao-federal",
        "pages_url": "https://gfvdata-web.github.io/fonte-arrecadacao-federal/",
        "caminho_dados": "docs/dados",
        "workflow_arquivo": None,
        "json_meta_url": "https://gfvdata-web.github.io/fonte-arrecadacao-federal/dados/arrecadacao_federal.json",
        "form_url": None,
        "destaque": False,
        "pipeline_bolao": False,
    },
    {
        "slug": "credito_modalidade",
        "nome": "Crédito por modalidade",
        "repo": "fonte-credito-modalidade",
        "pages_url": "https://gfvdata-web.github.io/fonte-credito-modalidade/",
        "caminho_dados": "docs/dados",
        "workflow_arquivo": None,
        "json_meta_url": "https://gfvdata-web.github.io/fonte-credito-modalidade/dados/credito_modalidade.json",
        "form_url": None,
        "destaque": False,
        "pipeline_bolao": False,
    },
    {
        "slug": "simulador_investimentos",
        "nome": "Simulador de investimentos",
        "repo": "simulador-investimentos",
        "pages_url": "https://gfvdata-web.github.io/simulador-investimentos/",
        "caminho_dados": "dados",
        "workflow_arquivo": "atualizar-dados.yml",
        "json_meta_url": None,
        "form_url": None,
        "destaque": False,
        "pipeline_bolao": False,
    },
    {
        "slug": "chess_tracking",
        "nome": "Chess Tracking",
        "repo": "chess-tracking",
        "pages_url": "https://gfvdata-web.github.io/chess-tracking/",
        "caminho_dados": "docs/dados",
        "workflow_arquivo": "atualizar-partidas.yml",
        "json_meta_url": None,
        "form_url": None,
        "destaque": False,
        "pipeline_bolao": False,
    },
]

# Google Forms de projetos sem site publicado para monitorar — viram o card
# "Outros forms" da grade. Só atalhos: nada é coletado sobre eles.
FORMS_AVULSOS = [
    {"nome": "Notas fiscais", "url": "https://forms.gle/ftgMMg1Lwpoi7j3Z8"},
    {"nome": "Update plantas", "url": "https://forms.gle/y3uXaukJXmP9GMED7"},
]

CAMPOS_SITE = {
    "slug", "nome", "repo", "pages_url", "caminho_dados", "workflow_arquivo",
    "json_meta_url", "form_url", "destaque", "pipeline_bolao",
}


def validar():
    """Falha cedo (antes de qualquer chamada de rede) se o cadastro estiver inconsistente."""
    problemas = []
    for site in SITES:
        rotulo = site.get("slug", "?")
        faltando = CAMPOS_SITE - site.keys()
        sobrando = site.keys() - CAMPOS_SITE
        if faltando:
            problemas.append(f"{rotulo}: faltam campos {sorted(faltando)}")
        if sobrando:
            problemas.append(f"{rotulo}: campos desconhecidos {sorted(sobrando)}")
        if site.get("pipeline_bolao") and not site.get("workflow_arquivo"):
            problemas.append(f"{rotulo}: pipeline_bolao exige workflow_arquivo")
    slugs = [site.get("slug") for site in SITES]
    if len(slugs) != len(set(slugs)):
        problemas.append("slugs repetidos em SITES")
    if sum(1 for site in SITES if site.get("destaque")) > 1:
        problemas.append("mais de um site com destaque=True")
    for form in FORMS_AVULSOS:
        if set(form) != {"nome", "url"}:
            problemas.append(f"form avulso malformado: {form}")
    if problemas:
        raise ValueError("config.py inconsistente:\n  " + "\n  ".join(problemas))
