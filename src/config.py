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
  branch_dados     branch onde fica caminho_dados, ou None (= branch padrão);
                   para sites cujos dados só são publicados no gh-pages
  workflow_arquivo arquivo em .github/workflows/ da coleta/pipeline, ou None;
                   com valor, o card mostra se a última execução passou
  json_meta_url    JSON publicado com meta.periodo {inicio, fim}, ou None;
                   com valor, o card mostra "Período coberto"
  form_url         Google Forms ligado ao site, ou None (link no card)
  form_rotulo      texto do link do form (sem a seta), ou None; exige
                   form_url. None no site com form = "Abrir Google Forms"
  destaque         True em no máximo um site: vira o card grande do topo
  pipeline_bolao   True só no Bolão F1: monta rodada, vigília e o passo a passo
                   (etapas_bolao.py); exige workflow_arquivo
  pipeline_forms   arquivo em .github/workflows/ do pipeline disparado pelo
                   Google Forms do site, ou None; com valor, o card mostra o
                   passo a passo do último envio (etapas_forms.py) e o
                   Apps Script do Forms avisa o painel (job vigiar-forms).
                   Exige form_url. Hoje só o PlantsCare
  categoria        cor da faixa do cartão na página principal: "dados_abertos",
                   "hobby", "curiosidade", ou None (cor neutra enquanto não
                   atribuída). Os nomes e a atribuição ainda não estão definidos
                   (ver CATEGORIAS)

Rastreio de acesso: um único site GoatCounter (`gfvdata`, ver coleta_goatcounter.py)
recebe o snippet padrão em todos os sites monitorados — a separação entre eles é
feita pelo prefixo do caminho (o nome do repositório), já que todos viviam sob
gfvdata-web.github.io/<repo>/. Site em domínio próprio precisa prefixar o caminho no
snippet (ver README). Não há campo por-site aqui para isso.
"""

OWNER = "gfvdata-web"

SITES = [
    {
        "slug": "bolao_f1",
        "nome": "Bolão F1",
        "repo": "page-bolao-formula1",
        # Cloudflare Pages (projeto bolao-formula1); o GitHub Pages antigo segue
        # publicado, mas não é mais o endereço oficial.
        "pages_url": "https://bolaof1.gfvdata.com/",
        "caminho_dados": "docs/data",
        "branch_dados": None,
        "workflow_arquivo": "pipeline.yml",
        "json_meta_url": None,
        # Onde se cola o bloco de palpites do WhatsApp — o envio dispara o
        # pipeline via Apps Script.
        "form_url": "https://forms.gle/7yZAx1WThPf51bv67",
        "form_rotulo": "Enviar palpites (Google Forms)",
        "destaque": True,
        "pipeline_bolao": True,
        "pipeline_forms": None,
        "categoria": None,
    },
    {
        "slug": "meios_pagamento_mensal",
        "nome": "Meios de pagamento",
        "repo": "fonte-meios-pagamento",
        # Cloudflare Pages (projeto meios-pagamento); a raiz serve a página principal
        # (antes era /index-v2). Sem ".html": a Cloudflare redireciona (308).
        "pages_url": "https://meiospagamento.gfvdata.com/",
        "caminho_dados": "docs/dados",
        "branch_dados": None,
        "workflow_arquivo": None,
        "json_meta_url": "https://meiospagamento.gfvdata.com/dados/meios_pagamento_mensal.json",
        "form_url": None,
        "form_rotulo": None,
        "destaque": False,
        "pipeline_bolao": False,
        "pipeline_forms": None,
        "categoria": None,
    },
    {
        "slug": "arrecadacao_federal",
        "nome": "Arrecadação federal",
        "repo": "fonte-arrecadacao-federal",
        "pages_url": "https://arrecadacaofederal.gfvdata.com/",
        "caminho_dados": "docs/dados",
        "branch_dados": None,
        "workflow_arquivo": None,
        "json_meta_url": "https://arrecadacaofederal.gfvdata.com/dados/arrecadacao_federal.json",
        "form_url": None,
        "form_rotulo": None,
        "destaque": False,
        "pipeline_bolao": False,
        "pipeline_forms": None,
        "categoria": None,
    },
    {
        "slug": "credito_modalidade",
        "nome": "Crédito por modalidade",
        "repo": "fonte-credito-modalidade",
        "pages_url": "https://creditomodalidade.gfvdata.com/",
        "caminho_dados": "docs/dados",
        "branch_dados": None,
        "workflow_arquivo": None,
        "json_meta_url": "https://creditomodalidade.gfvdata.com/dados/credito_modalidade.json",
        "form_url": None,
        "form_rotulo": None,
        "destaque": False,
        "pipeline_bolao": False,
        "pipeline_forms": None,
        "categoria": None,
    },
    {
        "slug": "simulador_investimentos",
        "nome": "Simulador de investimentos",
        "repo": "simulador-investimentos",
        "pages_url": "https://gfvdata-web.github.io/simulador-investimentos/",
        "caminho_dados": "dados",
        "branch_dados": None,
        "workflow_arquivo": "atualizar-dados.yml",
        "json_meta_url": None,
        "form_url": None,
        "form_rotulo": None,
        "destaque": False,
        "pipeline_bolao": False,
        "pipeline_forms": None,
        "categoria": None,
    },
    {
        "slug": "chess_tracking",
        "nome": "Chess Tracking",
        "repo": "chess-tracking",
        "pages_url": "https://gfvdata-web.github.io/chess-tracking/",
        "caminho_dados": "docs/dados",
        "branch_dados": None,
        "workflow_arquivo": "atualizar-partidas.yml",
        "json_meta_url": None,
        "form_url": None,
        "form_rotulo": None,
        "destaque": False,
        "pipeline_bolao": False,
        "pipeline_forms": None,
        "categoria": None,
    },
    {
        "slug": "plants_care",
        "nome": "Plants Care",
        # Repositório privado (precisa do PAINEL_TOKEN_LEITURA); o site sai dele
        # direto para a Cloudflare (Worker de arquivos estáticos), sem GitHub Pages.
        "repo": "PlantsCare",
        "pages_url": "https://plantscare.gfvdata.com/",
        "caminho_dados": "data",
        "branch_dados": None,
        # Planilha do ESP32 → data/ → site, de hora em hora (07h–00h de Brasília).
        "workflow_arquivo": "sensor-sync.yml",
        "json_meta_url": None,
        # Envio de fotos de uma planta pelo celular: o Apps Script grava em inbox/
        # e o pipeline.yml faz ficha → Gemini → PDF → e-mail.
        "form_url": "https://forms.gle/y3uXaukJXmP9GMED7",
        "form_rotulo": "Enviar fotos",
        "destaque": False,
        "pipeline_bolao": False,
        "pipeline_forms": "pipeline.yml",
        "categoria": None,
    },
    {
        "slug": "eleicoes_2026",
        "nome": "Eleições 2026",
        "repo": "eleicoes-2026",
        "pages_url": "https://gfvdata-web.github.io/eleicoes-2026/",
        "caminho_dados": "docs/data",
        "branch_dados": None,
        # pages.yml só publica a cada push (sem agenda): cadastrado, geraria o
        # alerta "sem coleta há N dias" sempre que o repo ficasse parado.
        "workflow_arquivo": None,
        "json_meta_url": None,
        "form_url": None,
        "form_rotulo": None,
        "destaque": False,
        "pipeline_bolao": False,
        "pipeline_forms": None,
        "categoria": None,
    },
    {
        "slug": "painel_esportivo",
        "nome": "Painel esportivo",
        "repo": "painel-esportivo",
        "pages_url": "https://gfvdata-web.github.io/painel-esportivo/",
        # Os dados (cifrados) só existem no gh-pages, publicados à mão por
        # scripts/publicar.mjs.
        "caminho_dados": "dados",
        "branch_dados": "gh-pages",
        "workflow_arquivo": None,
        "json_meta_url": None,
        "form_url": None,
        "form_rotulo": None,
        "destaque": False,
        "pipeline_bolao": False,
        "pipeline_forms": None,
        "categoria": None,
    },
    {
        "slug": "dashboard_municipios",
        "nome": "Dashboard dos municípios",
        "repo": "dashboard-municipios",
        "pages_url": "https://gfvdata-web.github.io/dashboard-municipios/",
        "caminho_dados": "site/dados",
        "branch_dados": None,
        "workflow_arquivo": None,
        "json_meta_url": None,
        "form_url": None,
        "form_rotulo": None,
        "destaque": False,
        "pipeline_bolao": False,
        "pipeline_forms": None,
        "categoria": None,
    },
    {
        "slug": "var_vendo",
        "nome": "VarVendo",
        "repo": "var-vendo",
        "pages_url": "https://gfvdata-web.github.io/var-vendo/",
        "caminho_dados": "docs/data",
        "branch_dados": None,
        # pages.yml só publica a cada push (sem agenda), como no Eleições 2026.
        "workflow_arquivo": None,
        "json_meta_url": None,
        "form_url": None,
        "form_rotulo": None,
        "destaque": False,
        "pipeline_bolao": False,
        "pipeline_forms": None,
        "categoria": None,
    },
    {
        "slug": "evolucao_fotografia",
        "nome": "Evolução Fotografia",
        "repo": "evolucao-fotografia",
        "pages_url": "https://gfvdata-web.github.io/evolucao-fotografia/",
        # Sem dados no repo (as fotos ficam no aparelho): "Dados atualizados"
        # é a última versão publicada do site.
        "caminho_dados": "site",
        "branch_dados": None,
        # ci-pages.yml roda testes + deploy a cada push, sem agenda.
        "workflow_arquivo": None,
        "json_meta_url": None,
        "form_url": None,
        "form_rotulo": None,
        "destaque": False,
        "pipeline_bolao": False,
        "pipeline_forms": None,
        "categoria": None,
    },
]

# Google Forms de projetos sem site publicado para monitorar — viram o card
# "Outros forms" da grade. Só atalhos: nada é coletado sobre eles.
FORMS_AVULSOS = [
    {"nome": "Notas fiscais", "url": "https://forms.gle/ftgMMg1Lwpoi7j3Z8"},
]

CAMPOS_SITE = {
    "slug", "nome", "repo", "pages_url", "caminho_dados", "branch_dados", "workflow_arquivo",
    "json_meta_url", "form_url", "form_rotulo", "destaque", "pipeline_bolao", "pipeline_forms",
    "categoria",
}

# Valores aceitos em `categoria` (None = sem categoria, cor neutra).
CATEGORIAS = {"dados_abertos", "hobby", "curiosidade"}


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
        if site.get("pipeline_forms") and not site.get("form_url"):
            problemas.append(f"{rotulo}: pipeline_forms exige form_url")
        if site.get("pipeline_forms") and site.get("pipeline_bolao"):
            problemas.append(f"{rotulo}: pipeline_forms e pipeline_bolao não se combinam")
        if site.get("form_rotulo") and not site.get("form_url"):
            problemas.append(f"{rotulo}: form_rotulo exige form_url")
        if site.get("categoria") is not None and site.get("categoria") not in CATEGORIAS:
            problemas.append(f"{rotulo}: categoria desconhecida {site.get('categoria')!r}")
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
