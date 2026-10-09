---
paths:
  - "android/**"
  - ".github/workflows/android.yml"
---

# Regras do app Android (`android/`)

Mesmo molde do app do Bolão F1 (`page-bolao-formula1/android` e a regra
`.claude/rules/android.md` de lá). Visão geral: seção "App Android" do `README.md`.

- **O build oficial é o do Actions** (`android.yml`: testes → lint → APK). Não
  há JDK/SDK na máquina local: mudança no app só está pronta com a **run verde**
  no GitHub (`gh run watch`). Run vermelha → corrigir antes de seguir.
- **O app só lê o painel.** Nada é cadastrado nele: sites, forms e etapas vêm
  do `status.json`. Campo novo no `status.json` → modelo em
  `data/modelo/Modelos.kt` (com padrão) + uso na tela; o leitor ignora campos
  desconhecidos e troca `null` pelo padrão. Renomear/mudar tipo quebra o app
  instalado: evitar.
- **Recarga = página:** `INTERVALO_DE_RECARGA_MS` (`App.kt`) igual a
  `INTERVALO_RECARGA_MS` (`docs/js/app.js`). A recarga roda só com o app
  visível (`repeatOnLifecycle(STARTED)` → `PainelViewModel.acompanhar`).
- **Versões só em `gradle/libs.versions.toml`** (inclusive SDKs), as mesmas do
  app do Bolão. Atualizar **uma coisa por commit**, com run verde.
- **Gradle wrapper:** trocar só com o jar oficial (checksum de
  `services.gradle.org`) e `distributionSha256Sum` atualizado.
- **Lint e avisos do Kotlin barram o build** (`warningsAsErrors`,
  `allWarningsAsErrors`): recurso não usado, API depreciada, parâmetro de
  lambda sem uso (usar `_`). Corrigir a causa; `@Suppress` só com comentário.
- A família `androidx.test` é fixada no catálogo (o Compose puxa uma antiga
  que quebra no Android 16+).
- **Teste junto com o código:** tela, ViewModel ou regra nova ganha teste JVM
  em `app/src/test` (Robolectric para telas). Os testes leem o `docs/dados/`
  real (`PainelPublicado`) e, para rede, um painel local (`PainelLocal`,
  MockWebServer). Nada de emulador nem internet.
- Regras de exibição em funções puras (`ui/painel/Apresentacao.kt`), as mesmas
  do `app.js`; telas recebem dados prontos e callbacks.
- Textos de tela em `res/values/strings.xml` (pt-BR), não literais no Kotlin.
- Links vindos do JSON só abrem se forem http(s) (`linkSeguro`); o histórico só
  é baixado de `dados/historico/<slug>.json` (`DadosDoPainel.historicoValido`).
- **Identidade:** `applicationId` `io.github.gfvdataweb.painelstatus` nunca
  muda; o debug usa o sufixo `.debug` e o nome "Painel Status (debug)".
- **`versionCode` = `github.run_number` do `android.yml`**: nunca fixar à mão
  e não renomear nem recriar o workflow.
- **Versão oficial = tag `app-vX.Y.Z`** num commit com run verde. O job
  `publicar` só aceita APK assinado pela chave oficial (a mesma do Bolão F1;
  SHA-256 em `android.yml`). Nunca trocar a chave nem o nome do APK
  (`painel-status-vX.Y.Z-buildN.apk` é lido pelo aviso de versão do app).
- **Segredos fora do app e do Git:** o app não tem token nenhum; keystore e
  senhas só nos secrets do repositório.
