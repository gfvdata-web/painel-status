package io.github.gfvdataweb.painelstatus.ui.ia

import io.github.gfvdataweb.painelstatus.data.ia.CatalogoIa
import io.github.gfvdataweb.painelstatus.data.ia.DiaUso
import io.github.gfvdataweb.painelstatus.data.ia.FaixaLimite
import io.github.gfvdataweb.painelstatus.data.ia.LimitesAtuais
import io.github.gfvdataweb.painelstatus.data.ia.PontoLimite
import io.github.gfvdataweb.painelstatus.data.ia.SessaoUso
import io.github.gfvdataweb.painelstatus.data.ia.UsoIa
import io.github.gfvdataweb.painelstatus.ui.comum.instante
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToLong

// Regras de exibição do painel de IA, as mesmas de painel-ia/docs/js/painel.js
// (limites, totais por modelo, série de 30 dias, agentes). Funções puras,
// testadas em JVM; as telas só desenham o que sai daqui.

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

/** Limites do painel-ia: a partir daqui a barra fica âmbar / vermelha. */
const val LIMITE_ATENCAO = 80.0
const val LIMITE_CRITICO = 95.0

/** Leitura dos limites mais velha que isto (30 min) ganha o aviso "leitura antiga". */
const val LEITURA_ANTIGA_S = 30 * 60

/** Janela do gráfico por modelo e do resumo "últimos 7 dias". */
const val DIAS_GRAFICO = 30
const val DIAS_SEMANA = 7

// ---------- Formatação ----------

/** `1234567` vira `1,2 mi`; `1500` vira `1,5 mil`; `1,50 bi`; abaixo de mil, o número. */
fun tokensCompactos(n: Long): String = when {
    n >= 1_000_000_000 -> "${decimal(n / 1e9, 2)} bi"
    n >= 1_000_000 -> "${decimal(n / 1e6, 1)} mi"
    n >= 1_000 -> "${decimal(n / 1e3, 1)} mil"
    else -> n.toString()
}

private fun decimal(valor: Double, casas: Int): String =
    String.format(Locale.ROOT, "%.${casas}f", valor).replace('.', ',')

/** `1234567` vira `1.234.567`. */
fun inteiro(n: Long): String = NumberFormat.getIntegerInstance(PT_BR).format(n)

/** `1234.5` vira `US$ 1.234,50`. */
fun dolar(valor: Double): String {
    val formato = NumberFormat.getNumberInstance(PT_BR).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "US$ ${formato.format(valor)}"
}

/** `claude-opus-5-5` vira `opus-5-5`. */
fun nomeModelo(id: String): String = id.removePrefix("claude-")

/** Tempo em partes; a tela escolhe o texto (como `duracao` em ui/comum). */
sealed interface Tempo {
    data object MenosDeUmMinuto : Tempo
    data class Minutos(val minutos: Long) : Tempo
    data class HorasEMinutos(val horas: Long, val minutos: Long) : Tempo
    data class DiasEHoras(val dias: Long, val horas: Long) : Tempo
}

/** Mesmos cortes de `duracao()` da página: menos de 1 min, minutos, h + min (até 48 h), d + h. */
fun tempoDe(segundos: Double): Tempo {
    val min = (segundos / 60).roundToLong().coerceAtLeast(0)
    if (min < 1) return Tempo.MenosDeUmMinuto
    if (min < 60) return Tempo.Minutos(min)
    val horas = min / 60
    return if (horas < 48) Tempo.HorasEMinutos(horas, min % 60) else Tempo.DiasEHoras(horas / 24, horas % 24)
}

// ---------- Limites ----------

enum class NivelLimite { NORMAL, ATENCAO, CRITICO }

fun nivelDoLimite(pct: Double): NivelLimite = when {
    pct >= LIMITE_CRITICO -> NivelLimite.CRITICO
    pct >= LIMITE_ATENCAO -> NivelLimite.ATENCAO
    else -> NivelLimite.NORMAL
}

/** Uso preso entre 0 e 100 (a barra não passa da caixa). */
fun pctDaBarra(pct: Double): Double = pct.coerceIn(0.0, 100.0)

/** `18.0` vira `18%`; `18.5` vira `18,5%`. */
fun rotuloDoPct(pct: Double): String {
    val limitado = pctDaBarra(pct)
    return if (limitado % 1.0 == 0.0) "${limitado.toLong()}%" else "${decimal(limitado, 1)}%"
}

/**
 * Segundos até a janela zerar, ou null se já zerou desde a leitura. Como a
 * página: sem `zera_em` conta como já zerado (a janela de 5 h fica sem horário
 * quando não há sessão em andamento).
 */
fun segundosParaZerar(faixa: FaixaLimite, agoraS: Double): Double? {
    val zeraEm = faixa.zeraEm ?: 0.0
    return if (faixa.zerouDesdeALeitura || zeraEm <= agoraS) null else zeraEm - agoraS
}

/** Há quanto tempo a janela foi lida (segundos), ou null sem `lido_em`. */
fun idadeDaLeituraS(faixa: FaixaLimite, agoraS: Double): Double? = faixa.lidoEm?.let { agoraS - it }

/** A leitura mais recente das duas janelas tem mais de [LEITURA_ANTIGA_S]. */
fun leituraAntiga(atual: LimitesAtuais, agoraS: Double): Boolean {
    val maisRecente = listOfNotNull(atual.cincoHoras?.lidoEm, atual.seteDias?.lidoEm).filter { it > 0 }.maxOrNull()
    return maisRecente != null && agoraS - maisRecente > LEITURA_ANTIGA_S
}

/** Pontos do histórico dos últimos [dias] dias, para o gráfico pequeno da aba Agora. */
fun historicoRecente(historico: List<PontoLimite>, agoraS: Double, dias: Int = DIAS_SEMANA): List<PontoLimite> =
    historico.filter { it.ts >= agoraS - dias * 86_400 }.sortedBy { it.ts }

/** Trechos contínuos (ts, pct) de uma janela: leitura sem a janela (null) interrompe a linha. */
fun trechosDoHistorico(pontos: List<PontoLimite>, valor: (PontoLimite) -> Double?): List<List<Pair<Double, Double>>> {
    val trechos = mutableListOf<MutableList<Pair<Double, Double>>>()
    var atual: MutableList<Pair<Double, Double>>? = null
    for (ponto in pontos) {
        val v = valor(ponto)
        if (v == null) {
            atual = null
        } else {
            val trecho = atual ?: mutableListOf<Pair<Double, Double>>().also { trechos += it }
            trecho += ponto.ts to pctDaBarra(v)
            atual = trecho
        }
    }
    return trechos
}

// ---------- Uso por modelo ----------

data class TotalModelo(
    val modelo: String,
    val entrada: Long,
    val saida: Long,
    val cacheLeitura: Long,
    val cacheEscrita: Long,
    val total: Long,
    val custoUsd: Double,
)

/** Soma as linhas por modelo; o de maior custo primeiro. */
fun somarPorModelo(dias: List<DiaUso>): List<TotalModelo> =
    dias.groupBy { it.modelo }
        .map { (modelo, linhas) ->
            TotalModelo(
                modelo = modelo,
                entrada = linhas.sumOf { it.entrada },
                saida = linhas.sumOf { it.saida },
                cacheLeitura = linhas.sumOf { it.cacheLeitura },
                cacheEscrita = linhas.sumOf { it.cacheEscrita },
                total = linhas.sumOf { it.total },
                custoUsd = linhas.sumOf { it.custoUsd },
            )
        }
        .sortedByDescending { it.custoUsd }

/** Totais de um período: tokens e custo gerais e a quebra por modelo. */
data class ResumoPeriodo(val porModelo: List<TotalModelo>, val tokens: Long, val custoUsd: Double)

fun resumoDoPeriodo(dias: List<DiaUso>): ResumoPeriodo {
    val por = somarPorModelo(dias)
    return ResumoPeriodo(por, por.sumOf { it.total }, por.sumOf { it.custoUsd })
}

/** Linhas de `dias` entre [de] e [ate] (inclusive); o dia é AAAA-MM-DD, que ordena como texto. */
fun diasNoPeriodo(dias: List<DiaUso>, de: LocalDate, ate: LocalDate): List<DiaUso> =
    dias.filter { it.dia >= de.toString() && it.dia <= ate.toString() }

/** Dia de calendário de "agora" no fuso do celular. */
fun hojeEm(agoraMs: Long, fuso: ZoneId): LocalDate = Instant.ofEpochMilli(agoraMs).atZone(fuso).toLocalDate()

/** Tokens totais por dia e por modelo, dos últimos dias até hoje: `tokens[modelo][dia]`. */
data class SerieModelos(val dias: List<LocalDate>, val modelos: List<String>, val tokens: List<List<Long>>) {
    /** Total de cada dia (altura da barra empilhada). */
    val totalPorDia: List<Long> get() = dias.indices.map { d -> tokens.sumOf { it[d] } }
}

fun serieDeTokens(dias: List<DiaUso>, hoje: LocalDate, quantos: Int = DIAS_GRAFICO): SerieModelos {
    val datas = (quantos - 1 downTo 0).map { hoje.minusDays(it.toLong()) }
    val modelos = dias.map { it.modelo }.distinct().sorted()
    val porDiaEModelo = dias.groupBy { it.dia to it.modelo }
    val tokens = modelos.map { modelo ->
        datas.map { data -> porDiaEModelo[data.toString() to modelo].orEmpty().sumOf { it.total } }
    }
    return SerieModelos(datas, modelos, tokens)
}

// ---------- Sessões e agentes ----------

/** Mais recentes primeiro (pelo fim). */
fun sessoesRecentes(uso: UsoIa): List<SessaoUso> = uso.sessoes.sortedByDescending { it.fim }

/** Duração da sessão em segundos; null se início ou fim não puderem ser lidos. */
fun duracaoDaSessaoS(sessao: SessaoUso): Long? {
    val inicio = instante(sessao.inicio) ?: return null
    val fim = instante(sessao.fim) ?: return null
    return Duration.between(inicio, fim).seconds.coerceAtLeast(0)
}

/** Modelos usados na sessão, do que mais gastou para o que menos. */
fun modelosDaSessao(sessao: SessaoUso): List<String> =
    sessao.modelos.entries.sortedByDescending { it.value.total }.map { nomeModelo(it.key) }

data class LinhaDeAgente(
    val nome: String,
    val total: Long,
    val custoUsd: Double,
    val sessoes: Long,
    /** Modelo configurado no catálogo (ex.: `sonnet`), ou null se o agente não está lá. */
    val configurado: String?,
    /** Rodou também em modelo diferente do configurado. */
    val diverge: Boolean,
    /** Modelos que de fato rodaram e seus tokens, do maior para o menor. */
    val usados: List<Pair<String, Long>>,
)

/** Uso por agente, do mais caro ao mais barato, comparado ao modelo configurado no catálogo. */
fun linhasDeAgentes(uso: UsoIa, catalogo: CatalogoIa?): List<LinhaDeAgente> {
    val configurados = catalogo?.agentes.orEmpty().associate { it.nome.lowercase() to it.modelo }
    return uso.agentes.entries.sortedByDescending { it.value.custoUsd }.map { (nome, agente) ->
        val configurado = configurados[nome.lowercase()]?.ifEmpty { null }
        val usados = agente.modelos.entries.sortedByDescending { it.value.total }.map { it.key to it.value.total }
        LinhaDeAgente(
            nome = nome,
            total = agente.total,
            custoUsd = agente.custoUsd,
            sessoes = agente.sessoes,
            configurado = configurado,
            diverge = configurado != null && usados.any { (modelo, _) -> !modelo.contains(configurado) },
            usados = usados,
        )
    }
}
