"""Troca os dados do painel com o Worker da Cloudflare (worker/, painel.gfvdata.com).

Uso: python -m src.envio enviar   manda docs/dados/status.json e docs/dados/historico/*.json
     python -m src.envio baixar   traz do Worker o status.json e os históricos publicados
                                  para docs/dados/ (o Worker é a fonte da verdade; a coleta
                                  parte do que já está publicado lá)

O Worker guarda os bytes como vieram — formato em publicacao.py e historico.py. Ao
enviar, os históricos vão antes do status, para o status nunca apontar para um histórico
que ainda não chegou. O Worker recusa status malformado (422) e status mais antigo que o
publicado (409).

Variáveis de ambiente:
  PAINEL_URL          raiz do painel (padrão: https://painel.gfvdata.com/)
  PAINEL_TOKEN_ENVIO  token do envio (secret do repositório; o mesmo TOKEN_ENVIO do Worker)

Sai com código 1 se algum arquivo não passou, depois de tentar todos.
"""

import json
import os
import sys
import urllib.error
from pathlib import Path

from .rede import ERROS_REDE, get_texto, put

DADOS = Path(__file__).resolve().parent.parent / "docs" / "dados"
URL_PADRAO = "https://painel.gfvdata.com/"


def _base():
    return os.environ.get("PAINEL_URL", "").strip().rstrip("/") or URL_PADRAO.rstrip("/")


def _descrever(exc):
    if isinstance(exc, urllib.error.HTTPError):
        corpo = exc.read().decode("utf-8", errors="replace")[:300]
        return f"HTTP {exc.code}: {corpo}"
    return str(exc)


def _arquivos_locais():
    historicos = sorted((DADOS / "historico").glob("*.json"))
    return [*historicos, DADOS / "status.json"]


def enviar():
    token = os.environ.get("PAINEL_TOKEN_ENVIO", "").strip()
    if not token:
        print("[envio] PAINEL_TOKEN_ENVIO não configurado", file=sys.stderr)
        return False
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json; charset=utf-8"}
    tudo_ok = True
    for arquivo in _arquivos_locais():
        caminho = arquivo.relative_to(DADOS).as_posix()
        try:
            put(f"{_base()}/api/envio/{caminho}", arquivo.read_bytes(), headers)
            print(f"[envio] {caminho} enviado")
        except ERROS_REDE as exc:
            print(f"[envio] falha em {caminho}: {_descrever(exc)}", file=sys.stderr)
            tudo_ok = False
    return tudo_ok


def baixar():
    """Grava em docs/dados/ o que está publicado no Worker. Sem o status publicado, não mexe
    em nada (a coleta segue com o que veio do Git)."""
    try:
        texto = get_texto(f"{_base()}/dados/status.json", {"Cache-Control": "no-cache"})
        status = json.loads(texto)
    except ERROS_REDE as exc:
        print(f"[envio] não deu para baixar o status.json: {_descrever(exc)}", file=sys.stderr)
        return False
    (DADOS / "historico").mkdir(parents=True, exist_ok=True)
    tudo_ok = True
    for site in status.get("sites", []):
        caminho = site.get("historico_arquivo")  # "dados/historico/<slug>.json"
        if not caminho:
            continue
        try:
            conteudo = get_texto(f"{_base()}/{caminho}", {"Cache-Control": "no-cache"})
            json.loads(conteudo)
        except ERROS_REDE as exc:
            print(f"[envio] falha ao baixar {caminho}: {_descrever(exc)}", file=sys.stderr)
            tudo_ok = False
            continue
        (DADOS.parent / caminho).write_text(conteudo, encoding="utf-8", newline="")
        print(f"[envio] {caminho} baixado")
    (DADOS / "status.json").write_text(texto, encoding="utf-8", newline="")
    print(f"[envio] dados/status.json baixado (gerado em {status['meta']['gerado_em']})")
    return tudo_ok


def main():
    acoes = {"enviar": enviar, "baixar": baixar}
    if len(sys.argv) != 2 or sys.argv[1] not in acoes:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    sys.exit(0 if acoes[sys.argv[1]]() else 1)


if __name__ == "__main__":
    main()
