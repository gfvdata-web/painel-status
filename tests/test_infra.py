"""Costuras entre arquivos: workflow do Actions ↔ módulos Python, Worker ↔ wrangler.jsonc/migrações/
workflow, documentação (README/CLAUDE.md) ↔ cadastro e arquivos, inventário do scripts/check."""

import json
import re
import unittest

from tests import apoio  # noqa: F401
from tests.apoio import RAIZ
from src import config

WORKFLOW = (RAIZ / ".github" / "workflows" / "atualizar-status.yml").read_text(encoding="utf-8")
INDEX_TS = (RAIZ / "worker" / "src" / "index.ts").read_text(encoding="utf-8")
README = (RAIZ / "README.md").read_text(encoding="utf-8")
CLAUDE = (RAIZ / "CLAUDE.md").read_text(encoding="utf-8")
CHECK = (RAIZ / "scripts" / "check").read_text(encoding="utf-8")
DE_OUTROS_REPOS = {"scripts/publicar.mjs", "docs/data"}  # citados na tabela de sites: são de outros repositórios


def jsonc(texto):
    """JSONC -> objeto: tira comentários // e /* */ (fora de strings) e vírgulas sobrando."""
    saida, i, em_texto = [], 0, False
    while i < len(texto):
        c = texto[i]
        if em_texto:
            saida.append(c)
            if c == "\\":
                saida.append(texto[i + 1])
                i += 1
            elif c == '"':
                em_texto = False
        elif c == '"':
            em_texto = True
            saida.append(c)
        elif texto.startswith("//", i):
            i = texto.find("\n", i)
            i = len(texto) if i < 0 else i - 1
        elif texto.startswith("/*", i):
            i = texto.find("*/", i) + 1
        else:
            saida.append(c)
        i += 1
    return json.loads(re.sub(r",(\s*[}\]])", r"\1", "".join(saida)))


class WorkflowDoStatus(unittest.TestCase):
    def test_modulos_chamados_existem_e_sao_executaveis(self):
        modulos = set(re.findall(r"python -m (src\.\w+)", WORKFLOW))
        self.assertTrue(modulos >= {"src.publicacao", "src.envio", "src.vigia_bolao", "src.vigia_forms"})
        for modulo in modulos:
            arquivo = RAIZ / (modulo.replace(".", "/") + ".py")
            self.assertTrue(arquivo.exists(), modulo)
            self.assertIn('if __name__ == "__main__":', arquivo.read_text(encoding="utf-8"), modulo)

    def test_flags_passadas_sao_aceitas_pelos_scripts(self):
        publicacao = (RAIZ / "src" / "publicacao.py").read_text(encoding="utf-8")
        for flag in set(re.findall(r"src\.publicacao (--[\w-]+)", WORKFLOW)):
            self.assertIn(f'"{flag}"', publicacao, flag)
        for vigia in ("vigia_bolao", "vigia_forms"):
            fonte = (RAIZ / "src" / f"{vigia}.py").read_text(encoding="utf-8")
            for flag in ("--desde", "--anterior"):
                self.assertIn(f'"{flag}"', fonte, f"{vigia} {flag}")
                self.assertIn(f"src.{vigia} --desde", WORKFLOW)
        for acao in set(re.findall(r"src\.envio (\w+)", WORKFLOW)):
            self.assertIn(acao, {"enviar", "baixar"})

    def test_workflow_le_as_linhas_que_os_vigias_imprimem(self):
        self.assertIn("s/^fase=//p", WORKFLOW)
        self.assertIn("s/^mudou=//p", WORKFLOW)
        for fase in ("concluido", "sem_execucao", "vigilia"):
            self.assertIn(fase, WORKFLOW)

    def test_disparos_externos_citados_nos_dois_lados(self):
        tipos = re.search(r"types: \[([^\]]+)\]", WORKFLOW).group(1).replace(" ", "").split(",")
        self.assertEqual(sorted(tipos), ["bolao_palpite", "plantas_envio"])
        for tipo in tipos:
            self.assertTrue(tipo in README, f"README sem {tipo}")
            self.assertIn(tipo, WORKFLOW.split("jobs:")[1])

    def test_um_job_por_vigia_e_ligado_ao_cadastro(self):
        self.assertIn("vigiar-bolao:", WORKFLOW)
        self.assertIn("vigiar-forms:", WORKFLOW)
        self.assertTrue(any(s["pipeline_bolao"] for s in config.SITES))
        self.assertTrue(any(s["pipeline_forms"] for s in config.SITES))

    def test_rotina_de_3_em_3_horas_igual_ao_worker(self):
        cron = re.search(r"cron: '([^']+)'", WORKFLOW).group(1)
        self.assertEqual(cron, "0 */3 * * *")
        self.assertEqual(int(re.search(r"HORAS_ENTRE_RODADAS = (\d+)", INDEX_TS).group(1)), 3)

    def test_workflow_tem_permissao_de_escrita_e_fila_unica(self):
        self.assertIn("contents: write", WORKFLOW)
        self.assertIn("group: atualizar-status", WORKFLOW)
        self.assertNotIn("\r", WORKFLOW)


class WorkerConfig(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.wrangler = jsonc((RAIZ / "worker" / "wrangler.jsonc").read_text(encoding="utf-8"))
        cls.sql = "".join(p.read_text(encoding="utf-8") for p in sorted((RAIZ / "worker" / "migrations").glob("*.sql")))

    def test_pasta_de_arquivos_estaticos_e_a_do_site(self):
        diretorio = (RAIZ / "worker" / self.wrangler["assets"]["directory"]).resolve()
        self.assertEqual(diretorio, (RAIZ / "docs").resolve())
        self.assertEqual(self.wrangler["assets"]["binding"], "ASSETS")

    def test_rotas_tratadas_pelo_worker_primeiro(self):
        rotas = self.wrangler["assets"]["run_worker_first"]
        for prefixo in ("PREFIXO_DADOS", "PREFIXO_APP", "PREFIXO_ENVIO_APP", "PREFIXO_ENVIO"):
            caminho = re.search(rf'{prefixo} = "([^"]+)"', INDEX_TS).group(1)
            self.assertTrue(any(caminho.startswith(r.rstrip("*")) for r in rotas), f"{caminho} fora de run_worker_first")

    def test_bindings_usados_no_codigo_estao_declarados(self):
        usados = set(re.findall(r"env\.([A-Z_]+)\b", INDEX_TS))
        declarados = {d["binding"] for d in self.wrangler["d1_databases"]} | {k["binding"] for k in self.wrangler["kv_namespaces"]}
        declarados.add(self.wrangler["assets"]["binding"])
        segredos = {"TOKEN_ENVIO", "GITHUB_TOKEN_DISPARO"}  # wrangler secret put (citados no wrangler.jsonc)
        self.assertEqual(usados - declarados - segredos, set())
        for segredo in segredos:
            self.assertIn(segredo, (RAIZ / "worker" / "wrangler.jsonc").read_text(encoding="utf-8"))

    def test_sem_r2_nem_produto_pago(self):
        self.assertNotIn("r2_buckets", self.wrangler)
        self.assertEqual(self.wrangler["d1_databases"][0]["migrations_dir"], "migrations")

    def test_cron_do_worker_bate_com_a_logica_da_agenda(self):
        self.assertEqual(self.wrangler["triggers"]["crons"], ["*/30 * * * *"])
        self.assertIn("getUTCMinutes() < 30", INDEX_TS)

    def test_tabelas_e_colunas_que_o_worker_consulta_existem_na_migracao(self):
        for tabela in set(re.findall(r"(?:FROM|INTO) (\w+)", INDEX_TS)):
            self.assertRegex(self.sql, rf"CREATE TABLE {tabela}\b", tabela)
        for coluna in ("chave", "conteudo", "etag", "gerado_em", "atualizado_em"):
            self.assertIn(coluna, self.sql)

    def test_workflow_disparado_pelo_cron_existe(self):
        arquivo = re.search(r"workflows/([\w.-]+)/dispatches", INDEX_TS).group(1)
        self.assertTrue((RAIZ / ".github" / "workflows" / arquivo).exists())
        self.assertIn("workflow_dispatch", (RAIZ / ".github" / "workflows" / arquivo).read_text(encoding="utf-8"))

    def test_script_check_do_worker_e_o_tsc(self):
        pacote = json.loads((RAIZ / "worker" / "package.json").read_text(encoding="utf-8"))
        self.assertEqual(pacote["scripts"]["check"], "tsc --noEmit")
        self.assertIn("tsc --noEmit", CHECK)  # o scripts/check roda o mesmo tsc


class DocumentacaoEmDia(unittest.TestCase):
    def test_readme_lista_cada_site_e_cada_form_do_cadastro(self):
        for site in config.SITES:
            self.assertTrue(site["repo"] in README, f"README sem o repo {site['repo']}")
            self.assertTrue(site["nome"] in README, f"README sem o nome {site['nome']}")
            if site["form_url"]:
                self.assertTrue(site["form_url"] in README, f"README sem o form de {site['slug']}")
        for form in config.FORMS_AVULSOS:
            self.assertTrue(form["url"] in README, f"README sem o form avulso {form['nome']}")

    def test_readme_cita_cada_arquivo_de_src_docs_worker_scripts_e_tests(self):
        arquivos = [p for pasta in ("src", "docs", "worker/src", "scripts", "tests")
                    for p in (RAIZ / pasta).iterdir() if p.is_file() and p.suffix in {".py", ".html", ".ts", ".css", ".mjs"} | {""}]
        arquivos += [RAIZ / "docs/js/app.js", RAIZ / "docs/js/lista.js", RAIZ / "docs/_redirects"]
        for arquivo in arquivos:
            if arquivo.name in {"__init__.py", "apoio.py", "contrato.py", "regenerar_fixtures.py"} or arquivo.name.startswith("test_"):
                continue  # os arquivos de apoio de tests/ entram pelo item "tests/" da árvore
            self.assertTrue(arquivo.name in README, f"README (árvore) sem {arquivo.relative_to(RAIZ)}")
        self.assertRegex(README, r"tests/")

    def test_caminhos_citados_na_documentacao_existem(self):
        for nome, texto in (("CLAUDE.md", CLAUDE), ("README.md", README)):
            for caminho in set(re.findall(r"`((?:src|docs|worker|scripts|tests|\.github|\.githooks)/[\w./-]+)`", texto)):
                if "<" in caminho or caminho.endswith("...") or caminho in DE_OUTROS_REPOS:
                    continue
                self.assertTrue((RAIZ / caminho.rstrip("/")).exists(), f"{nome} cita {caminho}, que não existe")

    def test_claude_md_exige_teste_junto_com_a_mudanca(self):
        self.assertIn("scripts/check", CLAUDE)
        self.assertRegex(CLAUDE, r"(?i)funcionalidade nova[^\n]*teste")

    def test_arquivos_de_shell_com_lf(self):
        self.assertNotIn("\r", CHECK)
        attrs = (RAIZ / ".gitattributes").read_text(encoding="utf-8")
        self.assertIn("scripts/* text eol=lf", attrs)
        self.assertIn(".githooks/* text eol=lf", attrs)
        hook = RAIZ / ".githooks" / "pre-commit"
        if hook.exists():
            self.assertNotIn("\r", hook.read_text(encoding="utf-8"))
            self.assertIn("scripts/check", hook.read_text(encoding="utf-8"))


class InventarioDoCheck(unittest.TestCase):
    """Todo arquivo de funcionalidade aparece na lista "Cobertura:" do scripts/check e é citado por um teste."""

    @classmethod
    def setUpClass(cls):
        cls.cobertura = CHECK.split("Cobertura:")[1].split("\n\n")[0] if "Cobertura:" in CHECK else ""
        cls.testes = "".join(p.read_text(encoding="utf-8") for p in (RAIZ / "tests").glob("*") if p.suffix in {".py", ".mjs"})

    def test_ha_inventario(self):
        self.assertTrue(self.cobertura.strip(), "scripts/check sem o bloco 'Cobertura:' no topo")

    def itens(self):
        return (
            [f"src/{p.name}" for p in (RAIZ / "src").glob("*.py") if p.name != "__init__.py"]
            + [f"docs/{p.name}" for p in (RAIZ / "docs").glob("*.html")]
            + [f"docs/js/{p.name}" for p in (RAIZ / "docs/js").glob("*.js")]
            + [f"docs/css/{p.name}" for p in (RAIZ / "docs/css").glob("*.css")]
            + ["worker/src/index.ts", ".github/workflows/atualizar-status.yml", "docs/_redirects"]
        )

    def test_toda_funcionalidade_esta_no_inventario(self):
        for item in self.itens():
            self.assertTrue(item.split("/")[-1] in self.cobertura, f"{item} fora do inventário 'Cobertura:' do scripts/check")

    def test_toda_funcionalidade_e_citada_por_algum_teste(self):
        for item in self.itens():
            nome = item.split("/")[-1]
            modulo = nome.removesuffix(".py")
            citado = any(t in self.testes for t in (nome, f"src.{modulo}", f"import {modulo}", f"{modulo},", f" {modulo}\n"))
            if nome.endswith(".css"):
                citado = "css" in self.testes
            self.assertTrue(citado, f"nenhum teste cita {item}")


if __name__ == "__main__":
    unittest.main()
