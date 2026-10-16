package io.github.gfvdataweb.painelstatus.data.ia

import io.github.gfvdataweb.painelstatus.apoio.IaFixtures
import io.github.gfvdataweb.painelstatus.apoio.IaLocal
import io.github.gfvdataweb.painelstatus.data.CacheDeArquivos
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Download dos três JSONs com cookie, 401, 404 e cache offline, contra um painel de IA local. */
class FonteIaTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val ia = IaLocal()
    private val cofre = CofreEmMemoria()
    private val cliente = OkHttpClient()
    private val fonte by lazy {
        FonteIa(
            cliente = cliente,
            base = ia.url,
            sessao = SessaoIa(cliente, ia.url, cofre, relogio = { IaFixtures.AGORA_MS }),
            cache = CacheDeArquivos(pasta.root),
            relogio = { IaFixtures.AGORA_MS },
        )
    }

    @After
    fun fecha() = ia.close()

    private fun entrar() = cofre.guardar(ia.cookie)

    @Test
    fun semCookieNemFazPedido() = runTest {
        assertEquals(ResultadoIa.PrecisaEntrar, fonte.carregar())
        assertTrue(ia.pedidos.isEmpty())
    }

    @Test
    fun baixaOsTresArquivosComOCookie() = runTest {
        entrar()

        val resultado = fonte.carregar() as ResultadoIa.Dados

        assertFalse(resultado.desatualizado)
        assertEquals(7, resultado.dados.uso!!.dias.size)
        assertEquals(82.5, resultado.dados.limites!!.atual!!.seteDias!!.usadoPct, 0.0)
        assertEquals("skill-de-teste", resultado.dados.catalogo!!.skills.single().nome)
        assertEquals(
            listOf("GET /dados/uso.json", "GET /dados/limites.json", "GET /dados/catalogo.json"),
            ia.pedidos.toList(),
        )
        assertEquals(List(3) { "painel_ia=${ia.cookie}" }, ia.cookiesRecebidos.toList())
    }

    @Test
    fun cookie401ApagaSessaoECacheEPedeSenha() = runTest {
        entrar()
        fonte.carregar() // enche o cache
        assertNotNull(fonte.doCache())
        ia.substituicoes["uso.json"] = MockResponse(code = 401)

        assertEquals(ResultadoIa.PrecisaEntrar, fonte.carregar())

        assertNull(cofre.ler())
        assertNull(fonte.doCache())
        assertNull(CacheDeArquivos(pasta.root).ler("dados/uso.json"))
    }

    @Test
    fun cookieVencidoNaoFazPedidoEApagaOCookie() = runTest {
        cofre.guardar("${IaFixtures.AGORA_MS - 1}.assinatura")

        assertEquals(ResultadoIa.PrecisaEntrar, fonte.carregar())

        assertTrue(ia.pedidos.isEmpty())
        assertNull(cofre.ler())
    }

    @Test
    fun semInternetUsaOUltimoDadoGuardado() = runTest {
        entrar()
        fonte.carregar()
        ia.foraDoAr = true

        val resultado = fonte.carregar() as ResultadoIa.Dados

        assertTrue(resultado.desatualizado)
        assertEquals(7, resultado.dados.uso!!.dias.size)
        assertNotNull(resultado.dados.catalogo)
        assertNotNull(cofre.ler()) // falha de rede não derruba a sessão
    }

    @Test
    fun semInternetENadaGuardadoViraErro() = runTest {
        entrar()
        ia.foraDoAr = true

        assertEquals(ResultadoIa.Erro, fonte.carregar())
    }

    @Test
    fun jsonQuebradoNaoApagaOCache() = runTest {
        entrar()
        fonte.carregar()
        ia.substituicoes["limites.json"] = MockResponse(body = "isto não é json")

        val resultado = fonte.carregar() as ResultadoIa.Dados

        assertTrue(resultado.desatualizado)
        assertEquals(82.5, resultado.dados.limites!!.atual!!.seteDias!!.usadoPct, 0.0)
    }

    @Test
    fun arquivoAindaNaoPublicadoFicaNulo() = runTest {
        entrar()
        ia.substituicoes["catalogo.json"] = MockResponse(code = 404)

        val resultado = fonte.carregar() as ResultadoIa.Dados

        assertNull(resultado.dados.catalogo)
        assertNotNull(resultado.dados.uso)
    }

    @Test
    fun sairApagaCookieEDadosGuardados() = runTest {
        entrar()
        fonte.carregar()

        fonte.sair()

        assertFalse(fonte.logada)
        assertNull(cofre.ler())
        assertNull(CacheDeArquivos(pasta.root).ler("dados/limites.json"))
    }

    @Test
    fun loginPelaFonteGuardaOCookie() = runTest {
        assertEquals(ResultadoLogin.ENTROU, fonte.entrar("segredo"))
        assertTrue(fonte.logada)
    }
}
