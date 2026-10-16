# CLAUDE.md — painel-status

Painel que monitora **todos** os sites publicados pela conta `gfvdata-web` (acesso, dados
atualizados, pipeline do Bolão F1) e reúne atalhos para Google Forms. Publicado em
https://painel.gfvdata.com/ (Worker da Cloudflare; o antigo https://gfvdata-web.github.io/painel-status/
segue atualizado até o fim da transição). Como funciona: [README.md](README.md).

Tudo sobre o painel, os sites que ele monitora e os forms é documentado **aqui**. O
`controle-global` é só do projeto de fontes financeiras e não recebe nada sobre este painel.

## Regra permanente: concordância entre código e documentação

**Toda mudança atualiza, no mesmo commit, todos os lugares que a referenciam.** Antes de
commitar, fazer `grep` pelo nome/slug/URL alterado.

Pedido do Guilherme: commit e push podem ser feitos sem pedir autorização de novo, desde que
nada seja quebrado (conferir a página localmente com `python -m http.server --directory docs`).

Deploy: automático no push para `main` que mexe em `docs/` ou `worker/` (`.github/workflows/deploy.yml`,
secret `CLOUDFLARE_API_TOKEN`). Depois do push, conferir a run do workflow e o site no ar (agente `site-checker`).
Sem o secret (ou com a run falhando), o deploy é manual — `npx --prefix worker wrangler deploy --config worker/wrangler.jsonc` —
só com OK do Guilherme no pedido (a autorização de push acima não cobre deploy manual).

| Mudança | Onde atualizar |
|---|---|
| Site entra, sai ou muda de nome/URL | `src/config.py` (`SITES`) e a tabela "Sites monitorados" do `README.md`; depois rodar o workflow `atualizar-status.yml` para regenerar `docs/dados/status.json` |
| Form de um site (link ou rótulo) | `form_url` / `form_rotulo` em `src/config.py` e a tabela "Sites monitorados" do `README.md` |
| Form avulso (card "Outros forms") | `FORMS_AVULSOS` em `src/config.py` e a tabela "Outros forms" do `README.md`; depois rodar o workflow |
| Campo novo no cadastro de sites | `CAMPOS_SITE` e a docstring de `src/config.py`, todas as entradas de `SITES` (mesmas chaves em todas) |
| Campo novo/renomeado no `status.json` | `tests/contrato.py` (+ `python -m tests.regenerar_fixtures`), docstring de `src/publicacao.py`, o uso em `docs/js/app.js` (V1, `/v1`) e `docs/js/lista.js` (página principal, `/`) e os modelos do app Android (`android/app/src/main/java/io/github/gfvdataweb/painelstatus/data/modelo/Modelos.kt`). Mudança só aditiva: renomear/mudar tipo quebra o app instalado |
| Worker da Cloudflare (`worker/`, `src/envio.py`) | seção "Cloudflare" e árvore do `README.md`. A validação do envio (`problemaNoStatus` em `worker/src/index.ts`) exige `meta.gerado_em`, `sites[].slug`/`nome` e `forms_avulsos`: mexeu nesses campos, mexer lá. Publicar com `npx --prefix worker wrangler deploy --config worker/wrangler.jsonc` depois de testar com `wrangler dev` |
| App Android (`android/`, `.github/workflows/android.yml`) | regras em `.claude/rules/android.md`; seção "App Android" e árvore do `README.md`. Intervalo de recarga do app = `INTERVALO_RECARGA_MS` do `app.js` (`INTERVALO_DE_RECARGA_MS` em `App.kt`; `INTERVALO_RECARGA_MS` em `lista.js`): mudou um, mudar os outros |
| JSON do painel de IA (`ia.gfvdata.com`, repo `painel-ia`) lido pelo app | `android/.../data/ia/ModelosIa.kt` e as fixtures em `android/app/src/test/resources/ia/`; mudança no painel-ia só aditiva (renomear quebra o app). Regra do aviso de limite (80%): `data/avisos/NovidadesIa.kt` |
| Etapas do pipeline do Bolão F1 | `src/etapas_bolao.py` (docstring descreve o fluxo) e a lista em "O que ele mostra" do `README.md`; estado novo de etapa → conferir `fase()` em `src/vigia_bolao.py`, `estadoDaEtapa` em `android/.../ui/painel/Apresentacao.kt` e os avisos em `android/.../data/avisos/Novidades.kt` (usam as chaves `resultado`/`pagina` e os estados `aguardando`/`ok`/`erro`) |
| Pipeline do Forms de um site (`pipeline_forms`, hoje o PlantsCare) | `src/etapas_forms.py` (casa os passos pelo começo do nome: renomeou um passo no `pipeline.yml` do PlantsCare, mudar `PASSO_*`), job `vigiar-forms` em `atualizar-status.yml`, `src/vigia_forms.py`, parágrafo "Envio do Forms do PlantsCare" no `README.md` e o `Code.gs` + `SETUP.md` em `PlantsCare/google-apps-script/` |
| Texto de alerta (`src/detalhes_site.py`) | os avisos do app comparam alertas pelo texto (números ignorados): texto novo = notificação nova para quem tem o app |
| Acompanhamento do envio do Forms (`bolao_palpite`) | job `vigiar-bolao` em `atualizar-status.yml`, `src/vigia_bolao.py`, parágrafo no `README.md` e o `Code.gs` + `SETUP.md` em `page-bolao-formula1/google-apps-script/` |
| Arquivo novo em `src/`, `docs/`, `worker/` ou `tests/` | árvore em "Como funciona" do `README.md` e a lista "Cobertura:" do `scripts/check` |

**Cadastro único:** sites e forms só existem em `src/config.py`. `docs/js/app.js` (V1, `/v1`), `docs/js/lista.js` (página principal) e o app
Android não guardam lista nenhuma — só desenham o que vem no `status.json`. Chamadas HTTP passam por `src/rede.py`
(capturar `ERROS_REDE`, nunca deixar exceção de rede derrubar a rodada).

Mudança no app Android só está pronta com a run do `android.yml` verde (não há JDK/SDK
local); commit e push valem a mesma autorização acima.

`docs/dados/status.json` e `docs/dados/historico/` são gerados pelo Action — não editar à mão (exceto para pré-visualizar
localmente, e o Action sobrescreve na próxima rodada).

## Regra permanente: funcionalidade nova entra com teste

Funcionalidade nova ou alterada entra com teste no `scripts/check`/`tests/` **no mesmo commit** (e, se for
arquivo ou página nova, na lista "Cobertura:" no topo do `scripts/check` — o `tests/test_infra.py` falha se faltar).
Rodar `bash scripts/check` antes de commitar (o `.githooks/pre-commit` faz isso e bloqueia o commit). Os testes
rodam sem rede, sem tokens e sem navegador: HTTP sai de `tests/fixtures/` (`tests/apoio.py`, `RedeFalsa`), as
páginas rodam no Node com DOM de mentira (`tests/paginas.test.mjs`) e o Worker com D1/KV em memória
(`tests/worker.test.mjs`). Formato do `status.json` mudou de propósito: atualizar `tests/contrato.py` e rodar
`python -m tests.regenerar_fixtures` (conferir o diff). Testes nunca escrevem em `docs/dados/`.
