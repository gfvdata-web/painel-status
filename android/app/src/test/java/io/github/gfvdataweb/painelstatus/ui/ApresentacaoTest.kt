package io.github.gfvdataweb.painelstatus.ui

import io.github.gfvdataweb.painelstatus.data.modelo.Alerta
import io.github.gfvdataweb.painelstatus.data.modelo.Aviso
import io.github.gfvdataweb.painelstatus.data.modelo.Etapa
import io.github.gfvdataweb.painelstatus.data.modelo.Execucao
import io.github.gfvdataweb.painelstatus.data.modelo.FormAvulso
import io.github.gfvdataweb.painelstatus.data.modelo.Historico
import io.github.gfvdataweb.painelstatus.data.modelo.Site
import io.github.gfvdataweb.painelstatus.data.modelo.Status
import io.github.gfvdataweb.painelstatus.ui.comum.Duracao
import io.github.gfvdataweb.painelstatus.ui.comum.Idade
import io.github.gfvdataweb.painelstatus.ui.comum.dataHora
import io.github.gfvdataweb.painelstatus.ui.comum.diasDesde
import io.github.gfvdataweb.painelstatus.ui.comum.duracao
import io.github.gfvdataweb.painelstatus.ui.comum.idadeDosDados
import io.github.gfvdataweb.painelstatus.ui.painel.SituacaoDaExecucao
import io.github.gfvdataweb.painelstatus.ui.painel.Tom
import io.github.gfvdataweb.painelstatus.ui.painel.atalhosDosForms
import io.github.gfvdataweb.painelstatus.ui.painel.atalhosDosSites
import io.github.gfvdataweb.painelstatus.ui.painel.fluxoEmAndamento
import io.github.gfvdataweb.painelstatus.ui.painel.linkSeguro
import io.github.gfvdataweb.painelstatus.ui.painel.mediana
import io.github.gfvdataweb.painelstatus.ui.painel.resultadoDe
import io.github.gfvdataweb.painelstatus.ui.painel.resumir
import io.github.gfvdataweb.painelstatus.ui.painel.semProblemas
import io.github.gfvdataweb.painelstatus.ui.painel.situacaoDaExecucao
import io.github.gfvdataweb.painelstatus.ui.painel.tomDoResultado
import io.github.gfvdataweb.painelstatus.ui.painel.tomDosAlertas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Regras de exibição (as mesmas de docs/js/app.js), sem Android. */
class ApresentacaoTest {

    private val bolao = Site(slug = "bolao_f1", nome = "Bolão F1", destaque = true)

    @Test
    fun tomDoResultadoSegueAPagina() {
        assertEquals(Tom.OK, tomDoResultado("success"))
        assertEquals(Tom.ERRO, tomDoResultado("failure"))
        assertEquals(Tom.ERRO, tomDoResultado("timed_out"))
        assertEquals(Tom.AVISO, tomDoResultado("in_progress"))
        assertEquals(Tom.AVISO, tomDoResultado("cancelled"))
        assertEquals(Tom.NEUTRO, tomDoResultado("skipped"))
        assertEquals(Tom.NEUTRO, tomDoResultado(null))
    }

    @Test
    fun resultadoUsaOStatusEnquantoRoda() {
        assertEquals("in_progress", resultadoDe(Execucao(status = "in_progress", conclusao = null)))
        assertEquals("failure", resultadoDe(Execucao(status = "completed", conclusao = "failure")))
        // Histórico acumulado: sem status, só concluídas.
        assertEquals("success", resultadoDe(Execucao(conclusao = "success")))
    }

    @Test
    fun seloDaExecucao() {
        assertEquals(SituacaoDaExecucao.SEM_REGISTRO, situacaoDaExecucao(null))
        assertEquals(SituacaoDaExecucao.SEM_REGISTRO, situacaoDaExecucao(Execucao(erro = "HTTP 500")))
        assertEquals(SituacaoDaExecucao.OK, situacaoDaExecucao(Execucao(status = "completed", conclusao = "success")))
        assertEquals(SituacaoDaExecucao.FALHOU, situacaoDaExecucao(Execucao(status = "completed", conclusao = "failure")))
        assertEquals(SituacaoDaExecucao.EM_ANDAMENTO, situacaoDaExecucao(Execucao(status = "in_progress")))
        assertEquals(SituacaoDaExecucao.OUTRA, situacaoDaExecucao(Execucao(status = "completed", conclusao = "cancelled")))
        // Vigília do resultado tem prioridade, como na página.
        assertEquals(SituacaoDaExecucao.VIGILIA, situacaoDaExecucao(Execucao(status = "in_progress"), emVigilia = true))
    }

    @Test
    fun fluxoEmAndamentoDoBolao() {
        val concluido = bolao.copy(
            ultimaExecucao = Execucao(status = "completed", conclusao = "success"),
            etapasPipeline = listOf(Etapa(estado = "ok"), Etapa(estado = "ok")),
        )
        assertFalse(fluxoEmAndamento(concluido))
        assertTrue(fluxoEmAndamento(concluido.copy(ultimaExecucao = Execucao(status = "in_progress"))))
        assertTrue(fluxoEmAndamento(concluido.copy(emVigilia = true)))
        assertTrue(fluxoEmAndamento(concluido.copy(etapasPipeline = listOf(Etapa(estado = "ok"), Etapa(estado = "aguardando")))))
        // API fora do ar não conta como "rodando".
        assertFalse(fluxoEmAndamento(concluido.copy(ultimaExecucao = Execucao(erro = "HTTP 502"))))
    }

    @Test
    fun alertasViramUmIcone() {
        assertNull(tomDosAlertas(emptyList()))
        assertEquals(Tom.AVISO, tomDosAlertas(listOf(Alerta("aviso", "a"))))
        assertEquals(Tom.ERRO, tomDosAlertas(listOf(Alerta("aviso", "a"), Alerta("erro", "b"))))
    }

    @Test
    fun linksSoAbremEnderecosWeb() {
        assertEquals("https://x.io/", linkSeguro("https://x.io/"))
        assertNull(linkSeguro("javascript:alert(1)"))
        assertNull(linkSeguro("intent://x"))
        assertNull(linkSeguro(null))
    }

    @Test
    fun atalhosComDestaquePrimeiroEFormsAvulsosNoFim() {
        val status = Status(
            sites = listOf(
                Site(slug = "a", nome = "A", pagesUrl = "https://a/", formUrl = "https://forms.gle/a"),
                bolao.copy(pagesUrl = "https://b/", formUrl = "https://forms.gle/b", formRotulo = "Enviar palpites"),
                Site(slug = "c", nome = "C", pagesUrl = "https://c/"),
            ),
            formsAvulsos = listOf(FormAvulso("Notas fiscais", "https://forms.gle/n")),
        )
        assertEquals(listOf("Bolão F1", "A", "C"), atalhosDosSites(status).map { it.titulo })
        val forms = atalhosDosForms(status)
        assertEquals(listOf("https://forms.gle/b", "https://forms.gle/a", "https://forms.gle/n"), forms.map { it.url })
        assertEquals("Enviar palpites", forms[0].titulo)
        assertNull("sem rótulo = padrão da tela", forms[1].titulo)
        assertEquals("A", forms[1].detalhe)
        assertNull("form avulso não tem site", forms[2].detalhe)
    }

    @Test
    fun resumoDoHistoricoSeparaAvisosRecorrentes() {
        val node = Aviso("notice", "Node.js 20 está depreciado")
        val execucoes = listOf(
            Execucao(conclusao = "success", duracaoS = 60, avisos = listOf(node), criadoEm = "2026-10-05T00:00:00Z"),
            Execucao(conclusao = "failure", duracaoS = 10, avisos = listOf(node), criadoEm = "2026-10-04T00:00:00Z"),
            Execucao(conclusao = "success", duracaoS = 120, avisos = listOf(node, Aviso("warning", "falta um arquivo"))),
            Execucao(conclusao = "success", duracaoS = 90, avisos = null, criadoEm = "2026-10-01T00:00:00Z"),
        )
        val resumo = resumir(Historico(execucoes = execucoes))
        assertEquals(4, resumo.total)
        assertEquals(3, resumo.sucessos)
        assertEquals(75, resumo.percentualDeSucesso)
        assertEquals(90L, resumo.duracaoTipicaS)
        assertEquals("2026-10-01T00:00:00Z", resumo.maisAntiga)
        assertEquals(3, resumo.lidas)
        assertEquals(mapOf(node.mensagem to 3), resumo.recorrentes)
        // Filtro "só com falha ou aviso": some só o sucesso sem aviso próprio.
        assertTrue(semProblemas(execucoes[0], resumo.recorrentes))
        assertFalse(semProblemas(execucoes[1], resumo.recorrentes))
        assertFalse(semProblemas(execucoes[2], resumo.recorrentes))
    }

    @Test
    fun medianaPegaOElementoDoMeioComoAPagina() {
        assertNull(mediana(emptyList()))
        assertEquals(5L, mediana(listOf(5)))
        assertEquals(20L, mediana(listOf(30, 10, 20)))
        assertEquals(30L, mediana(listOf(40, 10, 30, 20)))
    }

    @Test
    fun datasNoFusoDoCelular() {
        val sp = ZoneId.of("America/Sao_Paulo")
        assertEquals("03/10/26 09:31", dataHora("2026-10-03T12:31:27Z", sp))
        assertEquals("04/10/26 13:21", dataHora("2026-10-04T16:21:01.349155+00:00", sp))
        assertNull(dataHora("ontem", sp))
        assertNull(dataHora(null, sp))
    }

    @Test
    fun diasComoAPagina() {
        val agora = Instant.parse("2026-10-08T12:00:00Z")
        assertEquals(0L, diasDesde("2026-10-08T01:00:00Z", agora))
        assertEquals(1L, diasDesde("2026-10-07T01:00:00Z", agora))
        assertEquals(5L, diasDesde("2026-10-03T11:00:00Z", agora))
        assertEquals("data no futuro conta como hoje", 0L, diasDesde("2026-10-09T00:00:00Z", agora))
        assertNull(diasDesde(null, agora))
    }

    @Test
    fun idadeEDuracao() {
        val fuso = ZoneId.of("UTC")
        val agora = 1_800_000_000_000L
        assertEquals(Idade.AgoraMesmo, idadeDosDados(agora, agora - 30_000, fuso))
        assertEquals(Idade.Minutos(5), idadeDosDados(agora, agora - 5 * 60_000, fuso))
        assertEquals(Idade.Horas(2), idadeDosDados(agora, agora - 2 * 3_600_000, fuso))
        assertEquals(Duracao(1, 30, 15), duracao(5415))
        assertEquals(Duracao(0, 0, 40), duracao(40))
    }
}
