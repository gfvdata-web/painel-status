# CLAUDE.md — painel-status

Painel que monitora **todos** os sites publicados pela conta `gfvdata-web` (acesso, dados
atualizados, pipeline do Bolão F1) e reúne atalhos para Google Forms. Publicado em
https://gfvdata-web.github.io/painel-status/. Como funciona: [README.md](README.md).

Tudo sobre o painel, os sites que ele monitora e os forms é documentado **aqui**. O
`controle-global` é só do projeto de fontes financeiras e não recebe nada sobre este painel.

## Regra permanente: concordância entre código e documentação

**Toda mudança atualiza, no mesmo commit, todos os lugares que a referenciam.** Antes de
commitar, fazer `grep` pelo nome/slug/URL alterado.

Pedido do Guilherme: commit e push podem ser feitos sem pedir autorização de novo, desde que
nada seja quebrado (conferir a página localmente com `python -m http.server --directory docs`).

| Mudança | Onde atualizar |
|---|---|
| Site entra, sai ou muda de nome/URL | `src/config.py` (`SITES`) e a tabela "Sites monitorados" do `README.md`; depois rodar o workflow `atualizar-status.yml` para regenerar `docs/dados/status.json` |
| Link do Forms do Bolão F1 | `form_url` em `src/config.py` e a tabela "Sites monitorados" do `README.md` |
| Form avulso (card "Outros forms") | `FORMS_AVULSOS` em `src/config.py` e a tabela "Outros forms" do `README.md`; depois rodar o workflow |
| Campo novo no cadastro de sites | `CAMPOS_SITE` e a docstring de `src/config.py`, todas as entradas de `SITES` (mesmas chaves em todas) |
| Campo novo/renomeado no `status.json` | docstring de `src/publicacao.py` e o uso em `docs/js/app.js` |
| Etapas do pipeline do Bolão F1 | `src/etapas_bolao.py` (docstring descreve o fluxo) e a lista em "O que ele mostra" do `README.md` |
| Arquivo novo em `src/` ou `docs/` | árvore em "Como funciona" do `README.md` |

**Cadastro único:** sites e forms só existem em `src/config.py`. `docs/js/app.js` não guarda
lista nenhuma — só desenha o que vem no `status.json`. Chamadas HTTP passam por `src/rede.py`
(capturar `ERROS_REDE`, nunca deixar exceção de rede derrubar a rodada).

`docs/dados/status.json` é gerado pelo Action — não editar à mão (exceto para pré-visualizar
localmente, e o Action sobrescreve na próxima rodada).
