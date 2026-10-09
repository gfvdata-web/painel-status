package io.github.gfvdataweb.painelstatus.ui.painel

import androidx.annotation.StringRes
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.data.modelo.Alerta
import io.github.gfvdataweb.painelstatus.data.modelo.Aviso
import io.github.gfvdataweb.painelstatus.data.modelo.Execucao
import io.github.gfvdataweb.painelstatus.data.modelo.Historico
import io.github.gfvdataweb.painelstatus.data.modelo.Site
import io.github.gfvdataweb.painelstatus.data.modelo.Status
import kotlin.math.ceil
import kotlin.math.roundToInt

// Regras de exibição em funções puras (testadas em JVM), as mesmas de
// docs/js/app.js. As telas só desenham o que sai daqui.

/** Tom de cor de um resultado (verde, amarelo, vermelho ou cinza). */
enum class Tom { OK, AVISO, ERRO, NEUTRO }

private val FALHAS = setOf("failure", "timed_out", "startup_failure")
private val ESPERANDO = setOf("in_progress", "queued", "cancelled")

fun tomDoResultado(resultado: String?): Tom = when {
    resultado == "success" -> Tom.OK
    resultado != null && resultado in FALHAS -> Tom.ERRO
    resultado != null && resultado in ESPERANDO -> Tom.AVISO
    else -> Tom.NEUTRO
}

/** O que a execução "deu": a conclusão, ou o status enquanto ainda não terminou. */
fun resultadoDe(execucao: Execucao): String? =
    if (execucao.status == null || execucao.status == "completed") execucao.conclusao else execucao.status

@StringRes
fun rotuloDoResultado(resultado: String?): Int? = when (resultado) {
    "success" -> R.string.resultado_sucesso
    "failure" -> R.string.resultado_falhou
    "cancelled" -> R.string.resultado_cancelada
    "timed_out" -> R.string.resultado_tempo_esgotado
    "startup_failure" -> R.string.resultado_falhou_ao_iniciar
    "skipped" -> R.string.resultado_pulado
    "in_progress" -> R.string.resultado_em_andamento
    "queued" -> R.string.resultado_na_fila
    else -> null
}

@StringRes
fun rotuloDoEvento(evento: String?): Int? = when (evento) {
    "schedule" -> R.string.evento_agendada
    "workflow_dispatch" -> R.string.evento_manual
    "repository_dispatch" -> R.string.evento_disparo_externo
    "push" -> R.string.evento_push
    else -> null
}

/** Selo da última execução de um site (badgeExecucao / badgeStatusPipelineBolao da página). */
enum class SituacaoDaExecucao { VIGILIA, SEM_REGISTRO, OK, FALHOU, EM_ANDAMENTO, OUTRA }

fun situacaoDaExecucao(execucao: Execucao?, emVigilia: Boolean = false): SituacaoDaExecucao = when {
    emVigilia -> SituacaoDaExecucao.VIGILIA
    execucao == null || execucao.erro != null -> SituacaoDaExecucao.SEM_REGISTRO
    execucao.conclusao == "success" -> SituacaoDaExecucao.OK
    execucao.conclusao == "failure" -> SituacaoDaExecucao.FALHOU
    execucao.status != "completed" -> SituacaoDaExecucao.EM_ANDAMENTO
    else -> SituacaoDaExecucao.OUTRA
}

/** Estado de uma etapa do Bolão F1 (src/etapas_bolao.py). */
enum class EstadoDaEtapa { OK, ANDAMENTO, AGUARDANDO, ERRO, PENDENTE, PULADO, DESCONHECIDO }

fun estadoDaEtapa(estado: String): EstadoDaEtapa = when (estado) {
    "ok" -> EstadoDaEtapa.OK
    "andamento" -> EstadoDaEtapa.ANDAMENTO
    "aguardando" -> EstadoDaEtapa.AGUARDANDO
    "erro" -> EstadoDaEtapa.ERRO
    "pendente" -> EstadoDaEtapa.PENDENTE
    "pulado" -> EstadoDaEtapa.PULADO
    else -> EstadoDaEtapa.DESCONHECIDO
}

@StringRes
fun rotuloDaEtapa(estado: EstadoDaEtapa): Int? = when (estado) {
    EstadoDaEtapa.OK -> R.string.etapa_ok
    EstadoDaEtapa.ANDAMENTO -> R.string.etapa_andamento
    EstadoDaEtapa.AGUARDANDO -> R.string.etapa_aguardando
    EstadoDaEtapa.ERRO -> R.string.etapa_erro
    EstadoDaEtapa.PENDENTE -> R.string.etapa_pendente
    EstadoDaEtapa.PULADO -> R.string.etapa_pulado
    EstadoDaEtapa.DESCONHECIDO -> null
}

fun tomDaEtapa(estado: EstadoDaEtapa): Tom = when (estado) {
    EstadoDaEtapa.OK -> Tom.OK
    EstadoDaEtapa.ANDAMENTO, EstadoDaEtapa.AGUARDANDO -> Tom.AVISO
    EstadoDaEtapa.ERRO -> Tom.ERRO
    EstadoDaEtapa.PENDENTE, EstadoDaEtapa.PULADO, EstadoDaEtapa.DESCONHECIDO -> Tom.NEUTRO
}

/**
 * O fluxo do Bolão F1 ainda está andando (execução rodando, vigília do
 * resultado ou alguma etapa em andamento/aguardando)? A tela mostra o aviso
 * "acompanhando ao vivo".
 */
fun fluxoEmAndamento(site: Site): Boolean {
    val execucao = site.ultimaExecucao
    val rodando = execucao != null && execucao.erro == null && execucao.status != null && execucao.status != "completed"
    val etapaAberta = site.etapasPipeline.any {
        val estado = estadoDaEtapa(it.estado)
        estado == EstadoDaEtapa.ANDAMENTO || estado == EstadoDaEtapa.AGUARDANDO
    }
    return site.emVigilia || rodando || etapaAberta
}

/** Ícone ⚠ do card: vermelho se algum alerta é "erro", amarelo se só avisos, nada sem alertas. */
fun tomDosAlertas(alertas: List<Alerta>): Tom? = when {
    alertas.isEmpty() -> null
    alertas.any { it.nivel == "erro" } -> Tom.ERRO
    else -> Tom.AVISO
}

fun tomDoAlerta(alerta: Alerta): Tom = if (alerta.nivel == "erro") Tom.ERRO else Tom.AVISO

fun destaqueDe(status: Status): Site? = status.sites.firstOrNull { it.destaque }

fun outrosSites(status: Status): List<Site> = status.sites.filterNot { it.destaque }

/** Só endereços web abrem no navegador (o que vem no JSON não é executado). */
fun linkSeguro(url: String?): String? =
    url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

/** Um atalho da aba Links. [titulo] null = rótulo padrão ("Abrir Google Forms"). */
data class Atalho(val titulo: String?, val detalhe: String?, val url: String)

/** Os sites, com o destaque primeiro. */
fun atalhosDosSites(status: Status): List<Atalho> =
    status.sites.sortedByDescending { it.destaque }.mapNotNull { site ->
        linkSeguro(site.pagesUrl)?.let { Atalho(site.nome, it, it) }
    }

/** Forms dos sites (rótulo do cadastro + nome do site) e depois os forms avulsos. */
fun atalhosDosForms(status: Status): List<Atalho> {
    val dosSites = status.sites.sortedByDescending { it.destaque }.mapNotNull { site ->
        linkSeguro(site.formUrl)?.let { Atalho(site.formRotulo, site.nome, it) }
    }
    val avulsos = status.formsAvulsos.mapNotNull { form -> linkSeguro(form.url)?.let { Atalho(form.nome, null, it) } }
    return dosSites + avulsos
}

// ---------- Histórico acumulado (formato em src/historico.py) ----------

/** Aviso presente em pelo menos metade das execuções lidas (e em 3 ou mais) é "recorrente". */
private const val FRACAO_RECORRENTE = 0.5
private const val MINIMO_RECORRENTE = 3

data class ResumoDoHistorico(
    val total: Int,
    val sucessos: Int,
    val percentualDeSucesso: Int,
    /** Mediana da duração das execuções que deram certo, em segundos. */
    val duracaoTipicaS: Long?,
    val maisAntiga: String?,
    /** Avisos que se repetem em quase toda execução (mensagem → em quantas): mostrados uma vez só. */
    val recorrentes: Map<String, Int>,
    /** Quantas execuções tiveram os avisos lidos. */
    val lidas: Int,
)

fun resumir(historico: Historico): ResumoDoHistorico {
    val execucoes = historico.execucoes
    val sucessos = execucoes.filter { it.conclusao == "success" }
    val lidas = execucoes.mapNotNull { it.avisos }
    val contagem = lidas
        .flatMap { avisos -> avisos.map { it.mensagem }.distinct() }
        .groupingBy { it }
        .eachCount()
    val minimo = maxOf(MINIMO_RECORRENTE, ceil(lidas.size * FRACAO_RECORRENTE).toInt())
    return ResumoDoHistorico(
        total = execucoes.size,
        sucessos = sucessos.size,
        percentualDeSucesso = if (execucoes.isEmpty()) 0 else (sucessos.size * 100.0 / execucoes.size).roundToInt(),
        duracaoTipicaS = mediana(sucessos.mapNotNull { it.duracaoS }),
        maisAntiga = execucoes.lastOrNull()?.criadoEm,
        recorrentes = contagem.filterValues { it >= minimo },
        lidas = lidas.size,
    )
}

fun mediana(valores: List<Long>): Long? {
    if (valores.isEmpty()) return null
    val ordenados = valores.sorted()
    return ordenados[ordenados.size / 2]
}

/** Avisos da execução tirando os recorrentes. */
fun avisosProprios(execucao: Execucao, recorrentes: Map<String, Int>): List<Aviso> =
    execucao.avisos.orEmpty().filter { it.mensagem !in recorrentes }

/** Deu certo e sem aviso próprio: some com o filtro "só com falha ou aviso". */
fun semProblemas(execucao: Execucao, recorrentes: Map<String, Int>): Boolean =
    execucao.conclusao == "success" && avisosProprios(execucao, recorrentes).isEmpty()
