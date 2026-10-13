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

/** Aviso de nova versão contra um `app/versao.json` local (o do Worker do painel). */
class VerificadorDeAtualizacaoTest {

    private val painel = MockWebServer().apply { start() }
    private val urlDaVersao = painel.url("/app/versao.json")
    private val verificador = VerificadorDeAtualizacao(OkHttpClient(), urlDaVersao)

    @After
    fun encerra() {
        painel.close()
    }

    private fun versao(apk: String) = """
        {"versao": "0.2.0", "build": 20, "apk": "$apk", "notas": "Notas da versão.",
         "publicado_em": "2026-10-13T12:00:00Z", "campo_novo": 1}
    """.trimIndent()

    @Test
    fun leVersaoEBuildDoNomeDoApkEMontaOEnderecoAoLadoDoVersaoJson() = runTest {
        painel.enqueue(MockResponse(body = versao("painel-status-v0.2.0-build20.apk")))

        val publicada = verificador.maisRecente()

        assertEquals("0.2.0", publicada?.versao)
        assertEquals(20, publicada?.build)
        assertEquals(painel.url("/app/painel-status-v0.2.0-build20.apk").toString(), publicada?.urlDoApk)
        assertEquals("Notas da versão.", publicada?.notas)
        assertEquals("/app/versao.json", painel.takeRequest().url.encodedPath)
    }

    @Test
    fun apkForaDoPadraoOuLinkDeForaNaoViramAviso() = runTest {
        painel.enqueue(MockResponse(body = versao("nome-fora-do-padrao.apk")))
        assertNull(verificador.maisRecente())
        painel.enqueue(MockResponse(body = versao("https://outro.site/painel-status-v9.9.9-build99.apk")))
        assertNull(verificador.maisRecente())
    }

    @Test
    fun falhaNaConsultaNaoAtrapalha() = runTest {
        painel.enqueue(MockResponse(code = 404))
        assertNull(verificador.maisRecente())
        painel.enqueue(MockResponse(body = "isto não é json"))
        assertNull(verificador.maisRecente())
        painel.close()
        assertNull(verificador.maisRecente())
    }

    @Test
    fun soAvisaQuandoOBuildPublicadoEMaior() {
        val publicada = VersaoPublicada("0.2.0", build = 20, urlDoApk = "", notas = "")
        assertTrue(haAtualizacao(publicada, buildInstalado = 12))
        assertFalse(haAtualizacao(publicada, buildInstalado = 20))
        assertFalse(haAtualizacao(publicada, buildInstalado = 31))
        assertFalse(haAtualizacao(null, buildInstalado = 1))
    }
}
