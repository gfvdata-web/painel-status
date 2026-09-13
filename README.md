# Painel de Status — gfvdata-web

Painel único de monitoramento para todos os sites publicados via GitHub Pages pela conta
`gfvdata-web`, com destaque para o **Bolão F1**.

**Painel:** https://gfvdata-web.github.io/painel-status/

## O que ele mostra

Por site: se está no ar, quando os dados foram atualizados pela última vez (e o que mudou),
e — quando o GoatCounter estiver configurado (ver abaixo) — visitas e visitantes.

Para o **Bolão F1** especificamente, também: a rodada mais recente processada, se a última
execução do pipeline (`page-bolao-formula1`) terminou com sucesso ou erro, se está em
"vigília" (aguardando o resultado do quali sair na Jolpica), e um gráfico de acesso próprio.

## Como funciona

Sem servidor: um workflow do GitHub Actions (`.github/workflows/atualizar-status.yml`) roda a
cada 3 horas (e sob demanda via `workflow_dispatch`), consulta a API pública do GitHub — commits
recentes por caminho de dados, execuções de Actions, resposta HTTP de cada site — e, quando
configurado, a API do GoatCounter. O resultado vira `docs/dados/status.json`, e é só isso que
`docs/index.html` lê. Não é preciso mudar nada nos repositórios monitorados para o status de
dados funcionar — só a lista de sites em `src/config.py`.

```
src/
├── config.py             # lista dos sites monitorados
├── coleta_github.py      # commits, execuções de Actions, "site no ar"
├── coleta_goatcounter.py # visitas/visitantes via API do GoatCounter (opcional por site)
└── publicacao.py         # monta docs/dados/status.json
docs/                     # o que o GitHub Pages publica
```

Rodar localmente:

```bash
python -m src.publicacao
python -m http.server --directory docs 8000
```

## Configurar o rastreio de acesso (GoatCounter)

O rastreio de acesso é opcional por site — um site sem `goatcounter_code` configurado em
`src/config.py` simplesmente aparece sem números de visita, sem quebrar o resto do painel.
Para ativar:

1. Criar uma conta gratuita em https://www.goatcounter.com/signup.
2. Criar um "site" por propriedade a rastrear (um subdomínio `<code>.goatcounter.com` cada).
3. Em cada site, gerar um token de API em **Settings → API** (leitura de estatísticas basta).
4. Preencher o `goatcounter_code` de cada entrada em `src/config.py`.
5. Guardar os tokens como um único secret `GOATCOUNTER_TOKENS` neste repositório, em JSON:
   `{"<code>": "<token>", ...}`.
6. Adicionar em cada site monitorado, no `<head>` do HTML publicado:
   ```html
   <script data-goatcounter="https://<code>.goatcounter.com/count" async src="//gc.zgo.at/count.js"></script>
   ```

## Sites monitorados

Ver `src/config.py` — hoje são os seis sites publicados sob `gfvdata-web`: Bolão F1, Meios de
pagamento, Arrecadação federal, Crédito por modalidade, Simulador de investimentos e Cruzeiro
Indata. `cruzeiro-indata` (privado, sem Pages) fica de fora.
