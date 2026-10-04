# Painel de Status — gfvdata-web

Painel único de monitoramento para todos os sites publicados via GitHub Pages pela conta
`gfvdata-web`, com destaque para o **Bolão F1**.

**Painel:** https://gfvdata-web.github.io/painel-status/

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
3. Resultado oficial do quali (Jolpica) — na hora, ou em "vigília" até sair
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

## Como funciona

Sem servidor: um workflow do GitHub Actions (`.github/workflows/atualizar-status.yml`) roda a
cada 3 horas (e sob demanda via `workflow_dispatch`, ou pelo envio do Forms do Bolão — ver
acima), consulta a API pública do GitHub — commits
recentes por caminho de dados, execuções de Actions, resposta HTTP de cada site — e, quando
configurado, a API do GoatCounter. O resultado vira `docs/dados/status.json` (mais o histórico em
`docs/dados/historico/`), e é só isso que `docs/index.html` lê. Não é preciso mudar nada nos repositórios monitorados para o status de
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
└── publicacao.py         # monta docs/dados/status.json (formato descrito na docstring)
docs/                     # o que o GitHub Pages publica
├── index.html
├── css/estilo.css
├── js/app.js             # só lê status.json e desenha — nada é cadastrado aqui
└── dados/                # gerado pelo Action; não editar à mão
    ├── status.json
    └── historico/<slug>.json
```

Um problema de rede em um site (timeout, HTTP 5xx) vira um campo `erro` no card dele e não
derruba a coleta dos outros. Um cadastro inconsistente em `src/config.py` (campo faltando,
slug repetido, dois destaques) faz a coleta falhar logo no início, com a lista do que corrigir.

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

A lista que o painel usa é `src/config.py` (`SITES`; os campos de cada entrada estão
descritos na docstring do arquivo). Hoje são sete:

| Site | Repositório | Página | O que é |
|------|-------------|--------|---------|
| **Bolão F1** (destaque) | [`page-bolao-formula1`](https://github.com/gfvdata-web/page-bolao-formula1) | [bolão](https://gfvdata-web.github.io/page-bolao-formula1/) · [enviar palpites](https://forms.gle/7yZAx1WThPf51bv67) | Placar do bolão de Fórmula 1. Palpites colados no Google Forms → Apps Script dispara o pipeline no Actions → resultado oficial na Jolpica → pontuação → página republicada |
| Meios de pagamento | [`fonte-meios-pagamento`](https://github.com/gfvdata-web/fonte-meios-pagamento) | [painel v2](https://gfvdata-web.github.io/fonte-meios-pagamento/index-v2.html) | Fonte financeira (BCB) — projeto documentado no `controle-global` |
| Arrecadação federal | [`fonte-arrecadacao-federal`](https://github.com/gfvdata-web/fonte-arrecadacao-federal) | [painel](https://gfvdata-web.github.io/fonte-arrecadacao-federal/) | Fonte financeira (RFB) — projeto documentado no `controle-global` |
| Crédito por modalidade | [`fonte-credito-modalidade`](https://github.com/gfvdata-web/fonte-credito-modalidade) | [painel](https://gfvdata-web.github.io/fonte-credito-modalidade/) | Fonte financeira (BCB/SGS) — projeto documentado no `controle-global` |
| Simulador de investimentos | [`simulador-investimentos`](https://github.com/gfvdata-web/simulador-investimentos) | [simulador](https://gfvdata-web.github.io/simulador-investimentos/) | Comparação de rendimento de investimentos (bruto, líquido e real); coleta de dados oficiais em dias úteis via Actions |
| Chess Tracking | [`chess-tracking`](https://github.com/gfvdata-web/chess-tracking) | [página](https://gfvdata-web.github.io/chess-tracking/) | Histórico de partidas no Chess.com (conta `giggsmate`) com estatísticas de rating, aberturas, ritmo, horário e gestão de tempo; coleta diária via Actions |
| Plants Care | [`PlantsCare-publico`](https://github.com/gfvdata-web/PlantsCare-publico) | [painel](https://gfvdata-web.github.io/PlantsCare-publico/) · [enviar fotos](https://forms.gle/y3uXaukJXmP9GMED7) | Acompanhamento das plantas (fichas, medições, equipamentos); versão pública do `PlantsCare`, republicada a cada sync via `deploy-pages.yml`. Fotos novas chegam pelo Google Forms → pasta do Drive |

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
3. Conferir localmente: `python -c "import src.config as c; c.validar()"` e, para ver a
   página, `python -m src.publicacao` + `python -m http.server --directory docs 8000`
   (sem `GOATCOUNTER_TOKEN` local os números de acesso somem — não commitar esse
   `status.json`; o Action regenera).
4. Commit + push e rodar o workflow `atualizar-status.yml` (Actions → Run workflow, ou
   `gh workflow run atualizar-status.yml`).
