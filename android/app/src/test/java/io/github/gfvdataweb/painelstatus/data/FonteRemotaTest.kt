package io.github.gfvdataweb.painelstatus.data

import io.github.gfvdataweb.painelstatus.apoio.PainelLocal
import io.github.gfvdataweb.painelstatus.apoio.PainelPublicado
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Endereço novo (Cloudflare) com o antigo (GitHub Pages) de reserva, com dois painéis locais. */
class FonteRemotaTest {

    private val principal = PainelLocal()
    private val reserva = PainelLocal()
    private val fonte = FonteRemota(OkHttpClient(), principal.urlDoPainel, reserva.urlDoPainel)

    @After
    fun fecha() {
        principal.close()
        reserva.close()
    }

    @Test
    fun usaOPrincipalQuandoEleResponde() {
        principal.substituicoes[DadosDoPainel.STATUS] = "principal"

        assertEquals("principal", fonte.baixarTexto(DadosDoPainel.STATUS))
        assertEquals(0, reserva.pedidosDoStatus.get())
    }

    @Test
    fun principalForaDoArUsaAReservaComOMesmoCaminho() {
        principal.foraDoAr = true
        val historico = PainelPublicado.historicos.first()

        assertEquals(PainelPublicado.texto(DadosDoPainel.STATUS), fonte.baixarTexto(DadosDoPainel.STATUS))
        assertEquals(PainelPublicado.texto(historico), fonte.baixarTexto(historico))
        assertEquals(1, reserva.pedidosDoStatus.get())
    }

    @Test
    fun osDoisForaDoArValeOErroDoPrincipal() {
        principal.foraDoAr = true
        reserva.foraDoAr = true

        val erro = assertThrows(IOException::class.java) { fonte.baixarTexto(DadosDoPainel.STATUS) }
        assertTrue(erro.message!!.contains("503"))
        assertEquals(1, erro.suppressed.size)
    }

    @Test
    fun semReservaOErroSobe() {
        principal.foraDoAr = true
        val semReserva = FonteRemota(OkHttpClient(), principal.urlDoPainel)

        assertThrows(IOException::class.java) { semReserva.baixarTexto(DadosDoPainel.STATUS) }
    }
}
