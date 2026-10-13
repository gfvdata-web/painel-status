"""docs/dados/ (o que as páginas, o Worker e o app leem): JSON válido e dentro do contrato.

Só lê. O Action regenera estes arquivos; o teste vale para o que estiver commitado agora."""

import json
import unittest

from tests import contrato
from tests.apoio import RAIZ

DADOS = RAIZ / "docs" / "dados"


def ler(caminho):
    return json.loads(caminho.read_text(encoding="utf-8"))


class StatusPublicado(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.status = ler(DADOS / "status.json")

    def test_dentro_do_contrato(self):
        self.assertEqual(contrato.problemas_status(self.status), [])

    def test_slugs_unicos_e_um_destaque(self):
        slugs = [s["slug"] for s in self.status["sites"]]
        self.assertEqual(len(slugs), len(set(slugs)))
        self.assertEqual(sum(1 for s in self.status["sites"] if s["destaque"]), 1)

    def test_historico_apontado_existe_e_e_do_site(self):
        for site in self.status["sites"]:
            caminho = site.get("historico_arquivo")
            if not caminho:
                continue
            arquivo = RAIZ / "docs" / caminho
            self.assertTrue(arquivo.exists(), f"{site['slug']}: {caminho} não existe")
            self.assertEqual(contrato.problemas_historico(ler(arquivo), site["slug"]), [], caminho)

    def test_todo_historico_gravado_pertence_a_um_site(self):
        apontados = {s["historico_arquivo"] for s in self.status["sites"] if s.get("historico_arquivo")}
        for arquivo in (DADOS / "historico").glob("*.json"):
            self.assertIn(f"dados/historico/{arquivo.name}", apontados, f"{arquivo.name} sem site")

    def test_o_que_a_pagina_precisa_para_desenhar(self):
        # lista.js: cartão por site; app.js: card de destaque (Bolão) e grade com pop-up
        destaque = next(s for s in self.status["sites"] if s["destaque"])
        self.assertIn("etapas_pipeline", destaque)
        self.assertTrue(any(s.get("detalhes") for s in self.status["sites"]))
        self.assertTrue(self.status["forms_avulsos"])
        self.assertTrue(any(s.get("form_url") for s in self.status["sites"]))


if __name__ == "__main__":
    unittest.main()
