"""src/envio.py: envio ao Worker (PUT) e download do que está publicado, com rede e pasta de dados falsas."""

import contextlib
import io
import json
import os
import re
import sys
import unittest
import urllib.error
from unittest import mock

from tests.apoio import RAIZ, TesteBase, erro_http
from src import envio

STATUS = {"meta": {"gerado_em": "2026-10-13T12:00:00+00:00"},
          "sites": [{"slug": "a", "nome": "A", "historico_arquivo": "dados/historico/a.json"},
                    {"slug": "b", "nome": "B"}],
          "forms_avulsos": []}


class Envio(TesteBase):
    def setUp(self):
        super().setUp()
        self.dados = self.tmp / "dados"
        (self.dados / "historico").mkdir(parents=True)
        (self.dados / "status.json").write_text(json.dumps(STATUS), encoding="utf-8")
        (self.dados / "historico" / "a.json").write_text('{"slug": "a"}', encoding="utf-8")
        self.patch("src.envio.DADOS", self.dados)

    def executar(self, funcao, **ambiente):
        erro = io.StringIO()
        with mock.patch.dict(os.environ, ambiente, clear=False), contextlib.redirect_stderr(erro), \
             contextlib.redirect_stdout(io.StringIO()):
            return funcao(), erro.getvalue()

    def test_sem_token_nao_envia_nada(self):
        put = mock.Mock()
        with mock.patch.dict(os.environ, {}, clear=False), mock.patch.object(envio, "put", put):
            os.environ.pop("PAINEL_TOKEN_ENVIO", None)
            resultado, erro = self.executar(envio.enviar)
        self.assertFalse(resultado)
        self.assertIn("PAINEL_TOKEN_ENVIO não configurado", erro)
        put.assert_not_called()

    def test_historicos_vao_antes_do_status(self):
        chamadas = []
        with mock.patch.object(envio, "put", lambda url, corpo, headers: chamadas.append((url, corpo, headers))):
            resultado, _ = self.executar(envio.enviar, PAINEL_TOKEN_ENVIO="segredo", PAINEL_URL="https://painel.teste/")
        self.assertTrue(resultado)
        self.assertEqual([c[0] for c in chamadas],
                         ["https://painel.teste/api/envio/historico/a.json", "https://painel.teste/api/envio/status.json"])
        self.assertEqual(chamadas[1][2]["Authorization"], "Bearer segredo")
        self.assertEqual(json.loads(chamadas[1][1]), STATUS)

    def test_url_padrao(self):
        urls = []
        with mock.patch.object(envio, "put", lambda url, corpo, headers: urls.append(url)):
            self.executar(envio.enviar, PAINEL_TOKEN_ENVIO="t", PAINEL_URL="")
        self.assertTrue(urls[0].startswith("https://painel.gfvdata.com/api/envio/"))

    def test_caminhos_aceitos_pelo_worker(self):
        # o Worker só aceita status.json e historico/<slug>.json (RE_HISTORICO em worker/src/index.ts)
        fonte = (RAIZ / "worker" / "src" / "index.ts").read_text(encoding="utf-8")
        regex = re.search(r"RE_HISTORICO = /(.+)/;", fonte).group(1)
        urls = []
        with mock.patch.object(envio, "put", lambda url, corpo, headers: urls.append(url)):
            self.executar(envio.enviar, PAINEL_TOKEN_ENVIO="t", PAINEL_URL="https://p.teste")
        for url in urls:
            resto = url.removeprefix("https://p.teste/api/envio/")
            self.assertTrue(resto == "status.json" or re.match(regex, resto), url)

    def test_falha_num_arquivo_nao_impede_os_outros_e_sai_com_erro(self):
        def put(url, corpo, headers):
            if url.endswith("historico/a.json"):
                raise erro_http(422, url, b'{"erro": "slug nao bate"}')

        enviados = []
        with mock.patch.object(envio, "put", lambda u, c, h: (put(u, c, h), enviados.append(u))):
            resultado, erro = self.executar(envio.enviar, PAINEL_TOKEN_ENVIO="t")
        self.assertFalse(resultado)
        self.assertIn("HTTP 422", erro)
        self.assertIn("slug nao bate", erro)
        self.assertEqual(len(enviados), 1)  # o status.json foi

    def test_erro_de_rede_no_envio(self):
        with mock.patch.object(envio, "put", mock.Mock(side_effect=urllib.error.URLError("sem rede"))):
            resultado, erro = self.executar(envio.enviar, PAINEL_TOKEN_ENVIO="t")
        self.assertFalse(resultado)
        self.assertIn("sem rede", erro)

    def test_baixar_grava_status_e_historicos(self):
        for arquivo in self.dados.rglob("*.json"):
            arquivo.unlink()
        publicado = {"https://p.teste/dados/status.json": json.dumps(STATUS),
                     "https://p.teste/dados/historico/a.json": '{"slug": "a"}'}
        with mock.patch.object(envio, "get_texto", lambda url, headers=None: publicado[url]):
            resultado, _ = self.executar(envio.baixar, PAINEL_URL="https://p.teste")
        self.assertTrue(resultado)
        self.assertEqual(json.loads((self.dados / "status.json").read_text(encoding="utf-8")), STATUS)
        self.assertEqual((self.dados / "historico" / "a.json").read_text(encoding="utf-8"), '{"slug": "a"}')

    def test_baixar_sem_status_publicado_nao_mexe_em_nada(self):
        antes = (self.dados / "status.json").read_text(encoding="utf-8")
        with mock.patch.object(envio, "get_texto", mock.Mock(side_effect=erro_http(404))):
            resultado, erro = self.executar(envio.baixar)
        self.assertFalse(resultado)
        self.assertIn("não deu para baixar", erro)
        self.assertEqual((self.dados / "status.json").read_text(encoding="utf-8"), antes)

    def test_baixar_historico_ruim_marca_falha_mas_grava_o_status(self):
        def get_texto(url, headers=None):
            return json.dumps(STATUS) if url.endswith("status.json") else "<html>erro</html>"

        with mock.patch.object(envio, "get_texto", get_texto):
            resultado, erro = self.executar(envio.baixar)
        self.assertFalse(resultado)
        self.assertIn("falha ao baixar dados/historico/a.json", erro)
        self.assertEqual(json.loads((self.dados / "status.json").read_text(encoding="utf-8")), STATUS)

    def test_uso_incorreto_sai_com_2(self):
        for argv in (["envio"], ["envio", "outra"], ["envio", "enviar", "x"]):
            with mock.patch.object(sys, "argv", argv), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as ctx:
                    envio.main()
            self.assertEqual(ctx.exception.code, 2)

    def test_main_sai_com_1_quando_algo_falha(self):
        with mock.patch.object(sys, "argv", ["envio", "enviar"]), mock.patch.object(envio, "enviar", lambda: False):
            with self.assertRaises(SystemExit) as ctx:
                envio.main()
        self.assertEqual(ctx.exception.code, 1)


if __name__ == "__main__":
    unittest.main()
