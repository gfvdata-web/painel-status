"""src/detalhes_site.py: texto do cron, motivo da falha, alertas e o bloco detalhes do pop-up."""

import unittest

from tests import contrato
from tests.apoio import RedeFalsa, execucao, relogio_fixo
from src import detalhes_site as ds
from src import coleta_github as gh


class DescreverCron(unittest.TestCase):
    def test_dias_uteis_em_horario_de_brasilia(self):
        self.assertEqual(ds.descrever_cron("0 21 * * 1-5"), "de segunda a sexta às 18:00 (Brasília)")

    def test_todo_dia(self):
        self.assertEqual(ds.descrever_cron("30 9 * * *"), "todo dia às 06:30 (Brasília)")

    def test_virada_do_dia_desloca_o_dia_da_semana(self):
        # 02:00 UTC de segunda = 23:00 de domingo em Brasília
        self.assertEqual(ds.descrever_cron("0 2 * * 1"), "domingo às 23:00 (Brasília)")

    def test_domingo_como_7(self):
        self.assertEqual(ds.descrever_cron("0 12 * * 7"), "domingo às 09:00 (Brasília)")

    def test_lista_de_dias(self):
        self.assertEqual(ds.descrever_cron("0 12 * * 1,3"), "segunda, quarta às 09:00 (Brasília)")

    def test_a_cada_n_horas(self):
        self.assertEqual(ds.descrever_cron("0 */3 * * *"), "todo dia, a cada 3 h (minuto 00)")

    def test_formas_desconhecidas_voltam_cruas(self):
        for expr in ("*/5 * * * *", "0 21 1 * *", "0 21 * 3 *", "0 21 * * MON", "quebrado", "0 9-17 * * *", "0 1,13 * * *"):
            self.assertEqual(ds.descrever_cron(expr), f"{expr} (UTC)", expr)


class Duracao(unittest.TestCase):
    def test_duracao_em_segundos(self):
        self.assertEqual(ds.duracao_s(execucao(criado="2026-10-13T10:00:00Z", atualizado="2026-10-13T10:04:10Z")), 250)


class Relevantes(unittest.TestCase):
    def test_cancelamento_que_abriu_espaco_nao_conta(self):
        execs = [execucao(3, conclusao="success"), execucao(2, conclusao="cancelled"), execucao(1, conclusao="failure")]
        self.assertEqual([e["id"] for e in ds._relevantes(execs)], [3, 1])

    def test_cancelamento_mais_recente_conta(self):
        execs = [execucao(2, conclusao="cancelled"), execucao(1, conclusao="success")]
        self.assertEqual([e["id"] for e in ds._relevantes(execs)], [2, 1])

    def test_cancelamento_por_limite_de_6h_conta_mesmo_antigo(self):
        travada = execucao(2, conclusao="cancelled", criado="2026-10-12T00:00:00Z", atualizado="2026-10-12T06:00:00Z")
        self.assertTrue(ds._estourou_limite(travada))
        self.assertEqual([e["id"] for e in ds._relevantes([execucao(3), travada])], [3, 2])

    def test_em_andamento_fica_de_fora(self):
        execs = [execucao(2, status="in_progress", conclusao=None), execucao(1)]
        self.assertEqual([e["id"] for e in ds._relevantes(execs)], [1])


class MotivoDaFalha(unittest.TestCase):
    def test_motivo_vem_do_job_passo_e_log(self):
        falha = execucao(102, conclusao="failure")
        with RedeFalsa():
            motivo = ds.motivo_falha("gfvdata-web", "exemplo", falha)
        self.assertEqual(motivo, {"job": "coletar", "passo": "Coletar dados",
                                  "mensagem": "requests.exceptions.HTTPError: 503 Server Error: Service Unavailable"})

    def test_travou_por_6h(self):
        travada = execucao(2, conclusao="cancelled", criado="2026-10-12T00:00:00Z", atualizado="2026-10-12T06:00:00Z")
        motivo = ds.motivo_falha("o", "r", travada, jobs=[])
        self.assertIn("limite de 6 h", motivo["mensagem"])

    def test_sem_jobs_legiveis(self):
        with RedeFalsa(falhar=True):
            self.assertEqual(ds.motivo_falha("o", "r", execucao(1, conclusao="failure")),
                             {"job": None, "passo": None, "mensagem": None})

    def test_job_falho_sem_passo_falho(self):
        jobs = [{"id": 1, "nome": "j", "conclusao": "failure", "passos": [{"nome": "p", "conclusao": "success"}]}]
        with RedeFalsa():
            motivo = ds.motivo_falha("o", "r", execucao(102, conclusao="failure"), jobs=jobs)
        self.assertEqual((motivo["job"], motivo["passo"]), ("j", None))


class Alertas(unittest.TestCase):
    NO_AR = {"ok": True, "status_http": 200}

    def coleta(self, execs):
        concluidas = ds._relevantes(execs)
        return {
            "historico": execs,
            "ultimo_sucesso": next((e for e in concluidas if e["conclusao"] == "success"), None),
        }

    def textos(self, coleta=None, deploy=None, no_ar=NO_AR):
        with relogio_fixo("src.detalhes_site"):
            return [a["texto"] for a in ds._alertas(coleta, deploy, no_ar)]

    def test_tudo_bem_sem_alertas(self):
        self.assertEqual(self.textos(self.coleta([execucao(1, criado="2026-10-13T10:00:00Z")])), [])
        self.assertEqual(self.textos(None), [])

    def test_site_fora_do_ar_e_erro(self):
        with relogio_fixo("src.detalhes_site"):
            alertas = ds._alertas(None, None, {"ok": False, "status_http": 503})
        self.assertEqual(alertas, [{"nivel": "erro", "texto": "O site não respondeu na última checagem."}])
        self.assertEqual(len(ds._alertas(None, None, None)), 1)

    def test_ultima_coleta_falhou_e_cancelada(self):
        falhou = self.coleta([execucao(2, conclusao="failure"), execucao(1)])
        self.assertIn("A última coleta falhou.", self.textos(falhou))
        cancelada = self.coleta([execucao(2, conclusao="cancelled"), execucao(1)])
        self.assertIn("A última coleta foi cancelada.", self.textos(cancelada))

    def test_nenhum_sucesso_nas_execucoes_recentes(self):
        self.assertIn("Nenhuma coleta com sucesso nas execuções recentes.",
                      self.textos(self.coleta([execucao(1, conclusao="failure")])))

    def test_sem_coleta_ha_n_dias(self):
        velha = self.coleta([execucao(1, criado="2026-10-09T11:00:00Z", atualizado="2026-10-09T11:05:00Z")])
        self.assertEqual(self.textos(velha), ["Sem coleta com sucesso há 4 dias."])
        recente = self.coleta([execucao(1, criado="2026-10-10T10:00:00Z", atualizado="2026-10-10T10:05:00Z")])
        self.assertEqual(self.textos(recente), [])  # 3 dias: ainda dentro do fim de semana

    def test_deploy_do_pages_falhou(self):
        deploy = {"status": "completed", "conclusao": "failure"}
        self.assertEqual(self.textos(None, deploy), ["O último deploy do GitHub Pages falhou."])
        self.assertEqual(self.textos(None, {"status": "in_progress", "conclusao": None}), [])


class MontarDetalhes(unittest.TestCase):
    SITE = {"slug": "s", "nome": "S", "repo": "exemplo", "workflow_arquivo": "coleta.yml"}

    def test_site_com_workflow(self):
        with RedeFalsa(), relogio_fixo("src.detalhes_site"):
            execs = gh.execucoes_workflow("gfvdata-web", "exemplo", "coleta.yml", 15)
            commits = gh.commits_no_caminho("gfvdata-web", "exemplo", "docs/data")
            detalhes, alertas = ds.montar_detalhes("gfvdata-web", self.SITE, commits, execs, {"ok": True, "status_http": 200})
        coleta = detalhes["coleta"]
        self.assertEqual(coleta["workflow"], "coleta.yml")
        self.assertEqual(coleta["url_workflow"], "https://github.com/gfvdata-web/exemplo/actions/workflows/coleta.yml")
        self.assertEqual([a["descricao"] for a in coleta["agenda"]],
                         ["de segunda a sexta às 18:00 (Brasília)", "todo dia às 06:30 (Brasília)"])
        self.assertEqual([e["id"] for e in coleta["historico"]], [103, 102, 101])
        self.assertEqual(coleta["historico"][0]["duracao_s"], 247)
        self.assertEqual(coleta["ultimo_sucesso"]["id"], 103)
        self.assertEqual(coleta["ultima_falha"]["id"], 102)
        self.assertEqual(coleta["ultima_falha"]["motivo"]["passo"], "Coletar dados")
        self.assertEqual([p["nome"] for p in coleta["passos_ultima"]][:2], ["Set up job", "Roda pipeline (novo palpite)"])
        self.assertEqual(len(detalhes["commits_dados"]), 2)
        # o deploy cancelado (5) é ignorado: vale o último que não foi substituído
        self.assertEqual(detalhes["deploy_pages"]["conclusao"], "success")
        self.assertEqual(alertas, [])

    def test_site_sem_workflow(self):
        site = {**self.SITE, "workflow_arquivo": None}
        with RedeFalsa(), relogio_fixo("src.detalhes_site"):
            detalhes, alertas = ds.montar_detalhes("gfvdata-web", site, [], None, {"ok": False})
        self.assertIsNone(detalhes["coleta"])
        self.assertEqual(detalhes["commits_dados"], [])
        self.assertEqual([a["nivel"] for a in alertas], ["erro"])

    def test_api_fora_do_ar_nao_derruba(self):
        erro = {"erro": "falha de rede ao consultar execuções: x"}
        with RedeFalsa(falhar=True), relogio_fixo("src.detalhes_site"):
            detalhes, alertas = ds.montar_detalhes("gfvdata-web", self.SITE, erro, erro, {"ok": True})
        self.assertEqual(detalhes["coleta"]["erro"], erro["erro"])
        self.assertEqual(detalhes["coleta"]["historico"], [])
        self.assertEqual(detalhes["commits_dados"], [])
        self.assertIsNone(detalhes["deploy_pages"])
        self.assertEqual(alertas, [])

    def test_formato_bate_com_o_contrato(self):
        with RedeFalsa(), relogio_fixo("src.detalhes_site"):
            execs = gh.execucoes_workflow("gfvdata-web", "exemplo", "coleta.yml", 15)
            detalhes, _ = ds.montar_detalhes("gfvdata-web", self.SITE, [], execs, {"ok": True})
        problemas = []
        contrato._coleta(problemas, "coleta", detalhes["coleta"])
        self.assertEqual(problemas, [])


if __name__ == "__main__":
    unittest.main()
