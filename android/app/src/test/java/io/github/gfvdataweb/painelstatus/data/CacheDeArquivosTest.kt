package io.github.gfvdataweb.painelstatus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheDeArquivosTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val cache by lazy { CacheDeArquivos(pasta.root) }

    @Test
    fun arquivoAusenteDevolveNull() {
        assertNull(cache.ler("dados/status.json"))
    }

    @Test
    fun salvaELeComDataDoSalvamento() {
        cache.salvar("dados/status.json", "{\"a\":1}", salvoEm = 1_700_000_000_000)
        val lido = cache.ler("dados/status.json")
        assertEquals("{\"a\":1}", lido?.texto)
        assertEquals(1_700_000_000_000, lido?.salvoEm)
    }

    @Test
    fun sobrescreveOConteudoAnterior() {
        cache.salvar("seasons.json", "velho", salvoEm = 1_000_000)
        cache.salvar("seasons.json", "novo", salvoEm = 2_000_000)
        assertEquals("novo", cache.ler("seasons.json")?.texto)
    }

    @Test(expected = IllegalArgumentException::class)
    fun recusaCaminhoForaDaPasta() {
        cache.salvar("../fora.json", "x", salvoEm = 1_000_000)
    }
}
