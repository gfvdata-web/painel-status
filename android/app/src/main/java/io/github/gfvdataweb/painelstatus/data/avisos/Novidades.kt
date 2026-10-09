package io.github.gfvdataweb.painelstatus.data.avisos

import io.github.gfvdataweb.painelstatus.data.modelo.Site
import io.github.gfvdataweb.painelstatus.data.modelo.Status

/**
 * Algo que mudou no painel desde a última vez que o app olhou e vale uma
 * notificação. Detectado comparando dois status.json (função pura [novidades]).
 */
sealed interface Novidade {
    /** O fluxo do Bolão F1 começou: palpites recebidos pelo Forms, ou nova tentativa manual. */
    data class BolaoComecou(val manual: Boolean) : Novidade

    /** Palpites lidos; o resultado oficial do quali ainda não saiu (vigília). */
    data object BolaoAguardandoResultado : Novidade

    /** A etapa "Página atualizada" ficou ok: placar novo no ar. */
    data class BolaoPaginaAtualizada(val rodada: Int?) : Novidade

    /** [etapa] = título da etapa que falhou; null = a execução falhou sem etapa marcada. */
    data class BolaoFalhou(val etapa: String?) : Novidade

    /** O site em destaque deixou de responder (os outros sites avisam pelos alertas). */
    data class SiteForaDoAr(val slug: String, val nome: String) : Novidade

    /** Alerta novo num site da grade (src/detalhes_site.py): fora do ar, coleta falhou… */
    data class NovoAlerta(val slug: String, val nome: String, val texto: String, val erro: Boolean) : Novidade
}

/** Novidade do canal "Bolão F1" (o resto é do canal "Alertas dos sites"). */
val Novidade.doBolao: Boolean get() = this !is Novidade.NovoAlerta

private const val ETAPA_RESULTADO = "resultado"
private const val ETAPA_PAGINA = "pagina"

/**
 * O que mudou de [anterior] para [atual]. Site que não existia antes não gera
 * aviso (não há com o que comparar).
 */
fun novidades(anterior: Status, atual: Status): List<Novidade> {
    val antesPorSlug = anterior.sites.associateBy { it.slug }
    return atual.sites.flatMap { site ->
        val antes = antesPorSlug[site.slug] ?: return@flatMap emptyList()
        if (site.destaque) novidadesDoBolao(antes, site) else novidadesDoSite(antes, site)
    }
}

private fun novidadesDoBolao(antes: Site, agora: Site): List<Novidade> {
    val lista = mutableListOf<Novidade>()
    val execucao = agora.ultimaExecucao
    val idAtual = execucao?.id
    val novaExecucao = idAtual != null && idAtual != antes.ultimaExecucao?.id
    // Execução nova: as etapas da anterior não contam (tudo recomeçou).
    val estadosAntes = if (novaExecucao) emptyMap() else antes.etapasPipeline.associate { it.chave to it.estado }
    fun ficou(chave: String, estado: String) =
        agora.etapasPipeline.any { it.chave == chave && it.estado == estado } && estadosAntes[chave] != estado

    if (novaExecucao && execucao.status != "completed") {
        lista += Novidade.BolaoComecou(manual = execucao.evento == "workflow_dispatch")
    }
    if (ficou(ETAPA_RESULTADO, "aguardando")) lista += Novidade.BolaoAguardandoResultado
    if (ficou(ETAPA_PAGINA, "ok")) lista += Novidade.BolaoPaginaAtualizada(agora.rodadaMaisRecente)

    val falhas = agora.etapasPipeline.filter { it.estado == "erro" && estadosAntes[it.chave] != "erro" }
    falhas.forEach { lista += Novidade.BolaoFalhou(it.titulo) }
    val falhouAgora = execucao?.conclusao == "failure" &&
        (novaExecucao || antes.ultimaExecucao?.conclusao != "failure")
    if (falhas.isEmpty() && falhouAgora) lista += Novidade.BolaoFalhou(null)

    if (antes.siteNoAr?.ok == true && agora.siteNoAr?.ok != true) {
        lista += Novidade.SiteForaDoAr(agora.slug, agora.nome)
    }
    return lista
}

private fun novidadesDoSite(antes: Site, agora: Site): List<Novidade> {
    val vistos = antes.alertas.map { normalizado(it.texto) }.toSet()
    return agora.alertas
        .filter { normalizado(it.texto) !in vistos }
        .map { Novidade.NovoAlerta(agora.slug, agora.nome, it.texto, erro = it.nivel == "erro") }
}

/** "Sem coleta com sucesso há 5 dias" e "… há 6 dias" são o mesmo alerta: não avisa todo dia. */
private fun normalizado(texto: String): String = texto.replace(Regex("""\d+"""), "#")

/**
 * Do Bolão sai uma notificação só sobre o pipeline (ela é substituída a cada
 * passo): a mais importante do que aconteceu desde a última verificação.
 */
fun principalDoPipeline(novidades: List<Novidade>): Novidade? =
    novidades.filterIsInstance<Novidade.BolaoFalhou>().firstOrNull()
        ?: novidades.filterIsInstance<Novidade.BolaoPaginaAtualizada>().firstOrNull()
        ?: novidades.firstOrNull { it is Novidade.BolaoAguardandoResultado }
        ?: novidades.filterIsInstance<Novidade.BolaoComecou>().firstOrNull()
