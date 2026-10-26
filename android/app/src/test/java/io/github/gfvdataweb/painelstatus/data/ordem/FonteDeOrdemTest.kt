package io.github.gfvdataweb.painelstatus.data.ordem

import io.github.gfvdataweb.painelstatus.data.ia.CofreEmMemoria
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cliente da ordem dos cards contra o contrato do Worker (/api/login e /api/ordem?alvo=app). */
class FonteDeOrdemTest {

    private val servidor = MockWebServer().apply { start() }
    private val cofre = CofreEmMemoria()
    private val fonte = FonteDeOrdem(OkHttpClient(), servidor.url("/"), cofre)

    @After
    fun fecha() = servidor.close()

    @Test
    fun carregarLeAsSlugsNaOrdemSalva() = runTest {
        servidor.enqueue(MockResponse(code = 200, body = """{"slugs":["b","a"],"atualizado_em":"2026-10-26T10:00:00.000Z"}"""))

        assertEquals(listOf("b", "a"), fonte.carregar())

        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/api/ordem", pedido.url.encodedPath)
        assertEquals("app", pedido.url.queryParameter("alvo"))
    }

    @Test
    fun carregarSemOrdemSalvaDaListaVazia() = runTest {
        servidor.enqueue(MockResponse(code = 200, body = """{"slugs":[],"atualizado_em":null}"""))

        assertEquals(emptyList<String>(), fonte.carregar())
    }

    @Test
    fun carregarComErroOuLixoNaoQuebraENaoTemOrdem() = runTest {
        servidor.enqueue(MockResponse(code = 503))
        assertEquals(emptyList<String>(), fonte.carregar())

        servidor.enqueue(MockResponse(code = 200, body = "isto não é JSON"))
        assertEquals(emptyList<String>(), fonte.carregar())
    }

    @Test
    fun carregarComRedeForaDoArNaoQuebra() = runTest {
        val morto = MockWebServer().apply { start() }
        val url = morto.url("/")
        morto.close()

        assertEquals(emptyList<String>(), FonteDeOrdem(OkHttpClient(), url, cofre).carregar())
    }

    @Test
    fun entrarGuardaSoOTokenENuncaASenha() = runTest {
        servidor.enqueue(MockResponse(code = 200, body = """{"ok":true,"token":"123.abc","expira":"2027-01-24T00:00:00.000Z"}"""))

        assertEquals(ResultadoEntrada.ENTROU, fonte.entrar("gui", "segredo"))

        assertEquals("123.abc", cofre.ler())
        assertTrue(fonte.logada)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/login", pedido.url.encodedPath)
        assertTrue(pedido.body.toString().contains("segredo")) // vai no pedido, mas não fica no aparelho
    }

    @Test
    fun entrarComSenhaErradaNaoGuardaNada() = runTest {
        servidor.enqueue(MockResponse(code = 401, body = """{"ok":false}"""))

        assertEquals(ResultadoEntrada.INCORRETO, fonte.entrar("gui", "errada"))

        assertNull(cofre.ler())
        assertFalse(fonte.logada)
    }

    @Test
    fun entrarComLoginNaoConfiguradoOuSemRedeFalhaSemQuebrar() = runTest {
        servidor.enqueue(MockResponse(code = 503))
        assertEquals(ResultadoEntrada.FALHOU, fonte.entrar("gui", "segredo"))

        val morto = MockWebServer().apply { start() }
        val url = morto.url("/")
        morto.close()
        assertEquals(ResultadoEntrada.FALHOU, FonteDeOrdem(OkHttpClient(), url, cofre).entrar("gui", "segredo"))
        assertNull(cofre.ler())
    }

    @Test
    fun salvarEnviaOTokenEAsSlugs() = runTest {
        cofre.guardar("123.abc")
        servidor.enqueue(MockResponse(code = 200, body = """{"ok":true}"""))

        assertEquals(ResultadoSalvar.SALVOU, fonte.salvar(listOf("b", "a")))

        val pedido = servidor.takeRequest()
        assertEquals("PUT", pedido.method)
        assertEquals("app", pedido.url.queryParameter("alvo"))
        assertEquals("Bearer 123.abc", pedido.headers["Authorization"])
        assertTrue(pedido.body.toString().contains("\"slugs\":[\"b\",\"a\"]"))
    }

    @Test
    fun salvarComTokenRecusadoEsqueceOTokenEPedeLoginDeNovo() = runTest {
        cofre.guardar("123.abc")
        servidor.enqueue(MockResponse(code = 401))

        assertEquals(ResultadoSalvar.SESSAO_EXPIRADA, fonte.salvar(listOf("a")))

        assertNull(cofre.ler())
        assertFalse(fonte.logada)
    }

    @Test
    fun salvarSemTokenNemChamaARede() = runTest {
        assertEquals(ResultadoSalvar.SESSAO_EXPIRADA, fonte.salvar(listOf("a")))

        assertEquals(0, servidor.requestCount)
    }

    @Test
    fun salvarComRedeForaDoArOuErroDoServidorFalhaSemApagarOToken() = runTest {
        cofre.guardar("123.abc")
        servidor.enqueue(MockResponse(code = 500))
        assertEquals(ResultadoSalvar.FALHOU, fonte.salvar(listOf("a")))

        val morto = MockWebServer().apply { start() }
        val url = morto.url("/")
        morto.close()
        assertEquals(ResultadoSalvar.FALHOU, FonteDeOrdem(OkHttpClient(), url, cofre).salvar(listOf("a")))
        assertEquals("123.abc", cofre.ler())
    }

    @Test
    fun sairApagaOToken() {
        cofre.guardar("123.abc")

        fonte.sair()

        assertFalse(fonte.logada)
    }
}
