package io.github.gfvdataweb.painelstatus.ui

import io.github.gfvdataweb.painelstatus.data.modelo.Alerta
import io.github.gfvdataweb.painelstatus.data.modelo.Aviso
import io.github.gfvdataweb.painelstatus.data.lerStatus
import io.github.gfvdataweb.painelstatus.data.modelo.Downloads
import io.github.gfvdataweb.painelstatus.data.modelo.DownloadsDoDia
import io.github.gfvdataweb.painelstatus.data.modelo.Etapa
import io.github.gfvdataweb.painelstatus.ui.painel.ResumoDownloads
import io.github.gfvdataweb.painelstatus.ui.painel.downloadsRecentes
import io.github.gfvdataweb.painelstatus.data.modelo.Execucao
import io.github.gfvdataweb.painelstatus.data.modelo.Historico
import io.github.gfvdataweb.painelstatus.data.modelo.Site
import io.github.gfvdataweb.painelstatus.data.modelo.PipelineForms
import io.github.gfvdataweb.painelstatus.data.modelo.SiteNoAr
import io.github.gfvdataweb.painelstatus.data.modelo.Status
import io.github.gfvdataweb.painelstatus.data.modelo.Acesso
import io.github.gfvdataweb.painelstatus.data.modelo.VisitasDoDia
import io.github.gfvdataweb.painelstatus.ui.comum.Duracao
import io.github.gfvdataweb.painelstatus.ui.comum.Idade
import io.github.gfvdataweb.painelstatus.ui.comum.dataHora
import io.github.gfvdataweb.painelstatus.ui.comum.diasDesde
import io.github.gfvdataweb.painelstatus.ui.comum.duracao
import io.github.gfvdataweb.painelstatus.ui.comum.idadeDosDados
import io.github.gfvdataweb.painelstatus.ui.painel.SituacaoDaExecucao
import io.github.gfvdataweb.painelstatus.ui.painel.Tom
import io.github.gfvdataweb.painelstatus.ui.painel.Categoria
import io.github.gfvdataweb.painelstatus.ui.painel.PipelineDoSite
import io.github.gfvdataweb.painelstatus.ui.painel.SubLinha
import io.github.gfvdataweb.painelstatus.ui.painel.pipelineDoSite
import io.github.gfvdataweb.painelstatus.ui.painel.categoriaDe
import io.github.gfvdataweb.painelstatus.ui.painel.categoriasUsadas
import io.github.gfvdataweb.painelstatus.ui.painel.corDaCategoria
import io.github.gfvdataweb.painelstatus.ui.painel.enderecoCurto
import io.github.gfvdataweb.painelstatus.ui.painel.resumoDosSites
import io.github.gfvdataweb.painelstatus.ui.painel.situacaoDoSite
import io.github.gfvdataweb.painelstatus.ui.painel.sitesNaOrdemDaLista
import io.github.gfvdataweb.painelstatus.ui.painel.urlDoRepositorio
import io.github.gfvdataweb.painelstatus.ui.painel.visitasRecentes
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

    private val noAr = SiteNoAr(ok = true, statusHttp = 200)
    private val comum = Site(slug = "a", nome = "A", pagesUrl = "https://a.gfvdata.com/", siteNoAr = noAr)

    @Test
    fun categoriaTemCorEValorNeutroPadrao() {
        assertEquals(Categoria.DADOS_ABERTOS, categoriaDe("dados_abertos"))
        assertEquals(Categoria.HOBBY, categoriaDe("hobby"))
        assertEquals(Categoria.CURIOSIDADE, categoriaDe("curiosidade"))
        assertEquals(Categoria.NEUTRA, categoriaDe(null))
        assertEquals(Categoria.NEUTRA, categoriaDe("outra"))
        assertEquals(0xFF0891B2, corDaCategoria(Categoria.DADOS_ABERTOS))
        assertEquals(0xFF94A3B8, corDaCategoria(Categoria.NEUTRA))
    }

    @Test
    fun legendaSoTemCategoriasUsadasSemRepetir() {
        val sites = listOf(comum, comum.copy(categoria = "hobby"), comum.copy(categoria = "hobby"), comum.copy(categoria = "x"))
        assertEquals(listOf(Categoria.HOBBY), categoriasUsadas(sites))
        assertTrue(categoriasUsadas(listOf(comum)).isEmpty())
    }

    @Test
    fun destaqueVaiPrimeiroEORestoFicaNaOrdemDoCadastro() {
        val status = Status(sites = listOf(comum, comum.copy(slug = "b"), bolao, comum.copy(slug = "c")))
        assertEquals(listOf("bolao_f1", "a", "b", "c"), sitesNaOrdemDaLista(status).map { it.slug })
    }

    @Test
    fun visitasSomamOsUltimosSeteDias() {
        assertNull(visitasRecentes(comum))
        assertNull(visitasRecentes(comum.copy(acesso = Acesso())))
        val serie = (1..10).map { VisitasDoDia("2026-10-%02d".format(it), it) }
        // 4+5+...+10 = 49: os três primeiros dias ficam de fora.
        assertEquals(49, visitasRecentes(comum.copy(acesso = Acesso(serieDiaria = serie))))
        assertEquals(3, visitasRecentes(comum.copy(acesso = Acesso(serieDiaria = serie.take(2)))))
    }

    @Test
    fun downloadsLeEDesserializaComESemBloco() {
        val json = """{"sites":[
            {"slug":"a","nome":"A","downloads":{"total":8,"skill":3,"csv":5,"por_navegador":6,"por_script":2,
              "desde":"2026-09-14","serie_diaria":[{"data":"2026-09-14","downloads":1}]}},
            {"slug":"b","nome":"B","downloads":{"total":null,"desde":null}},
            {"slug":"c","nome":"C"}]}"""
        val (com, vazio, sem) = lerStatus(json).sites
        val d = checkNotNull(com.downloads)
        assertEquals(8, d.total)
        assertEquals(6, d.porNavegador)
        assertEquals("2026-09-14", d.desde)
        assertEquals(1, d.serieDiaria.single().downloads)
        assertEquals(0, checkNotNull(vazio.downloads).total)
        assertNull(sem.downloads)
    }

    @Test
    fun downloadsSomamOsUltimosSeteDiasEMantemSkillECsv() {
        assertNull(downloadsRecentes(comum))
        val serie = (1..10).map { DownloadsDoDia("2026-10-%02d".format(it), it) }
        val site = comum.copy(downloads = Downloads(total = 99, skill = 4, csv = 7, serieDiaria = serie))
        // 4+5+...+10 = 49: os três primeiros dias ficam de fora.
        assertEquals(ResumoDownloads(49, 4, 7), downloadsRecentes(site))
        assertEquals(ResumoDownloads(0, 0, 0), downloadsRecentes(comum.copy(downloads = Downloads())))
    }

    @Test
    fun resumoContaSitesForaDoAr() {
        assertEquals(0, resumoDosSites(listOf(comum, comum)).foraDoAr)
        val resumo = resumoDosSites(listOf(comum, comum.copy(siteNoAr = SiteNoAr(ok = false)), comum.copy(siteNoAr = null)))
        assertEquals(3, resumo.total)
        assertEquals(2, resumo.foraDoAr)
    }

    @Test
    fun situacaoDoSiteSegueAOrdemDaPagina() {
        // No ar e sem nada: o endereço curto, bolinha verde.
        val normal = situacaoDoSite(comum)
        assertEquals(Tom.OK, normal.tom)
        assertEquals(SubLinha.Endereco("a.gfvdata.com"), normal.subLinha)
        // Fora do ar vence alertas e vigília.
        val fora = situacaoDoSite(bolao.copy(siteNoAr = SiteNoAr(ok = false, statusHttp = 503), emVigilia = true, alertas = listOf(Alerta("aviso", "x"))))
        assertEquals(Tom.ERRO, fora.tom)
        assertEquals(SubLinha.ForaDoAr(503), fora.subLinha)
        assertEquals(SubLinha.ForaDoAr(null), situacaoDoSite(comum.copy(siteNoAr = SiteNoAr(ok = false))).subLinha)
        // Alertas: âmbar, ou vermelho se algum for erro; vencem a vigília.
        val avisos = situacaoDoSite(bolao.copy(siteNoAr = noAr, emVigilia = true, alertas = listOf(Alerta("aviso", "um"), Alerta("aviso", "dois"))))
        assertEquals(Tom.AVISO, avisos.tom)
        assertEquals(SubLinha.Alertas("⚠ um dois"), avisos.subLinha)
        assertEquals(Tom.ERRO, situacaoDoSite(comum.copy(alertas = listOf(Alerta("aviso", "a"), Alerta("erro", "b")))).tom)
        // Vigília só vale para o destaque.
        assertEquals(SubLinha.Vigilia, situacaoDoSite(bolao.copy(siteNoAr = noAr, emVigilia = true)).subLinha)
        assertTrue(situacaoDoSite(comum.copy(emVigilia = true)).subLinha is SubLinha.Endereco)
    }

    @Test
    fun pipelineDoCardVemDoBolaoOuDoForms() {
        assertNull(pipelineDoSite(comum))
        val bolaoComEtapas = bolao.copy(etapasPipeline = listOf(Etapa(estado = "ok")), ultimaExecucao = Execucao(url = "https://g/1"))
        assertEquals(PipelineDoSite(bolaoComEtapas.etapasPipeline, "https://g/1", false), pipelineDoSite(bolaoComEtapas))
        val etapas = listOf(Etapa(estado = "ok"))
        val forms = comum.copy(pipelineForms = PipelineForms("pipeline.yml", Execucao(url = "https://g/2"), etapas))
        assertEquals(PipelineDoSite(etapas, "https://g/2", true), pipelineDoSite(forms))
        assertNull(pipelineDoSite(comum.copy(pipelineForms = PipelineForms("p.yml", Execucao(erro = "HTTP 500"), etapas))))
        assertNull(pipelineDoSite(comum.copy(pipelineForms = PipelineForms("p.yml", Execucao(), emptyList()))))
    }

    @Test
    fun enderecoCurtoTiraProtocoloEBarraFinal() {
        assertEquals("a.gfvdata.com", enderecoCurto("https://a.gfvdata.com/"))
        assertEquals("x.io/y", enderecoCurto("http://x.io/y/"))
        assertEquals("", enderecoCurto(""))
    }

    @Test
    fun repositorioFicaNaContaDoPainel() {
        assertEquals("https://github.com/gfvdata-web/BolaoF1", urlDoRepositorio(bolao.copy(repo = "BolaoF1")))
    }

    @Test
    fun abaInicialDaNotificacao() {
        assertEquals(RotaStatus, rotaInicial(null))
        assertEquals(RotaStatus, rotaInicial("BOLAO"))
        assertEquals(RotaStatus, rotaInicial("SITES"))
        assertEquals(RotaIa, rotaInicial("IA"))
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
