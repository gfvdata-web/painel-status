package io.github.gfvdataweb.painelstatus.avisos

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.gfvdataweb.painelstatus.PainelApp
import io.github.gfvdataweb.painelstatus.data.ErroDeDados
import io.github.gfvdataweb.painelstatus.ui.painel.destaqueDe
import io.github.gfvdataweb.painelstatus.ui.painel.fluxoEmAndamento
import kotlinx.coroutines.CancellationException

/**
 * Verificação em segundo plano: baixa o status.json, compara com o último
 * visto, notifica o que mudou e, se o fluxo do Bolão estiver andando, agenda
 * a próxima conferência para daqui a ~5 min.
 */
class VerificacaoWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PainelApp).container
        val preferencias = container.preferencias.atuais.value
        if (!preferencias.ativado) return Result.success()

        val verificacao = try {
            container.vigia.verificar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sem internet ou painel fora do ar: a próxima rodada tenta de novo.
            return if (ErroDeDados.de(e) != null) Result.success() else Result.failure()
        }

        container.avisador.avisar(verificacao.novidades, preferencias)

        val bolao = destaqueDe(verificacao.status)
        if (preferencias.bolao && bolao != null && fluxoEmAndamento(bolao)) {
            container.agendador.conferirLogo(emSequencia = AgendadorWorkManager.TAG_RAPIDA in tags)
        }
        return Result.success()
    }
}
