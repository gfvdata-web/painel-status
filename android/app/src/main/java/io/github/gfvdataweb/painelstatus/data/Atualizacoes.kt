package io.github.gfvdataweb.painelstatus.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Uma versão oficial publicada nos Releases do GitHub (tag `app-vX.Y.Z`). */
data class VersaoPublicada(
    val versao: String,
    /** versionCode do APK (número da run do Actions que o gerou). */
    val build: Int,
    val urlDoApk: String,
    val urlDaPagina: String,
)

/** Há versão mais nova que a instalada? Compara o versionCode, que só cresce. */
fun haAtualizacao(publicada: VersaoPublicada?, buildInstalado: Int): Boolean =
    publicada != null && publicada.build > buildInstalado

/**
 * Consulta a API pública de Releases (sem token; limite de 60 consultas por
 * hora por IP, folgado para uma consulta ao abrir o app). Qualquer falha vira
 * "sem informação" (null): o aviso de versão nunca atrapalha o uso do app.
 */
class VerificadorDeAtualizacao(
    private val cliente: OkHttpClient,
    private val urlDosReleases: HttpUrl,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun maisRecente(): VersaoPublicada? = withContext(io) {
        try {
            val pedido = Request.Builder()
                .url(urlDosReleases)
                .header("Accept", "application/vnd.github+json")
                .build()
            cliente.newCall(pedido).execute().use { resposta ->
                if (!resposta.isSuccessful) return@use null
                escolherMaisRecente(json.decodeFromString<List<ReleaseDoGitHub>>(resposta.body.string()))
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

        fun escolherMaisRecente(releases: List<ReleaseDoGitHub>): VersaoPublicada? =
            releases
                .filter { !it.rascunho && !it.preRelease && it.tag.startsWith("app-v") }
                .flatMap { release ->
                    release.arquivos.mapNotNull { arquivo ->
                        NOME_DO_APK.matchEntire(arquivo.nome)?.let { achado ->
                            VersaoPublicada(
                                versao = achado.groupValues[1],
                                build = achado.groupValues[2].toInt(),
                                urlDoApk = arquivo.url,
                                urlDaPagina = release.pagina,
                            )
                        }
                    }
                }
                .maxByOrNull { it.build }
    }
}

@Serializable
internal data class ReleaseDoGitHub(
    @SerialName("tag_name") val tag: String,
    @SerialName("html_url") val pagina: String = "",
    @SerialName("draft") val rascunho: Boolean = false,
    @SerialName("prerelease") val preRelease: Boolean = false,
    @SerialName("assets") val arquivos: List<ArquivoDoRelease> = emptyList(),
)

@Serializable
internal data class ArquivoDoRelease(
    @SerialName("name") val nome: String,
    @SerialName("browser_download_url") val url: String,
)
