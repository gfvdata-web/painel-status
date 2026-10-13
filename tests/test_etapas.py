"""Etapas do pipeline do Bolão F1 (src/etapas_bolao.py) e do Forms (src/etapas_forms.py)."""

import unittest
from datetime import datetime, timedelta, timezone

from tests import contrato
from tests.apoio import execucao, passo
from src import etapas_bolao as eb
from src import etapas_forms as ef

T0 = "2026-10-13T10:00:00Z"
AGORA = datetime(2026, 10, 13, 10, 30, tzinfo=timezone.utc)


def por_chave(etapas):
    return {e["chave"]: e for e in etapas}


def estados(etapas):
    return [(e["chave"], e["estado"]) for e in etapas]


def deploy(status="completed", conclusao="success", criado="2026-10-13T10:02:00Z", atualizado="2026-10-13T10:03:00Z"):
    return {"status": status, "conclusao": conclusao, "criado_em": criado, "atualizado_em": atualizado, "url": "u"}


class Bolao(unittest.TestCase):
    EXEC = execucao(1, evento="repository_dispatch", criado=T0)

    def montar(self, passos, deploys=(), execucao_=None, agora=AGORA):
        return eb.montar_etapas(execucao_ or self.EXEC, passos, None if deploys is None else list(deploys), agora)

    def test_sem_execucao_ou_com_erro_nao_ha_etapas(self):
        self.assertIsNone(eb.montar_etapas(None, [], []))
        self.assertIsNone(eb.montar_etapas({"erro": "x"}, [], []))

    def test_resultado_ja_publicado_e_pagina_no_ar(self):
        passos = [passo(eb.PASSO_NOVO_PALPITE, fim="2026-10-13T10:01:00Z"),
                  passo("Verificador — espera", conclusao="skipped")]
        etapas = self.montar(passos, [deploy()])
        self.assertEqual(estados(etapas), [("forms", "ok"), ("leitura", "ok"), ("resultado", "ok"),
                                           ("pontuacao", "ok"), ("pagina", "ok")])
        self.assertEqual(por_chave(etapas)["resultado"]["quando"], "2026-10-13T10:01:00Z")
        self.assertEqual(contrato_etapas(etapas), [])

    def test_reexecucao_manual_pula_o_forms_e_usa_o_outro_passo(self):
        manual = execucao(1, evento="workflow_dispatch", criado=T0)
        passos = [passo(eb.PASSO_RETRY_MANUAL), passo("Verificador", conclusao="skipped")]
        etapas = self.montar(passos, [deploy()], manual)
        self.assertEqual(por_chave(etapas)["forms"]["estado"], "pulado")
        self.assertEqual(por_chave(etapas)["leitura"]["estado"], "ok")

    def test_nao_conseguiu_ler_os_passos(self):
        etapas = self.montar(None)
        self.assertEqual(estados(etapas), [("forms", "ok"), ("leitura", "pendente")])

    def test_leitura_em_andamento_e_com_erro(self):
        andamento = self.montar([passo(eb.PASSO_NOVO_PALPITE, status="in_progress", conclusao=None)])
        self.assertEqual(estados(andamento), [("forms", "ok"), ("leitura", "andamento"), ("resultado", "pendente"),
                                              ("pontuacao", "pendente"), ("pagina", "pendente")])
        self.assertEqual(self.montar([])[1]["estado"], "andamento")  # passo ainda não apareceu
        erro = self.montar([passo(eb.PASSO_NOVO_PALPITE, conclusao="failure")])
        self.assertEqual(por_chave(erro)["leitura"]["estado"], "erro")
        self.assertEqual(por_chave(erro)["pagina"]["estado"], "pendente")

    def test_vigilia_conta_as_consultas_pelo_tempo(self):
        leitura = passo(eb.PASSO_NOVO_PALPITE, fim="2026-10-13T10:01:00Z")
        ver = passo(eb.PASSO_VERIFICADOR, status="in_progress", conclusao=None, inicio="2026-10-13T10:01:00Z", fim=None)
        agora = datetime(2026, 10, 13, 10, 13, tzinfo=timezone.utc)  # 12 min depois: 2 voltas
        etapas = por_chave(self.montar([leitura, ver], agora=agora))
        self.assertEqual(etapas["resultado"]["estado"], "aguardando")
        self.assertIn("3 consultas sem resultado", etapas["resultado"]["detalhe"])
        self.assertEqual(etapas["resultado"]["quando"], "2026-10-13T10:11:00Z")
        self.assertEqual(etapas["pontuacao"]["estado"], "pendente")
        self.assertEqual(etapas["pagina"]["estado"], "pendente")

    def test_vigilia_recem_aberta_tem_uma_consulta_no_singular(self):
        leitura = passo(eb.PASSO_NOVO_PALPITE, fim="2026-10-13T10:01:00Z")
        ver = passo(eb.PASSO_VERIFICADOR, status="in_progress", conclusao=None, inicio="2026-10-13T10:01:00Z")
        agora = datetime(2026, 10, 13, 10, 2, tzinfo=timezone.utc)
        etapa = por_chave(self.montar([leitura, ver], agora=agora))["resultado"]
        self.assertIn("1 consulta sem resultado", etapa["detalhe"])
        self.assertEqual(etapa["quando"], "2026-10-13T10:01:00Z")

    def test_consultas_nunca_passam_do_maximo(self):
        leitura = passo(eb.PASSO_NOVO_PALPITE, fim=T0)
        ver = passo(eb.PASSO_VERIFICADOR, status="in_progress", conclusao=None, inicio=T0)
        consultas, _ = eb._consultas_da_vigilia(leitura, ver, AGORA + timedelta(days=2))
        self.assertEqual(consultas, 1 + eb.MAX_TENTATIVAS_VERIFICADOR)
        sem_inicio = dict(ver, inicio=None)
        self.assertEqual(eb._consultas_da_vigilia(leitura, sem_inicio, AGORA), (1, T0))

    def test_vigilia_que_deu_certo_e_que_estourou_5h(self):
        leitura = passo(eb.PASSO_NOVO_PALPITE, fim="2026-10-13T10:01:00Z")
        ok = passo(eb.PASSO_VERIFICADOR, fim="2026-10-13T11:00:00Z")
        etapas = por_chave(self.montar([leitura, ok], [deploy(criado="2026-10-13T11:01:00Z", atualizado="2026-10-13T11:02:00Z")]))
        self.assertEqual((etapas["resultado"]["estado"], etapas["resultado"]["quando"]), ("ok", "2026-10-13T11:00:00Z"))
        self.assertEqual(etapas["pagina"]["estado"], "ok")
        estourou = passo(eb.PASSO_VERIFICADOR, conclusao="failure", fim="2026-10-13T15:00:00Z")
        etapas = por_chave(self.montar([leitura, estourou]))
        self.assertEqual(etapas["resultado"]["estado"], "erro")
        self.assertEqual(etapas["pontuacao"]["estado"], "pendente")
        self.assertEqual(etapas["pagina"]["estado"], "pendente")

    def test_estados_do_deploy_da_pagina(self):
        passos = [passo(eb.PASSO_NOVO_PALPITE, fim="2026-10-13T10:01:00Z"), passo("Verificador", conclusao="skipped")]
        casos = {
            "sem deploy": ([], "andamento"),
            "deploy anterior à pontuação não conta": ([deploy(criado="2026-10-13T10:00:30Z")], "andamento"),
            "em andamento": ([deploy(status="in_progress", conclusao=None)], "andamento"),
            "falhou": ([deploy(conclusao="failure")], "erro"),
            "sucesso": ([deploy()], "ok"),
        }
        for nome, (deploys, esperado) in casos.items():
            with self.subTest(nome):
                self.assertEqual(por_chave(self.montar(passos, deploys))["pagina"]["estado"], esperado)

    def test_vale_o_primeiro_deploy_depois_da_pontuacao(self):
        passos = [passo(eb.PASSO_NOVO_PALPITE, fim="2026-10-13T10:01:00Z"), passo("Verificador", conclusao="skipped")]
        recentes_primeiro = [deploy(conclusao="failure", criado="2026-10-13T10:09:00Z"), deploy(criado="2026-10-13T10:02:00Z")]
        self.assertEqual(por_chave(self.montar(passos, recentes_primeiro))["pagina"]["estado"], "ok")

    def test_deploys_none_nao_quebra(self):
        passos = [passo(eb.PASSO_NOVO_PALPITE), passo("Verificador", conclusao="skipped")]
        self.assertEqual(por_chave(self.montar(passos, None))["pagina"]["estado"], "andamento")


def contrato_etapas(etapas):
    problemas = []
    contrato._etapas(problemas, "etapas", etapas)
    return problemas


class Forms(unittest.TestCase):
    PUSH = execucao(1, evento="push", criado=T0)
    TODOS_OK = [passo("Set up job"), passo("Estágio 1 — ficha"), passo("Estágio 2-4 — relatório"), passo("Commitar ficha e PDF")]

    def test_sem_execucao(self):
        self.assertIsNone(ef.montar_etapas(None, []))
        self.assertIsNone(ef.montar_etapas({"erro": "x"}, []))

    def test_tudo_ok(self):
        etapas = ef.montar_etapas(self.PUSH, self.TODOS_OK)
        self.assertEqual(estados(etapas), [("forms", "ok"), ("ficha", "ok"), ("relatorio", "ok"), ("commit", "ok")])
        self.assertEqual(contrato_etapas(etapas), [])

    def test_execucao_manual_so_do_relatorio(self):
        manual = execucao(1, evento="workflow_dispatch")
        passos = [passo("Estágio 1 — ficha", conclusao="skipped"), passo("Estágio 2-4 — relatório"), passo("Commitar x")]
        etapas = ef.montar_etapas(manual, passos)
        self.assertEqual(estados(etapas), [("forms", "pulado"), ("ficha", "pulado"), ("relatorio", "ok"), ("commit", "ok")])

    def test_passos_ilegiveis_ou_fila(self):
        self.assertEqual(estados(ef.montar_etapas(self.PUSH, None)), [("forms", "ok"), ("ficha", "pendente")])
        fila = ef.montar_etapas(execucao(1, status="queued", conclusao=None), [])
        self.assertEqual(estados(fila), [("forms", "ok"), ("ficha", "andamento"), ("relatorio", "pendente"), ("commit", "pendente")])

    def test_relatorio_falhou_mas_commit_rodou(self):
        passos = [passo("Estágio 1"), passo("Estágio 2-4", conclusao="failure"), passo("Commitar")]
        etapas = por_chave(ef.montar_etapas(self.PUSH, passos))
        self.assertEqual(etapas["relatorio"]["estado"], "erro")
        self.assertIn("gh workflow run", etapas["relatorio"]["detalhe"])
        self.assertEqual(etapas["commit"]["estado"], "ok")

    def test_execucao_rodando_passos_que_ainda_nao_apareceram(self):
        rodando = execucao(1, status="in_progress", conclusao=None)
        passos = [passo("Estágio 1", status="in_progress", conclusao=None, inicio="2026-10-13T10:00:05Z", fim=None)]
        etapas = por_chave(ef.montar_etapas(rodando, passos))
        self.assertEqual((etapas["ficha"]["estado"], etapas["ficha"]["quando"]), ("andamento", "2026-10-13T10:00:05Z"))
        self.assertEqual(etapas["relatorio"]["estado"], "pendente")
        self.assertEqual(etapas["commit"]["estado"], "pendente")

    def test_execucao_terminada_sem_chegar_no_passo_e_pulado(self):
        etapas = por_chave(ef.montar_etapas(self.PUSH, [passo("Estágio 1")]))
        self.assertEqual(etapas["relatorio"]["estado"], "pulado")
        self.assertEqual(etapas["commit"]["estado"], "pulado")

    def test_passo_na_fila_e_pendente(self):
        passos = [passo("Estágio 1", status="queued", conclusao=None)]
        rodando = execucao(1, status="in_progress", conclusao=None)
        self.assertEqual(por_chave(ef.montar_etapas(rodando, passos))["ficha"]["estado"], "pendente")

    def test_passos_casados_pelo_comeco_do_nome(self):
        # contrato com o pipeline.yml do PlantsCare: renomeou lá, mudar PASSO_* aqui
        self.assertEqual((ef.PASSO_FICHA, ef.PASSO_RELATORIO, ef.PASSO_COMMIT), ("Estágio 1", "Estágio 2-4", "Commitar"))
        self.assertEqual((eb.PASSO_NOVO_PALPITE, eb.PASSO_RETRY_MANUAL, eb.PASSO_VERIFICADOR),
                         ("Roda pipeline (novo palpite)", "Roda pipeline (retry manual)", "Verificador"))


if __name__ == "__main__":
    unittest.main()
