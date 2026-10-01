"""Acesso HTTP compartilhado pelos coletores (GitHub e GoatCounter).

Só biblioteca padrão. ERROS_REDE agrupa tudo o que uma chamada pode levantar
por causa de rede instável ou resposta inválida (HTTP 4xx/5xx, DNS, timeout,
download cortado, JSON quebrado) — os coletores capturam essa tupla para que
um site com problema vire um campo de erro no status.json em vez de derrubar
a rodada.
"""

import http.client
import json
import urllib.error
import urllib.parse
import urllib.request

USER_AGENT = "painel-status"
TIMEOUT_S = 20

# HTTPError é subclasse de URLError; quem precisa do código HTTP captura
# urllib.error.HTTPError antes desta tupla.
ERROS_REDE = (
    urllib.error.URLError, TimeoutError, OSError, http.client.HTTPException, json.JSONDecodeError,
)


class _RedirecionaSemToken(urllib.request.HTTPRedirectHandler):
    """Não repassa Authorization quando o redirecionamento muda de host (ex.: a API de
    logs do GitHub redireciona para um armazenamento que recusa o token e não deve vê-lo)."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        novo = super().redirect_request(req, fp, code, msg, headers, newurl)
        if novo is not None and urllib.parse.urlsplit(newurl).netloc != urllib.parse.urlsplit(req.full_url).netloc:
            novo.remove_header("Authorization")
        return novo


_abrir = urllib.request.build_opener(_RedirecionaSemToken).open


def get_texto(url, headers=None):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, **(headers or {})})
    with _abrir(req, timeout=TIMEOUT_S) as resp:
        return resp.read().decode("utf-8", errors="replace")


def get_json(url, headers=None):
    return json.loads(get_texto(url, headers))


def get_status(url):
    """Código HTTP de um GET simples (levanta HTTPError para 4xx/5xx)."""
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with _abrir(req, timeout=TIMEOUT_S) as resp:
        return resp.status
