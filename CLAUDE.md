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
| Form avulso (card "Outros forms") | constante `OUTROS_FORMS` em `docs/js/app.js` e a seção "Outros forms" do `README.md` |
| Etapas do pipeline do Bolão F1 | `src/etapas_bolao.py` (docstring descreve o fluxo) e a lista em "O que ele mostra" do `README.md` |
| Arquivo novo em `src/` | árvore em "Como funciona" do `README.md` |

`docs/dados/status.json` é gerado pelo Action — não editar à mão (exceto para pré-visualizar
localmente, e o Action sobrescreve na próxima rodada).
