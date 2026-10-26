"""src/coleta_github.py contra a API do GitHub falsa (tests/fixtures): formato de saída e erros de rede."""

import os
import unittest
import urllib.error
from unittest import mock

from tests import apoio
from tests.apoio import RedeFalsa
from src import coleta_github as gh


class Commits(unittest.TestCase):
    def test_formato_e_primeira_linha_da_mensagem(self):
        with RedeFalsa() as rede:
            commits = gh.commits_no_caminho("gfvdata-web", "exemplo", "docs/data")
        self.assertEqual(commits[0], {
            "data": "2026-10-13T08:00:12Z",
            "mensagem": "Atualiza dados da rodada 21",
            "sha": "a1b2c3d",
            "url": "https://github.com/gfvdata-web/exemplo/commit/a1b2c3d4e5f60718293a4b5c6d7e8f9012345678",
        })
        self.assertEqual(len(commits), 2)
        self.assertIn("/repos/gfvdata-web/exemplo/commits?path=docs/data&per_page=5", rede.chamadas[0])

    def test_branch_vira_sha_na_url(self):
        with RedeFalsa() as rede:
            gh.commits_no_caminho("gfvdata-web", "exemplo", "dados", branch="gh-pages")
        self.assertTrue(rede.chamadas[0].endswith("&sha=gh-pages"))

    def test_sem_commits_vira_erro(self):
        with mock.patch.object(gh, "get_json", lambda url, headers=None: []):
            self.assertEqual(gh.commits_no_caminho("o", "r", "c"), {"erro": "nenhum commit encontrado nesse caminho"})

    def test_erro_http_e_de_rede_viram_dict_com_erro(self):
        with mock.patch.object(gh, "get_json", mock.Mock(side_effect=apoio.erro_http(404))):
            self.assertEqual(gh.commits_no_caminho("o", "r", "c"), {"erro": "HTTP 404 ao consultar commits"})
        with RedeFalsa(falhar=True):
            erro = gh.commits_no_caminho("o", "r", "c")
        self.assertIn("falha de rede ao consultar commits", erro["erro"])

    def test_commit_com_mensagem_vazia_nao_derruba_a_coleta(self):
        # git permite commit sem mensagem; splitlines()[0] levantaria IndexError e derrubaria a rodada
        vazio = [{"sha": "abcdef012345", "commit": {"author": {"date": "2026-10-13T08:00:00Z"}, "message": ""},
                  "html_url": "https://github.com/o/r/commit/abcdef0"}]
        with mock.patch.object(gh, "get_json", lambda url, headers=None: vazio):
            commits = gh.commits_no_caminho("o", "r", "c")
        self.assertEqual(commits[0]["mensagem"], "")


class Execucoes(unittest.TestCase):
    def test_formato_das_execucoes(self):
        with RedeFalsa() as rede:
            runs = gh.execucoes_workflow("gfvdata-web", "exemplo", "coleta.yml", 100)
        self.assertEqual([r["id"] for r in runs], [103, 102, 101])
        self.assertEqual(runs[0], {
            "id": 103, "evento": "schedule", "status": "completed", "conclusao": "success",
            "criado_em": "2026-10-13T10:00:03Z", "atualizado_em": "2026-10-13T10:04:10Z",
            "url": "https://github.com/gfvdata-web/exemplo/actions/runs/103",
        })
        self.assertIn("/actions/workflows/coleta.yml/runs?per_page=100", rede.chamadas[0])

    def test_sem_execucoes_e_erro_de_rede(self):
        with mock.patch.object(gh, "get_json", lambda url, headers=None: {"workflow_runs": []}):
            self.assertEqual(gh.execucoes_workflow("o", "r", "w.yml"), {"erro": "nenhuma execução encontrada"})
        with RedeFalsa(falhar=True):
            self.assertIn("erro", gh.execucoes_workflow("o", "r", "w.yml"))

    def test_execucao_em_andamento_tem_conclusao_none(self):
        resposta = {"workflow_runs": [{"id": 1, "event": "push", "status": "in_progress", "conclusion": None,
                                       "created_at": "x", "updated_at": "y", "html_url": "u"}]}
        with mock.patch.object(gh, "get_json", lambda url, headers=None: resposta):
            self.assertIsNone(gh.execucoes_workflow("o", "r", "w.yml")[0]["conclusao"])


class JobsEPassos(unittest.TestCase):
    def test_jobs_e_passos_na_ordem(self):
        with RedeFalsa():
            jobs = gh.jobs_execucao("o", "r", 103)
            passos = gh.passos_execucao("o", "r", 103)
        self.assertEqual(jobs[0]["id"], 9001)
        self.assertEqual(jobs[0]["passos"][0]["nome"], "Set up job")
        self.assertEqual(passos[1], {"nome": "Roda pipeline (novo palpite)", "status": "completed",
                                     "conclusao": "success", "inicio": "2026-10-13T10:00:06Z",
                                     "fim": "2026-10-13T10:01:00Z"})
        self.assertEqual(len(passos), 6)

    def test_falha_devolve_none(self):
        with RedeFalsa(falhar=True):
            self.assertIsNone(gh.jobs_execucao("o", "r", 1))
            self.assertIsNone(gh.passos_execucao("o", "r", 1))
            self.assertIsNone(gh.anotacoes_job("o", "r", 1))
            self.assertIsNone(gh.crons_do_workflow("o", "r", "w.yml"))
            self.assertIsNone(gh.execucoes_pages("o", "r"))
            self.assertIsNone(gh.linha_de_erro_do_log("o", "r", 1))

    def test_job_sem_passos(self):
        with mock.patch.object(gh, "get_json", lambda url, headers=None: {"jobs": [{"id": 1, "name": "n"}]}):
            self.assertEqual(gh.jobs_execucao("o", "r", 1), [{"id": 1, "nome": "n", "conclusao": None, "passos": []}])

    def test_anotacoes(self):
        with RedeFalsa():
            self.assertEqual(gh.anotacoes_job("o", "r", 9001), [
                {"nivel": "warning", "mensagem": "Node.js 20 is deprecated."},
                {"nivel": "failure", "mensagem": "Process completed with exit code 1."},
            ])


class LogCronPages(unittest.TestCase):
    def test_linha_de_erro_pega_a_antes_do_error_generico(self):
        with RedeFalsa():
            linha = gh.linha_de_erro_do_log("o", "r", 9002)
        self.assertEqual(linha, "requests.exceptions.HTTPError: 503 Server Error: Service Unavailable")

    def test_linha_de_erro_usa_a_mensagem_propria_quando_nao_e_generica(self):
        log = "2026-10-12T10:00:00.1Z passo ok\n2026-10-12T10:00:01.1Z ##[error]Bolão: rodada não encontrada\n"
        with mock.patch.object(gh, "get_texto", lambda url, headers=None: log):
            self.assertEqual(gh.linha_de_erro_do_log("o", "r", 1), "Bolão: rodada não encontrada")

    def test_log_sem_erro(self):
        with mock.patch.object(gh, "get_texto", lambda url, headers=None: "2026-10-12T10:00:00.1Z tudo bem\n"):
            self.assertIsNone(gh.linha_de_erro_do_log("o", "r", 1))

    def test_mensagem_longa_e_cortada(self):
        log = "2026-10-12T10:00:00.1Z ##[error]" + "x" * 1000
        with mock.patch.object(gh, "get_texto", lambda url, headers=None: log):
            self.assertEqual(len(gh.linha_de_erro_do_log("o", "r", 1)), 400)

    def test_crons_lidos_do_arquivo_do_workflow(self):
        with RedeFalsa() as rede:
            crons = gh.crons_do_workflow("o", "r", "coleta.yml")
        self.assertEqual(crons, ["0 21 * * 1-5", "30 9 * * *"])
        self.assertIn("/contents/.github/workflows/coleta.yml", rede.chamadas[0])

    def test_deploys_do_pages_so_o_workflow_certo(self):
        with RedeFalsa():
            deploys = gh.execucoes_pages("o", "r")
        self.assertEqual([d["conclusao"] for d in deploys], ["success", "cancelled"])
        self.assertNotIn("id", deploys[0])


class SiteNoArEPeriodo(unittest.TestCase):
    def test_no_ar(self):
        with RedeFalsa():
            self.assertEqual(gh.site_no_ar("https://s.test/"), {"ok": True, "status_http": 200})

    def test_fora_do_ar_com_codigo(self):
        with RedeFalsa(sites_fora={"https://s.test/": 503}):
            self.assertEqual(gh.site_no_ar("https://s.test/"), {"ok": False, "status_http": 503})

    def test_login_401_conta_como_no_ar(self):
        with RedeFalsa(sites_fora={"https://s.test/": 401}):
            self.assertEqual(gh.site_no_ar("https://s.test/"), {"ok": True, "status_http": 401})

    def test_fora_do_ar_por_dns_ou_timeout(self):
        for exc in (urllib.error.URLError("dns"), TimeoutError("lento")):
            with mock.patch.object(gh, "get_status", mock.Mock(side_effect=exc)):
                resultado = gh.site_no_ar("https://s.test/")
            self.assertFalse(resultado["ok"])
            self.assertIn("erro", resultado)

    def test_redirecionamento_2xx_vs_3xx(self):
        with mock.patch.object(gh, "get_status", lambda url: 301):
            self.assertFalse(gh.site_no_ar("https://s.test/")["ok"])

    def test_periodo_publicado(self):
        with RedeFalsa():
            self.assertEqual(gh.periodo_publicado("https://s.test/d.json"), {"inicio": "2020-01", "fim": "2026-08"})

    def test_periodo_ausente_ou_rede_fora(self):
        with mock.patch.object(gh, "get_json", lambda url, headers=None: {"dados": []}):
            self.assertIsNone(gh.periodo_publicado("https://s.test/d.json"))
        with RedeFalsa(falhar=True):
            self.assertIsNone(gh.periodo_publicado("https://s.test/d.json"))


class Cabecalhos(unittest.TestCase):
    def test_token_vai_como_bearer_so_se_existir(self):
        with mock.patch.dict(os.environ, {"GITHUB_TOKEN": "tok"}):
            self.assertEqual(gh._headers()["Authorization"], "Bearer tok")
        with mock.patch.dict(os.environ, {}, clear=True):
            self.assertNotIn("Authorization", gh._headers())

    def test_agora_iso_e_utc(self):
        self.assertTrue(gh.agora_iso().endswith("+00:00"))


if __name__ == "__main__":
    unittest.main()
