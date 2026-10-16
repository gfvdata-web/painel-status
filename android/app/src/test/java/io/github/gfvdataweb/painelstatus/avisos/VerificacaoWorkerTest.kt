package io.github.gfvdataweb.painelstatus.avisos

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.gfvdataweb.painelstatus.apoio.AgendadorDeTeste
import io.github.gfvdataweb.painelstatus.apoio.IaDeTeste
import io.github.gfvdataweb.painelstatus.apoio.PainelAppDeTeste
import io.github.gfvdataweb.painelstatus.apoio.PainelDeTeste
import io.github.gfvdataweb.painelstatus.data.DadosDoPainel
import io.github.gfvdataweb.painelstatus.ui.ABA_INICIAL_IA
import io.github.gfvdataweb.painelstatus.data.avisos.PreferenciasDeAvisos
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A verificação em segundo plano de ponta a ponta: painel local → worker →
 * notificação no Android simulado (e o agendamento da conferência rápida).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PainelAppDeTeste::class)
class VerificacaoWorkerTest {

    private val app: PainelAppDeTeste = ApplicationProvider.getApplicationContext()
    private val painel = PainelDeTeste.painel
    private val ia = IaDeTeste.ia
    private val agendador get() = app.container.agendador as AgendadorDeTeste
    private val notificacoes get() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    private fun status(estadoDaPagina: String, execucao: String) = """
        {"meta": {"gerado_em": "2026-10-09T12:00:00Z"},
         "sites": [{"slug": "bolao_f1", "nome": "Bolão F1", "destaque": true, "rodada_mais_recente": 19,
                    "ultima_execucao_pipeline": {"id": 7, $execucao},
                    "etapas_pipeline": [{"chave": "pagina", "titulo": "Página atualizada", "estado": "$estadoDaPagina"}]}]}
    """.trimIndent()

    private fun rodar(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<VerificacaoWorker>(app).build().doWork()
    }

    @Before
    fun prepara() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.container.preferencias.salvar(PreferenciasDeAvisos(ativado = true, perguntado = true))
    }

    @After
    fun limpa() {
        painel.substituicoes.clear()
        ia.substituicoes.clear()
    }

    private fun limites(pctSemana: Double) = """
        {"meta": {"gerado_em": "2026-10-15T14:00:00+00:00"},
         "atual": {"seven_day": {"usado_pct": $pctSemana, "zera_em": 1792252800, "lido_em": 1792076000, "zerou_desde_a_leitura": false}},
         "historico": []}
    """.trimIndent()

    private fun pedidosDeLimites() = ia.pedidos.count { it == "GET /dados/limites.json" }

    private fun entrarNaIa() = runBlocking { app.container.fonteIa.entrar("segredo") }

    @Test
    fun desligadoNaoFazNada() {
        app.container.preferencias.salvar(PreferenciasDeAvisos(ativado = false))
        assertEquals(ListenableWorker.Result.success(), rodar())
        assertTrue(notificacoes.isEmpty())
    }

    @Test
    fun paginaAtualizadaViraNotificacaoEFluxoAndandoAgendaConferenciaRapida() {
        painel.substituicoes[DadosDoPainel.STATUS] = status("pendente", """"status": "in_progress"""")
        assertEquals(ListenableWorker.Result.success(), rodar())
        assertTrue("a primeira verificação só guarda a referência", notificacoes.isEmpty())
        assertEquals("fluxo andando: confere de novo em ~5 min", listOf(false), agendador.rapidas)

        painel.substituicoes[DadosDoPainel.STATUS] = status("ok", """"status": "completed", "conclusao": "success"""")
        assertEquals(ListenableWorker.Result.success(), rodar())
        val notificacao = notificacoes.single()
        assertEquals("Bolão F1: página atualizada", notificacao.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Rodada 19 pontuada e publicada.", notificacao.extras.getString(Notification.EXTRA_TEXT))
        assertEquals("fluxo terminou: sem nova conferência rápida", 1, agendador.rapidas.size)
    }

    @Test
    fun avisosDoBolaoDesligadosNaoNotificam() {
        app.container.preferencias.salvar(PreferenciasDeAvisos(ativado = true, bolao = false, perguntado = true))
        painel.substituicoes[DadosDoPainel.STATUS] = status("pendente", """"status": "in_progress"""")
        rodar()
        painel.substituicoes[DadosDoPainel.STATUS] = status("ok", """"status": "completed", "conclusao": "success"""")
        rodar()
        assertTrue(notificacoes.isEmpty())
    }

    @Test
    fun painelForaDoArNaoFalhaOWorker() {
        painel.foraDoAr = true
        try {
            assertEquals(ListenableWorker.Result.success(), rodar())
        } finally {
            painel.foraDoAr = false
        }
    }

    @Test
    fun limitesDoClaudeViramNotificacaoQueAbreAAbaIa() {
        entrarNaIa()
        ia.substituicoes["limites.json"] = MockResponse(body = limites(70.0))
        assertEquals(ListenableWorker.Result.success(), rodar())
        assertTrue("a primeira verificação só guarda a linha de base", notificacoes.isEmpty())

        ia.substituicoes["limites.json"] = MockResponse(body = limites(85.0))
        assertEquals(ListenableWorker.Result.success(), rodar())
        val notificacao = notificacoes.single()
        assertEquals("Claude: janela de 7 dias acima de 80%", notificacao.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(AvisadorDeNotificacoes.CANAL_LIMITES, notificacao.channelId)
        val abrir = shadowOf(notificacao.contentIntent).savedIntent
        assertEquals(AbaDoAviso.IA.name, abrir.getStringExtra(EXTRA_ABA))
        assertEquals(ABA_INICIAL_IA, AbaDoAviso.IA.name)
    }

    @Test
    fun limitesDesligadosNaoPedemNemNotificam() {
        entrarNaIa()
        app.container.preferencias.salvar(PreferenciasDeAvisos(ativado = true, limites = false, perguntado = true))
        ia.substituicoes["limites.json"] = MockResponse(body = limites(95.0))
        val antes = pedidosDeLimites()

        rodar()

        assertEquals(antes, pedidosDeLimites())
        assertTrue(notificacoes.isEmpty())
    }

    @Test
    fun semSessaoNaIaNaoPedeLimites() {
        val antes = pedidosDeLimites()
        assertEquals(ListenableWorker.Result.success(), rodar())
        assertEquals(antes, pedidosDeLimites())
    }

    @Test
    fun sessaoRecusadaNaoNotificaENaoQuebraOWorker() {
        entrarNaIa()
        ia.substituicoes["limites.json"] = MockResponse(code = 401)

        assertEquals(ListenableWorker.Result.success(), rodar())

        assertTrue(notificacoes.isEmpty())
        assertTrue(!app.container.fonteIa.logada)
    }

    @Test
    fun notificacaoDeTeste() {
        app.container.avisador.testar()
        // Um teste por canal: Bolão/sites e Limites do Claude.
        assertEquals(
            setOf("Painel de Status: teste", "Limites do Claude: teste"),
            notificacoes.map { it.extras.getString(Notification.EXTRA_TITLE) }.toSet(),
        )
        assertEquals(2, notificacoes.size)
    }
}
