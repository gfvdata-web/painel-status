"""Cadastro (src/config.py): o que validar() aceita e recusa, e o cadastro real."""

import copy
import unittest
from unittest import mock

from tests import apoio  # noqa: F401  (bloqueia a rede)
from src import config


def site_valido(**mudancas):
    base = copy.deepcopy(config.SITES[1])  # um site simples (sem form nem pipeline)
    base.update(mudancas)
    return base


class CadastroReal(unittest.TestCase):
    def test_cadastro_do_repo_e_valido(self):
        config.validar()

    def test_slugs_unicos_e_snake_case(self):
        slugs = [s["slug"] for s in config.SITES]
        self.assertEqual(len(slugs), len(set(slugs)))
        for slug in slugs:
            self.assertRegex(slug, r"^[a-z][a-z0-9_]*$")

    def test_todas_as_entradas_tem_as_mesmas_chaves(self):
        for site in config.SITES:
            self.assertEqual(set(site), config.CAMPOS_SITE, site["slug"])

    def test_urls_bem_formadas(self):
        for site in config.SITES:
            for campo in ("pages_url", "json_meta_url", "form_url"):
                if site[campo]:
                    self.assertRegex(site[campo], r"^https://[a-z0-9.-]+\.[a-z]{2,}(/\S*)?$", f"{site['slug']}.{campo}")
        for form in config.FORMS_AVULSOS:
            self.assertRegex(form["url"], r"^https://forms\.gle/\S+$")
            self.assertTrue(form["nome"].strip())

    def test_repo_e_workflow_bem_formados(self):
        for site in config.SITES:
            self.assertRegex(site["repo"], r"^[A-Za-z0-9._-]+$")
            self.assertTrue(site["nome"].strip())
            self.assertFalse(site["caminho_dados"].startswith("/") or site["caminho_dados"].endswith("/"))
            if site["workflow_arquivo"]:
                self.assertRegex(site["workflow_arquivo"], r"^[\w.-]+\.ya?ml$")
            if site["pipeline_forms"]:
                self.assertRegex(site["pipeline_forms"], r"^[\w.-]+\.ya?ml$")

    def test_um_destaque_e_um_pipeline_bolao(self):
        self.assertEqual(sum(1 for s in config.SITES if s["destaque"]), 1)
        self.assertEqual(sum(1 for s in config.SITES if s["pipeline_bolao"]), 1)
        self.assertEqual(config.OWNER, "gfvdata-web")

    def test_ha_site_com_pipeline_forms_e_form_avulso(self):
        # a página desenha o passo a passo do Forms e o card "Outros forms": precisam de dado real
        self.assertTrue(any(s["pipeline_forms"] for s in config.SITES))
        self.assertTrue(config.FORMS_AVULSOS)


class Validacao(unittest.TestCase):
    def valida(self, sites=None, avulsos=None):
        with mock.patch.object(config, "SITES", sites if sites is not None else [site_valido()]), \
             mock.patch.object(config, "FORMS_AVULSOS", avulsos if avulsos is not None else []):
            config.validar()

    def recusa(self, trecho, **kw):
        with self.assertRaises(ValueError) as ctx:
            self.valida(**kw)
        self.assertIn(trecho, str(ctx.exception))

    def test_site_valido_passa(self):
        self.valida()

    def test_campo_faltando(self):
        site = site_valido()
        del site["form_url"]
        self.recusa("faltam campos", sites=[site])

    def test_campo_desconhecido(self):
        self.recusa("campos desconhecidos", sites=[site_valido(extra=1)])

    def test_slug_repetido(self):
        self.recusa("slugs repetidos", sites=[site_valido(), site_valido()])

    def test_dois_destaques(self):
        a, b = site_valido(destaque=True), site_valido(slug="outro", destaque=True)
        self.recusa("mais de um site com destaque", sites=[a, b])

    def test_pipeline_bolao_exige_workflow(self):
        self.recusa("pipeline_bolao exige workflow_arquivo", sites=[site_valido(pipeline_bolao=True)])

    def test_pipeline_forms_exige_form_url(self):
        self.recusa("pipeline_forms exige form_url", sites=[site_valido(pipeline_forms="p.yml")])

    def test_pipeline_forms_e_bolao_nao_se_combinam(self):
        s = site_valido(pipeline_forms="p.yml", pipeline_bolao=True, form_url="https://forms.gle/x",
                        workflow_arquivo="w.yml")
        self.recusa("não se combinam", sites=[s])

    def test_form_rotulo_exige_form_url(self):
        self.recusa("form_rotulo exige form_url", sites=[site_valido(form_rotulo="Enviar")])

    def test_form_avulso_malformado(self):
        self.recusa("form avulso malformado", avulsos=[{"nome": "sem url"}])
        self.recusa("form avulso malformado", avulsos=[{"nome": "x", "url": "y", "extra": 1}])

    def test_todos_os_problemas_aparecem_juntos(self):
        with self.assertRaises(ValueError) as ctx:
            self.valida(sites=[site_valido(pipeline_bolao=True, form_rotulo="x")])
        texto = str(ctx.exception)
        self.assertIn("pipeline_bolao exige", texto)
        self.assertIn("form_rotulo exige", texto)


class Docstring(unittest.TestCase):
    def test_docstring_descreve_todos_os_campos(self):
        for campo in config.CAMPOS_SITE:
            self.assertRegex(config.__doc__, rf"(?m)^\s+{campo}\s", f"campo {campo} fora da docstring de config.py")


if __name__ == "__main__":
    unittest.main()
