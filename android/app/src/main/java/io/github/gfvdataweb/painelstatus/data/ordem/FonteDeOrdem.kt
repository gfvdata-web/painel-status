package io.github.gfvdataweb.painelstatus.data.ordem

import io.github.gfvdataweb.painelstatus.data.ia.CofreDeSegredo
import io.github.gfvdataweb.painelstatus.data.leitorJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Resultado de [FonteDeOrdem.entrar]. */
enum class ResultadoEntrada { ENTROU, INCORRETO, FALHOU }

/** Resultado de [FonteDeOrdem.salvar]. */
enum class ResultadoSalvar { SALVOU, SESSAO_EXPIRADA, FALHOU }

/** Ordem guardada no Worker (`GET /api/ordem`): `slugs` vazio = nenhuma ordem salva. */
@Serializable
data class OrdemSalva(
    val slugs: List<String> = emptyList(),
    @SerialName("atualizado_em") val atualizadoEm: String? = null,
)

@Serializable
private data class PedidoDeLogin(val usuario: String, val senha: String)

@Serializable
private data class RespostaDeLogin(val token: String = "")

@Serializable
private data class PedidoDeOrdem(val slugs: List<String>)

fun lerOrdem(texto: String): OrdemSalva = leitorJson.decodeFromString(OrdemSalva.serializer(), texto)

/**
 * Ordem dos cards só do app (`/api/ordem?alvo=app`), no mesmo Worker do painel.
 * Ler é público; salvar exige o token de `POST /api/login` (vale 90 dias), guardado
 * cifrado no [cofre]. A senha nunca é gravada. Falha de rede nunca vira exceção:
 * ler devolve lista vazia (= sem ordem salva) e entrar/salvar devolvem `FALHOU`.
 */
class FonteDeOrdem(
    private val cliente: OkHttpClient,
    private val base: HttpUrl,
    private val cofre: CofreDeSegredo,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    val logada: Boolean get() = cofre.ler() != null

    /** Slugs na ordem salva; vazia se não há ordem, a rede falha ou a resposta não é entendida. */
    suspend fun carregar(): List<String> = withContext(io) {
        try {
            val url = base.resolve("api/ordem?alvo=app") ?: return@withContext emptyList()
            val pedido = Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).build()
            cliente.newCall(pedido).execute().use { resposta ->
                if (!resposta.isSuccessful) emptyList() else lerOrdem(resposta.body.string()).slugs
            }
        } catch (e: IOException) {
            emptyList()
        } catch (e: IllegalArgumentException) { // JSON que o app não entende (SerializationException)
            emptyList()
        }
    }

    suspend fun entrar(usuario: String, senha: String): ResultadoEntrada = withContext(io) {
        try {
            val url = base.resolve("api/login") ?: return@withContext ResultadoEntrada.FALHOU
            val corpo = leitorJson.encodeToString(PedidoDeLogin.serializer(), PedidoDeLogin(usuario, senha))
            val pedido = Request.Builder().url(url).post(corpo.toRequestBody(JSON)).build()
            cliente.newCall(pedido).execute().use { resposta ->
                when {
                    resposta.code == 401 -> ResultadoEntrada.INCORRETO
                    !resposta.isSuccessful -> ResultadoEntrada.FALHOU
                    else -> {
                        val token = leitorJson.decodeFromString(RespostaDeLogin.serializer(), resposta.body.string()).token
                        if (token.isEmpty()) {
                            ResultadoEntrada.FALHOU
                        } else {
                            cofre.guardar(token)
                            ResultadoEntrada.ENTROU
                        }
                    }
                }
            }
        } catch (e: IOException) {
            ResultadoEntrada.FALHOU
        } catch (e: IllegalArgumentException) {
            ResultadoEntrada.FALHOU
        }
    }

    /** Grava a ordem. Com 401 (ou sem token) esquece o token: a tela pede o login de novo. */
    suspend fun salvar(slugs: List<String>): ResultadoSalvar = withContext(io) {
        val token = cofre.ler() ?: return@withContext ResultadoSalvar.SESSAO_EXPIRADA
        try {
            val url = base.resolve("api/ordem?alvo=app") ?: return@withContext ResultadoSalvar.FALHOU
            val corpo = leitorJson.encodeToString(PedidoDeOrdem.serializer(), PedidoDeOrdem(slugs))
            val pedido = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .put(corpo.toRequestBody(JSON))
                .build()
            cliente.newCall(pedido).execute().use { resposta ->
                when {
                    resposta.code == 401 -> {
                        cofre.apagar()
                        ResultadoSalvar.SESSAO_EXPIRADA
                    }
                    resposta.isSuccessful -> ResultadoSalvar.SALVOU
                    else -> ResultadoSalvar.FALHOU
                }
            }
        } catch (e: IOException) {
            ResultadoSalvar.FALHOU
        }
    }

    /** Esquece o token deste aparelho. */
    fun sair() = cofre.apagar()

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
