"""Histórico acumulado das execuções do workflow de cada site que tem um (inclusive
o pipeline do Bolão F1).

O status.json só guarda as execuções recentes; este módulo grava, a cada rodada,
docs/dados/historico/<slug>.json com toda execução concluída já vista — para
reparos e análises depois (o GitHub apaga os logs em 90 dias; o motivo de uma
falha fica guardado aqui). A página abre esse arquivo sob demanda: pelo pop-up do
card na grade e pelo link "Histórico de execuções" no card do Bolão F1.

Formato:
  slug, nome, workflow, url_workflow, atualizado_em
  execucoes[]  mais recente primeiro, no máximo LIMITE_EXECUCOES:
      id, criado_em, evento, conclusao, duracao_s, url
      avisos   [{nivel, mensagem}] — anotações da execução (::warning:: etc.), sem
               filtro: inclui as do próprio GitHub (ex.: aviso de Node.js), que a
               página agrupa como "recorrentes"; None se não deu para ler
      motivo   {job, passo, mensagem} — só nas falhas (formato de detalhes_site.py)

Só execuções concluídas entram. Cada execução nova custa 1 chamada (jobs) + 1 por
job (anotações) + o log, se falhou; por isso no máximo MAX_NOVAS_POR_RODADA por
site a cada rodada — as que ficarem para trás entram nas rodadas seguintes.
"""

import json
from pathlib import Path

from . import coleta_github as gh
from .detalhes_site import CONCLUSOES_FALHA, duracao_s, motivo_falha

PASTA = Path(__file__).resolve().parent.parent / "docs" / "dados" / "historico"
LIMITE_EXECUCOES = 500
MAX_NOVAS_POR_RODADA = 40

# Anotação que o GitHub põe em todo passo que sai com erro; o motivo real já vem do log.
GENERICA_SAIDA = "Process completed with exit code"


def caminho_publicado(slug):
    """Caminho relativo a docs/, como a página faz o fetch."""
    return f"dados/historico/{slug}.json"


def _ler(arquivo):
    if not arquivo.exists():
        return []
    try:
        return json.loads(arquivo.read_text(encoding="utf-8")).get("execucoes", [])
    except (json.JSONDecodeError, OSError):
        return []


def _detalhar(owner, repo, execucao):
    jobs = gh.jobs_execucao(owner, repo, execucao["id"])
    avisos = None
    if jobs is not None:
        avisos = []
        for job in jobs:
            anotacoes = gh.anotacoes_job(owner, repo, job["id"])
            if anotacoes is None:
                avisos = None
                break
            avisos += [a for a in anotacoes if not a["mensagem"].startswith(GENERICA_SAIDA)]
    registro = {
        "id": execucao["id"],
        "criado_em": execucao["criado_em"],
        "evento": execucao["evento"],
        "conclusao": execucao["conclusao"],
        "duracao_s": duracao_s(execucao),
        "url": execucao["url"],
        "avisos": avisos,
    }
    if execucao["conclusao"] in CONCLUSOES_FALHA:
        registro["motivo"] = motivo_falha(owner, repo, execucao, jobs or [])
    return registro


def atualizar(owner, site, execucoes):
    """Acrescenta ao histórico do site as execuções concluídas ainda não gravadas.
    `execucoes` é a lista de coleta_github.execucoes_workflow (ou {"erro"}: nada muda).
    Devolve o caminho publicado do arquivo, ou None se ele não existe."""
    arquivo = PASTA / f"{site['slug']}.json"
    gravadas = _ler(arquivo)
    if isinstance(execucoes, list):
        vistas = {e["id"] for e in gravadas}
        novas = [e for e in execucoes if e["status"] == "completed" and e["id"] not in vistas]
        novas = [_detalhar(owner, site["repo"], e) for e in novas[:MAX_NOVAS_POR_RODADA]]
        if novas:
            gravadas = sorted(novas + gravadas, key=lambda e: e["criado_em"], reverse=True)
            gravadas = gravadas[:LIMITE_EXECUCOES]
            PASTA.mkdir(parents=True, exist_ok=True)
            arquivo.write_text(json.dumps({
                "slug": site["slug"],
                "nome": site["nome"],
                "workflow": site["workflow_arquivo"],
                "url_workflow": f"https://github.com/{owner}/{site['repo']}/actions/workflows/{site['workflow_arquivo']}",
                "atualizado_em": gh.agora_iso(),
                "execucoes": gravadas,
            }, ensure_ascii=False, indent=1), encoding="utf-8")
    return caminho_publicado(site["slug"]) if arquivo.exists() else None
