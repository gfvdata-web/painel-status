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
| Form de um site (link ou rótulo) | `form_url` / `form_rotulo` em `src/config.py` e a tabela "Sites monitorados" do `README.md` |
| Form avulso (card "Outros forms") | `FORMS_AVULSOS` em `src/config.py` e a tabela "Outros forms" do `README.md`; depois rodar o workflow |
| Campo novo no cadastro de sites | `CAMPOS_SITE` e a docstring de `src/config.py`, todas as entradas de `SITES` (mesmas chaves em todas) |
| Campo novo/renomeado no `status.json` | docstring de `src/publicacao.py`, o uso em `docs/js/app.js` e os modelos do app Android (`android/app/src/main/java/io/github/gfvdataweb/painelstatus/data/modelo/Modelos.kt`). Mudança só aditiva: renomear/mudar tipo quebra o app instalado |
| App Android (`android/`, `.github/workflows/android.yml`) | regras em `.claude/rules/android.md`; seção "App Android" e árvore do `README.md`. Intervalo de recarga do app = `INTERVALO_RECARGA_MS` do `app.js` (`INTERVALO_DE_RECARGA_MS` em `App.kt`): mudou um, mudar o outro |
| Etapas do pipeline do Bolão F1 | `src/etapas_bolao.py` (docstring descreve o fluxo) e a lista em "O que ele mostra" do `README.md`; estado novo de etapa → conferir `fase()` em `src/vigia_bolao.py` |
| Acompanhamento do envio do Forms (`bolao_palpite`) | job `vigiar-bolao` em `atualizar-status.yml`, `src/vigia_bolao.py`, parágrafo no `README.md` e o `Code.gs` + `SETUP.md` em `page-bolao-formula1/google-apps-script/` |
| Arquivo novo em `src/` ou `docs/` | árvore em "Como funciona" do `README.md` |

**Cadastro único:** sites e forms só existem em `src/config.py`. `docs/js/app.js` e o app
Android não guardam lista nenhuma — só desenham o que vem no `status.json`. Chamadas HTTP passam por `src/rede.py`
(capturar `ERROS_REDE`, nunca deixar exceção de rede derrubar a rodada).

Mudança no app Android só está pronta com a run do `android.yml` verde (não há JDK/SDK
local); commit e push valem a mesma autorização acima.

`docs/dados/status.json` e `docs/dados/historico/` são gerados pelo Action — não editar à mão (exceto para pré-visualizar
localmente, e o Action sobrescreve na próxima rodada).
