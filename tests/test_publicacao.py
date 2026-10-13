"""src/publicacao.py de ponta a ponta com a API falsa: o status.json gerado cumpre o contrato
(tests/contrato.py), nunca escreve em docs/dados e uma API fora do ar não derruba a rodada."""

import contextlib
import copy
import io
import json
import os
import sys
import unittest
from unittest import mock

from tests import contrato
from tests.apoio import RedeFalsa, TesteBase, fixture, relogio_fixo
from src import config, publicacao

RELOGIOS = ("src.detalhes_site", "src.coleta_goatcounter")
REPO_BOLAO = "page-bolao-formula1"


class PublicacaoBase(TesteBase):
    def setUp(self):
        super().setUp()
        self.saida = self.tmp / "dados" / "status.json"
        self.patch("src.publicacao.SAIDA", self.saida)
        self.patch("src.historico.PASTA", self.tmp / "dados" / "historico")
        ambiente = mock.patch.dict(os.environ, {"GOATCOUNTER_TOKEN": "tok-teste"})
        ambiente.start()
        self.addCleanup(ambiente.stop)

    def montar(self, **rede):
        with relogio_fixo(*RELOGIOS), RedeFalsa(**rede) as falsa:
            self.rede = falsa
            return publicacao.montar()

    @staticmethod
    def site(status, slug):
        return next(s for s in status["sites"] if s["slug"] == slug)


class RodadaCompleta(PublicacaoBase):
    def test_status_gerado_cumpre_o_contrato(self):
        status = self.montar()
        self.assertEqual(contrato.problemas_status(status, config.SITES), [])
        self.assertEqual([s["slug"] for s in status["sites"]], [s["slug"] for s in config.SITES])
        self.assertEqual(status["forms_avulsos"], config.FORMS_AVULSOS)
        self.assertIn("gerado_em", status["meta"])

    def test_bolao_tem_pipeline_e_os_outros_tem_detalhes(self):
        status = self.montar()
        bolao = self.site(status, "bolao_f1")
        self.assertEqual(bolao["rodada_mais_recente"], 21)  # "Atualiza dados da rodada 21"
        self.assertFalse(bolao["em_vigilia"])
        self.assertEqual([e["chave"] for e in bolao["etapas_pipeline"]], ["forms", "leitura", "resultado", "pontuacao", "pagina"])
        self.assertNotIn("detalhes", bolao)
        chess = self.site(status, "chess_tracking")
        self.assertEqual(chess["detalhes"]["coleta"]["workflow"], "atualizar-partidas.yml")
        self.assertEqual(chess["historico_arquivo"], "dados/historico/chess_tracking.json")
        self.assertNotIn("rodada_mais_recente", chess)

    def test_site_sem_workflow_nem_form_nem_periodo(self):
        eleicoes = self.site(self.montar(), "eleicoes_2026")
        for campo in ("ultima_execucao_pipeline", "historico_arquivo", "form_url", "periodo_publicado", "pipeline_forms"):
            self.assertNotIn(campo, eleicoes)
        self.assertIsNone(eleicoes["detalhes"]["coleta"])

    def test_periodo_publicado_vem_do_json_do_site(self):
        status = self.montar()
        self.assertEqual(self.site(status, "meios_pagamento_mensal")["periodo_publicado"], {"inicio": "2020-01", "fim": "2026-08"})

    def test_form_do_site_e_pipeline_do_forms(self):
        plantas = self.site(self.montar(), "plants_care")
        self.assertEqual(plantas["form_url"], "https://forms.gle/y3uXaukJXmP9GMED7")
        self.assertEqual(plantas["form_rotulo"], "Enviar fotos")
        self.assertEqual(plantas["pipeline_forms"]["workflow"], "pipeline.yml")
        self.assertEqual([e["chave"] for e in plantas["pipeline_forms"]["etapas"]], ["forms", "ficha", "relatorio", "commit"])
        self.assertEqual(plantas["pipeline_forms"]["execucao"]["id"], 103)

    def test_historicos_gravados_so_na_pasta_temporaria(self):
        self.montar()
        gravados = sorted(p.name for p in (self.tmp / "dados" / "historico").glob("*.json"))
        com_workflow = sorted(f"{s['slug']}.json" for s in config.SITES if s["workflow_arquivo"])
        self.assertEqual(gravados, com_workflow)

    def test_acesso_so_dos_repos_com_visita(self):
        status = self.montar()
        self.assertEqual(self.site(status, "bolao_f1")["acesso"]["visitantes_unicos"], 123)
        self.assertEqual(self.site(status, "chess_tracking")["acesso"]["visitantes_unicos"], 123)
        self.assertNotIn("acesso", self.site(status, "meios_pagamento_mensal"))

    def test_sem_token_do_goatcounter_ninguem_tem_acesso(self):
        with mock.patch.dict(os.environ, {"GOATCOUNTER_TOKEN": ""}):
            status = self.montar()
        self.assertTrue(all("acesso" not in s for s in status["sites"]))

    def test_goatcounter_fora_reaproveita_o_acesso_anterior(self):
        self.saida.parent.mkdir(parents=True)
        anterior = {"sites": [{"slug": "eleicoes_2026", "acesso": {"visitantes_unicos": 77, "serie_diaria": []}}]}
        self.saida.write_text(json.dumps(anterior), encoding="utf-8")
        with contextlib.redirect_stderr(io.StringIO()):
            status = self.montar(sem_goatcounter=True)
        self.assertEqual(self.site(status, "eleicoes_2026")["acesso"]["visitantes_unicos"], 77)
        self.assertNotIn("acesso", self.site(status, "chess_tracking"))

    def test_status_anterior_ilegivel_e_ignorado(self):
        self.saida.parent.mkdir(parents=True)
        self.saida.write_text("{quebrado", encoding="utf-8")
        with contextlib.redirect_stderr(io.StringIO()):
            status = self.montar(sem_goatcounter=True)
        self.assertEqual(contrato.problemas_status(status, config.SITES), [])


class CasosDeBorda(PublicacaoBase):
    def test_api_fora_do_ar_nao_derruba_a_rodada(self):
        with contextlib.redirect_stderr(io.StringIO()):
            status = self.montar(falhar=True)
        self.assertEqual(contrato.problemas_status(status, config.SITES), [])
        bolao = self.site(status, "bolao_f1")
        self.assertIn("erro", bolao["ultimo_commit_dados"])
        self.assertIn("erro", bolao["ultima_execucao_pipeline"])
        self.assertIsNone(bolao["rodada_mais_recente"])
        self.assertNotIn("etapas_pipeline", bolao)
        self.assertFalse(bolao["site_no_ar"]["ok"])
        plantas = self.site(status, "plants_care")
        self.assertIn("erro", plantas["pipeline_forms"]["execucao"])
        self.assertIsNone(plantas["pipeline_forms"]["etapas"])

    def test_site_que_nao_responde_vira_alerta_de_erro(self):
        fora = next(s for s in config.SITES if s["slug"] == "chess_tracking")["pages_url"]
        status = self.montar(sites_fora={fora: 503})
        chess = self.site(status, "chess_tracking")
        self.assertEqual(chess["site_no_ar"], {"ok": False, "status_http": 503})
        self.assertEqual(chess["alertas"], [{"nivel": "erro", "texto": "O site não respondeu na última checagem."}])
        self.assertEqual(self.site(status, "var_vendo")["alertas"], [])

    def test_execucao_rodando_do_bolao_liga_a_vigilia(self):
        rodando = copy.deepcopy(fixture("runs.json")["workflow_runs"])
        rodando[0].update(status="in_progress", conclusion=None)
        status = self.montar(runs=rodando)
        self.assertTrue(self.site(status, "bolao_f1")["em_vigilia"])

    def test_commit_sem_rodada_no_texto(self):
        commits = copy.deepcopy(fixture("commits.json"))
        commits[0]["commit"]["message"] = "Ajusta layout"
        self.assertIsNone(self.site(self.montar(commits=commits), "bolao_f1")["rodada_mais_recente"])

    def test_commit_sem_mensagem_nao_derruba_a_rodada(self):
        commits = copy.deepcopy(fixture("commits.json"))
        commits[0]["commit"]["message"] = ""
        status = self.montar(commits=commits)
        self.assertEqual(self.site(status, "bolao_f1")["ultimo_commit_dados"]["mensagem"], "")

    def test_envio_pelo_forms_que_falhou_vira_alerta(self):
        falhou = copy.deepcopy(fixture("runs.json")["workflow_runs"])
        falhou[0].update(conclusion="failure")
        plantas = self.site(self.montar(runs=falhou), "plants_care")
        textos = [a["texto"] for a in plantas["alertas"]]
        self.assertIn("O último envio pelo Forms falhou no pipeline.", textos)

    def test_validacao_do_cadastro_roda_antes_de_qualquer_chamada(self):
        quebrado = copy.deepcopy(config.SITES)
        quebrado[0]["slug"] = quebrado[1]["slug"]
        with relogio_fixo(*RELOGIOS), RedeFalsa() as rede, \
             mock.patch.object(publicacao, "SITES", quebrado), mock.patch.object(config, "SITES", quebrado):
            with self.assertRaises(ValueError):
                publicacao.montar()
        self.assertEqual(rede.chamadas, [])


class RodadaParcial(PublicacaoBase):
    def setUp(self):
        super().setUp()
        self.completo = self.montar()
        self.saida.parent.mkdir(parents=True, exist_ok=True)
        self.saida.write_text(json.dumps(self.completo, ensure_ascii=False), encoding="utf-8")

    def parcial(self, campo, **rede):
        with relogio_fixo(*RELOGIOS), RedeFalsa(**rede) as falsa:
            self.rede = falsa
            return publicacao.montar_so(campo)

    def repos_consultados(self):
        import re
        return {m.group(1) for u in self.rede.chamadas if (m := re.search(r"/repos/gfvdata-web/([^/]+)/", u))}

    def test_so_bolao_consulta_so_o_repo_do_bolao(self):
        status = self.parcial("pipeline_bolao")
        self.assertEqual(self.repos_consultados(), {REPO_BOLAO})
        self.assertEqual(contrato.problemas_status(status, config.SITES), [])
        self.assertEqual([s["slug"] for s in status["sites"]], [s["slug"] for s in config.SITES])

    def test_so_forms_consulta_so_o_repo_do_forms(self):
        self.parcial("pipeline_forms")
        self.assertEqual(self.repos_consultados(), {"PlantsCare"})

    def test_os_outros_sites_ficam_como_estavam(self):
        antes = copy.deepcopy(self.completo)
        status = self.parcial("pipeline_bolao")
        for slug in (s["slug"] for s in config.SITES if not s["pipeline_bolao"]):
            self.assertEqual(self.site(status, slug), self.site(antes, slug), slug)
        self.assertEqual(status["forms_avulsos"], antes["forms_avulsos"])

    def test_acesso_do_card_recoletado_vem_do_status_anterior(self):
        anterior = json.loads(self.saida.read_text(encoding="utf-8"))
        self.site(anterior, "bolao_f1")["acesso"] = {"visitantes_unicos": 5, "serie_diaria": []}
        self.saida.write_text(json.dumps(anterior), encoding="utf-8")
        self.assertEqual(self.site(self.parcial("pipeline_bolao"), "bolao_f1")["acesso"]["visitantes_unicos"], 5)

    def test_sem_status_anterior_vira_rodada_completa(self):
        self.saida.unlink()
        status = self.parcial("pipeline_bolao")
        self.assertGreater(len(self.repos_consultados()), 2)
        self.assertEqual(len(status["sites"]), len(config.SITES))

    def test_card_que_sumiu_do_status_anterior_volta_no_lugar_certo(self):
        anterior = json.loads(self.saida.read_text(encoding="utf-8"))
        anterior["sites"] = [s for s in anterior["sites"] if s["slug"] != "bolao_f1"]
        self.saida.write_text(json.dumps(anterior), encoding="utf-8")
        status = self.parcial("pipeline_bolao")
        self.assertEqual(status["sites"][0]["slug"], "bolao_f1")  # destaque entra no topo


class ArquivoDeReferencia(unittest.TestCase):
    """tests/fixtures/status_exemplo.json é a saída da coleta com a API falsa; os testes de página
    desenham esse arquivo. Formato mudou de propósito? `python -m tests.regenerar_fixtures`."""

    def test_fixtures_de_pagina_estao_em_dia_com_a_coleta(self):
        from tests import regenerar_fixtures as regen

        status, historico = regen.gerar()
        self.assertEqual(regen.texto(status), (regen.FIXTURES / "status_exemplo.json").read_text(encoding="utf-8"),
                         "status_exemplo.json defasado: rode python -m tests.regenerar_fixtures")
        self.assertEqual(regen.texto(historico), (regen.FIXTURES / "historico_exemplo.json").read_text(encoding="utf-8"),
                         "historico_exemplo.json defasado: rode python -m tests.regenerar_fixtures")
        self.assertEqual(contrato.problemas_status(status, config.SITES), [])
        self.assertEqual(contrato.problemas_historico(historico, "chess_tracking"), [])


class Principal(PublicacaoBase):
    def rodar(self, *argumentos):
        with relogio_fixo(*RELOGIOS), RedeFalsa(), mock.patch.object(sys, "argv", ["publicacao", *argumentos]), \
             contextlib.redirect_stdout(io.StringIO()):
            publicacao.main()

    def test_main_grava_o_json_valido(self):
        self.rodar()
        status = json.loads(self.saida.read_text(encoding="utf-8"))
        self.assertEqual(contrato.problemas_status(status, config.SITES), [])
        self.assertIn("ã", self.saida.read_text(encoding="utf-8"))  # UTF-8 de verdade, sem \u escapes

    def test_flags_do_workflow_sao_aceitas(self):
        self.rodar()
        self.rodar("--so-bolao")
        self.rodar("--so-forms")


if __name__ == "__main__":
    unittest.main()
