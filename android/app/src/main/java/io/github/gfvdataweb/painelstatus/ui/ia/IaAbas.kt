package io.github.gfvdataweb.painelstatus.ui.ia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.data.ia.DadosIa
import io.github.gfvdataweb.painelstatus.data.ia.FaixaLimite
import io.github.gfvdataweb.painelstatus.data.ia.SessaoUso
import io.github.gfvdataweb.painelstatus.ui.comum.Secao
import io.github.gfvdataweb.painelstatus.ui.comum.Selo
import io.github.gfvdataweb.painelstatus.ui.comum.coresDoTom
import io.github.gfvdataweb.painelstatus.ui.comum.dataHora
import io.github.gfvdataweb.painelstatus.ui.painel.Tom
import java.time.ZoneId

// Conteúdo de cada aba do painel de IA. Os números e rótulos saem de
// ApresentacaoIa.kt (as mesmas regras da página); aqui só se desenha.

/** Texto do [Tempo] ("5 min", "2 h 05 min", "3 d 4 h"). */
@Composable
fun textoDoTempo(tempo: Tempo): String = when (tempo) {
    Tempo.MenosDeUmMinuto -> stringResource(R.string.ia_tempo_menos_de_um_min)
    is Tempo.Minutos -> stringResource(R.string.ia_tempo_min, tempo.minutos)
    is Tempo.HorasEMinutos -> stringResource(R.string.ia_tempo_h_min, tempo.horas, tempo.minutos)
    is Tempo.DiasEHoras -> stringResource(R.string.ia_tempo_d_h, tempo.dias, tempo.horas)
}

/** Junta as partes não vazias com " · " (o mesmo separador do resto do app). */
@Composable
private fun juntar(vararg partes: String?): String =
    partes.filter { !it.isNullOrEmpty() }.joinToString(stringResource(R.string.par, "", ""))

/** Aba rolável com o espaçamento padrão. */
@Composable
private fun Rolavel(conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = conteudo,
    )
}

@Composable
private fun Vazio(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
}

@Composable
private fun Marca(cor: Color) {
    Box(Modifier.size(10.dp).background(cor, CircleShape))
}

@Composable
private fun Legenda(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Marca(cor)
        Spacer(Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium)
    }
}

// ---------- Agora ----------

@Composable
internal fun AbaAgora(dados: DadosIa, agoraMs: Long, fuso: ZoneId) {
    val agoraS = agoraMs / 1000.0
    Rolavel {
        val limites = dados.limites
        val atual = limites?.atual
        if (limites == null || atual == null) {
            Text(stringResource(R.string.ia_sem_dados_limites), style = MaterialTheme.typography.bodyLarge)
        } else {
            if (leituraAntiga(atual, agoraS)) Faixa(stringResource(R.string.ia_leitura_antiga), Tom.AVISO)
            atual.cincoHoras?.let { Medidor(stringResource(R.string.ia_janela_5h), it, agoraS) }
            atual.seteDias?.let { Medidor(stringResource(R.string.ia_janela_semanal), it, agoraS) }
            val pontos = historicoRecente(limites.historico, agoraS)
            if (pontos.size >= 2) {
                val corCinco = MaterialTheme.colorScheme.primary
                val corSete = coresDoTom(Tom.AVISO).first
                Secao(stringResource(R.string.ia_historico_titulo)) {
                    GraficoLimites(
                        cincoHoras = trechosDoHistorico(pontos) { it.cincoHoras },
                        seteDias = trechosDoHistorico(pontos) { it.seteDias },
                        desdeS = agoraS - DIAS_SEMANA * 86_400,
                        ateS = agoraS,
                        corCincoHoras = corCinco,
                        corSeteDias = corSete,
                        corGrade = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legenda(corCinco, stringResource(R.string.ia_legenda_5h))
                        Legenda(corSete, stringResource(R.string.ia_legenda_semana))
                    }
                }
            }
        }
        dados.uso?.let { uso ->
            val hoje = hojeEm(agoraMs, fuso)
            Periodo(stringResource(R.string.ia_hoje), resumoDoPeriodo(diasNoPeriodo(uso.dias, hoje, hoje)))
            Periodo(
                stringResource(R.string.ia_ultimos_7_dias),
                resumoDoPeriodo(diasNoPeriodo(uso.dias, hoje.minusDays(DIAS_SEMANA - 1L), hoje)),
            )
            Nota()
        }
    }
}

@Composable
private fun Nota() {
    Text(
        stringResource(R.string.ia_nota_custo),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Faixa colorida de aviso (mesma linguagem dos alertas da aba Status). */
@Composable
private fun Faixa(texto: String, tom: Tom) {
    val (frente, fundo) = coresDoTom(tom)
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall,
        color = frente,
        modifier = Modifier.fillMaxWidth().background(fundo, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Uma janela de limite: porcentagem, barra, quando zera e há quanto tempo foi lida. */
@Composable
private fun Medidor(titulo: String, faixa: FaixaLimite, agoraS: Double) {
    val nivel = nivelDoLimite(faixa.usadoPct)
    val cor = when (nivel) {
        NivelLimite.NORMAL -> MaterialTheme.colorScheme.primary
        NivelLimite.ATENCAO -> coresDoTom(Tom.AVISO).first
        NivelLimite.CRITICO -> coresDoTom(Tom.ERRO).first
    }
    val zera = segundosParaZerar(faixa, agoraS)?.let { stringResource(R.string.ia_zera_em, textoDoTempo(tempoDe(it))) }
        ?: stringResource(R.string.ia_ja_zerou)
    val lido = idadeDaLeituraS(faixa, agoraS)?.let { stringResource(R.string.ia_lido_ha, textoDoTempo(tempoDe(it))) }
    Secao(titulo) {
        Text(rotuloDoPct(faixa.usadoPct), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(
            progress = { (pctDaBarra(faixa.usadoPct) / 100).toFloat() },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = cor,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
        when (nivel) {
            NivelLimite.NORMAL -> Unit
            NivelLimite.ATENCAO -> Text(stringResource(R.string.ia_perto_do_limite), style = MaterialTheme.typography.labelLarge, color = cor)
            NivelLimite.CRITICO -> Text(stringResource(R.string.ia_limite_estourando), style = MaterialTheme.typography.labelLarge, color = cor)
        }
        Text(juntar(zera, lido), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Hoje: 1,2 mi tokens · US$ 3,40" e uma linha por modelo. */
@Composable
private fun Periodo(titulo: String, resumo: ResumoPeriodo) {
    if (resumo.porModelo.isEmpty()) {
        Secao(stringResource(R.string.ia_sem_uso_no_periodo, titulo)) {}
        return
    }
    Secao(stringResource(R.string.ia_resumo_periodo, titulo, tokensCompactos(resumo.tokens), dolar(resumo.custoUsd))) {
        resumo.porModelo.forEach { m ->
            Text(
                stringResource(R.string.ia_modelo_linha, nomeModelo(m.modelo), tokensCompactos(m.total), dolar(m.custoUsd)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ---------- Modelos ----------

@Composable
internal fun AbaModelos(dados: DadosIa, agoraMs: Long, fuso: ZoneId) {
    val uso = dados.uso
    if (uso == null) {
        Vazio(stringResource(R.string.ia_sem_dados_uso))
        return
    }
    val serie = remember(uso, agoraMs / 60_000) { serieDeTokens(uso.dias, hojeEm(agoraMs, fuso)) }
    val totais = remember(uso) { somarPorModelo(uso.dias).associateBy { it.modelo } }
    val descricao = stringResource(R.string.ia_grafico_descricao)
    Rolavel {
        Secao(stringResource(R.string.ia_grafico_titulo)) {
            Text(
                stringResource(R.string.ia_grafico_maximo, tokensCompactos(serie.totalPorDia.maxOrNull() ?: 0)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GraficoModelos(
                serie,
                corGrade = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = descricao },
            )
        }
        Secao(stringResource(R.string.ia_totais_titulo)) {
            Nota()
            // A cor de cada modelo é a mesma do gráfico (posição na lista ordenada).
            serie.modelos.forEachIndexed { indice, modelo ->
                totais[modelo]?.let { LinhaDeTotal(corDaSerie(indice), it) }
            }
        }
    }
}

@Composable
private fun LinhaDeTotal(cor: Color, total: TotalModelo) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp)) { Marca(cor) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(nomeModelo(total.modelo), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.ia_tokens_entrada_saida, inteiro(total.entrada), inteiro(total.saida)),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.ia_tokens_cache, inteiro(total.cacheLeitura), inteiro(total.cacheEscrita)),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(dolar(total.custoUsd), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- Sessões ----------

@Composable
internal fun AbaSessoes(dados: DadosIa, fuso: ZoneId) {
    val uso = dados.uso
    if (uso == null) {
        Vazio(stringResource(R.string.ia_sem_dados_uso))
        return
    }
    val lista = remember(uso) { sessoesRecentes(uso) }
    if (lista.isEmpty()) {
        Vazio(stringResource(R.string.ia_nenhuma_sessao))
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(lista) { CartaoDeSessao(it, fuso) }
    }
}

@Composable
private fun CartaoDeSessao(sessao: SessaoUso, fuso: ZoneId) {
    val duracao = duracaoDaSessaoS(sessao)?.let { textoDoTempo(tempoDe(it.toDouble())) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                sessao.titulo.ifEmpty { stringResource(R.string.ia_sem_titulo) },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(juntar(sessao.projeto, dataHora(sessao.fim, fuso)), style = MaterialTheme.typography.bodySmall)
            Text(juntar(duracao, modelosDaSessao(sessao).joinToString(", ")), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.ia_sessao_total, tokensCompactos(sessao.total), dolar(sessao.custoUsd)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- Agentes ----------

@Composable
internal fun AbaAgentes(dados: DadosIa) {
    val uso = dados.uso
    if (uso == null) {
        Vazio(stringResource(R.string.ia_sem_dados_uso))
        return
    }
    val linhas = remember(uso, dados.catalogo) { linhasDeAgentes(uso, dados.catalogo) }
    Rolavel {
        Text(
            stringResource(R.string.ia_agentes_nota),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        linhas.forEach { CartaoDeAgente(it) }
    }
}

@Composable
private fun CartaoDeAgente(agente: LinhaDeAgente) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(agente.nome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.ia_agente_resumo, tokensCompactos(agente.total), dolar(agente.custoUsd), agente.sessoes),
                style = MaterialTheme.typography.bodySmall,
            )
            val configurado = agente.configurado
            if (configurado != null) {
                Selo(
                    stringResource(R.string.ia_agente_configurado, configurado),
                    if (agente.diverge) Tom.AVISO else Tom.NEUTRO,
                )
            } else {
                Text(
                    stringResource(R.string.ia_agente_sem_cadastro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // map é inline (joinToString não é): só assim dá para chamar stringResource.
            val usados = agente.usados.map { (modelo, tokens) ->
                stringResource(R.string.ia_agente_modelo_usado, nomeModelo(modelo), tokensCompactos(tokens))
            }.joinToString(", ")
            Text(stringResource(R.string.ia_agente_usado, usados), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------- Catálogo ----------

@Composable
internal fun AbaCatalogo(dados: DadosIa) {
    val catalogo = dados.catalogo
    if (catalogo == null) {
        Vazio(stringResource(R.string.ia_sem_dados_catalogo))
        return
    }
    Rolavel {
        Secao(stringResource(R.string.ia_agentes_titulo, catalogo.agentes.size)) {
            if (catalogo.agentes.isEmpty()) ItemVazio()
            catalogo.agentes.forEach { agente ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(agente.nome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (agente.modelo.isNotEmpty()) Selo(agente.modelo, Tom.NEUTRO)
                    }
                    Text(agente.descricao, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Secao(stringResource(R.string.ia_skills_titulo, catalogo.skills.size)) {
            if (catalogo.skills.isEmpty()) ItemVazio()
            catalogo.skills.forEach { skill ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(skill.nome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(skill.descricao, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Secao(stringResource(R.string.ia_candidatos_titulo, catalogo.candidatos.size)) {
            if (catalogo.candidatos.isEmpty()) ItemVazio()
            catalogo.candidatos.forEach { candidato ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.par, candidato.projeto, candidato.tipo),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.ia_candidato_vezes, candidato.vezes, candidato.quando),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(candidato.tarefa, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ItemVazio() {
    Text(
        stringResource(R.string.ia_nenhum_item),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
