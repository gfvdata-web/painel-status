package io.github.gfvdataweb.painelstatus.data

import io.github.gfvdataweb.painelstatus.apoio.PainelPublicado
import io.github.gfvdataweb.painelstatus.ui.painel.EstadoDaEtapa
import io.github.gfvdataweb.painelstatus.ui.painel.destaqueDe
import io.github.gfvdataweb.painelstatus.ui.painel.estadoDaEtapa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O app entende o que o painel publica de verdade (docs/dados). Se o formato
 * do status.json ou do histórico mudar de um jeito que quebra o app, falha aqui.
 */
class DadosPublicadosTest {

    private val status = PainelPublicado.status

    @Test
    fun statusTemSitesEDataDeGeracao() {
        assertTrue("status.json sem sites", status.sites.isNotEmpty())
        assertNotNull("meta.gerado_em ausente", status.meta.geradoEm)
        assertEquals("slugs repetidos", status.sites.size, status.sites.map { it.slug }.toSet().size)
        status.sites.forEach { site ->
            assertTrue("pages_url de ${site.slug} não é https", site.pagesUrl.startsWith("https://"))
        }
    }

    @Test
    fun haExatamenteUmDestaqueComOPassoAPassoDoBolao() {
        assertEquals(1, status.sites.count { it.destaque })
        val bolao = checkNotNull(destaqueDe(status))
        assertNotNull("destaque sem última execução do pipeline", bolao.ultimaExecucao)
        // Todo estado de etapa publicado é conhecido pelo app (src/etapas_bolao.py).
        bolao.etapasPipeline.forEach { etapa ->
            assertNotEquals("estado de etapa desconhecido: ${etapa.estado}", EstadoDaEtapa.DESCONHECIDO, estadoDaEtapa(etapa.estado))
        }
    }

    @Test
    fun todoHistoricoPublicadoEhLidoEValido() {
        assertTrue("nenhum histórico publicado", PainelPublicado.historicos.isNotEmpty())
        PainelPublicado.historicos.forEach { caminho ->
            assertTrue("caminho fora do padrão: $caminho", DadosDoPainel.historicoValido(caminho))
            val historico = lerHistorico(PainelPublicado.texto(caminho))
            assertTrue("histórico vazio: $caminho", historico.execucoes.isNotEmpty())
        }
    }

    @Test
    fun historicoArquivoDosSitesApontaParaArquivosPublicados() {
        status.sites.mapNotNull { it.historicoArquivo }.forEach { caminho ->
            assertTrue("caminho inválido: $caminho", DadosDoPainel.historicoValido(caminho))
            assertTrue("arquivo não publicado: $caminho", caminho in PainelPublicado.historicos)
        }
    }

    @Test
    fun campoNovoENullNaoQuebramALeitura() {
        val texto = """
            {"meta": {"gerado_em": "2026-10-08T12:00:00+00:00", "campo_novo": 1},
             "sites": [{"slug": "x", "nome": "X", "etapas_pipeline": null, "rodada_mais_recente": null,
                        "ultima_execucao_pipeline": {"status": "in_progress", "conclusao": null}}],
             "forms_avulsos": []}
        """.trimIndent()
        val lido = lerStatus(texto)
        assertEquals("X", lido.sites.single().nome)
        assertTrue(lido.sites.single().etapasPipeline.isEmpty())
    }
}
