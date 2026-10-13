"""src/historico.py: histórico acumulado por site (docs/dados/historico/<slug>.json), em pasta temporária."""

import json
import unittest

from tests import contrato
from tests.apoio import RedeFalsa, TesteBase, execucao
from src import historico

SITE = {"slug": "exemplo", "nome": "Exemplo", "repo": "exemplo", "workflow_arquivo": "coleta.yml"}
URL = "https://github.com/gfvdata-web/exemplo/actions/workflows/coleta.yml"


class Historico(TesteBase):
    def setUp(self):
        super().setUp()
        self.pasta = self.tmp / "historico"
        self.patch("src.historico.PASTA", self.pasta)
        self.arquivo = self.pasta / "exemplo.json"

    def ler(self):
        return json.loads(self.arquivo.read_text(encoding="utf-8"))

    def atualizar(self, execs, site=SITE):
        with RedeFalsa():
            return historico.atualizar("gfvdata-web", site, execs)

    def recentes(self):
        from src import coleta_github as gh
        with RedeFalsa():
            return gh.execucoes_workflow("gfvdata-web", "exemplo", "coleta.yml", 100)

    def test_primeira_rodada_grava_as_concluidas(self):
        caminho = self.atualizar(self.recentes())
        self.assertEqual(caminho, "dados/historico/exemplo.json")
        dados = self.ler()
        self.assertEqual((dados["slug"], dados["nome"], dados["workflow"], dados["url_workflow"]),
                         ("exemplo", "Exemplo", "coleta.yml", URL))
        self.assertEqual([e["id"] for e in dados["execucoes"]], [103, 102, 101])
        self.assertEqual(contrato.problemas_historico(dados, "exemplo"), [])

    def test_sucesso_guarda_avisos_sem_o_generico_de_saida(self):
        self.atualizar(self.recentes())
        ok = self.ler()["execucoes"][0]
        self.assertEqual(ok["avisos"], [{"nivel": "warning", "mensagem": "Node.js 20 is deprecated."}])
        self.assertNotIn("motivo", ok)
        self.assertEqual(ok["duracao_s"], 247)

    def test_falha_guarda_o_motivo_do_log(self):
        self.atualizar(self.recentes())
        falha = self.ler()["execucoes"][1]
        self.assertEqual(falha["conclusao"], "failure")
        self.assertEqual(falha["motivo"]["passo"], "Coletar dados")
        self.assertIn("503 Server Error", falha["motivo"]["mensagem"])

    def test_execucao_em_andamento_nao_entra(self):
        andamento = execucao(200, status="in_progress", conclusao=None)
        self.atualizar([andamento, *self.recentes()])
        self.assertNotIn(200, [e["id"] for e in self.ler()["execucoes"]])

    def test_segunda_rodada_sem_novidade_nao_reescreve(self):
        execs = self.recentes()
        self.atualizar(execs)
        antes = self.arquivo.read_text(encoding="utf-8")
        with RedeFalsa() as rede:
            historico.atualizar("gfvdata-web", SITE, execs)
        self.assertEqual(self.arquivo.read_text(encoding="utf-8"), antes)
        self.assertEqual(rede.chamadas, [])  # nada novo: nenhuma chamada de jobs/anotações/log

    def test_execucao_nova_entra_no_topo_e_as_antigas_ficam(self):
        self.atualizar(self.recentes()[1:])  # sem a 103
        self.assertEqual([e["id"] for e in self.ler()["execucoes"]], [102, 101])
        self.atualizar(self.recentes())
        self.assertEqual([e["id"] for e in self.ler()["execucoes"]], [103, 102, 101])

    def test_execucoes_com_erro_de_api_nao_mudam_nada(self):
        self.assertIsNone(self.atualizar({"erro": "x"}))
        self.assertFalse(self.arquivo.exists())
        self.atualizar(self.recentes())
        antes = self.arquivo.read_text(encoding="utf-8")
        self.assertEqual(self.atualizar({"erro": "x"}), "dados/historico/exemplo.json")
        self.assertEqual(self.arquivo.read_text(encoding="utf-8"), antes)

    def test_arquivo_de_outro_workflow_e_descartado(self):
        self.pasta.mkdir()
        self.arquivo.write_text(json.dumps({"url_workflow": "https://outro", "execucoes": [{"id": 1, "criado_em": "2020"}]}),
                                encoding="utf-8")
        self.atualizar(self.recentes())
        self.assertEqual([e["id"] for e in self.ler()["execucoes"]], [103, 102, 101])

    def test_arquivo_corrompido_vira_historico_novo(self):
        self.pasta.mkdir()
        self.arquivo.write_text("{quebrado", encoding="utf-8")
        self.atualizar(self.recentes())
        self.assertEqual(len(self.ler()["execucoes"]), 3)

    def test_limites_por_rodada_e_total(self):
        muitas = [execucao(1000 + i, criado=f"2026-09-{1 + i // 24:02d}T{i % 24:02d}:00:00Z",
                           atualizado=f"2026-09-{1 + i // 24:02d}T{i % 24:02d}:01:00Z") for i in range(60)]
        muitas.sort(key=lambda e: e["criado_em"], reverse=True)
        self.patch("src.historico.LIMITE_EXECUCOES", 30)
        self.atualizar(muitas)
        # só MAX_NOVAS_POR_RODADA (as mais recentes da lista) são detalhadas, e o arquivo respeita o limite
        self.assertEqual(len(self.ler()["execucoes"]), 30)
        self.assertEqual(self.ler()["execucoes"][0]["id"], muitas[0]["id"])

    def test_avisos_none_quando_anotacoes_nao_puderam_ser_lidas(self):
        with RedeFalsa() as rede:
            original = rede.get_json

            def get_json(url, headers=None):
                if "/check-runs/" in url:
                    import urllib.error
                    raise urllib.error.URLError("sem anotações")
                return original(url, headers)

            from unittest import mock
            with mock.patch("src.coleta_github.get_json", get_json):
                historico.atualizar("gfvdata-web", SITE, self.recentes()[:1])
        self.assertIsNone(self.ler()["execucoes"][0]["avisos"])

    def test_caminho_publicado_bate_com_o_que_a_pagina_busca(self):
        self.assertEqual(historico.caminho_publicado("bolao_f1"), "dados/historico/bolao_f1.json")


if __name__ == "__main__":
    unittest.main()
