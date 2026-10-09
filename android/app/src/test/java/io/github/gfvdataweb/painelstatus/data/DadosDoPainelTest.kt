package io.github.gfvdataweb.painelstatus.data

import io.github.gfvdataweb.painelstatus.apoio.PainelLocal
import io.github.gfvdataweb.painelstatus.apoio.PainelPublicado
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** Download, cache offline e proteção contra JSON quebrado, com um painel local. */
class DadosDoPainelTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val painel = PainelLocal()
    private var agora = 1_000L

    private fun dados() = DadosDoPainel(
        fonte = FonteRemota(OkHttpClient(), painel.urlDoPainel),
        cache = CacheDeArquivos(pasta.root),
        relogio = { agora },
        io = Dispatchers.Unconfined,
    )

    @After
    fun fecha() = painel.close()

    @Test
    fun primeiraVezBaixaEGuardaNoCache() = runTest {
        val estados = dados().observar(DadosDoPainel.STATUS, ::lerStatus).toList()
        assertEquals(2, estados.size)
        assertNull("sem cache, o primeiro estado não tem dados", estados[0].dados)
        assertTrue(estados[0].atualizando)
        val fim = estados[1]
        assertEquals(PainelPublicado.status, fim.dados)
        assertEquals(1_000L, fim.atualizadoEm)
        assertNull(fim.erro)
        assertTrue(File(pasta.root, DadosDoPainel.STATUS).isFile)
    }

    @Test
    fun semInternetMostraOCacheComAviso() = runTest {
        dados().baixar(DadosDoPainel.STATUS, ::lerStatus)
        painel.foraDoAr = true
        agora = 5_000L
        val fim = dados().observar(DadosDoPainel.STATUS, ::lerStatus).toList().last()
        assertEquals(PainelPublicado.status, fim.dados)
        assertEquals("a idade é a do cache, não a da tentativa", 1_000L, fim.atualizadoEm)
        assertEquals(ErroDeDados.FALHA_NO_DOWNLOAD, fim.erro)
        assertFalse(fim.atualizando)
    }

    @Test
    fun jsonQuebradoNoPainelNaoApagaOCache() = runTest {
        dados().baixar(DadosDoPainel.STATUS, ::lerStatus)
        painel.substituicoes[DadosDoPainel.STATUS] = "{ isto não é json"
        val fim = dados().observar(DadosDoPainel.STATUS, ::lerStatus).toList().last()
        assertEquals(ErroDeDados.FORMATO_INESPERADO, fim.erro)
        assertEquals(PainelPublicado.status, fim.dados)
        // O arquivo do cache continua sendo o bom.
        assertEquals(PainelPublicado.status, dados().doCache(DadosDoPainel.STATUS, ::lerStatus)?.valor)
    }

    @Test
    fun painelForaDoArViraErroDeRede() = runTest {
        painel.foraDoAr = true
        val erro = runCatching { dados().baixar(DadosDoPainel.STATUS, ::lerStatus) }.exceptionOrNull()
        assertTrue(erro is IOException)
        assertEquals(ErroDeDados.FALHA_NO_DOWNLOAD, erro?.let { ErroDeDados.de(it) })
    }

    @Test
    fun historicoSoAceitaAPastaDoPainel() {
        assertTrue(DadosDoPainel.historicoValido("dados/historico/bolao_f1.json"))
        assertFalse(DadosDoPainel.historicoValido(null))
        assertFalse(DadosDoPainel.historicoValido("../segredo.json"))
        assertFalse(DadosDoPainel.historicoValido("dados/historico/../../x.json"))
        assertFalse(DadosDoPainel.historicoValido("https://exemplo.com/dados/historico/x.json"))
    }
}
