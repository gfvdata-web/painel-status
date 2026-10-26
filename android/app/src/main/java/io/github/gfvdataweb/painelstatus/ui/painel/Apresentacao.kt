package io.github.gfvdataweb.painelstatus.ui.painel

import androidx.annotation.StringRes
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.data.modelo.Alerta
import io.github.gfvdataweb.painelstatus.data.modelo.Aviso
import io.github.gfvdataweb.painelstatus.data.modelo.Etapa
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

/**
 * Ordem dos cards do app: os [slugs] salvos primeiro, na ordem salva; os sites que
 * não estão na lista vão para o fim, na ordem do cadastro. Slug salvo que não existe
 * mais é ignorado. [slugs] vazio = comportamento de antes (destaque primeiro).
 */
fun ordenar(sites: List<Site>, slugs: List<String>): List<Site> {
    if (slugs.isEmpty()) return sites.sortedByDescending { it.destaque }
    val posicao = HashMap<String, Int>()
    slugs.forEachIndexed { indice, slug -> posicao.putIfAbsent(slug, indice) }
    return sites.sortedBy { posicao[it.slug] ?: Int.MAX_VALUE } // sortedBy é estável
}

/** Move o item de [de] para [para] (limites ignorados: devolve a lista como estava). */
fun moverSlug(slugs: List<String>, de: Int, para: Int): List<String> {
    if (de !in slugs.indices || para !in slugs.indices || de == para) return slugs
    val nova = slugs.toMutableList()
    nova.add(para, nova.removeAt(de))
    return nova
}

/** Só endereços web abrem no navegador (o que vem no JSON não é executado). */
fun linkSeguro(url: String?): String? =
    url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

/** Repositório do site na conta gfvdata-web (mesmo link do pop-up da página). */
fun urlDoRepositorio(site: Site): String = "https://github.com/gfvdata-web/${site.repo}"

// ---------- Aba Status (a lista de docs/js/lista.js) ----------

/** Dias somados no "visitas · 7d" do card. */
const val DIAS_VISITAS = 7

/** Categoria do card (config.py: CATEGORIAS); valor ausente ou desconhecido = [NEUTRA]. */
enum class Categoria { DADOS_ABERTOS, HOBBY, CURIOSIDADE, NEUTRA }

fun categoriaDe(valor: String?): Categoria = when (valor) {
    "dados_abertos" -> Categoria.DADOS_ABERTOS
    "hobby" -> Categoria.HOBBY
    "curiosidade" -> Categoria.CURIOSIDADE
    else -> Categoria.NEUTRA
}

/** Cor da faixa do card (ARGB), as `--cat-*` de docs/css/estilo.css. */
fun corDaCategoria(categoria: Categoria): Long = when (categoria) {
    Categoria.DADOS_ABERTOS -> 0xFF0891B2
    Categoria.HOBBY -> 0xFF7C3AED
    Categoria.CURIOSIDADE -> 0xFF16A34A
    Categoria.NEUTRA -> 0xFF94A3B8
}

@StringRes
fun rotuloDaCategoria(categoria: Categoria): Int = when (categoria) {
    Categoria.DADOS_ABERTOS -> R.string.categoria_dados_abertos
    Categoria.HOBBY -> R.string.categoria_hobby
    Categoria.CURIOSIDADE -> R.string.categoria_curiosidade
    Categoria.NEUTRA -> R.string.categoria_neutra
}

/** O destaque (Bolão F1) primeiro; o resto na ordem do cadastro. */
fun sitesNaOrdemDaLista(status: Status): List<Site> = status.sites.sortedByDescending { it.destaque }

/** Categorias com algum site, sem repetir e sem a neutra (a legenda some se não houver nenhuma). */
fun categoriasUsadas(sites: List<Site>): List<Categoria> =
    sites.map { categoriaDe(it.categoria) }.filter { it != Categoria.NEUTRA }.distinct()

/** Soma dos visitantes únicos dos últimos [DIAS_VISITAS] dias da série; null = site sem rastreio. */
fun visitasRecentes(site: Site): Int? =
    site.acesso?.serieDiaria?.takeIf { it.isNotEmpty() }?.takeLast(DIAS_VISITAS)?.sumOf { it.visitantes }

/** Linha de downloads do card: [recentes] = soma dos últimos [DIAS_VISITAS] dias; skill e CSV são acumulados. */
data class ResumoDownloads(val recentes: Int, val skill: Int, val csv: Int)

/** null = site sem bloco de downloads. */
fun downloadsRecentes(site: Site): ResumoDownloads? =
    site.downloads?.let { d ->
        ResumoDownloads(d.serieDiaria.takeLast(DIAS_VISITAS).sumOf { it.downloads }, d.skill, d.csv)
    }

/** Cabeçalho da lista: quantos sites e quantos não responderam. */
data class ResumoDosSites(val total: Int, val foraDoAr: Int)

fun resumoDosSites(sites: List<Site>): ResumoDosSites =
    ResumoDosSites(sites.size, sites.count { it.siteNoAr?.ok != true })

/** O que vai embaixo do nome no card (a ordem de prioridade é a do lista.js). */
sealed interface SubLinha {
    data class Endereco(val texto: String) : SubLinha
    data class ForaDoAr(val statusHttp: Int?) : SubLinha
    data class Alertas(val texto: String) : SubLinha
    object Vigilia : SubLinha
}

/** [tom] vira a bolinha do card (OK, AVISO ou ERRO) e a cor da sub-linha. */
data class SituacaoDoSite(val tom: Tom, val subLinha: SubLinha)

fun situacaoDoSite(site: Site): SituacaoDoSite = when {
    site.siteNoAr?.ok != true -> SituacaoDoSite(Tom.ERRO, SubLinha.ForaDoAr(site.siteNoAr?.statusHttp?.takeIf { it != 0 }))
    site.alertas.isNotEmpty() -> SituacaoDoSite(
        if (site.alertas.any { it.nivel == "erro" }) Tom.ERRO else Tom.AVISO,
        SubLinha.Alertas("⚠ " + site.alertas.joinToString(" ") { it.texto }),
    )
    site.destaque && site.emVigilia -> SituacaoDoSite(Tom.OK, SubLinha.Vigilia)
    else -> SituacaoDoSite(Tom.OK, SubLinha.Endereco(enderecoCurto(site.pagesUrl)))
}

/** Passo a passo do card: etapas, link da execução e se é o envio do Forms (senão, a atualização de dados). */
data class PipelineDoSite(val etapas: List<Etapa>, val urlDaExecucao: String?, val doForms: Boolean)

/** Pipeline do Bolão (etapas_pipeline) ou, na falta, o do Forms (pipeline_forms); null = card sem pipeline. */
fun pipelineDoSite(site: Site): PipelineDoSite? {
    if (site.etapasPipeline.isNotEmpty()) return PipelineDoSite(site.etapasPipeline, site.ultimaExecucao?.url, false)
    val bloco = site.pipelineForms ?: return null
    val execucao = bloco.execucao
    if (execucao == null || execucao.erro != null || bloco.etapas.isEmpty()) return null
    return PipelineDoSite(bloco.etapas, execucao.url, true)
}

/** `https://x.io/y/` vira `x.io/y`. */
fun enderecoCurto(url: String): String = url.removePrefix("https://").removePrefix("http://").removeSuffix("/")

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
