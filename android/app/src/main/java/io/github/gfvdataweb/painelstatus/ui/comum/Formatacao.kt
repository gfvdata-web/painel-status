package io.github.gfvdataweb.painelstatus.ui.comum

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Formatação pura (testada em JVM). O app é em português, independente do
// idioma do celular.

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm", PT_BR)
private val DIA_MES_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm", PT_BR)

/** ISO do painel (`2026-10-03T12:31:27Z` ou `…+00:00`) → instante; null se não der para ler. */
fun instante(iso: String?): Instant? =
    iso?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

/** `2026-10-03T12:31:27Z` → `03/10/26 09:31` no fuso do celular; null sem data válida. */
fun dataHora(iso: String?, fuso: ZoneId): String? = instante(iso)?.atZone(fuso)?.format(DATA_HORA)

/** Dias inteiros desde [iso], como a página (0 = nas últimas 24 h); null sem data válida. */
fun diasDesde(iso: String?, agora: Instant): Long? =
    instante(iso)?.let { Duration.between(it, agora).toDays().coerceAtLeast(0) }

/** Idade dos dados exibidos, para a linha "conferido há…". */
sealed interface Idade {
    data object AgoraMesmo : Idade
    data class Minutos(val quantos: Long) : Idade
    data class Horas(val quantas: Long) : Idade
    /** Mais de um dia: mostra a data e hora (`13/09 18:40`). */
    data class EmData(val texto: String) : Idade
}

fun idadeDosDados(agora: Long, quando: Long, fuso: ZoneId): Idade {
    val minutos = (agora - quando).coerceAtLeast(0) / 60_000
    return when {
        minutos < 1 -> Idade.AgoraMesmo
        minutos < 60 -> Idade.Minutos(minutos)
        minutos < 24 * 60 -> Idade.Horas(minutos / 60)
        else -> Idade.EmData(Instant.ofEpochMilli(quando).atZone(fuso).format(DIA_MES_HORA))
    }
}

/** Duração de uma execução quebrada em partes; a tela escolhe o texto ("2 h 5 min", "3 min 10 s", "40 s"). */
data class Duracao(val horas: Long, val minutos: Long, val segundos: Long)

fun duracao(segundos: Long): Duracao {
    val total = segundos.coerceAtLeast(0)
    return Duracao(total / 3600, total % 3600 / 60, total % 60)
}
