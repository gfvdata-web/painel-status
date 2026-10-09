package io.github.gfvdataweb.painelstatus.apoio

import io.github.gfvdataweb.painelstatus.AppContainer
import io.github.gfvdataweb.painelstatus.PainelApp
import io.github.gfvdataweb.painelstatus.avisos.Agendador
import io.github.gfvdataweb.painelstatus.data.DadosDoPainel
import io.github.gfvdataweb.painelstatus.data.lerStatus
import io.github.gfvdataweb.painelstatus.data.modelo.Status
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// Peças compartilhadas pelos testes. Nada aqui acessa a internet.

/** O que o painel publica de verdade (pasta docs/, passada pelo Gradle). */
object PainelPublicado {
    val pasta: File = File(
        checkNotNull(System.getProperty("painel.publicado")) { "Rode pelo Gradle (propriedade painel.publicado)" },
    )

    fun texto(caminho: String): String = File(pasta, caminho).readText()

    /** docs/dados/status.json já lido. */
    val status: Status by lazy { lerStatus(texto(DadosDoPainel.STATUS)) }

    /** Todos os docs/dados/historico/<slug>.json, como caminhos relativos à raiz. */
    val historicos: List<String> by lazy {
        File(pasta, "dados/historico").listFiles().orEmpty()
            .filter { it.extension == "json" }
            .map { "dados/historico/${it.name}" }
            .sorted()
    }
}

/**
 * Painel local (MockWebServer) que serve a pasta docs/ real em `/painel-status/`.
 * Os testes podem trocar o conteúdo de um caminho ([substituicoes]) ou derrubar o painel.
 */
class PainelLocal : Closeable {
    val substituicoes = ConcurrentHashMap<String, String>()
    val pedidosDoStatus = AtomicInteger()

    @Volatile
    var foraDoAr = false

    private val servidor = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (foraDoAr) return MockResponse(code = 503)
                val caminho = request.url.encodedPath.removePrefix("/painel-status/")
                if (caminho == DadosDoPainel.STATUS) pedidosDoStatus.incrementAndGet()
                substituicoes[caminho]?.let { return MockResponse(body = it) }
                val arquivo = File(PainelPublicado.pasta, caminho)
                return if (arquivo.isFile) MockResponse(body = arquivo.readText()) else MockResponse(code = 404)
            }
        }
        start()
    }

    val urlDoPainel: HttpUrl get() = servidor.url("/painel-status/")

    /** Sem Releases publicados no painel local (responde 404): o app não mostra aviso. */
    val urlDosReleases: HttpUrl get() = servidor.url("/releases")

    override fun close() = servidor.close()
}

/** Um painel local para a JVM de testes inteira (o app de teste aponta para ele). */
object PainelDeTeste {
    val painel: PainelLocal by lazy { PainelLocal() }
}

/** Agendador que só anota o que pediram (o WorkManager não roda nos testes). */
class AgendadorDeTeste : Agendador {
    val aplicados = mutableListOf<Boolean>()
    val rapidas = mutableListOf<Boolean>()

    override fun aplicar(ativado: Boolean) {
        aplicados += ativado
    }

    override fun conferirLogo(emSequencia: Boolean) {
        rapidas += emSequencia
    }
}

/**
 * App usado nos testes Robolectric do app inteiro: lê do painel local. A
 * recarga periódica fica em 1 h para não disparar no meio de um teste, e a
 * verificação em segundo plano usa o [AgendadorDeTeste].
 */
class PainelAppDeTeste : PainelApp() {
    override fun criarContainer(): AppContainer = AppContainer(
        this,
        PainelDeTeste.painel.urlDoPainel,
        PainelDeTeste.painel.urlDosReleases,
        intervaloDeRecargaMs = 3_600_000,
        agendador = AgendadorDeTeste(),
    )
}
