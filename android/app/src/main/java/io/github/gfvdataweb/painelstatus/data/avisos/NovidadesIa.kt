package io.github.gfvdataweb.painelstatus.data.avisos

import io.github.gfvdataweb.painelstatus.data.ia.FaixaLimite
import io.github.gfvdataweb.painelstatus.data.ia.LimitesIa

/** A partir de quantos % de uso o aviso "passou" sai. */
const val LIMITE_AVISO_PCT = 80.0

/** `zera_em` que difere menos que isto (segundos) é o mesmo ciclo (a leitura oscila alguns segundos). */
private const val TOLERANCIA_DO_CICLO_S = 300.0

/** As duas janelas do plano do Claude. */
enum class JanelaLimite { CINCO_HORAS, SETE_DIAS }

/** Aviso sobre os limites do Claude (painel de IA), do mesmo jeito que [Novidade] para o painel de status. */
sealed interface NovidadeIa {
    val janela: JanelaLimite

    /** O uso da [janela] cruzou [LIMITE_AVISO_PCT] no ciclo atual (uma vez por ciclo). */
    data class LimitePassou80(override val janela: JanelaLimite) : NovidadeIa

    /** A [janela] zerou depois de ter passado do limite de aviso. */
    data class LimiteZerou(override val janela: JanelaLimite) : NovidadeIa
}

/**
 * O que mudou nos limites de [anterior] (último visto) para [atual]. Sem
 * [anterior] não há aviso (só se guarda a linha de base). O ciclo de cada
 * janela é identificado pelo `zera_em`: ciclo novo = `zera_em` diferente.
 */
fun novidadesIa(anterior: LimitesIa?, atual: LimitesIa): List<NovidadeIa> {
    val antes = anterior?.atual ?: return emptyList()
    val agora = atual.atual ?: return emptyList()
    return listOf(
        JanelaLimite.CINCO_HORAS to (antes.cincoHoras to agora.cincoHoras),
        JanelaLimite.SETE_DIAS to (antes.seteDias to agora.seteDias),
    ).flatMap { (janela, par) ->
        val (a, b) = par
        if (a == null || b == null) emptyList() else novidadesDaJanela(janela, a, b)
    }
}

private fun novidadesDaJanela(janela: JanelaLimite, antes: FaixaLimite, agora: FaixaLimite): List<NovidadeIa> {
    val cicloNovo = cicloMudou(antes, agora)
    val lista = mutableListOf<NovidadeIa>()
    // Zerou: o ciclo que o app viu tinha passado do limite de aviso e acabou (uma vez só: o
    // último visto já com "zerou_desde_a_leitura" não repete).
    if (antes.usadoPct >= LIMITE_AVISO_PCT && !antes.zerouDesdeALeitura && (agora.zerouDesdeALeitura || cicloNovo)) {
        lista += NovidadeIa.LimiteZerou(janela)
    }
    // Passou: no mesmo ciclo, cruzou o limite; em ciclo novo, já começou acima dele.
    if (agora.usadoPct >= LIMITE_AVISO_PCT && !agora.zerouDesdeALeitura && (cicloNovo || antes.usadoPct < LIMITE_AVISO_PCT)) {
        lista += NovidadeIa.LimitePassou80(janela)
    }
    return lista
}

private fun cicloMudou(antes: FaixaLimite, agora: FaixaLimite): Boolean {
    val a = antes.zeraEm
    val b = agora.zeraEm
    return when {
        a == null && b == null -> false
        a == null || b == null -> true
        else -> kotlin.math.abs(a - b) > TOLERANCIA_DO_CICLO_S
    }
}
