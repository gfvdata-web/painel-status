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

A lista que o painel usa é `src/config.py` (`SITES`). Hoje são seis:

| Site | Repositório | Página | O que é |
|------|-------------|--------|---------|
| **Bolão F1** (destaque) | [`page-bolao-formula1`](https://github.com/gfvdata-web/page-bolao-formula1) | [bolão](https://gfvdata-web.github.io/page-bolao-formula1/) · [enviar palpites](https://forms.gle/7yZAx1WThPf51bv67) | Placar do bolão de Fórmula 1. Palpites colados no Google Forms → Apps Script dispara o pipeline no Actions → resultado oficial na Jolpica → pontuação → página republicada |
| Meios de pagamento | [`fonte-meios-pagamento`](https://github.com/gfvdata-web/fonte-meios-pagamento) | [painel](https://gfvdata-web.github.io/fonte-meios-pagamento/) | Fonte financeira (BCB) — projeto documentado no `controle-global` |
| Arrecadação federal | [`fonte-arrecadacao-federal`](https://github.com/gfvdata-web/fonte-arrecadacao-federal) | [painel](https://gfvdata-web.github.io/fonte-arrecadacao-federal/) | Fonte financeira (RFB) — projeto documentado no `controle-global` |
| Crédito por modalidade | [`fonte-credito-modalidade`](https://github.com/gfvdata-web/fonte-credito-modalidade) | [painel](https://gfvdata-web.github.io/fonte-credito-modalidade/) | Fonte financeira (BCB/SGS) — projeto documentado no `controle-global` |
| Simulador de investimentos | [`simulador-investimentos`](https://github.com/gfvdata-web/simulador-investimentos) | [simulador](https://gfvdata-web.github.io/simulador-investimentos/) | Comparação de rendimento de investimentos (bruto, líquido e real); coleta de dados oficiais em dias úteis via Actions |
| Chess Tracking | [`chess-tracking`](https://github.com/gfvdata-web/chess-tracking) | [página](https://gfvdata-web.github.io/chess-tracking/) | Histórico de partidas no Chess.com (conta `giggsmate`) com estatísticas de rating, aberturas, ritmo, horário e gestão de tempo; coleta diária via Actions |

O Cruzeiro Indata saiu do painel.

## Outros forms

Além dos sites monitorados, a grade tem um card fixo **"Outros forms"** com atalhos para Google
Forms de projetos sem site publicado. Ele não é monitorado nem vem do `status.json`: a lista
fica na constante `OUTROS_FORMS` em `docs/js/app.js`.

| Form | Link |
|------|------|
| Notas fiscais | https://forms.gle/ftgMMg1Lwpoi7j3Z8 |
| Update plantas | https://forms.gle/y3uXaukJXmP9GMED7 |

Os dois exigem login Google para abrir (configuração do próprio Forms).
