package io.github.gfvdataweb.painelstatus.data

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

/** Aviso de nova versão (9f) contra uma "API do GitHub" local. */
class VerificadorDeAtualizacaoTest {

    private val github = MockWebServer().apply { start() }
    private val verificador = VerificadorDeAtualizacao(OkHttpClient(), github.url("/repos/x/y/releases"))

    @After
    fun encerra() {
        github.close()
    }

    private fun release(tag: String, apk: String, rascunho: Boolean = false, preRelease: Boolean = false) = """
        {"tag_name": "$tag", "html_url": "https://github.com/x/y/releases/tag/$tag",
         "draft": $rascunho, "prerelease": $preRelease, "campo_novo": 1,
         "assets": [{"name": "$apk", "browser_download_url": "https://github.com/x/y/releases/download/$tag/$apk"}]}
    """.trimIndent()

    @Test
    fun escolheOMaiorBuildEntreOsReleasesDoApp() = runTest {
        val lista = listOf(
            release("app-v0.1.0", "painel-status-v0.1.0-build12.apk"),
            release("app-v0.2.0", "painel-status-v0.2.0-build20.apk"),
            release("app-v0.3.0", "painel-status-v0.3.0-build25.apk", rascunho = true),
            release("app-v0.4.0", "painel-status-v0.4.0-build30.apk", preRelease = true),
            release("outra-coisa", "painel-status-v9.9.9-build99.apk"),
            release("app-v0.5.0", "nome-fora-do-padrao.apk"),
        )
        github.enqueue(MockResponse(body = lista.joinToString(prefix = "[", postfix = "]")))

        val publicada = verificador.maisRecente()

        assertEquals("0.2.0", publicada?.versao)
        assertEquals(20, publicada?.build)
        assertEquals("https://github.com/x/y/releases/download/app-v0.2.0/painel-status-v0.2.0-build20.apk", publicada?.urlDoApk)
        assertEquals("application/vnd.github+json", github.takeRequest().headers["Accept"])
    }

    @Test
    fun falhaNaConsultaNaoAtrapalha() = runTest {
        github.enqueue(MockResponse(code = 403, body = """{"message":"API rate limit exceeded"}"""))
        assertNull(verificador.maisRecente())
        github.enqueue(MockResponse(body = "isto não é json"))
        assertNull(verificador.maisRecente())
        github.close()
        assertNull(verificador.maisRecente())
    }

    @Test
    fun soAvisaQuandoOBuildPublicadoEMaior() {
        val publicada = VersaoPublicada("0.2.0", build = 20, urlDoApk = "", urlDaPagina = "")
        assertTrue(haAtualizacao(publicada, buildInstalado = 12))
        assertFalse(haAtualizacao(publicada, buildInstalado = 20))
        assertFalse(haAtualizacao(publicada, buildInstalado = 31))
        assertFalse(haAtualizacao(null, buildInstalado = 1))
    }
}
