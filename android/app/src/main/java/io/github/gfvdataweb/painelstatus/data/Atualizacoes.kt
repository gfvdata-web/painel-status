package io.github.gfvdataweb.painelstatus.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Uma versão oficial publicada (tag `app-vX.Y.Z`), anunciada em `app/versao.json` no painel. */
data class VersaoPublicada(
    val versao: String,
    /** versionCode do APK (número da run do Actions que o gerou). */
    val build: Int,
    val urlDoApk: String,
    val notas: String,
)

/** Há versão mais nova que a instalada? Compara o versionCode, que só cresce. */
fun haAtualizacao(publicada: VersaoPublicada?, buildInstalado: Int): Boolean =
    publicada != null && publicada.build > buildInstalado

/**
 * Consulta o `versao.json` publicado pelo android.yml no Worker do painel (o
 * repositório é privado: o APK não sai mais do GitHub Releases). Versão e build
 * vêm do nome do APK, e o endereço do APK é montado a partir do próprio
 * `versao.json` — o app nunca segue um link arbitrário. Qualquer falha vira
 * "sem informação" (null): o aviso de versão nunca atrapalha o uso do app.
 */
class VerificadorDeAtualizacao(
    private val cliente: OkHttpClient,
    private val urlDaVersao: HttpUrl,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun maisRecente(): VersaoPublicada? = withContext(io) {
        try {
            val pedido = Request.Builder().url(urlDaVersao).cacheControl(CacheControl.FORCE_NETWORK).build()
            cliente.newCall(pedido).execute().use { resposta ->
                if (!resposta.isSuccessful) return@use null
                interpretar(urlDaVersao, json.decodeFromString<VersaoDoPainel>(resposta.body.string()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    internal companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Nome do APK publicado pelo android.yml: `painel-status-v1.2.3-build45.apk`. */
        private val NOME_DO_APK = Regex("""^painel-status-v(\d+\.\d+\.\d+)-build(\d+)\.apk$""")

        fun interpretar(urlDaVersao: HttpUrl, versao: VersaoDoPainel): VersaoPublicada? {
            val achado = NOME_DO_APK.matchEntire(versao.apk) ?: return null
            val urlDoApk = urlDaVersao.resolve(versao.apk) ?: return null
            return VersaoPublicada(
                versao = achado.groupValues[1],
                build = achado.groupValues[2].toInt(),
                urlDoApk = urlDoApk.toString(),
                notas = versao.notas,
            )
        }
    }
}

/** `app/versao.json`, gerado pelo job "publicar" do android.yml. */
@Serializable
internal data class VersaoDoPainel(
    @SerialName("apk") val apk: String,
    @SerialName("notas") val notas: String = "",
)
