"""Diz em que pé está o pipeline do Bolão F1 no status.json recém-gerado — usado
pelo job `vigiar-bolao` do atualizar-status.yml, que roda quando o Apps Script do
Forms avisa o painel de um palpite novo (repository_dispatch `bolao_palpite`).

O job repete: `python -m src.publicacao --so-bolao` → este script → commit (só se
mudou) → espera. Para quando a fase for `concluido` (ou `sem_execucao`) e o
painel volta para a rotina de 3 em 3 horas.

Uso: python -m src.vigia_bolao --desde <ISO> --anterior <status.json do último commit>

Saída (linhas no formato do $GITHUB_OUTPUT):
  fase=  sem_execucao  nenhuma execução do pipeline criada desde --desde (ainda)
         andamento     execução rodando ou deploy do Pages pendente — checar logo
         vigilia       Verificador esperando o resultado na Jolpica (até 5 h)
         concluido     todas as etapas em estado final (ok, erro, pulado, pendente)
  mudou= sim | nao     o card do Bolão mudou em relação ao --anterior; o
                       gerado_em sempre muda, então sem isso cada volta viraria
                       um commit (e um deploy do Pages)
"""

import argparse
import json
from datetime import datetime, timedelta, timezone

from .config import SITES
from .publicacao import SAIDA

SLUG_BOLAO = next(s["slug"] for s in SITES if s["pipeline_bolao"])

# Execução concluída e o deploy do Pages não apareceu nesse tempo: o pipeline
# não mudou o placar (nada a publicar), não adianta seguir esperando.
ESPERA_MAX_DEPLOY = timedelta(minutes=15)


def _data(iso):
    return datetime.fromisoformat(iso.replace("Z", "+00:00"))


def _bolao(status):
    if not status:
        return None
    return next((s for s in status.get("sites", []) if s["slug"] == SLUG_BOLAO), None)


def assinatura(site):
    """O que, se mudar, merece commit: execução, etapas e o último commit de dados."""
    if not site:
        return None
    execucao = site.get("ultima_execucao_pipeline") or {}
    return {
        "execucao": [execucao.get(k) for k in ("id", "status", "conclusao", "erro")],
        "etapas": [[e["chave"], e["estado"], e["detalhe"]] for e in site.get("etapas_pipeline") or []],
        "commit": (site.get("ultimo_commit_dados") or {}).get("sha"),
    }


def fase(site, desde, agora):
    execucao = (site or {}).get("ultima_execucao_pipeline") or {}
    if not execucao or execucao.get("erro") or _data(execucao["criado_em"]) < desde:
        return "sem_execucao"
    estados = {e["estado"] for e in site.get("etapas_pipeline") or []}
    if "aguardando" in estados:
        return "vigilia"
    if execucao["status"] != "completed":
        return "andamento"
    if "andamento" in estados:
        if agora - _data(execucao["atualizado_em"]) > ESPERA_MAX_DEPLOY:
            return "concluido"
        return "andamento"
    return "concluido"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--desde", required=True, help="só conta execuções criadas a partir daqui (ISO, UTC)")
    parser.add_argument("--anterior", required=True, help="status.json do último commit")
    args = parser.parse_args()

    atual = _bolao(json.loads(SAIDA.read_text(encoding="utf-8")))
    try:
        with open(args.anterior, encoding="utf-8") as arquivo:
            anterior = _bolao(json.load(arquivo))
    except (OSError, json.JSONDecodeError):
        anterior = None

    print(f"fase={fase(atual, _data(args.desde), datetime.now(timezone.utc))}")
    print(f"mudou={'sim' if assinatura(atual) != assinatura(anterior) else 'nao'}")


if __name__ == "__main__":
    main()
