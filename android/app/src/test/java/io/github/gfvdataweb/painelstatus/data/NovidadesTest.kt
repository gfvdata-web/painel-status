package io.github.gfvdataweb.painelstatus.data

import io.github.gfvdataweb.painelstatus.apoio.PainelPublicado
import io.github.gfvdataweb.painelstatus.data.avisos.Novidade
import io.github.gfvdataweb.painelstatus.data.avisos.doBolao
import io.github.gfvdataweb.painelstatus.data.avisos.novidades
import io.github.gfvdataweb.painelstatus.data.avisos.principalDoPipeline
import io.github.gfvdataweb.painelstatus.data.modelo.Alerta
import io.github.gfvdataweb.painelstatus.data.modelo.Etapa
import io.github.gfvdataweb.painelstatus.data.modelo.Execucao
import io.github.gfvdataweb.painelstatus.data.modelo.Site
import io.github.gfvdataweb.painelstatus.data.modelo.SiteNoAr
import io.github.gfvdataweb.painelstatus.data.modelo.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O que vira notificação ao comparar dois status.json. */
class NovidadesTest {

    private fun etapas(vararg estados: Pair<String, String>) =
        estados.map { (chave, estado) -> Etapa(chave = chave, titulo = "Etapa $chave", estado = estado) }

    private fun bolao(
        id: Long?,
        status: String = "completed",
        conclusao: String? = "success",
        evento: String = "repository_dispatch",
        etapas: List<Etapa> = emptyList(),
        noAr: Boolean = true,
        rodada: Int? = 18,
    ) = Site(
        slug = "bolao_f1",
        nome = "Bolão F1",
        destaque = true,
        ultimaExecucao = Execucao(id = id, status = status, conclusao = conclusao, evento = evento),
        etapasPipeline = etapas,
        siteNoAr = SiteNoAr(ok = noAr),
        rodadaMaisRecente = rodada,
    )

    private fun site(vararg alertas: Alerta) = Site(slug = "chess", nome = "Chess Tracking", alertas = alertas.toList())

    private fun status(vararg sites: Site) = Status(sites = sites.toList())

    private val concluido = bolao(
        id = 1,
        etapas = etapas("forms" to "ok", "leitura" to "ok", "resultado" to "ok", "pontuacao" to "ok", "pagina" to "ok"),
    )

    @Test
    fun nadaMudouNadaAvisa() {
        val real = PainelPublicado.status
        assertTrue(novidades(real, real).isEmpty())
        assertTrue(novidades(status(concluido, site()), status(concluido, site())).isEmpty())
    }

    @Test
    fun fluxoDoBolaoPassoAPasso() {
        val comecou = bolao(
            id = 2,
            status = "in_progress",
            conclusao = null,
            etapas = etapas("forms" to "ok", "leitura" to "andamento", "resultado" to "pendente", "pagina" to "pendente"),
        )
        assertEquals(listOf(Novidade.BolaoComecou(manual = false)), novidades(status(concluido), status(comecou)))

        val aguardando = comecou.copy(
            emVigilia = true,
            etapasPipeline = etapas("forms" to "ok", "leitura" to "ok", "resultado" to "aguardando", "pagina" to "pendente"),
        )
        assertEquals(listOf(Novidade.BolaoAguardandoResultado), novidades(status(comecou), status(aguardando)))

        val publicado = aguardando.copy(
            emVigilia = false,
            ultimaExecucao = Execucao(id = 2, status = "completed", conclusao = "success"),
            etapasPipeline = etapas("forms" to "ok", "leitura" to "ok", "resultado" to "ok", "pagina" to "ok"),
            rodadaMaisRecente = 19,
        )
        assertEquals(listOf(Novidade.BolaoPaginaAtualizada(19)), novidades(status(aguardando), status(publicado)))
    }

    @Test
    fun execucaoInteiraEntreDuasVerificacoesAvisaSoAPaginaAtualizada() {
        val outraConcluida = concluido.copy(ultimaExecucao = Execucao(id = 3, status = "completed", conclusao = "success"))
        assertEquals(listOf(Novidade.BolaoPaginaAtualizada(18)), novidades(status(concluido), status(outraConcluida)))
    }

    @Test
    fun retryManualEhIdentificado() {
        val manual = bolao(id = 4, status = "queued", conclusao = null, evento = "workflow_dispatch")
        assertEquals(listOf(Novidade.BolaoComecou(manual = true)), novidades(status(concluido), status(manual)))
    }

    @Test
    fun falhaNumaEtapaEFalhaSemEtapa() {
        val falhou = bolao(
            id = 5,
            conclusao = "failure",
            etapas = etapas("forms" to "ok", "leitura" to "erro", "pagina" to "pendente"),
        )
        assertEquals(listOf(Novidade.BolaoFalhou("Etapa leitura")), novidades(status(concluido), status(falhou)))
        // A mesma falha vista de novo não avisa outra vez.
        assertTrue(novidades(status(falhou), status(falhou)).isEmpty())

        val semEtapa = bolao(id = 6, conclusao = "failure")
        assertEquals(listOf(Novidade.BolaoFalhou(null)), novidades(status(concluido), status(semEtapa)))
    }

    @Test
    fun siteDoBolaoForaDoAr() {
        val fora = concluido.copy(siteNoAr = SiteNoAr(ok = false, statusHttp = 404))
        assertEquals(listOf(Novidade.SiteForaDoAr("bolao_f1", "Bolão F1")), novidades(status(concluido), status(fora)))
        assertTrue("continua fora: não repete", novidades(status(fora), status(fora)).isEmpty())
    }

    @Test
    fun alertaNovoDeUmSiteMasNaoOMesmoComOutroNumero() {
        val antes = status(site(Alerta("aviso", "Sem coleta com sucesso há 4 dias.")))
        val mesmoAlerta = status(site(Alerta("aviso", "Sem coleta com sucesso há 5 dias.")))
        assertTrue(novidades(antes, mesmoAlerta).isEmpty())

        val novo = status(site(Alerta("aviso", "Sem coleta com sucesso há 5 dias."), Alerta("erro", "O site não respondeu na última checagem.")))
        val lista = novidades(antes, novo)
        assertEquals(
            listOf(Novidade.NovoAlerta("chess", "Chess Tracking", "O site não respondeu na última checagem.", erro = true)),
            lista,
        )
        assertFalse(lista.single().doBolao)
    }

    @Test
    fun siteNovoNoPainelNaoAvisa() {
        assertTrue(novidades(status(concluido), status(concluido, site(Alerta("erro", "x")))).isEmpty())
    }

    @Test
    fun notificacaoDoPipelineEscolheAMaisImportante() {
        assertNull(principalDoPipeline(emptyList()))
        val varias = listOf(
            Novidade.BolaoComecou(manual = false),
            Novidade.BolaoAguardandoResultado,
            Novidade.BolaoPaginaAtualizada(19),
        )
        assertEquals(Novidade.BolaoPaginaAtualizada(19), principalDoPipeline(varias))
        assertEquals(Novidade.BolaoFalhou("x"), principalDoPipeline(varias + Novidade.BolaoFalhou("x")))
        assertEquals(Novidade.BolaoComecou(manual = false), principalDoPipeline(listOf(Novidade.BolaoComecou(manual = false))))
    }
}
