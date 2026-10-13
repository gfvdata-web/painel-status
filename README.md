# Painel de Status — gfvdata-web

Painel único de monitoramento para todos os sites publicados pela conta
`gfvdata-web`, com destaque para o **Bolão F1**.

**Painel (principal, em lista):** https://painel.gfvdata.com/ (reserva: https://painel-status.pages.dev/ — ver
"Cloudflare") · **App Android:** APK em https://painel.gfvdata.com/app/versao.json, campo `apk`
(ver "App Android" abaixo) ·
**Painel completo (V1):** https://painel.gfvdata.com/v1 — a página principal mostra um cartão
por site, com status, form e visitas dos últimos 7 dias; as duas leem o mesmo `status.json` e têm o mesmo switch de tema.
Na lista, a **faixa à esquerda** é a categoria do site (`categoria` em `src/config.py`; sem categoria, cinza), a
**bolinha à direita** é a situação (verde no ar, âmbar com aviso, vermelho fora do ar), e o Bolão F1 tem o botão
"Pipeline de atualização", recolhido por padrão, que mostra o passo a passo na horizontal.
O switch **V1 / V2** no canto superior das duas páginas alterna entre elas (V1 = `/v1`, V2 = `/`)

## O que ele mostra

Por site: se está no ar, quando os dados foram atualizados pela última vez (e o que mudou),
se a última execução do workflow de coleta passou (quando o site tem um cadastrado), o
período coberto pelos dados (quando o site publica `meta.periodo`) e — quando o GoatCounter
estiver configurado (ver abaixo) — visitantes únicos (total e um gráfico por dia dos últimos
30 dias).

Clicar no card de um site da grade abre um **pop-up de detalhes** (`src/detalhes_site.py`):
a rotina de coleta (workflow, agenda lida do próprio `cron`, último sucesso, última falha com
o motivo tirado do log do job, histórico das execuções recentes e os passos da última), os
commits recentes na pasta de dados e o último deploy do GitHub Pages. Problemas viram
**alertas** (⚠ no card): site fora do ar, última coleta falhou ou foi cancelada, coleta sem
sucesso há 4 dias ou mais, deploy do Pages com falha.

Dentro do pop-up, **"Ver histórico completo"** abre todas as execuções já registradas do
workflow (`src/historico.py`): resultado, duração, motivo das falhas e os avisos
(`::warning::` etc.) de cada uma, com filtro "só com falha ou aviso". O histórico é
acumulado em `docs/dados/historico/<slug>.json` a cada rodada (até 500 execuções por site)
e guarda o motivo das falhas mesmo depois que o GitHub apaga os logs (90 dias). Avisos que
aparecem em quase toda execução — em geral do próprio GitHub — são mostrados uma vez só,
como "recorrentes".

Para o **Bolão F1** especificamente, também: a rodada mais recente processada, o link do
Google Forms de envio de palpites e o **passo a passo da última atualização de dados**
(`src/etapas_bolao.py`), montado a partir dos passos da execução do pipeline
(`page-bolao-formula1`) e do deploy do GitHub Pages:

1. Palpites enviados (Google Forms → Apps Script dispara o pipeline)
2. Palpites lidos e rodada identificada
3. Resultado oficial do quali (Jolpica) — na hora, ou em "vigília" até sair (consulta a cada
   5 min; durante a vigília o horário mostrado é o da última consulta)
4. Pontuação calculada
5. Página atualizada (GitHub Pages)

O card do Bolão F1 também tem o link **"Histórico de execuções"**, que abre o mesmo
histórico completo dos outros sites (ver acima), aqui com as execuções do pipeline.

**Acompanhamento ao vivo do envio do Forms.** O Apps Script do Forms do Bolão, além de
disparar o pipeline no `page-bolao-formula1`, manda um `repository_dispatch`
(`bolao_palpite`) para este repositório. Isso roda o job `vigiar-bolao` do
`atualizar-status.yml`, que recoleta só o card do Bolão (`python -m src.publicacao --so-bolao`)
a cada 1 min (10 min durante a vigília do resultado) e commita a cada etapa que muda, até o
fluxo terminar (`src/vigia_bolao.py`). Depois volta à rotina de 3 em 3 horas. A página
aberta confere o `status.json` a cada 1 min e se redesenha sozinha quando ele muda.
O token do Apps Script (`GITHUB_TOKEN` nas Propriedades do script) precisa ter acesso
também a este repositório (Contents: Read and write).

**Envio do Forms do PlantsCare** (sites com `pipeline_forms` em `src/config.py`). O card
mostra o **passo a passo do último envio** (`src/etapas_forms.py`), montado a partir dos
passos da execução do `pipeline.yml` do repositório:

1. Fotos enviadas (Google Forms → Apps Script grava em `inbox/` e dispara o pipeline)
2. Ficha da planta atualizada
3. Relatório da IA (Gemini) → PDF → e-mail
4. Salvo no repositório

Uma falha na última execução vira o alerta "O último envio pelo Forms falhou no pipeline."
Como no Bolão, o Apps Script do Forms (`PlantsCare/google-apps-script/Code.gs`) manda um
`repository_dispatch` (`plantas_envio`) para cá; o job `vigiar-forms` do
`atualizar-status.yml` recoleta só esse card (`python -m src.publicacao --so-forms`) a cada
1 min até o pipeline terminar (`src/vigia_forms.py`). O token do Apps Script precisa ter
acesso ao `PlantsCare` e a este repositório (Contents: Read and write). O app Android ainda
não desenha esse passo a passo (o campo é ignorado), só o alerta.

## Como funciona

Um workflow do GitHub Actions (`.github/workflows/atualizar-status.yml`) roda a
cada 3 horas — disparado pelo Cron do Worker da Cloudflare, ver "Cloudflare" — (e sob demanda via `workflow_dispatch`, ou pelo envio do Forms do Bolão — ver
acima), consulta a API do GitHub — commits
recentes por caminho de dados, execuções de Actions, resposta HTTP de cada site — e, quando
configurado, a API do GoatCounter. O resultado vira `docs/dados/status.json` (mais o histórico em
`docs/dados/historico/`), e é só isso que `docs/index.html` e `docs/v1.html` leem. Não é preciso mudar nada nos repositórios monitorados para o status de
dados funcionar — só o cadastro em `src/config.py`.

```
src/
├── config.py             # cadastro único: sites monitorados e forms avulsos (+ validação)
├── rede.py               # HTTP compartilhado pelos coletores (GET JSON/texto, erros de rede)
├── coleta_github.py      # commits, execuções de Actions, "site no ar", período publicado
├── coleta_goatcounter.py # visitantes únicos via API do GoatCounter (opcional por site)
├── detalhes_site.py      # pop-up de detalhes e alertas dos sites da grade
├── historico.py          # histórico acumulado das execuções (docs/dados/historico/)
├── etapas_bolao.py       # passo a passo da última atualização do Bolão F1
├── vigia_bolao.py        # fase do pipeline do Bolão para o job vigiar-bolao (até quando acompanhar)
├── etapas_forms.py       # passo a passo do último envio do Forms (sites com pipeline_forms)
├── vigia_forms.py        # fase desse pipeline para o job vigiar-forms
├── publicacao.py         # monta docs/dados/status.json (formato descrito na docstring)
└── envio.py              # manda docs/dados ao Worker da Cloudflare (e baixa de lá)
worker/                   # Worker da Cloudflare (painel.gfvdata.com): página + dados + APK
├── wrangler.jsonc        # nome, domínio, arquivos estáticos (../docs), banco D1, KV e Cron
├── migrations/           # tabelas do D1 (arquivos, retratos)
└── src/index.ts          # serve /dados/* e /app/*, recebe os envios (PUT /api/*), agenda
docs/                     # a página (publicada pelo Worker e, na transição, pelo GitHub Pages)
├── index.html            # página principal, em lista (um cartão por site)
├── v1.html               # painel completo (V1, em /v1)
├── _redirects            # /lista e /lista.html -> / (links antigos)
├── css/estilo.css
├── css/lista.css         # só o que é da lista; cores e switch de tema vêm de estilo.css
├── js/app.js             # só lê status.json e desenha — nada é cadastrado aqui
├── js/lista.js           # idem, para index.html (página principal)
└── dados/                # gerado pelo Action; não editar à mão
    ├── status.json
    └── historico/<slug>.json
android/                  # app Android nativo (lê o mesmo status.json; ver "App Android")
└── app/src/main/java/io/github/gfvdataweb/painelstatus/
    ├── data/             # modelos do status.json/histórico, download, cache offline, aviso de versão
    │   └── avisos/       # o que mudou desde o último status visto (Novidades.kt), preferências
    ├── avisos/           # verificação em segundo plano (WorkManager) e notificações
    └── ui/               # telas Compose: Bolão F1, Sites (+ detalhes e histórico), Links, Avisos, Sobre
.github/workflows/
├── atualizar-status.yml  # coleta (3 em 3 h), vigia do Bolão e vigia do envio do Forms
└── android.yml           # testes → lint → APK; tag app-vX.Y.Z publica o APK assinado
scripts/
└── check                 # testes sem rede/tokens/navegador: sintaxe, JSON, tsc do worker e tests/; sem argumento roda tudo, com um arquivo só o que vale para ele
tests/                    # testes automáticos (unittest + node --test), rodados pelo scripts/check
├── test_*.py             # Python: cadastro, coletores (API do GitHub/GoatCounter falsa), etapas, vigias, histórico, publicação, envio, páginas, docs/workflow/Worker
├── paginas.test.mjs      # roda lista.js e app.js no Node com um DOM de mentira e o status.json de exemplo
├── worker.test.mjs       # roda worker/src/index.ts no Node com D1/KV em memória (rotas, validação do envio, APK, agenda)
├── contrato.py           # contrato do status.json/histórico (campos que páginas, Worker e app leem)
├── apoio.py              # rede bloqueada, relógio fixo, API falsa; regenerar_fixtures.py refaz o status de exemplo
└── fixtures/             # respostas da API (formato real, reduzidas) e o status.json de exemplo
.githooks/
└── pre-commit            # roda scripts/check completo antes de cada commit (git config core.hooksPath .githooks)
```

Um problema de rede em um site (timeout, HTTP 5xx) vira um campo `erro` no card dele e não
derruba a coleta dos outros. Um cadastro inconsistente em `src/config.py` (campo faltando,
slug repetido, dois destaques) faz a coleta falhar logo no início, com a lista do que corrigir.

Rodar localmente:

```bash
python -m src.publicacao
python -m http.server --directory docs 8000
```

Testes (sem rede, sem tokens, sem navegador; ~10 s): `bash scripts/check`. Com um arquivo só, `bash scripts/check src/etapas_bolao.py`.
O pre-commit (`.githooks/pre-commit`) roda o completo e bloqueia o commit se algo falhar; depois de clonar, `git config core.hooksPath .githooks`.
O `tsc` do worker precisa de `npm ci --prefix worker` uma vez.

## Cloudflare (Worker + D1 + KV)

**Em transição.** O endereço novo é **https://painel.gfvdata.com/**: um Worker (`worker/`)
serve a página (os arquivos de `docs/`) e os dados (`/dados/status.json`,
`/dados/historico/<slug>.json`) guardados no D1 (banco `painel-status`, tabela `arquivos`),
e o app Android (`/app/versao.json` no D1, o APK no KV `painel-status-app`). O Pages da Cloudflare (`painel-status.pages.dev`, publicado a cada commit)
é a reserva, atualizado pelos commits de dados até o fim da transição. O repositório vai
ficar privado e o GitHub Pages dele, desligado: nada aqui depende de `github.io`.

- **Custo zero garantido:** só serviços do plano grátis que **dão erro no limite em vez de
  cobrar**, e a conta não tem forma de pagamento cadastrada. Por isso **não usamos R2**: ele
  exige cartão e cobra o excedente (só tem alerta de gasto, não teto). Limites diários e uso
  esperado: D1 100 mil gravações (usamos ~500) e 5 milhões de leituras; KV 1.000 gravações,
  100 mil leituras e 1 GB (ficam só os 3 APKs mais novos, ~27 MB); Worker 100 mil chamadas.
  No pior caso, o painel para de atualizar até 00:00 UTC (21h em Brasília) e o app usa a
  reserva. **Nunca** cadastrar cartão nem assinar o Workers Paid sem rever isto.
- **Repositórios privados:** a coleta lê a API do GitHub com o secret `PAINEL_TOKEN_LEITURA`
  — token fine-grained com dono `gfvdata-web`, todos os repositórios, permissões só de
  leitura: **Actions**, **Contents** e **Metadata**. Sem ele, o workflow usa o `GITHUB_TOKEN`,
  que só enxerga os repositórios públicos (os privados viram erro no card).

- **Agenda:** o Cron do Worker (`*/30 * * * *`, `agendar()` em `worker/src/index.ts`) dispara o
  workflow pela API do GitHub nas horas múltiplas de 3 (UTC) — no lugar do `schedule` do
  Actions, que atrasa e é desligado após 60 dias sem atividade (o `schedule` fica de reserva
  até a validação). Nas outras meias horas é vigia: status com mais de 3h20 → dispara de novo
  (no máximo 1 vez por hora). O último disparo e o resultado ficam em
  https://painel.gfvdata.com/api/agenda. O disparo usa o segredo `GITHUB_TOKEN_DISPARO` do
  Worker: token fine-grained só deste repositório, permissão **Actions: Read and write**. Quando
  ele expirar, `/api/agenda` mostra `HTTP 401` e o `schedule` de reserva segura as rodadas.
- **A coleta continua em Python, no Actions.** Antes de coletar, o job baixa o que está
  publicado no Worker (`python -m src.envio baixar`: o Worker é a fonte da verdade). Depois de gerar, o job manda os arquivos ao
  Worker (`python -m src.envio enviar`, `PUT /api/envio/...` com o token do secret
  `PAINEL_TOKEN_ENVIO`, que é o `TOKEN_ENVIO` do Worker). O Worker guarda os bytes como
  vieram — o formato é o de `publicacao.py`/`historico.py`, sem tradução.
- **O Worker recusa** status malformado (sem `meta.gerado_em`, `sites` vazio, site sem
  `slug`/`nome`, sem `forms_avulsos`) e status mais antigo que o publicado — um envio
  atrasado nunca apaga um mais novo.
- **Retratos:** o status recebido também vai para a tabela `retratos` do D1 (no máximo 1 a
  cada 25 min; o Cron apaga os de mais de 30 dias). Servem para voltar a um status anterior.
- **Por que D1 para os dados:** leitura sempre a mais nova (o KV leva até 60 s para propagar,
  e a recarga é de 1 min), e guarda o texto como veio — o mesmo formato por construção (até
  2 MB por arquivo; o Worker recusa acima de 1,9 MB). O APK (~9 MB) passa do limite do D1 e
  vai para o KV (até 25 MiB); um APK novo pode levar até 1 min para aparecer em todo lugar.
- **Banco:** a estrutura fica em `worker/migrations/`. Mudou? Nova migração e
  `npx --prefix worker wrangler d1 migrations apply painel-status --remote --config worker/wrangler.jsonc`.
- **Rodar localmente:** `npm install --prefix worker`, depois
  `npx --prefix worker wrangler dev --config worker/wrangler.jsonc` (D1 e KV simulados — antes, `wrangler d1 migrations apply painel-status --local`; token em
  `worker/.dev.vars`: `TOKEN_ENVIO=teste-local`) e
  `PAINEL_URL=http://localhost:8787 PAINEL_TOKEN_ENVIO=teste-local python -m src.envio enviar`.
- **Testar a agenda localmente:** acrescentar `--test-scheduled` ao `wrangler dev` e abrir
  `http://localhost:8787/cdn-cgi/handler/scheduled?cron=*/30+*+*+*+*&time=<ms>`; o resultado
  aparece em `/api/agenda`.
- **Publicar o Worker:** `npx --prefix worker wrangler deploy --config worker/wrangler.jsonc`.
- **Rollback:** a agenda volta a ser só o `schedule` do Actions removendo `triggers` do
  `wrangler.jsonc` e publicando; o app usa sozinho a reserva (`painel-status.pages.dev`) se o
  Worker cair.
  Retratos do status: `npx --prefix worker wrangler d1 execute painel-status --remote --config
  worker/wrangler.jsonc --command "SELECT gerado_em FROM retratos ORDER BY gerado_em DESC"`.

## App Android

App nativo (Kotlin + Jetpack Compose) em `android/`, no mesmo molde do app do Bolão F1
(`page-bolao-formula1/android`): instalado por APK, fora da Play Store. Não tem servidor nem
token: **lê o mesmo `dados/status.json`** que a página, em `painel.gfvdata.com` (e
`dados/historico/<slug>.json` sob demanda), então tudo que a página mostra vem do mesmo
lugar e nada é cadastrado no app. Se o endereço novo não responder, o app tenta a reserva
(`painel-status.pages.dev`, `Enderecos.PAINEL_RESERVA` em `App.kt`, mesmos caminhos).

- **Abas:** **Bolão F1** (situação do site e do pipeline, o passo a passo da última
  atualização, rodada, visitantes e atalhos para o site, o Forms, o histórico e o
  repositório), **Sites** (um card por site, com alertas; toque abre os detalhes — rotina de
  coleta, última falha e motivo, execuções recentes, commits, deploy — e dali o histórico
  completo) e **Links** (todos os sites e todos os Google Forms, inclusive os avulsos).
  "Sobre" (ícone ⓘ) mostra a versão e o aviso de versão nova.
- **Mesmo ritmo da página:** com o app aberto, o `status.json` é conferido a cada **1 min**
  (`INTERVALO_DE_RECARGA_MS` em `App.kt`, igual a `INTERVALO_RECARGA_MS` do `app.js`), então
  as etapas do Bolão andam no app junto com o job `vigiar-bolao`. Em segundo plano, para; ao
  voltar, confere na hora. Puxar a tela para baixo confere na hora também.
- **Avisos com o app fechado** (desde a 0.2.0; ícone do sino): o app pergunta uma vez se pode
  avisar e, ligado, o WorkManager confere o `status.json` a cada ~15 min (o mínimo do
  Android) e, enquanto o fluxo do Bolão anda, a cada ~5 min, até a página ser atualizada.
  Compara com o último status visto (na tela ou na verificação anterior) e notifica:
  **Bolão F1** — palpites recebidos (ou retry manual), aguardando o resultado do quali,
  página atualizada com a rodada, falha numa etapa, site fora do ar; **sites** — alerta novo
  (os mesmos de `src/detalhes_site.py`; "há 5 dias"/"há 6 dias" contam como o mesmo).
  O que a pessoa viu com o app aberto não vira notificação. Tocar abre a aba certa. Dá para
  desligar cada tipo e mandar uma notificação de teste. Não há servidor nem Firebase: o atraso
  depende do Android (economia de bateria pode adiar).
- **Offline:** o último `status.json` bom fica no aparelho; sem internet o app abre com ele e
  avisa no topo. JSON quebrado no painel nunca apaga o cache.
- **Contrato:** o app é um **segundo consumidor** do `status.json`. Campos novos não quebram
  nada (são ignorados); renomear ou mudar o tipo de um campo quebra — por isso os testes do
  app leem o `status.json` e os históricos reais de `docs/dados/`.

**Build e versões.** O build oficial é o do Actions (`.github/workflows/android.yml`:
testes JVM/Robolectric → lint → APK de debug como artefato). Não há JDK/SDK na máquina
local: mudança no app só está pronta com a run verde. Regras em `.claude/rules/android.md`.
`versionCode` = número da run do `android.yml` (não renomear nem recriar o workflow).
`applicationId` `io.github.gfvdataweb.painelstatus` (o debug usa o sufixo `.debug` e
instala ao lado).

**Publicar uma versão:** commit(s) no `main` com a run verde → `git tag -a app-vX.Y.Z -m "…"`
→ `git push origin app-vX.Y.Z`. O job `publicar` assina com a chave oficial, confere a
assinatura, manda `painel-status-vX.Y.Z-buildN.apk` e depois o `versao.json` ao Worker
(`painel.gfvdata.com/app/`, secret `PAINEL_TOKEN_ENVIO`). Esse é o único lugar onde o APK é
publicado: não há GitHub Release. O app consulta
`painel.gfvdata.com/app/versao.json` ao abrir e mostra "Nova versão disponível"; versão e build
saem do nome do APK (não mudar o padrão). Até a versão com esse aviso novo, o app consultava o
GitHub Releases: quem tem uma versão anterior precisa instalar a nova **à mão, uma vez**.

**Assinatura:** a mesma chave do app do Bolão F1 (um keystore só para guardar, em
`Documents/BolaoF1-assinatura-app/`, fora do Git), cadastrada aqui como os secrets
`ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_SENHA`, `ANDROID_KEY_ALIAS` e
`ANDROID_KEY_SENHA`. Perder o keystore = nenhuma atualização instala por cima dos dois apps.

**Instalar no celular:** abrir `https://painel.gfvdata.com/app/<apk>` pelo celular (o nome
está no campo `apk` de `painel.gfvdata.com/app/versao.json`, ou toque em "Nova versão
disponível" no app), baixar o `.apk` e instalar
(o Android pede para permitir instalação desta fonte). Valem as mesmas observações do app do
Bolão sobre a verificação de desenvolvedor do Android.

## Configurar o rastreio de acesso (GoatCounter)

Um único site GoatCounter (`gfvdata`) recebe o rastreio de **todos** os sites monitorados.
Isso funciona porque todos vivem sob o mesmo domínio `gfvdata-web.github.io/<repo>/` — o
caminho que o GoatCounter registra já vem prefixado com o nome do repositório, então dá pra
separar as estatísticas por site sem precisar de um snippet diferente em cada um.

O rastreio de acesso é opcional: sem o secret configurado, todo site aparece sem números de
visita, sem quebrar o resto do painel. Para ativar:

1. No site `gfvdata` do GoatCounter, gerar um token em **[username] → API**, com permissão de
   leitura de estatísticas.
2. Guardar esse token como o secret `GOATCOUNTER_TOKEN` neste repositório.
3. Adicionar, no `<head>` de cada site monitorado, o snippet padrão (igual em todos, nada de
   JavaScript extra):
   ```html
   <script data-goatcounter="https://gfvdata.goatcounter.com/count" async src="//gc.zgo.at/count.js"></script>
   ```

A lista de repositórios cobertos é `src/config.py` — nenhum campo lá precisa saber do
GoatCounter; a separação por site é feita em `src/coleta_goatcounter.py` filtrando os
caminhos pelo prefixo `/<repo>/`.

**Sites que migraram para domínio próprio na Cloudflare** (`bolaof1.gfvdata.com`,
`meiospagamento.gfvdata.com`): lá o caminho não tem mais o prefixo `/<repo>/`, então o snippet
padrão registra `/` e as visitas novas não entram na conta do site. A correção é no snippet do
próprio site, prefixando o caminho com o nome do repositório:
```html
<script>window.goatcounter = { path: (p) => "/<repo>" + p };</script>
```
(antes do snippet padrão). O Bolão F1 ainda está sem isso; o Meios de pagamento não tem
snippet.

## Sites monitorados

A lista que o painel usa é `src/config.py` (`SITES`; os campos de cada entrada estão
descritos na docstring do arquivo). Hoje são doze:

| Site | Repositório | Página | O que é |
|------|-------------|--------|---------|
| **Bolão F1** (destaque) | [`page-bolao-formula1`](https://github.com/gfvdata-web/page-bolao-formula1) | [bolão](https://bolaof1.gfvdata.com/) · [enviar palpites](https://forms.gle/7yZAx1WThPf51bv67) | Placar do bolão de Fórmula 1. Palpites colados no Google Forms → Apps Script dispara o pipeline no Actions → resultado oficial na Jolpica → pontuação → página republicada |
| Meios de pagamento | [`fonte-meios-pagamento`](https://github.com/gfvdata-web/fonte-meios-pagamento) | [painel v2](https://meiospagamento.gfvdata.com/index-v2) | Fonte financeira (BCB) — projeto documentado no `controle-global` |
| Arrecadação federal | [`fonte-arrecadacao-federal`](https://github.com/gfvdata-web/fonte-arrecadacao-federal) | [painel](https://gfvdata-web.github.io/fonte-arrecadacao-federal/) | Fonte financeira (RFB) — projeto documentado no `controle-global` |
| Crédito por modalidade | [`fonte-credito-modalidade`](https://github.com/gfvdata-web/fonte-credito-modalidade) | [painel](https://gfvdata-web.github.io/fonte-credito-modalidade/) | Fonte financeira (BCB/SGS) — projeto documentado no `controle-global` |
| Simulador de investimentos | [`simulador-investimentos`](https://github.com/gfvdata-web/simulador-investimentos) | [simulador](https://gfvdata-web.github.io/simulador-investimentos/) | Comparação de rendimento de investimentos (bruto, líquido e real); coleta de dados oficiais em dias úteis via Actions |
| Chess Tracking | [`chess-tracking`](https://github.com/gfvdata-web/chess-tracking) | [página](https://gfvdata-web.github.io/chess-tracking/) | Histórico de partidas no Chess.com (conta `giggsmate`) com estatísticas de rating, aberturas, ritmo, horário e gestão de tempo; coleta diária via Actions |
| Plants Care | [`PlantsCare`](https://github.com/gfvdata-web/PlantsCare) (privado) | [painel](https://plantscare.gfvdata.com/) · [enviar fotos](https://forms.gle/y3uXaukJXmP9GMED7) | Acompanhamento das plantas (fichas, sensores, equipamentos). Site na Cloudflare, republicado pelo `sensor-sync.yml` (planilha do ESP32 → `data/`, de hora em hora) — é o workflow do card. Fotos pelo Google Forms → `inbox/` → `pipeline.yml` (ficha → Gemini → PDF → e-mail), com o passo a passo no card (`pipeline_forms`, ver "O que ele mostra") |
| Eleições 2026 | [`eleicoes-2026`](https://github.com/gfvdata-web/eleicoes-2026) | [mapa](https://gfvdata-web.github.io/eleicoes-2026/) | Mapa das pesquisas para governador e Senado por estado, mais a apuração. Publicado a cada push via `pages.yml` (sem agenda, por isso sem workflow cadastrado) |
| Painel esportivo | [`painel-esportivo`](https://github.com/gfvdata-web/painel-esportivo) | [painel](https://gfvdata-web.github.io/painel-esportivo/) | Histórico esportivo (Strava + Samsung Health) com dados cifrados. Os dados só existem no branch `gh-pages` (publicados à mão por `scripts/publicar.mjs`), daí `branch_dados: "gh-pages"` |
| Dashboard dos municípios | [`dashboard-municipios`](https://github.com/gfvdata-web/dashboard-municipios) | [painel](https://gfvdata-web.github.io/dashboard-municipios/) | 18 indicadores públicos dos 5.571 municípios (segurança, saneamento, saúde, educação, finanças, transparência); dados em `site/dados`, coletados localmente |
| VarVendo | [`var-vendo`](https://github.com/gfvdata-web/var-vendo) | [página](https://gfvdata-web.github.io/var-vendo/) | Arbitragem no Brasileirão Série A e na Copa do Brasil 2026 (API da CBF + ogol): quem apita, clubes, resultados e cartões. Dados em `docs/data`; publicado a cada push via `pages.yml` (sem agenda, por isso sem workflow cadastrado) |
| Evolução Fotografia | [`evolucao-fotografia`](https://github.com/gfvdata-web/evolucao-fotografia) | [app](https://gfvdata-web.github.io/evolucao-fotografia/) | Análise da qualidade de captura das fotos da Canon EOS RP, no navegador. O repo não guarda fotos: `caminho_dados` é `site/`, então "Dados atualizados" = última versão publicada. Testes + deploy a cada push via `ci-pages.yml` (sem agenda, sem workflow cadastrado) |

O Cruzeiro Indata saiu do painel.

## Outros forms

Além dos sites monitorados, a grade tem um card **"Outros forms"** com atalhos para Google
Forms de projetos sem site publicado. Nada é coletado sobre eles — são só links. A lista fica
em `FORMS_AVULSOS` no `src/config.py` e chega à página pelo `status.json` (campo
`forms_avulsos`), então um form novo aparece na próxima rodada do workflow.

| Form | Link |
|------|------|
| Notas fiscais | https://forms.gle/ftgMMg1Lwpoi7j3Z8 |

Exige login Google para abrir (configuração do próprio Forms). O antigo "Update plantas" virou o link
"enviar fotos" do card do Plants Care.

## Adicionar um site ou form

1. **Site:** copiar uma entrada de `SITES` em `src/config.py` (todas têm os mesmos campos) e
   ajustar. **Form avulso:** acrescentar `{"nome": ..., "url": ...}` em `FORMS_AVULSOS`.
2. Atualizar a tabela correspondente neste README.
3. Conferir localmente: `bash scripts/check` (valida o cadastro e confere se o README lista o site) e, para ver a
   página, `python -m src.publicacao` + `python -m http.server --directory docs 8000`
   (sem `GOATCOUNTER_TOKEN` local os números de acesso somem — não commitar esse
   `status.json`; o Action regenera).
4. Commit + push e rodar o workflow `atualizar-status.yml` (Actions → Run workflow, ou
   `gh workflow run atualizar-status.yml`).
