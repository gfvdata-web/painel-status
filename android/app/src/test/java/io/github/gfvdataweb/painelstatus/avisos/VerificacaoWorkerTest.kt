package io.github.gfvdataweb.painelstatus.avisos

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.gfvdataweb.painelstatus.apoio.AgendadorDeTeste
import io.github.gfvdataweb.painelstatus.apoio.PainelAppDeTeste
import io.github.gfvdataweb.painelstatus.apoio.PainelDeTeste
import io.github.gfvdataweb.painelstatus.data.DadosDoPainel
import io.github.gfvdataweb.painelstatus.data.avisos.PreferenciasDeAvisos
import kotlinx.coroutines.runBlocking
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
    fun limpa() = painel.substituicoes.clear()

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
    fun notificacaoDeTeste() {
        app.container.avisador.testar()
        assertEquals("Painel de Status: teste", notificacoes.single().extras.getString(Notification.EXTRA_TITLE))
    }
}
