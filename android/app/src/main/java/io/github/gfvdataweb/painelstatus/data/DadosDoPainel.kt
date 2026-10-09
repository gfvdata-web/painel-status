package io.github.gfvdataweb.painelstatus.data

import io.github.gfvdataweb.painelstatus.data.modelo.Historico
import io.github.gfvdataweb.painelstatus.data.modelo.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException

/** Um valor já lido e quando o texto dele foi baixado (epoch ms). */
data class Guardado<T>(val valor: T, val salvoEm: Long)

/** O que a tela mostra: dados (talvez do cache), idade deles e situação da atualização. */
data class EstadoDados<T>(
    val dados: T?,
    /** Quando os dados exibidos foram baixados (epoch ms); null se não há dados. */
    val atualizadoEm: Long?,
    val atualizando: Boolean,
    val erro: ErroDeDados?,
)

/** Por que a atualização falhou: sem internet/painel fora do ar, ou JSON que o app não entende. */
enum class ErroDeDados {
    FALHA_NO_DOWNLOAD,
    FORMATO_INESPERADO,
    ;

    companion object {
        /** Erro esperado (rede ou formato) → tipo; qualquer outro é bug e volta null, para não ser engolido. */
        fun de(erro: Throwable): ErroDeDados? = when (erro) {
            is IOException -> FALHA_NO_DOWNLOAD
            // SerializationException é uma IllegalArgumentException.
            is IllegalArgumentException -> FORMATO_INESPERADO
            else -> null
        }
    }
}

/** JSON do painel: campos novos são ignorados e null vira o valor padrão do modelo. */
val leitorJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun lerStatus(texto: String): Status = leitorJson.decodeFromString(texto)

fun lerHistorico(texto: String): Historico = leitorJson.decodeFromString(texto)

/**
 * JSONs do painel com cache offline. Cada arquivo baixado só substitui o do
 * cache depois de lido sem erro: um JSON quebrado no painel nunca apaga o
 * último conjunto bom.
 */
class DadosDoPainel(
    private val fonte: Fonte,
    private val cache: CacheDeArquivos,
    private val relogio: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** O que está guardado no aparelho, ou null (nunca baixou, ou não dá mais para ler). */
    suspend fun <T> doCache(caminho: String, ler: (String) -> T): Guardado<T>? = withContext(io) {
        val guardado = cache.ler(caminho)
        if (guardado == null) {
            null
        } else {
            try {
                Guardado(ler(guardado.texto), guardado.salvoEm)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    /** Baixa, confere e guarda. Lança IOException (rede) ou IllegalArgumentException (formato). */
    suspend fun <T> baixar(caminho: String, ler: (String) -> T): Guardado<T> = withContext(io) {
        val texto = fonte.baixarTexto(caminho)
        val valor = ler(texto) // confere antes de mexer no cache
        val agora = relogio()
        cache.salvar(caminho, texto, agora)
        Guardado(valor, agora)
    }

    /** *Stale-while-revalidate*: o cache na hora e, em seguida, o painel. */
    fun <T> observar(caminho: String, ler: (String) -> T): Flow<EstadoDados<T>> = flow {
        val guardado = doCache(caminho, ler)
        emit(EstadoDados(guardado?.valor, guardado?.salvoEm, atualizando = true, erro = null))

        // O emit fica fora do try: exceções de quem coleta não podem ser engolidas aqui.
        val estado = try {
            val baixado = baixar(caminho, ler)
            EstadoDados(baixado.valor, baixado.salvoEm, atualizando = false, erro = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val erro = ErroDeDados.de(e) ?: throw e
            EstadoDados(guardado?.valor, guardado?.salvoEm, atualizando = false, erro = erro)
        }
        emit(estado)
    }

    companion object {
        const val STATUS = "dados/status.json"

        private val CAMINHO_DE_HISTORICO = Regex("""^dados/historico/[A-Za-z0-9_-]+\.json$""")

        /** `historico_arquivo` vem do status.json: só aceita a pasta de histórico do próprio painel. */
        fun historicoValido(caminho: String?): Boolean = caminho != null && CAMINHO_DE_HISTORICO.matches(caminho)
    }
}
