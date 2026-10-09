package io.github.gfvdataweb.painelstatus.data

import io.github.gfvdataweb.painelstatus.data.avisos.ArquivoDePreferencias
import io.github.gfvdataweb.painelstatus.data.avisos.Novidade
import io.github.gfvdataweb.painelstatus.data.avisos.PreferenciasDeAvisos
import io.github.gfvdataweb.painelstatus.data.avisos.VigiaDoPainel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Referência do "último visto" e preferências gravadas em arquivo. */
class VigiaDoPainelTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private var texto = statusDoBolao(etapaPagina = "pendente")

    private fun statusDoBolao(etapaPagina: String) = """
        {"meta": {"gerado_em": "2026-10-09T12:00:00Z"},
         "sites": [{"slug": "bolao_f1", "nome": "Bolão F1", "destaque": true, "rodada_mais_recente": 19,
                    "ultima_execucao_pipeline": {"id": 7, "status": "in_progress"},
                    "etapas_pipeline": [{"chave": "pagina", "titulo": "Página atualizada", "estado": "$etapaPagina"}]}]}
    """.trimIndent()

    private fun vigia(): VigiaDoPainel {
        val dados = DadosDoPainel(
            fonte = { texto },
            cache = CacheDeArquivos(pasta.newFolder()),
            relogio = { 1_000L },
            io = Dispatchers.Unconfined,
        )
        return VigiaDoPainel(dados, CacheDeArquivos(pastaDosAvisos), relogio = { 5_000L }, io = Dispatchers.Unconfined)
    }

    private val pastaDosAvisos by lazy { pasta.newFolder("avisos") }

    @Test
    fun primeiraVerificacaoSoGuardaAReferencia() = runTest {
        val vigia = vigia()
        assertNull(vigia.ultimaVerificacao())
        assertTrue(vigia.verificar().novidades.isEmpty())
        assertEquals(5_000L, vigia.ultimaVerificacao())
    }

    @Test
    fun segundaVerificacaoAvisaOQueMudou() = runTest {
        vigia().verificar()
        texto = statusDoBolao(etapaPagina = "ok")
        assertEquals(listOf(Novidade.BolaoPaginaAtualizada(19)), vigia().verificar().novidades)
        // Já avisado: a próxima não repete.
        assertTrue(vigia().verificar().novidades.isEmpty())
    }

    @Test
    fun oQueATelaViuNaoViraAviso() = runTest {
        vigia().verificar()
        texto = statusDoBolao(etapaPagina = "ok")
        vigia().marcarComoVisto(lerStatus(texto))
        assertTrue(vigia().verificar().novidades.isEmpty())
    }

    @Test
    fun preferenciasSobrevivemAoReinicio() {
        val pastaDePreferencias = CacheDeArquivos(pastaDosAvisos)
        assertEquals(PreferenciasDeAvisos(), ArquivoDePreferencias(pastaDePreferencias).atuais.value)
        ArquivoDePreferencias(pastaDePreferencias).salvar(PreferenciasDeAvisos(ativado = true, sites = false, perguntado = true))
        assertEquals(
            PreferenciasDeAvisos(ativado = true, sites = false, perguntado = true),
            ArquivoDePreferencias(pastaDePreferencias).atuais.value,
        )
    }
}
