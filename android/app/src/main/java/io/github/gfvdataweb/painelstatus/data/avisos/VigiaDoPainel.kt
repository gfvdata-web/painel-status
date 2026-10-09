package io.github.gfvdataweb.painelstatus.data.avisos

import io.github.gfvdataweb.painelstatus.data.CacheDeArquivos
import io.github.gfvdataweb.painelstatus.data.DadosDoPainel
import io.github.gfvdataweb.painelstatus.data.leitorJson
import io.github.gfvdataweb.painelstatus.data.lerStatus
import io.github.gfvdataweb.painelstatus.data.modelo.Status
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado de uma verificação em segundo plano. */
data class Verificacao(val status: Status, val novidades: List<Novidade>)

/**
 * Compara o status.json do painel com o último que o app "viu" (na tela ou
 * numa verificação anterior) e devolve o que mudou. A referência fica em
 * `avisos/visto.json`; a tela também a atualiza, para não notificar o que
 * a pessoa já viu com o app aberto.
 */
class VigiaDoPainel(
    private val dados: DadosDoPainel,
    private val pasta: CacheDeArquivos,
    private val relogio: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Baixa o status. Lança IOException (rede) ou IllegalArgumentException (formato), como [DadosDoPainel.baixar]. */
    suspend fun verificar(): Verificacao {
        val atual = dados.baixar(DadosDoPainel.STATUS, ::lerStatus).valor
        return withContext(io) {
            val anterior = lerVisto()
            guardarVisto(atual)
            pasta.salvar(ULTIMA_VERIFICACAO, relogio().toString(), relogio())
            // Primeira verificação: só guarda a referência.
            Verificacao(atual, if (anterior == null) emptyList() else novidades(anterior, atual))
        }
    }

    suspend fun marcarComoVisto(status: Status) = withContext(io) { guardarVisto(status) }

    /** Quando a última verificação em segundo plano terminou (epoch ms), ou null. */
    suspend fun ultimaVerificacao(): Long? = withContext(io) { pasta.ler(ULTIMA_VERIFICACAO)?.texto?.toLongOrNull() }

    private fun lerVisto(): Status? {
        val texto = pasta.ler(VISTO)?.texto ?: return null
        return try {
            lerStatus(texto)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun guardarVisto(status: Status) =
        pasta.salvar(VISTO, leitorJson.encodeToString(Status.serializer(), status), relogio())

    private companion object {
        const val VISTO = "visto.json"
        const val ULTIMA_VERIFICACAO = "ultima-verificacao.txt"
    }
}
