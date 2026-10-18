"""Apoio dos testes: rede bloqueada, relógio fixo, fixtures e uma API do GitHub/GoatCounter falsa.

Nenhum teste usa rede, token ou navegador. Qualquer tentativa de abrir um socket
levanta AssertionError; as chamadas HTTP dos coletores são trocadas por RedeFalsa,
que responde com os arquivos de tests/fixtures/ (respostas no formato da API real,
reduzidas ao que o painel lê).
"""

import base64
import contextlib
import io
import json
import re
import socket
import sys
import tempfile
import unittest
import urllib.error
from datetime import datetime, timezone
from pathlib import Path
from unittest import mock

RAIZ = Path(__file__).resolve().parent.parent
FIXTURES = Path(__file__).resolve().parent / "fixtures"
if str(RAIZ) not in sys.path:
    sys.path.insert(0, str(RAIZ))

# Instante em que as fixtures foram "coletadas" (as execuções delas são de 11 a 13/10/2026).
AGORA = datetime(2026, 10, 13, 12, 0, 0, tzinfo=timezone.utc)


def _bloquear_socket(*_args, **_kwargs):
    raise AssertionError("teste tentou usar a rede real")


socket.socket.connect = _bloquear_socket
socket.getaddrinfo = _bloquear_socket  # nem DNS
socket.create_connection = _bloquear_socket


def fixture(nome):
    """Conteúdo de tests/fixtures/<nome>: objeto para .json, texto para o resto."""
    caminho = FIXTURES / nome
    texto = caminho.read_text(encoding="utf-8")
    return json.loads(texto) if nome.endswith(".json") else texto


class _RelogioFixo(datetime):
    @classmethod
    def now(cls, tz=None):
        return AGORA if tz else AGORA.replace(tzinfo=None)


@contextlib.contextmanager
def relogio_fixo(*modulos):
    """Faz `datetime.now()` dos módulos (ex.: "src.detalhes_site") devolver AGORA."""
    with contextlib.ExitStack() as pilha:
        for modulo in modulos:
            pilha.enter_context(mock.patch(f"{modulo}.datetime", _RelogioFixo))
        yield


def erro_http(codigo, url="https://exemplo.test/", corpo=b"corpo do erro"):
    return urllib.error.HTTPError(url, codigo, f"HTTP {codigo}", {}, io.BytesIO(corpo))


def execucao(id_=1, evento="schedule", status="completed", conclusao="success",
             criado="2026-10-13T10:00:00Z", atualizado="2026-10-13T10:05:00Z"):
    """Execução no formato de coleta_github.execucoes_workflow."""
    return {"id": id_, "evento": evento, "status": status, "conclusao": conclusao,
            "criado_em": criado, "atualizado_em": atualizado,
            "url": f"https://github.com/gfvdata-web/exemplo/actions/runs/{id_}"}


def passo(nome, conclusao="success", status="completed", inicio="2026-10-13T10:00:00Z", fim="2026-10-13T10:01:00Z"):
    return {"nome": nome, "status": status, "conclusao": conclusao, "inicio": inicio, "fim": fim}


class RedeFalsa:
    """GitHub + GoatCounter + sites, sem rede. Use como contexto: `with RedeFalsa() as rede:`.

    falhar=True       toda chamada levanta URLError (API fora do ar)
    sites_fora        pages_url que respondem com erro (ex.: {"https://...": 503})
    sem_goatcounter   /paths do GoatCounter falha (URLError)
    sem_contagem      todo <site>/contagem.json falha (URLError)
    runs / commits    trocam a resposta das execuções / commits (lista no formato da API)
    `chamadas` guarda cada URL pedida, para os testes conferirem o que foi (ou não) consultado.
    """

    def __init__(self, falhar=False, sites_fora=None, sem_goatcounter=False, runs=None, commits=None,
                 sem_contagem=False):
        self.runs = runs
        self.commits = commits
        self.falhar = falhar
        self.sites_fora = sites_fora or {}
        self.sem_goatcounter = sem_goatcounter
        self.sem_contagem = sem_contagem
        self.chamadas = []
        self._pilha = contextlib.ExitStack()

    def __enter__(self):
        for alvo in ("src.coleta_github", "src.coleta_goatcounter", "src.coleta_downloads"):
            self._pilha.enter_context(mock.patch(f"{alvo}.get_json", self.get_json))
        self._pilha.enter_context(mock.patch("src.coleta_github.get_texto", self.get_texto))
        self._pilha.enter_context(mock.patch("src.coleta_github.get_status", self.get_status))
        return self

    def __exit__(self, *exc):
        self._pilha.close()

    def _registrar(self, url):
        self.chamadas.append(url)
        if self.falhar:
            raise urllib.error.URLError("rede falsa fora do ar")

    def get_json(self, url, headers=None):
        self._registrar(url)
        if "goatcounter.com" in url:
            if self.sem_goatcounter:
                raise urllib.error.URLError("goatcounter fora do ar")
            return fixture("goatcounter_paths.json" if "/paths" in url else "goatcounter_stats.json")
        if "/contents/.github/workflows/" in url:
            texto = fixture("workflow.yml")
            return {"content": base64.b64encode(texto.encode()).decode() + "\n"}
        if "/check-runs/" in url:
            return fixture("annotations.json")
        if re.search(r"/actions/runs/\d+/jobs", url):
            return fixture("jobs_falha.json" if "/runs/102/" in url else "jobs.json")
        if "/actions/runs?event=dynamic" in url:
            return fixture("pages_runs.json")
        if "/actions/workflows/" in url:
            return {"workflow_runs": self.runs} if self.runs is not None else fixture("runs.json")
        if "/commits?path=" in url:
            return self.commits if self.commits is not None else fixture("commits.json")
        if url.endswith("/contagem.json"):  # contagem_url do cadastro
            if self.sem_contagem:
                raise urllib.error.URLError("contagem fora do ar")
            return fixture("contagem.json")
        if url.endswith(".json"):  # json_meta_url do cadastro
            return fixture("meta_publicado.json")
        raise AssertionError(f"URL sem fixture: {url}")

    def get_texto(self, url, headers=None):
        self._registrar(url)
        if "/actions/jobs/" in url and url.endswith("/logs"):
            return fixture("job_log.txt")
        raise AssertionError(f"URL de texto sem fixture: {url}")

    def get_status(self, url):
        self._registrar(url)
        if url in self.sites_fora:
            raise erro_http(self.sites_fora[url], url)
        return 200


class TesteBase(unittest.TestCase):
    """Cada teste ganha uma pasta temporária (self.tmp) para qualquer escrita."""

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = Path(self._tmp.name)

    def patch(self, alvo, novo):
        patcher = mock.patch(alvo, novo)
        patcher.start()
        self.addCleanup(patcher.stop)
        return novo
