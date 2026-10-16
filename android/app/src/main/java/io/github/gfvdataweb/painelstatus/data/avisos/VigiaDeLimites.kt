package io.github.gfvdataweb.painelstatus.data.avisos

import io.github.gfvdataweb.painelstatus.data.CacheDeArquivos
import io.github.gfvdataweb.painelstatus.data.ia.FonteIa
import io.github.gfvdataweb.painelstatus.data.ia.LimitesIa
import io.github.gfvdataweb.painelstatus.data.ia.ResultadoIa
import io.github.gfvdataweb.painelstatus.data.ia.lerLimites
import io.github.gfvdataweb.painelstatus.data.leitorJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Avisos dos limites do Claude: baixa só o limites.json do painel de IA (com a
 * sessão guardada), compara com o último visto (`avisos/limites-visto.json`,
 * atualizado também pela tela de IA) e devolve o que vale notificar. Sem sessão,
 * 401 ou falha de rede: lista vazia e a linha de base não muda.
 */
class VigiaDeLimites(
    private val fonte: FonteIa,
    private val pasta: CacheDeArquivos,
    private val relogio: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun verificar(): List<NovidadeIa> {
        if (!fonte.logada) return emptyList()
        val resultado = fonte.carregarLimites()
        val atual = (resultado as? ResultadoIa.Dados)?.dados?.limites ?: return emptyList()
        return withContext(io) {
            val anterior = lerVisto()
            guardarVisto(atual)
            novidadesIa(anterior, atual) // sem anterior: só guarda a linha de base
        }
    }

    /** A tela de IA mostrou estes limites: o que a pessoa viu com o app aberto não vira notificação. */
    suspend fun marcarComoVisto(limites: LimitesIa) = withContext(io) { guardarVisto(limites) }

    private fun lerVisto(): LimitesIa? {
        val texto = pasta.ler(VISTO)?.texto ?: return null
        return try {
            lerLimites(texto)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun guardarVisto(limites: LimitesIa) =
        pasta.salvar(VISTO, leitorJson.encodeToString(LimitesIa.serializer(), limites), relogio())

    private companion object {
        const val VISTO = "limites-visto.json"
    }
}
