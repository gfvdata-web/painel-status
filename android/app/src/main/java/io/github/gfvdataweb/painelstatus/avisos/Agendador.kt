package io.github.gfvdataweb.painelstatus.avisos

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Quando a verificação em segundo plano roda. Nos testes, um dublê que só anota. */
interface Agendador {
    /** Liga (a cada ~15 min, o mínimo do Android) ou desliga a verificação periódica. */
    fun aplicar(ativado: Boolean)

    /**
     * Confere de novo em ~5 min: usado enquanto o fluxo do Bolão anda, para o
     * aviso não esperar a próxima rodada de 15 min. [emSequencia] = chamado de
     * dentro da própria verificação rápida (encadeia a próxima depois dela).
     */
    fun conferirLogo(emSequencia: Boolean = false)
}

/** [Agendador] com o WorkManager (sobrevive a reinício do celular e respeita a bateria). */
class AgendadorWorkManager(private val contexto: Context) : Agendador {

    private val gerenciador: WorkManager get() = WorkManager.getInstance(contexto)

    private val comInternet = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun aplicar(ativado: Boolean) {
        if (ativado) {
            val periodica = PeriodicWorkRequestBuilder<VerificacaoWorker>(INTERVALO_PERIODICO_MIN, TimeUnit.MINUTES)
                .setConstraints(comInternet)
                .build()
            gerenciador.enqueueUniquePeriodicWork(PERIODICA, ExistingPeriodicWorkPolicy.KEEP, periodica)
        } else {
            gerenciador.cancelUniqueWork(PERIODICA)
            gerenciador.cancelUniqueWork(RAPIDA)
        }
    }

    override fun conferirLogo(emSequencia: Boolean) {
        val rapida = OneTimeWorkRequestBuilder<VerificacaoWorker>()
            .setInitialDelay(INTERVALO_RAPIDO_MIN, TimeUnit.MINUTES)
            .setConstraints(comInternet)
            .addTag(TAG_RAPIDA)
            .build()
        // Fora da sequência, KEEP: se já há uma rápida esperando, não empilha outra.
        // Dentro dela, a atual ainda está rodando: a próxima entra depois dela.
        val politica = if (emSequencia) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
        gerenciador.enqueueUniqueWork(RAPIDA, politica, rapida)
    }

    companion object {
        const val INTERVALO_PERIODICO_MIN = 15L
        const val INTERVALO_RAPIDO_MIN = 5L
        const val TAG_RAPIDA = "verificacao-rapida"
        private const val PERIODICA = "verificacao-periodica"
        private const val RAPIDA = "verificacao-rapida"
    }
}
