package io.github.gfvdataweb.painelstatus

import android.app.Application
import android.content.Context
import io.github.gfvdataweb.painelstatus.data.CacheDeArquivos
import io.github.gfvdataweb.painelstatus.data.DadosDoPainel
import io.github.gfvdataweb.painelstatus.data.FonteRemota
import io.github.gfvdataweb.painelstatus.data.VerificadorDeAtualizacao
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Endereços externos que o app conhece (só leitura, nada de token). */
object Enderecos {
    /** Raiz do painel no GitHub Pages: `dados/status.json` e `dados/historico/` ficam abaixo dela. */
    const val PAINEL = "https://gfvdata-web.github.io/painel-status/"

    /** API pública dos Releases (aviso de nova versão). */
    const val RELEASES = "https://api.github.com/repos/gfvdata-web/painel-status/releases?per_page=20"
}

/**
 * Com o app aberto, de quanto em quanto tempo o status.json é conferido de
 * novo. Igual a INTERVALO_RECARGA_MS de docs/js/app.js: o passo a passo do
 * Bolão F1 anda no app no mesmo ritmo que na página.
 */
const val INTERVALO_DE_RECARGA_MS = 60_000L

/**
 * Contêiner de dependências manual (sem Hilt): cria cada peça uma vez e
 * entrega para as telas. Nos testes, as peças são montadas à mão com dublês.
 */
class AppContainer(
    contexto: Context,
    urlDoPainel: HttpUrl = Enderecos.PAINEL.toHttpUrl(),
    urlDosReleases: HttpUrl = Enderecos.RELEASES.toHttpUrl(),
    val intervaloDeRecargaMs: Long = INTERVALO_DE_RECARGA_MS,
) {
    val clienteHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val dados = DadosDoPainel(
        fonte = FonteRemota(clienteHttp, urlDoPainel),
        cache = CacheDeArquivos(File(contexto.filesDir, "dados-do-painel")),
    )

    val verificadorDeAtualizacao = VerificadorDeAtualizacao(clienteHttp, urlDosReleases)
}

/** Os testes trocam o contêiner (ex.: painel local) sobrescrevendo [criarContainer]. */
open class PainelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = criarContainer()
    }

    protected open fun criarContainer(): AppContainer = AppContainer(this)
}
