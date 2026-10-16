package io.github.gfvdataweb.painelstatus.data

import io.github.gfvdataweb.painelstatus.data.avisos.JanelaLimite
import io.github.gfvdataweb.painelstatus.data.avisos.LIMITE_AVISO_PCT
import io.github.gfvdataweb.painelstatus.data.avisos.NovidadeIa
import io.github.gfvdataweb.painelstatus.data.avisos.novidadesIa
import io.github.gfvdataweb.painelstatus.data.ia.FaixaLimite
import io.github.gfvdataweb.painelstatus.data.ia.LimitesAtuais
import io.github.gfvdataweb.painelstatus.data.ia.LimitesIa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quando os limites do Claude viram notificação: passou de 80% (uma vez por ciclo) e zerou. */
class NovidadesIaTest {

    private val cicloA = 1_792_252_800.0
    private val cicloB = cicloA + 7 * 86_400

    private fun faixa(pct: Double, zeraEm: Double? = cicloA, zerou: Boolean = false) =
        FaixaLimite(usadoPct = pct, zeraEm = zeraEm, lidoEm = 1.0, zerouDesdeALeitura = zerou)

    private fun sete(faixa: FaixaLimite) = LimitesIa(atual = LimitesAtuais(seteDias = faixa))

    private fun cinco(faixa: FaixaLimite) = LimitesIa(atual = LimitesAtuais(cincoHoras = faixa))

    @Test
    fun limiteDeAvisoE80() {
        assertEquals(80.0, LIMITE_AVISO_PCT, 0.0)
    }

    @Test
    fun semEstadoAnteriorNaoAvisaNada() {
        assertTrue(novidadesIa(null, sete(faixa(95.0))).isEmpty())
        assertTrue(novidadesIa(LimitesIa(), sete(faixa(95.0))).isEmpty())
        assertTrue(novidadesIa(sete(faixa(95.0)), LimitesIa()).isEmpty())
    }

    @Test
    fun cruzar80NoMesmoCicloAvisaUmaVezSo() {
        assertEquals(
            listOf(NovidadeIa.LimitePassou80(JanelaLimite.SETE_DIAS)),
            novidadesIa(sete(faixa(79.9)), sete(faixa(80.0))),
        )
        // Já estava acima e continua: nada (uma vez por ciclo).
        assertTrue(novidadesIa(sete(faixa(82.0)), sete(faixa(90.0))).isEmpty())
        // Abaixo e continua abaixo.
        assertTrue(novidadesIa(sete(faixa(10.0)), sete(faixa(79.0))).isEmpty())
    }

    @Test
    fun cicloNovoJaAcimaDe80AvisaDeNovo() {
        assertEquals(
            listOf(NovidadeIa.LimitePassou80(JanelaLimite.SETE_DIAS)),
            novidadesIa(sete(faixa(40.0, cicloA)), sete(faixa(85.0, cicloB))),
        )
    }

    @Test
    fun zerarDepoisDe80AvisaPeloCicloNovo() {
        assertEquals(
            listOf(NovidadeIa.LimiteZerou(JanelaLimite.SETE_DIAS)),
            novidadesIa(sete(faixa(90.0, cicloA)), sete(faixa(3.0, cicloB))),
        )
    }

    @Test
    fun zerarDepoisDe80AvisaPeloZerouDesdeALeitura() {
        assertEquals(
            listOf(NovidadeIa.LimiteZerou(JanelaLimite.CINCO_HORAS)),
            novidadesIa(cinco(faixa(88.0)), cinco(faixa(88.0, zerou = true))),
        )
    }

    @Test
    fun zerouNaoRepeteNemAvisaPassouComLeituraVelha() {
        val jaZerou = cinco(faixa(88.0, zerou = true))
        // Leitura ainda velha, mesmo estado: nada.
        assertTrue(novidadesIa(jaZerou, jaZerou).isEmpty())
        // Depois chega a leitura do ciclo novo: o "zerou" já foi dado.
        assertTrue(novidadesIa(jaZerou, cinco(faixa(2.0, cicloB))).isEmpty())
    }

    @Test
    fun zerarSemTerPassadoDe80NaoAvisa() {
        assertTrue(novidadesIa(sete(faixa(50.0, cicloA)), sete(faixa(1.0, cicloB))).isEmpty())
        assertTrue(novidadesIa(sete(faixa(79.9)), sete(faixa(79.9, zerou = true))).isEmpty())
    }

    @Test
    fun oscilacaoPequenaDoZeraEmNaoEOutroCiclo() {
        assertTrue(novidadesIa(sete(faixa(85.0, cicloA)), sete(faixa(86.0, cicloA + 60))).isEmpty())
    }

    @Test
    fun janelaDeCincoHorasSemZeraEmComecandoUmCiclo() {
        // Parada (zera_em nulo, 0%) → nada.
        assertTrue(novidadesIa(cinco(faixa(0.0, null)), cinco(faixa(0.0, null))).isEmpty())
        // Começou um ciclo novo já alto: avisa.
        assertEquals(
            listOf(NovidadeIa.LimitePassou80(JanelaLimite.CINCO_HORAS)),
            novidadesIa(cinco(faixa(0.0, null)), cinco(faixa(85.0, cicloA))),
        )
    }

    @Test
    fun asDuasJanelasSaoIndependentes() {
        val antes = LimitesIa(atual = LimitesAtuais(cincoHoras = faixa(70.0, cicloA), seteDias = faixa(90.0, cicloA)))
        val depois = LimitesIa(atual = LimitesAtuais(cincoHoras = faixa(81.0, cicloA), seteDias = faixa(2.0, cicloB)))

        assertEquals(
            listOf(NovidadeIa.LimitePassou80(JanelaLimite.CINCO_HORAS), NovidadeIa.LimiteZerou(JanelaLimite.SETE_DIAS)),
            novidadesIa(antes, depois),
        )
    }

    @Test
    fun janelaQueSumiuDoJsonNaoAvisa() {
        val antes = LimitesIa(atual = LimitesAtuais(cincoHoras = faixa(90.0), seteDias = faixa(90.0)))
        assertTrue(novidadesIa(antes, sete(faixa(2.0, cicloB))).filter { it.janela == JanelaLimite.CINCO_HORAS }.isEmpty())
    }
}
