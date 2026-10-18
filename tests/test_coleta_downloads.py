"""src/coleta_downloads.py: soma, robôs fora, série de 30 dias com zeros, site sem url, falhas."""

import contextlib
import copy
import io
import json
import unittest
from datetime import datetime, timezone
from unittest import mock

from tests import contrato
from tests.apoio import RedeFalsa, TesteBase, fixture, relogio_fixo
from src import coleta_downloads as cd
from src import config, publicacao

RELOGIOS = ("src.detalhes_site", "src.coleta_goatcounter", "src.coleta_downloads")
URL = "https://meiospagamento.gfvdata.com/contagem.json"


def coletar(**rede):
    with relogio_fixo("src.coleta_downloads"), RedeFalsa(**rede):
        return cd.coletar(URL)


class Bloco(unittest.TestCase):
    def test_soma_por_tipo_de_arquivo_e_de_cliente(self):
        bloco = coletar()
        self.assertEqual(bloco["total"], 8)  # 5 csv + 3 skill; "total" do JSON já exclui robôs
        self.assertEqual((bloco["csv"], bloco["skill"]), (5, 3))
        self.assertEqual((bloco["por_navegador"], bloco["por_script"]), (3, 5))  # robôs (4+1) fora
        self.assertEqual(bloco["desde"], "2026-10-08")

    def test_serie_tem_30_dias_continuos_ate_hoje_em_brasilia(self):
        serie = coletar()["serie_diaria"]
        self.assertEqual(len(serie), 30)
        self.assertEqual(serie[0]["data"], "2026-09-14")
        self.assertEqual(serie[-1]["data"], "2026-10-13")  # AGORA = 13/10 12:00 UTC = 09:00 em Brasília
        com_download = {d["data"]: d["downloads"] for d in serie if d["downloads"]}
        # soma dos dois arquivos por dia; os demais dias vêm com zero
        self.assertEqual(com_download, {"2026-10-08": 2, "2026-10-12": 5, "2026-10-13": 1})

    def test_virada_do_dia_usa_brasilia_e_nao_utc(self):
        # 01:00 UTC de 14/10 ainda é 22:00 de 13/10 em Brasília
        bloco = cd.montar_bloco(fixture("contagem.json"), datetime(2026, 10, 14, 1, 0, tzinfo=timezone.utc))
        self.assertEqual(bloco["serie_diaria"][-1]["data"], "2026-10-13")

    def test_dia_antigo_fora_da_janela_nao_entra(self):
        dados = copy.deepcopy(fixture("contagem.json"))
        dados["arquivos"][0]["serie_30d"].append({"dia": "2026-08-01", "total": 9})
        bloco = cd.montar_bloco(dados)
        self.assertNotIn("2026-08-01", [d["data"] for d in bloco["serie_diaria"]])

    def test_sem_arquivos_tudo_zero(self):
        bloco = cd.montar_bloco({"desde": None, "arquivos": []})
        self.assertEqual((bloco["total"], bloco["desde"]), (0, None))
        self.assertTrue(all(d["downloads"] == 0 for d in bloco["serie_diaria"]))


class Falhas(unittest.TestCase):
    def test_falha_de_rede_devolve_none_e_avisa_no_stderr(self):
        erro = io.StringIO()
        with contextlib.redirect_stderr(erro):
            self.assertIsNone(coletar(sem_contagem=True))
        self.assertIn("[downloads] falha ao ler", erro.getvalue())

    def test_json_invalido_devolve_none(self):
        erro = io.StringIO()
        with mock.patch("src.coleta_downloads.get_json", side_effect=json.JSONDecodeError("x", "", 0)), \
             contextlib.redirect_stderr(erro):
            self.assertIsNone(cd.coletar(URL))
        self.assertIn("[downloads]", erro.getvalue())

    def test_json_fora_do_formato_devolve_none(self):
        for lixo in ({}, {"arquivos": 3}, {"arquivos": [{"total": 1}]}, [], None):
            erro = io.StringIO()
            with mock.patch("src.coleta_downloads.get_json", return_value=lixo), contextlib.redirect_stderr(erro):
                self.assertIsNone(cd.coletar(URL), lixo)
            self.assertIn("fora do formato", erro.getvalue())


class NaPublicacao(TesteBase):
    def setUp(self):
        super().setUp()
        self.patch("src.publicacao.SAIDA", self.tmp / "dados" / "status.json")
        self.patch("src.historico.PASTA", self.tmp / "dados" / "historico")
        ambiente = mock.patch.dict("os.environ", {"GOATCOUNTER_TOKEN": "tok-teste"})
        ambiente.start()
        self.addCleanup(ambiente.stop)

    def montar(self, **rede):
        with relogio_fixo(*RELOGIOS), RedeFalsa(**rede) as falsa:
            self.rede = falsa
            return publicacao.montar()

    @staticmethod
    def site(status, slug):
        return next(s for s in status["sites"] if s["slug"] == slug)

    def test_so_os_sites_com_contagem_url_ganham_o_bloco(self):
        status = self.montar()
        com_url = {s["slug"] for s in config.SITES if s["contagem_url"]}
        self.assertEqual(com_url, {"meios_pagamento_mensal", "arrecadacao_federal", "credito_modalidade"})
        self.assertEqual({s["slug"] for s in status["sites"] if "downloads" in s}, com_url)
        self.assertEqual(contrato.problemas_status(status, config.SITES), [])

    def test_site_sem_url_nao_consulta_contagem(self):
        sites = [copy.deepcopy(s) for s in config.SITES]
        for s in sites:
            s["contagem_url"] = None
        with mock.patch.object(publicacao, "SITES", sites):
            status = self.montar()
        self.assertTrue(all("downloads" not in s for s in status["sites"]))
        self.assertFalse([u for u in self.rede.chamadas if "contagem" in u])

    def test_falha_de_rede_reaproveita_o_downloads_anterior(self):
        anterior = {"sites": [{"slug": "arrecadacao_federal", "downloads": {"total": 77, "serie_diaria": []}}]}
        self.patch("src.publicacao._status_anterior", lambda: anterior)
        with contextlib.redirect_stderr(io.StringIO()):
            status = self.montar(sem_contagem=True)
        self.assertEqual(self.site(status, "arrecadacao_federal")["downloads"]["total"], 77)
        self.assertNotIn("downloads", self.site(status, "credito_modalidade"))  # sem anterior: sem bloco

    def test_coleta_nova_vence_o_anterior(self):
        anterior = {"sites": [{"slug": "arrecadacao_federal", "downloads": {"total": 77, "serie_diaria": []}}]}
        self.patch("src.publicacao._status_anterior", lambda: anterior)
        self.assertEqual(self.site(self.montar(), "arrecadacao_federal")["downloads"]["total"], 8)


if __name__ == "__main__":
    unittest.main()
