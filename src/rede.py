"""Acesso HTTP compartilhado pelos coletores (GitHub e GoatCounter).

Só biblioteca padrão. ERROS_REDE agrupa tudo o que uma chamada pode levantar
por causa de rede instável ou resposta inválida (HTTP 4xx/5xx, DNS, timeout,
JSON quebrado) — os coletores capturam essa tupla para que um site com
problema vire um campo de erro no status.json em vez de derrubar a rodada.
"""

import json
import urllib.error
import urllib.request

USER_AGENT = "painel-status"
TIMEOUT_S = 20

# HTTPError é subclasse de URLError; quem precisa do código HTTP captura
# urllib.error.HTTPError antes desta tupla.
ERROS_REDE = (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError)


def get_json(url, headers=None):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, **(headers or {})})
    with urllib.request.urlopen(req, timeout=TIMEOUT_S) as resp:
        return json.loads(resp.read().decode("utf-8"))


def get_status(url):
    """Código HTTP de um GET simples (levanta HTTPError para 4xx/5xx)."""
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=TIMEOUT_S) as resp:
        return resp.status
