"""Contrato do status.json e do histórico: o que a página (docs/js/lista.js e app.js), o Worker
(problemaNoStatus) e o app Android (Modelos.kt) esperam. Fixado aqui à mão a partir da docstring
de src/publicacao.py, de src/historico.py e do que os JS leem — mudou o formato, mudar aqui
(e nos dois lados) no mesmo commit.

problemas_status(status) devolve a lista de problemas (vazia = ok); serve tanto para o
docs/dados/status.json publicado quanto para o que a coleta gera com a rede falsa.
"""

import re
from datetime import datetime

ESTADOS_ETAPA = {"ok", "andamento", "aguardando", "erro", "pendente", "pulado"}
NIVEIS_ALERTA = {"aviso", "erro"}
CATEGORIAS_VALIDAS = {"dados_abertos", "hobby", "curiosidade"}  # = src/config.py CATEGORIAS
RE_HISTORICO_ARQUIVO = re.compile(r"^dados/historico/([A-Za-z0-9_-]+)\.json$")

# Todo nome de campo que a página, o Worker ou o app podem ler. O teste das páginas exige que
# toda propriedade snake_case usada nos JS esteja aqui (renomeou no JS sem renomear aqui = falha).
CAMPOS_CONHECIDOS = {
    "meta", "gerado_em", "descricao", "sites", "forms_avulsos", "nome", "url",
    "slug", "repo", "pages_url", "destaque", "categoria", "na_lista", "form_url", "form_rotulo", "ultimo_commit_dados",
    "site_no_ar", "periodo_publicado", "ultima_execucao_pipeline", "acesso", "downloads", "rodada_mais_recente",
    "em_vigilia", "etapas_pipeline", "detalhes", "alertas", "pipeline_forms", "historico_arquivo",
    # site_no_ar / erro de coleta
    "ok", "status_http", "erro",
    # commit
    "data", "mensagem", "sha",
    # execução
    "id", "evento", "status", "conclusao", "criado_em", "atualizado_em", "duracao_s", "motivo", "avisos",
    "job", "passo", "nivel", "texto", "inicio", "fim",
    # acesso
    "visitantes_unicos", "serie_diaria", "visitantes",
    # downloads
    "total", "skill", "csv", "por_navegador", "por_script", "desde",
    # etapas
    "chave", "titulo", "estado", "detalhe", "quando",
    # detalhes
    "coleta", "commits_dados", "deploy_pages", "workflow", "url_workflow", "agenda", "cron",
    "historico", "ultimo_sucesso", "ultima_falha", "passos_ultima", "execucao", "etapas",
    # histórico
    "execucoes",
}

CAMPOS_SITE_OBRIGATORIOS = ("slug", "nome", "repo", "pages_url", "destaque", "ultimo_commit_dados", "site_no_ar")


def _iso(valor):
    try:
        datetime.fromisoformat(valor.replace("Z", "+00:00"))
        return True
    except (AttributeError, ValueError):
        return False


def _tipo(problemas, onde, objeto, campo, tipos, opcional=False):
    if not isinstance(objeto, dict):
        problemas.append(f"{onde}: não é objeto")
        return
    if campo not in objeto:
        if not opcional:
            problemas.append(f"{onde}: falta '{campo}'")
        return
    valor = objeto[campo]
    aceitos = tipos if isinstance(tipos, tuple) else (tipos,)
    # bool é subclasse de int: só aceita bool onde se pediu bool
    if isinstance(valor, bool) and bool not in aceitos:
        problemas.append(f"{onde}.{campo}: bool inesperado")
    elif not isinstance(valor, aceitos):
        problemas.append(f"{onde}.{campo}: tipo {type(valor).__name__}, esperado {tipos}")


def _commit(problemas, onde, commit):
    if not isinstance(commit, dict):
        problemas.append(f"{onde}: não é objeto")
        return
    if "erro" in commit:
        _tipo(problemas, onde, commit, "erro", str)
        return
    for campo in ("data", "mensagem", "sha", "url"):
        _tipo(problemas, onde, commit, campo, str)
    if "data" in commit and not _iso(commit["data"]):
        problemas.append(f"{onde}.data: não é ISO")


def _execucao(problemas, onde, execucao, completa=True):
    if not isinstance(execucao, dict):
        problemas.append(f"{onde}: não é objeto")
        return
    if "erro" in execucao:
        _tipo(problemas, onde, execucao, "erro", str)
        return
    if completa:
        _tipo(problemas, onde, execucao, "id", int)
        _tipo(problemas, onde, execucao, "evento", str)
    _tipo(problemas, onde, execucao, "status", str)
    _tipo(problemas, onde, execucao, "conclusao", (str, type(None)))
    _tipo(problemas, onde, execucao, "criado_em", str)
    _tipo(problemas, onde, execucao, "atualizado_em", str)
    _tipo(problemas, onde, execucao, "url", str)


def _etapas(problemas, onde, etapas):
    if etapas is None:
        return
    if not isinstance(etapas, list):
        problemas.append(f"{onde}: não é lista")
        return
    for i, etapa in enumerate(etapas):
        aqui = f"{onde}[{i}]"
        for campo in ("chave", "titulo", "estado", "detalhe"):
            _tipo(problemas, aqui, etapa, campo, str)
        _tipo(problemas, aqui, etapa, "quando", (str, type(None)))
        if etapa.get("estado") not in ESTADOS_ETAPA:
            problemas.append(f"{aqui}.estado inválido: {etapa.get('estado')!r}")


def _coleta(problemas, onde, coleta):
    if coleta is None:
        return
    for campo in ("workflow", "url_workflow"):
        _tipo(problemas, onde, coleta, campo, str)
    for campo in ("agenda", "historico", "passos_ultima"):
        _tipo(problemas, onde, coleta, campo, list)
    for i, item in enumerate(coleta.get("agenda", [])):
        _tipo(problemas, f"{onde}.agenda[{i}]", item, "cron", str)
        _tipo(problemas, f"{onde}.agenda[{i}]", item, "descricao", str)
    for i, item in enumerate(coleta.get("historico", [])):
        _execucao(problemas, f"{onde}.historico[{i}]", item)
    for campo in ("ultimo_sucesso", "ultima_falha"):
        _tipo(problemas, onde, coleta, campo, (dict, type(None)))
        if isinstance(coleta.get(campo), dict):
            _execucao(problemas, f"{onde}.{campo}", coleta[campo])
    falha = coleta.get("ultima_falha")
    if isinstance(falha, dict):
        _tipo(problemas, f"{onde}.ultima_falha", falha, "motivo", dict)
    for i, p in enumerate(coleta.get("passos_ultima", [])):
        _tipo(problemas, f"{onde}.passos_ultima[{i}]", p, "nome", str)
        _tipo(problemas, f"{onde}.passos_ultima[{i}]", p, "conclusao", (str, type(None)))


def problemas_status(status, cadastro=None):
    """Problemas do status. `cadastro` (src.config.SITES) liga as checagens "campo X existe
    porque o cadastro pede": pipeline_bolao, pipeline_forms, form_url, workflow."""
    problemas = []
    if not isinstance(status, dict):
        return ["status não é objeto"]
    meta = status.get("meta")
    if not isinstance(meta, dict):
        return ["falta meta"]
    _tipo(problemas, "meta", meta, "gerado_em", str)
    if not _iso(meta.get("gerado_em")):
        problemas.append("meta.gerado_em não é ISO")
    _tipo(problemas, "meta", meta, "descricao", str)

    sites = status.get("sites")
    if not isinstance(sites, list) or not sites:
        problemas.append("sites vazio ou ausente")
        sites = []
    por_slug = {c["slug"]: c for c in cadastro or []}
    destaques = 0
    for i, site in enumerate(sites):
        onde = f"sites[{site.get('slug', i)}]"
        for campo in ("slug", "nome", "repo", "pages_url"):
            _tipo(problemas, onde, site, campo, str)
        _tipo(problemas, onde, site, "destaque", bool)
        destaques += bool(site.get("destaque"))
        if "ultimo_commit_dados" in site:
            _commit(problemas, f"{onde}.ultimo_commit_dados", site["ultimo_commit_dados"])
        else:
            problemas.append(f"{onde}: falta 'ultimo_commit_dados'")
        noar = site.get("site_no_ar")
        if not isinstance(noar, dict):
            problemas.append(f"{onde}: falta 'site_no_ar'")
        else:
            _tipo(problemas, f"{onde}.site_no_ar", noar, "ok", bool)
            _tipo(problemas, f"{onde}.site_no_ar", noar, "status_http", int, opcional=True)
        for campo in ("form_url", "form_rotulo"):
            _tipo(problemas, onde, site, campo, str, opcional=True)
        # categoria é sempre enviada (null = sem categoria): a página escolhe a cor a partir dela.
        if "categoria" in site and site["categoria"] is not None and site["categoria"] not in CATEGORIAS_VALIDAS:
            problemas.append(f"{onde}: categoria desconhecida {site['categoria']!r}")
        # na_lista (False = botão no topo, sem cartão): opcional para o JSON antigo, mas bool quando vem.
        _tipo(problemas, onde, site, "na_lista", bool, opcional=True)
        if site.get("periodo_publicado") is not None and "periodo_publicado" in site:
            for campo in ("inicio", "fim"):
                _tipo(problemas, f"{onde}.periodo_publicado", site["periodo_publicado"], campo, str)
        if "ultima_execucao_pipeline" in site:
            _execucao(problemas, f"{onde}.ultima_execucao_pipeline", site["ultima_execucao_pipeline"])
        if "acesso" in site:
            acesso = site["acesso"]
            _tipo(problemas, f"{onde}.acesso", acesso, "visitantes_unicos", (int, type(None)))
            _tipo(problemas, f"{onde}.acesso", acesso, "serie_diaria", list)
            for j, dia in enumerate(acesso.get("serie_diaria", [])):
                _tipo(problemas, f"{onde}.acesso.serie_diaria[{j}]", dia, "data", str)
                _tipo(problemas, f"{onde}.acesso.serie_diaria[{j}]", dia, "visitantes", int)
        if "downloads" in site:
            baixados = site["downloads"]
            onde_d = f"{onde}.downloads"
            for campo in ("total", "skill", "csv", "por_navegador", "por_script"):
                _tipo(problemas, onde_d, baixados, campo, int)
            _tipo(problemas, onde_d, baixados, "desde", (str, type(None)))
            _tipo(problemas, onde_d, baixados, "serie_diaria", list)
            for j, dia in enumerate(baixados.get("serie_diaria", [])):
                _tipo(problemas, f"{onde_d}.serie_diaria[{j}]", dia, "data", str)
                _tipo(problemas, f"{onde_d}.serie_diaria[{j}]", dia, "downloads", int)
        if "etapas_pipeline" in site:
            _etapas(problemas, f"{onde}.etapas_pipeline", site["etapas_pipeline"])
        if "detalhes" in site:
            detalhes = site["detalhes"]
            _coleta(problemas, f"{onde}.detalhes.coleta", detalhes.get("coleta"))
            _tipo(problemas, f"{onde}.detalhes", detalhes, "commits_dados", list)
            for j, c in enumerate(detalhes.get("commits_dados", [])):
                _commit(problemas, f"{onde}.detalhes.commits_dados[{j}]", c)
            _tipo(problemas, f"{onde}.detalhes", detalhes, "deploy_pages", (dict, type(None)))
        if "alertas" in site:
            _tipo(problemas, onde, site, "alertas", list)
            for j, alerta in enumerate(site["alertas"]):
                _tipo(problemas, f"{onde}.alertas[{j}]", alerta, "texto", str)
                if alerta.get("nivel") not in NIVEIS_ALERTA:
                    problemas.append(f"{onde}.alertas[{j}].nivel inválido: {alerta.get('nivel')!r}")
        if "pipeline_forms" in site:
            bloco = site["pipeline_forms"]
            _tipo(problemas, f"{onde}.pipeline_forms", bloco, "workflow", str)
            _execucao(problemas, f"{onde}.pipeline_forms.execucao", bloco.get("execucao"))
            _etapas(problemas, f"{onde}.pipeline_forms.etapas", bloco.get("etapas"))
        if site.get("historico_arquivo") is not None:  # None = ainda não existe (primeira rodada com a API fora)
            achado = RE_HISTORICO_ARQUIVO.match(site["historico_arquivo"])
            if not achado or achado.group(1) != site.get("slug"):
                problemas.append(f"{onde}.historico_arquivo fora do padrão: {site['historico_arquivo']!r}")

        cad = por_slug.get(site.get("slug"))
        if cad:
            problemas += _conforme_cadastro(onde, site, cad)
    if destaques > 1:
        problemas.append("mais de um site em destaque")

    forms = status.get("forms_avulsos")
    if not isinstance(forms, list):
        problemas.append("forms_avulsos ausente")
    else:
        for i, form in enumerate(forms):
            _tipo(problemas, f"forms_avulsos[{i}]", form, "nome", str)
            _tipo(problemas, f"forms_avulsos[{i}]", form, "url", str)
    return problemas


def _conforme_cadastro(onde, site, cad):
    """O cadastro (config.py) decide que blocos o site tem."""
    problemas = []
    for campo in ("nome", "repo", "pages_url", "destaque"):
        if site.get(campo) != cad[campo]:
            problemas.append(f"{onde}.{campo} diferente do cadastro")
    if bool(cad["form_url"]) != ("form_url" in site):
        problemas.append(f"{onde}: form_url presente/ausente diferente do cadastro")
    if cad["form_url"] and site.get("form_url") != cad["form_url"]:
        problemas.append(f"{onde}.form_url diferente do cadastro")
    if bool(cad["json_meta_url"]) != ("periodo_publicado" in site):
        problemas.append(f"{onde}: periodo_publicado presente/ausente diferente do cadastro")
    if bool(cad["workflow_arquivo"]) != ("ultima_execucao_pipeline" in site):
        problemas.append(f"{onde}: ultima_execucao_pipeline presente/ausente diferente do cadastro")
    if bool(cad["workflow_arquivo"]) != ("historico_arquivo" in site):
        problemas.append(f"{onde}: historico_arquivo presente/ausente diferente do cadastro")
    if bool(cad["pipeline_forms"]) != ("pipeline_forms" in site):
        problemas.append(f"{onde}: pipeline_forms presente/ausente diferente do cadastro")
    if cad["pipeline_bolao"]:
        for campo in ("rodada_mais_recente", "em_vigilia"):
            if campo not in site:
                problemas.append(f"{onde}: falta '{campo}' (pipeline_bolao)")
        if "detalhes" in site or "alertas" in site:
            problemas.append(f"{onde}: o Bolão não tem detalhes/alertas")
    else:
        for campo in ("detalhes", "alertas"):
            if campo not in site:
                problemas.append(f"{onde}: falta '{campo}'")
        for campo in ("rodada_mais_recente", "em_vigilia", "etapas_pipeline"):
            if campo in site:
                problemas.append(f"{onde}: '{campo}' só existe no Bolão")
    return problemas


def problemas_historico(historico, slug=None):
    problemas = []
    if not isinstance(historico, dict):
        return ["histórico não é objeto"]
    for campo in ("slug", "nome", "workflow", "url_workflow", "atualizado_em"):
        _tipo(problemas, "histórico", historico, campo, str)
    if slug and historico.get("slug") != slug:
        problemas.append(f"slug do histórico ({historico.get('slug')!r}) != {slug!r}")
    execucoes = historico.get("execucoes")
    if not isinstance(execucoes, list):
        return problemas + ["histórico: falta 'execucoes'"]
    anterior = None
    for i, e in enumerate(execucoes):
        onde = f"execucoes[{i}]"
        _tipo(problemas, onde, e, "id", int)
        _tipo(problemas, onde, e, "criado_em", str)
        _tipo(problemas, onde, e, "evento", str)
        _tipo(problemas, onde, e, "conclusao", (str, type(None)))
        _tipo(problemas, onde, e, "duracao_s", int)
        _tipo(problemas, onde, e, "url", str)
        _tipo(problemas, onde, e, "avisos", (list, type(None)))
        for a in e.get("avisos") or []:
            _tipo(problemas, f"{onde}.avisos", a, "nivel", str)
            _tipo(problemas, f"{onde}.avisos", a, "mensagem", str)
        if e.get("conclusao") in ("failure", "timed_out", "startup_failure", "cancelled") and "motivo" not in e:
            problemas.append(f"{onde}: falha sem 'motivo'")
        if anterior is not None and isinstance(e.get("criado_em"), str) and e["criado_em"] > anterior:
            problemas.append(f"{onde}: fora de ordem (mais recente primeiro)")
        anterior = e.get("criado_em")
    return problemas
