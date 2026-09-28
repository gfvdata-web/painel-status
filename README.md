# Painel de Status — gfvdata-web

Painel único de monitoramento para todos os sites publicados via GitHub Pages pela conta
`gfvdata-web`, com destaque para o **Bolão F1**.

**Painel:** https://gfvdata-web.github.io/painel-status/

## O que ele mostra

Por site: se está no ar, quando os dados foram atualizados pela última vez (e o que mudou),
e — quando o GoatCounter estiver configurado (ver abaixo) — visitantes únicos (total e um
gráfico por dia dos últimos 30 dias).

Para o **Bolão F1** especificamente, também: a rodada mais recente processada, o link do
Google Forms de envio de palpites e o **passo a passo da última atualização de dados**
(`src/etapas_bolao.py`), montado a partir dos passos da execução do pipeline
(`page-bolao-formula1`) e do deploy do GitHub Pages:

1. Palpites enviados (Google Forms → Apps Script dispara o pipeline)
2. Palpites lidos e rodada identificada
3. Resultado oficial do quali (Jolpica) — na hora, ou em "vigília" até sair
4. Pontuação calculada
5. Página atualizada (GitHub Pages)

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
├── coleta_goatcounter.py # visitantes únicos via API do GoatCounter (opcional por site)
├── etapas_bolao.py       # passo a passo da última atualização do Bolão F1
└── publicacao.py         # monta docs/dados/status.json
docs/                     # o que o GitHub Pages publica
```

Rodar localmente:

```bash
python -m src.publicacao
python -m http.server --directory docs 8000
```

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

## Sites monitorados

Ver `src/config.py` — hoje são seis sites publicados sob `gfvdata-web`: Bolão F1, Meios de
pagamento, Arrecadação federal, Crédito por modalidade, Simulador de investimentos e Chess
Tracking. O Cruzeiro Indata saiu do painel.

Além dos sites monitorados, a grade tem um card fixo **"Outros forms"** com atalhos para Google
Forms de projetos sem site publicado (Notas fiscais e Update plantas). Ele não é monitorado nem
vem do `status.json`: a lista fica na constante `OUTROS_FORMS` em `docs/js/app.js`.
