"""Diz em que pé está o pipeline do Forms (sites com pipeline_forms, hoje o
PlantsCare) no status.json recém-gerado — usado pelo job `vigiar-forms` do
atualizar-status.yml, que roda quando o Apps Script do Forms avisa o painel de um
envio novo (repository_dispatch `plantas_envio`).

O job repete: `python -m src.publicacao --so-forms` → este script → commit (só se
mudou) → espera 1 min. Para quando a fase for `concluido` (ou `sem_execucao` por
muito tempo) e o painel volta para a rotina de 3 em 3 horas. Mesmo desenho do
vigia_bolao.py, sem a vigília da Jolpica nem o deploy do Pages.

Uso: python -m src.vigia_forms --desde <ISO> --anterior <status.json do último commit>

Saída (linhas no formato do $GITHUB_OUTPUT):
  fase=  sem_execucao  nenhuma execução do pipeline criada desde --desde (ainda)
         andamento     alguma execução na fila ou rodando
         concluido     todas as execuções desde --desde terminaram
  mudou= sim | nao     algum card com pipeline_forms mudou em relação ao --anterior
                       (o gerado_em sempre muda, então sem isso cada volta viraria
                       um commit)
"""

import argparse
import json
from datetime import datetime

from .config import SITES
from .publicacao import SAIDA

SLUGS_FORMS = {s["slug"] for s in SITES if s["pipeline_forms"]}


def _data(iso):
    return datetime.fromisoformat(iso.replace("Z", "+00:00"))


def _sites(status):
    if not status:
        return {}
    return {s["slug"]: s for s in status.get("sites", []) if s["slug"] in SLUGS_FORMS}


def assinatura(status):
    """O que, se mudar, merece commit: execução e etapas de cada pipeline do Forms."""
    resultado = {}
    for slug, site in _sites(status).items():
        bloco = site.get("pipeline_forms") or {}
        execucao = bloco.get("execucao") or {}
        resultado[slug] = {
            "execucao": [execucao.get(k) for k in ("id", "status", "conclusao", "erro")],
            "etapas": [[e["chave"], e["estado"]] for e in bloco.get("etapas") or []],
        }
    return resultado


def fase(status, desde):
    recentes = []
    for site in _sites(status).values():
        execucao = (site.get("pipeline_forms") or {}).get("execucao") or {}
        if execucao and not execucao.get("erro") and _data(execucao["criado_em"]) >= desde:
            recentes.append(execucao)
    if not recentes:
        return "sem_execucao"
    if any(e["status"] != "completed" for e in recentes):
        return "andamento"
    return "concluido"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--desde", required=True, help="só conta execuções criadas a partir daqui (ISO, UTC)")
    parser.add_argument("--anterior", required=True, help="status.json do último commit")
    args = parser.parse_args()

    atual = json.loads(SAIDA.read_text(encoding="utf-8"))
    try:
        with open(args.anterior, encoding="utf-8") as arquivo:
            anterior = json.load(arquivo)
    except (OSError, json.JSONDecodeError):
        anterior = None

    print(f"fase={fase(atual, _data(args.desde))}")
    print(f"mudou={'sim' if assinatura(atual) != assinatura(anterior) else 'nao'}")


if __name__ == "__main__":
    main()
