package io.github.gfvdataweb.painelstatus.data.ia

import io.github.gfvdataweb.painelstatus.apoio.IaFixtures
import io.github.gfvdataweb.painelstatus.apoio.IaLocal
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Login do painel de IA contra um Worker local: cookie guardado, senha nunca. */
class SessaoIaTest {

    private val ia = IaLocal()
    private val cofre = CofreEmMemoria()
    private var agora = IaFixtures.AGORA_MS
    private val sessao = SessaoIa(OkHttpClient(), ia.url, cofre, relogio = { agora })

    @After
    fun fecha() = ia.close()

    @Test
    fun senhaCertaGuardaSoOCookie() = runTest {
        assertEquals(ResultadoLogin.ENTROU, sessao.entrar("segredo"))

        assertEquals(ia.cookie, cofre.ler())
        assertFalse(cofre.ler()!!.contains("segredo"))
        assertEquals("painel_ia=${ia.cookie}", sessao.cabecalhoCookie())
        assertTrue(sessao.logada)
        assertEquals(listOf("POST /entrar"), ia.pedidos) // o 303 não foi seguido
    }

    @Test
    fun senhaErradaNaoGuardaNada() = runTest {
        assertEquals(ResultadoLogin.SENHA_ERRADA, sessao.entrar("errada"))

        assertNull(cofre.ler())
        assertFalse(sessao.logada)
        assertEquals(listOf("POST /entrar"), ia.pedidos)
    }

    @Test
    fun painelForaDoArViraIOException() = runTest {
        ia.foraDoAr = true

        try {
            sessao.entrar("segredo")
            fail("devia lançar IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("503"))
        }
        assertNull(cofre.ler())
    }

    @Test
    fun cookieVencidoEApagadoEPedeLoginDeNovo() {
        cofre.guardar("${agora - 1}.assinatura")

        assertNull(sessao.cookie())
        assertNull(cofre.ler())
    }

    @Test
    fun cookieEmFormatoEstranhoEDescartado() {
        cofre.guardar("lixo-sem-ponto")

        assertNull(sessao.cookie())
        assertNull(cofre.ler())
    }

    @Test
    fun sairApagaOCookie() {
        cofre.guardar(ia.cookie)
        assertTrue(sessao.logada)

        sessao.sair()

        assertFalse(sessao.logada)
        assertNull(cofre.ler())
    }

    @Test
    fun valorDoCookieLeSoOCookieDoPainel() {
        assertEquals("123.abc", SessaoIa.valorDoCookie("painel_ia=123.abc; Path=/; HttpOnly; Secure"))
        assertNull(SessaoIa.valorDoCookie("outro=1; Path=/"))
        assertNull(SessaoIa.valorDoCookie("painel_ia=; Path=/; Max-Age=0"))
        assertEquals(123L, SessaoIa.expiraEm("123.abc"))
        assertNull(SessaoIa.expiraEm("123"))
        assertNull(SessaoIa.expiraEm("x.abc"))
    }
}
