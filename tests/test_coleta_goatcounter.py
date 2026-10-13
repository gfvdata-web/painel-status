"""src/coleta_goatcounter.py: visitantes por repositório (prefixo do caminho), sem token, falhas."""

import contextlib
import io
import os
import unittest
import urllib.error
from unittest import mock

from tests.apoio import RedeFalsa, fixture, relogio_fixo
from src import coleta_goatcounter as gc


def com_token(token="tok"):
    return mock.patch.dict(os.environ, {"GOATCOUNTER_TOKEN": token})


class Estatisticas(unittest.TestCase):
    def test_sem_token_nenhum_site_tem_acesso(self):
        with mock.patch.dict(os.environ, {}, clear=True), RedeFalsa() as rede:
            self.assertEqual(gc.estatisticas_por_repo(["a", "b"]), {"a": None, "b": None})
        self.assertEqual(rede.chamadas, [])

    def test_token_em_branco_conta_como_sem_token(self):
        with com_token("   "), RedeFalsa() as rede:
            self.assertEqual(gc.estatisticas_por_repo(["a"]), {"a": None})
        self.assertEqual(rede.chamadas, [])

    def test_visitantes_e_serie_dos_ultimos_30_dias(self):
        with com_token(), relogio_fixo("src.coleta_goatcounter"), RedeFalsa() as rede:
            r = gc.estatisticas_por_repo(["chess-tracking", "page-bolao-formula1", "var-vendo", "sem-visita"])
        self.assertEqual(r["chess-tracking"]["visitantes_unicos"], 123)
        # 2025-12-31 fica fora (mais de 30 dias antes de 13/10/2026)
        self.assertEqual(r["chess-tracking"]["serie_diaria"], [
            {"data": "2026-10-01", "visitantes": 4},
            {"data": "2026-10-02", "visitantes": 6},
            {"data": "2026-10-12", "visitantes": 9},
        ])
        self.assertIsNone(r["sem-visita"])  # repo sem caminho conhecido: sem bloco de acesso
        self.assertIsNotNone(r["var-vendo"])  # caminho "/var-vendo" sem barra final também conta

    def test_prefixo_nao_confunde_repos_parecidos(self):
        caminhos = fixture("goatcounter_paths.json")["paths"]
        self.assertEqual(gc._ids_do_site(caminhos, "chess-tracking"), [3])
        self.assertEqual(gc._ids_do_site(caminhos, "page-bolao-formula1"), [1, 2])
        self.assertEqual(gc._ids_do_site(caminhos, "var-vendo"), [5])

    def test_ids_do_repo_vao_na_consulta(self):
        with com_token(), relogio_fixo("src.coleta_goatcounter"), RedeFalsa() as rede:
            gc.estatisticas_por_repo(["page-bolao-formula1"])
        consulta = [u for u in rede.chamadas if "/stats/total" in u][0]
        self.assertIn("include_paths=1&include_paths=2", consulta)

    def test_falha_nos_paths_devolve_none_e_avisa_no_stderr(self):
        erro = io.StringIO()
        with com_token(), RedeFalsa(sem_goatcounter=True), contextlib.redirect_stderr(erro):
            self.assertIsNone(gc.estatisticas_por_repo(["a"]))
        self.assertIn("[goatcounter] falha ao buscar /paths", erro.getvalue())

    def test_falha_so_nas_stats_de_um_repo_nao_derruba_os_outros(self):
        original = RedeFalsa.get_json

        def get_json(self, url, headers=None):
            if "/stats/total" in url and "include_paths=3" in url:
                raise urllib.error.URLError("falhou")
            return original(self, url, headers)

        erro = io.StringIO()
        with com_token(), relogio_fixo("src.coleta_goatcounter"), \
             mock.patch.object(RedeFalsa, "get_json", get_json), RedeFalsa(), contextlib.redirect_stderr(erro):
            r = gc.estatisticas_por_repo(["chess-tracking", "page-bolao-formula1"])
        self.assertIsNone(r["chess-tracking"])
        self.assertEqual(r["page-bolao-formula1"]["visitantes_unicos"], 123)
        self.assertIn("stats de chess-tracking", erro.getvalue())

    def test_paginacao_de_paths(self):
        paginas = [
            {"paths": [{"id": 1, "path": "/a/"}], "more": True},
            {"paths": [{"id": 2, "path": "/a/x"}], "more": False},
        ]
        urls = []

        def get_json(url, token):
            urls.append(url)
            return paginas[len(urls) - 1]

        with mock.patch.object(gc, "_get_json", get_json):
            caminhos = gc._todos_os_caminhos("https://gfvdata.goatcounter.com/api/v0", "t")
        self.assertEqual([c["id"] for c in caminhos], [1, 2])
        self.assertIn("After=1", urls[1])

    def test_dia_sem_daily_vira_zero(self):
        dados = {"total": 1, "stats": [{"day": "2026-10-12"}]}
        with com_token(), relogio_fixo("src.coleta_goatcounter"), \
             mock.patch.object(gc, "_get_json", lambda url, t: {"paths": [{"id": 1, "path": "/r/"}]} if "/paths" in url else dados):
            r = gc.estatisticas_por_repo(["r"])
        self.assertEqual(r["r"]["serie_diaria"], [{"data": "2026-10-12", "visitantes": 0}])


if __name__ == "__main__":
    unittest.main()
