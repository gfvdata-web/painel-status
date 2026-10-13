"""Páginas (docs/index.html = lista, docs/v1.html = painel completo) e seus JS/CSS, sem navegador:
links locais, ids que o JS usa, campos do status.json que o JS lê, classes de estado com estilo,
_redirects e o intervalo de recarga igual em app.js, lista.js e App.kt."""

import re
import unittest
from html.parser import HTMLParser
from urllib.parse import urlsplit

from tests import contrato
from tests.apoio import RAIZ

DOCS = RAIZ / "docs"
PAGINAS = {"index.html": "js/lista.js", "v1.html": "js/app.js"}


def ler(caminho):
    return (DOCS / caminho).read_text(encoding="utf-8")


class Pagina(HTMLParser):
    def __init__(self, html):
        super().__init__()
        self.ids, self.classes, self.links, self.scripts, self.estilos = set(), set(), [], [], []
        self.titulo, self._em_titulo = "", False
        self.feed(html)

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a:
            self.ids.add(a["id"])
        self.classes.update((a.get("class") or "").split())
        if tag == "a" and a.get("href"):
            self.links.append((a["href"], a))
        if tag == "script" and a.get("src"):
            self.scripts.append(a["src"])
        if tag == "link" and a.get("href"):
            self.estilos.append(a["href"])
        self._em_titulo = tag == "title"

    def handle_data(self, data):
        if self._em_titulo:
            self.titulo += data

    def handle_endtag(self, tag):
        self._em_titulo = False


def e_local(url):
    partes = urlsplit(url)
    return not partes.scheme and not partes.netloc and not url.startswith(("#", "//", "mailto:", "javascript:"))


def resolver(url):
    """Arquivo de docs/ que o Worker (arquivos estáticos, sem .html na URL) serviria; None se não existe."""
    caminho = urlsplit(url).path
    if caminho in ("", "./", "/"):
        caminho = "index.html"
    caminho = caminho.lstrip("/")
    for candidato in (caminho, caminho + ".html"):
        if (DOCS / candidato).is_file():
            return candidato
    return None


class LinksELocais(unittest.TestCase):
    def test_links_scripts_e_estilos_locais_existem(self):
        for nome in PAGINAS:
            pagina = Pagina(ler(nome))
            for href, _ in pagina.links:
                if e_local(href):
                    self.assertIsNotNone(resolver(href), f"{nome}: link local quebrado: {href}")
            for src in (*pagina.scripts, *pagina.estilos):
                if e_local(src):
                    self.assertTrue((DOCS / urlsplit(src).path).is_file(), f"{nome}: arquivo ausente: {src}")

    def test_toda_pagina_carrega_seu_js_e_o_estilo(self):
        for nome, js in PAGINAS.items():
            pagina = Pagina(ler(nome))
            self.assertIn(js, pagina.scripts, nome)
            self.assertIn("css/estilo.css", pagina.estilos, nome)
            self.assertTrue(pagina.titulo.strip(), f"{nome} sem <title>")
        self.assertIn("css/lista.css", Pagina(ler("index.html")).estilos)

    def test_links_externos_abrem_com_noopener(self):
        for nome in PAGINAS:
            for href, atributos in Pagina(ler(nome)).links:
                if not e_local(href) and atributos.get("target") == "_blank":
                    self.assertIn("noopener", atributos.get("rel", ""), f"{nome}: {href}")

    def test_alternador_de_versao_aponta_um_para_o_outro(self):
        # V1 (/v1) <-> V2 (/): cada página marca a sua como atual
        index, v1 = Pagina(ler("index.html")), Pagina(ler("v1.html"))
        destinos = lambda p: {resolver(h) for h, a in p.links if "versao-switch__link" in (a.get("class") or "")}
        self.assertEqual(destinos(index), {"index.html", "v1.html"})
        self.assertEqual(destinos(v1), {"index.html", "v1.html"})
        for nome, atual in (("index.html", "index.html"), ("v1.html", "v1.html")):
            marcados = [h for h, a in Pagina(ler(nome)).links if a.get("aria-current") == "page"]
            self.assertEqual([resolver(h) for h in marcados], [atual], nome)

    def test_redirects_levam_a_pagina_existente(self):
        linhas = [l.split() for l in ler("_redirects").splitlines() if l.strip() and not l.startswith("#")]
        self.assertTrue(linhas)
        for origem, destino, *codigo in linhas:
            self.assertTrue(origem.startswith("/"), origem)
            self.assertIsNotNone(resolver(destino), f"_redirects: {origem} -> {destino}")
            self.assertIn(codigo[0] if codigo else "302", {"301", "302", "308"})

    def test_css_nao_referencia_arquivo_inexistente(self):
        for css in (DOCS / "css").glob("*.css"):
            for url in re.findall(r"url\(['\"]?([^)'\"]+)", css.read_text(encoding="utf-8")):
                if e_local(url) and not url.startswith("data:"):
                    self.assertTrue((css.parent / urlsplit(url).path).is_file(), f"{css.name}: {url}")


class IdsDoJs(unittest.TestCase):
    DINAMICOS = set()  # ids criados pelo próprio JS (canvas "grafico-*" nunca aparecem como literal)

    def ids_usados(self, js):
        usados = set(re.findall(r"getElementById\(\s*['\"]([\w-]+)['\"]", js))
        for seletor in re.findall(r"querySelector(?:All)?\(\s*['\"]([^'\"]+)['\"]", js):
            usados.update(re.findall(r"#([\w-]+)", seletor))
        return usados

    def test_todo_id_usado_pelo_js_existe_no_html(self):
        for nome, js in PAGINAS.items():
            html = Pagina(ler(nome))
            faltando = self.ids_usados(ler(js)) - html.ids - self.dinamicos(js)
            self.assertEqual(faltando, set(), f"{js} usa ids que {nome} não tem")

    def dinamicos(self, js):
        # ids que o JS cria em innerHTML (ex.: dialogo-titulo) e depois consulta no mesmo arquivo
        texto = ler(js)
        return {i for i in self.ids_usados(texto) if re.search(rf'id="{re.escape(i)}"', texto)}

    def test_ids_do_html_nao_se_repetem(self):
        for nome in PAGINAS:
            ids = re.findall(r'\bid="([^"]+)"', ler(nome))
            self.assertEqual(len(ids), len(set(ids)), f"{nome}: id repetido")

    def test_botoes_de_tema_existem_nas_duas_paginas(self):
        for nome in PAGINAS:
            html = ler(nome)
            self.assertIn('id="tema-switch"', html)
            self.assertEqual(re.findall(r'data-tema="(\w+)"', html), ["light", "dark"])

    def test_chave_do_tema_e_a_mesma_no_html_e_nos_js(self):
        for nome, js in PAGINAS.items():
            self.assertIn('localStorage.getItem("tema")', ler(nome))
            self.assertIn('localStorage.setItem("tema"', ler(js))

    def test_app_js_precisa_do_chart_js_e_do_dialogo(self):
        v1 = Pagina(ler("v1.html"))
        self.assertTrue(any("chart.js" in s for s in v1.scripts))
        self.assertIn("dialogo-conteudo", v1.ids)
        self.assertIn("dialogo-site", v1.ids)


class CamposDoStatus(unittest.TestCase):
    """Toda propriedade snake_case que os JS leem é um campo do contrato (e vice-versa para o essencial)."""

    # Campos sem os quais a página não desenha o que promete (lista.js / app.js).
    LISTA_LE = {"sites", "forms_avulsos", "gerado_em", "destaque", "site_no_ar", "ok", "status_http", "alertas",
                "nivel", "texto", "em_vigilia", "pages_url", "nome", "form_url", "form_rotulo", "acesso",
                "serie_diaria", "visitantes", "url"}
    APP_LE = LISTA_LE | {"ultimo_commit_dados", "periodo_publicado", "ultima_execucao_pipeline", "etapas_pipeline",
                         "rodada_mais_recente", "visitantes_unicos", "historico_arquivo", "detalhes", "coleta",
                         "pipeline_forms", "etapas", "execucao", "commits_dados", "deploy_pages", "agenda",
                         "ultimo_sucesso", "ultima_falha", "motivo", "passos_ultima", "url_workflow", "workflow",
                         "criado_em", "duracao_s", "conclusao", "evento", "avisos", "execucoes", "mensagem", "sha",
                         "data", "estado", "titulo", "detalhe", "quando", "erro", "repo", "slug", "descricao", "cron"}

    def propriedades(self, js):
        texto = re.sub(r"//[^\n]*", "", ler(js))  # comentários citam campos de outros lugares
        return set(re.findall(r"\.([a-z]+(?:_[a-z0-9]+)+)\b", texto)) | set(re.findall(r"\.(url|ok|nivel|texto)\b", texto))

    def test_js_so_le_campos_do_contrato(self):
        for js in PAGINAS.values():
            desconhecidos = self.propriedades(js) - contrato.CAMPOS_CONHECIDOS
            self.assertEqual(desconhecidos, set(), f"{js} lê campo que o contrato não tem (renomeou?)")

    def test_js_ainda_le_o_essencial(self):
        lista, app = ler("js/lista.js"), ler("js/app.js")
        for campo in self.LISTA_LE - {"sites", "forms_avulsos"}:
            self.assertTrue(re.search(rf"\b{campo}\b", lista), f"lista.js deixou de ler '{campo}'")
        for campo in self.APP_LE:
            self.assertTrue(re.search(rf"\b{campo}\b", app), f"app.js deixou de ler '{campo}'")
        for campo in ("sites", "forms_avulsos"):
            self.assertTrue(campo in lista, f"lista.js deixou de ler '{campo}'")

    def test_campos_obrigatorios_do_site_estao_no_contrato(self):
        self.assertTrue(set(contrato.CAMPOS_SITE_OBRIGATORIOS) <= contrato.CAMPOS_CONHECIDOS)

    def test_recarga_a_cada_minuto_igual_nos_tres_lugares(self):
        valores = {}
        for js in PAGINAS.values():
            achado = re.search(r"INTERVALO_RECARGA_MS\s*=\s*([\d* ]+);", ler(js))
            self.assertIsNotNone(achado, js)
            valores[js] = eval(achado.group(1), {"__builtins__": {}})  # só produto de inteiros
        kt = (RAIZ / "android/app/src/main/java/io/github/gfvdataweb/painelstatus/App.kt").read_text(encoding="utf-8")
        valores["App.kt"] = int(re.search(r"const val INTERVALO_DE_RECARGA_MS = ([\d_]+)L", kt).group(1).replace("_", ""))
        self.assertEqual(len(set(valores.values())), 1, valores)

    def test_modelos_do_app_conhecem_os_campos_do_contrato(self):
        modelos = (RAIZ / "android/app/src/main/java/io/github/gfvdataweb/painelstatus/data/modelo/Modelos.kt").read_text(encoding="utf-8")
        declarados = set(re.findall(r'@SerialName\("(\w+)"\)', modelos)) | set(re.findall(r"\bval (\w+)\b", modelos))
        # campos que o app não precisa (a página usa; o app ignora): ver README "o app ainda não desenha"
        so_da_pagina = {"descricao", "pipeline_forms", "etapas", "execucao", "workflow_dispatch", "inicio", "fim"}
        for campo in contrato.CAMPOS_CONHECIDOS - so_da_pagina:
            self.assertIn(campo, declarados, f"Modelos.kt não declara '{campo}' (contrato mudou?)")


class ClassesDeEstado(unittest.TestCase):
    """Estados que o JS monta em classes (`etapa--ok`...) precisam ter estilo."""

    ESTILO = None

    @classmethod
    def setUpClass(cls):
        css = "".join(p.read_text(encoding="utf-8") for p in (DOCS / "css").glob("*.css"))
        cls.css = set(re.findall(r"\.([A-Za-z_][\w-]*)", css))

    def test_estados_das_etapas(self):
        for estado in contrato.ESTADOS_ETAPA:
            self.assertIn(f"etapa--{estado}", self.css)

    def test_niveis_de_alerta_e_badges(self):
        for nivel in contrato.NIVEIS_ALERTA:
            self.assertIn(f"alerta-icone--{nivel}", self.css)
            self.assertIn(f"alerta-linha--{nivel}", self.css)
        for badge in ("ok", "erro", "aviso", "neutro"):
            self.assertIn(badge, self.css)
        for cartao in ("cartao--erro", "cartao--aviso", "cartao--destaque"):
            self.assertIn(cartao, self.css)

    def test_classes_fixas_do_js_tem_estilo(self):
        sem_estilo_ok = {"hist-vazio"}  # gancho do JS (atributo hidden), sem regra própria
        for js in PAGINAS.values():
            for valor in re.findall(r'class="([^"]*)"', ler(js)):
                valor = re.sub(r"\$\{(?:[^{}]|\{[^{}]*\})*\}", " ", valor)
                for token in valor.split():
                    if not re.fullmatch(r"[a-z][\w-]*[a-z0-9]", token) or token in sem_estilo_ok:
                        continue  # resto de template aninhado / gancho
                    existe = token in self.css or any(c.startswith((token + "--", token + "__")) for c in self.css)
                    self.assertTrue(existe, f"{js}: classe '{token}' sem estilo")
            for prefixo in re.findall(r'class="[^"]*?([a-z][\w-]*--)\$\{', ler(js)):
                self.assertTrue(any(c.startswith(prefixo) for c in self.css), f"{js}: prefixo '{prefixo}' sem estilo")


if __name__ == "__main__":
    unittest.main()
