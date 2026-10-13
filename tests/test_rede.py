"""src/rede.py: ERROS_REDE cobre o que uma chamada pode levantar; Authorization não vaza em redirecionamento."""

import http.client
import io
import json
import unittest
import urllib.error
import urllib.request
from unittest import mock

from tests import apoio
from src import rede


class Resposta(io.BytesIO):
    status = 200

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()


class ErrosDeRede(unittest.TestCase):
    def test_tupla_cobre_os_erros_do_dia_a_dia(self):
        for exc in (urllib.error.URLError("dns"), apoio.erro_http(500), TimeoutError(), ConnectionResetError(),
                    http.client.IncompleteRead(b""), json.JSONDecodeError("x", "", 0)):
            try:
                raise exc
            except rede.ERROS_REDE:
                pass


class Chamadas(unittest.TestCase):
    def test_get_json_manda_user_agent_e_cabecalhos(self):
        capturado = {}

        def abrir(req, timeout):
            capturado["req"], capturado["timeout"] = req, timeout
            return Resposta('{"a": "ç"}'.encode("utf-8"))

        with mock.patch.object(rede, "_abrir", abrir):
            self.assertEqual(rede.get_json("https://x.test/a", {"Accept": "j"}), {"a": "ç"})
        req = capturado["req"]
        self.assertEqual(req.get_header("User-agent"), "painel-status")
        self.assertEqual(req.get_header("Accept"), "j")
        self.assertEqual(capturado["timeout"], rede.TIMEOUT_S)

    def test_json_quebrado_vira_erro_de_rede(self):
        with mock.patch.object(rede, "_abrir", lambda req, timeout: Resposta(b"<html>")):
            with self.assertRaises(rede.ERROS_REDE):
                rede.get_json("https://x.test/")

    def test_put_manda_corpo_e_metodo(self):
        capturado = {}

        def abrir(req, timeout):
            capturado["req"] = req
            return Resposta(b"ok")

        with mock.patch.object(rede, "_abrir", abrir):
            self.assertEqual(rede.put("https://x.test/", b"abc", {"Authorization": "Bearer t"}), "ok")
        self.assertEqual(capturado["req"].get_method(), "PUT")
        self.assertEqual(capturado["req"].data, b"abc")

    def test_get_status_devolve_codigo(self):
        resposta = Resposta(b"")
        resposta.status = 204
        with mock.patch.object(rede, "_abrir", lambda req, timeout: resposta):
            self.assertEqual(rede.get_status("https://x.test/"), 204)

    def test_http_error_propaga_com_codigo(self):
        def abrir(req, timeout):
            raise apoio.erro_http(404)

        with mock.patch.object(rede, "_abrir", abrir):
            with self.assertRaises(urllib.error.HTTPError) as ctx:
                rede.get_status("https://x.test/")
        self.assertEqual(ctx.exception.code, 404)

    def test_rede_real_esta_bloqueada_nos_testes(self):
        with self.assertRaises(AssertionError):
            rede.get_texto("http://example.invalid/")


class RedirecionamentoSemToken(unittest.TestCase):
    def redirecionar(self, de, para):
        req = urllib.request.Request(de, headers={"Authorization": "Bearer segredo", "User-Agent": "x"})
        return rede._RedirecionaSemToken().redirect_request(req, io.BytesIO(), 302, "Found", {}, para)

    def test_outro_host_perde_authorization(self):
        novo = self.redirecionar("https://api.github.com/logs", "https://storage.exemplo.net/log")
        self.assertIsNotNone(novo)
        self.assertFalse(novo.has_header("Authorization"))

    def test_mesmo_host_mantem_authorization(self):
        novo = self.redirecionar("https://api.github.com/a", "https://api.github.com/b")
        self.assertTrue(novo.has_header("Authorization"))


if __name__ == "__main__":
    unittest.main()
