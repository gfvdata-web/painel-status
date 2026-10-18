"""Regera tests/fixtures/status_exemplo.json e historico_exemplo.json (a saída da coleta com a API falsa).

Uso: python -m tests.regenerar_fixtures
Os testes comparam a coleta com estes arquivos ("golden"): mudou o formato do status.json de
propósito, regenere, confira o diff e commite junto. Os testes de página (tests/paginas.test.mjs)
desenham estes mesmos arquivos.
"""

import json
import os
import tempfile
from pathlib import Path
from unittest import mock

from tests.apoio import FIXTURES, RedeFalsa, relogio_fixo

RELOGIOS = ("src.detalhes_site", "src.coleta_goatcounter", "src.coleta_github", "src.coleta_downloads")


def gerar():
    """(status, historico) da coleta com a API falsa, sem escrever em docs/dados."""
    from src import historico, publicacao

    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        with mock.patch.object(publicacao, "SAIDA", tmp / "status.json"), \
             mock.patch.object(historico, "PASTA", tmp / "historico"), \
             mock.patch.dict(os.environ, {"GOATCOUNTER_TOKEN": "tok-teste"}), \
             relogio_fixo(*RELOGIOS), RedeFalsa():
            status = publicacao.montar()
        arquivo = tmp / "historico" / "chess_tracking.json"
        return status, json.loads(arquivo.read_text(encoding="utf-8"))


def texto(dados):
    return json.dumps(dados, ensure_ascii=False, indent=2) + "\n"


if __name__ == "__main__":
    status, hist = gerar()
    (FIXTURES / "status_exemplo.json").write_text(texto(status), encoding="utf-8", newline="\n")
    (FIXTURES / "historico_exemplo.json").write_text(texto(hist), encoding="utf-8", newline="\n")
    print("fixtures regeneradas")
