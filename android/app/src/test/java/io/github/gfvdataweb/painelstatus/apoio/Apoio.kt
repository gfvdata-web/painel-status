package io.github.gfvdataweb.painelstatus.apoio

import io.github.gfvdataweb.painelstatus.AppContainer
import io.github.gfvdataweb.painelstatus.PainelApp
import io.github.gfvdataweb.painelstatus.avisos.Agendador
import io.github.gfvdataweb.painelstatus.data.ia.CofreEmMemoria
import io.github.gfvdataweb.painelstatus.data.DadosDoPainel
import io.github.gfvdataweb.painelstatus.data.lerStatus
import io.github.gfvdataweb.painelstatus.data.modelo.Status
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
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

    /** Sem versao.json no painel local (responde 404): o app não mostra aviso. */
    val urlDaVersao: HttpUrl get() = servidor.url("/painel-status/app/versao.json")

    override fun close() = servidor.close()
}

/** Um painel local para a JVM de testes inteira (o app de teste aponta para ele). */
object PainelDeTeste {
    val painel: PainelLocal by lazy { PainelLocal() }
}

/**
 * Fixtures do painel de IA em src/test/resources/ia/: dados sintéticos com o
 * formato real de uso.json, limites.json e catalogo.json (o painel de verdade
 * é privado e fica fora do Git). "Agora" dos testes = 2026-10-15 15:00 UTC.
 */
object IaFixtures {
    const val AGORA_MS = 1_792_076_400_000L

    fun texto(nome: String): String = checkNotNull(IaFixtures::class.java.classLoader!!.getResource("ia/$nome")) {
        "Fixture ia/$nome não encontrada"
    }.readText()
}

/**
 * Painel de IA local (MockWebServer) com o contrato do Worker do painel-ia:
 * `POST /entrar` (303 + cookie, ou 303 para `/?erro` sem cookie) e
 * `GET /dados/<arquivo>.json` (401 sem o cookie certo).
 */
class IaLocal : Closeable {
    var senha = "segredo"
    // Vence em 2100: os testes com o relógio real (Robolectric) não podem depender da data de hoje.
    val cookie = "4102444800000.assinaturadeteste"
    val substituicoes = ConcurrentHashMap<String, MockResponse>()
    val pedidos = CopyOnWriteArrayList<String>()
    val cookiesRecebidos = CopyOnWriteArrayList<String>()

    @Volatile
    var foraDoAr = false

    private val servidor = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val caminho = request.url.encodedPath
                pedidos += "${request.method} $caminho"
                if (foraDoAr) return MockResponse(code = 503)
                if (caminho == "/entrar") {
                    // toString() do corpo curto é "[text=senha=...]" (Buffer ou ByteString): não depende do tipo exato.
                    val corpo = request.body.toString()
                    return if (corpo.contains("senha=$senha]")) {
                        MockResponse(
                            code = 303,
                            headers = headersOf(
                                "Location", "/",
                                "Set-Cookie", "painel_ia=$cookie; Path=/; Max-Age=2592000; HttpOnly; Secure; SameSite=Lax",
                            ),
                        )
                    } else {
                        MockResponse(code = 303, headers = headersOf("Location", "/?erro"))
                    }
                }
                request.headers["Cookie"]?.let { cookiesRecebidos += it }
                if (request.headers["Cookie"] != "painel_ia=$cookie") return MockResponse(code = 401)
                val arquivo = caminho.removePrefix("/dados/")
                substituicoes[arquivo]?.let { return it }
                val existe = IaFixtures::class.java.classLoader!!.getResource("ia/$arquivo") != null
                return if (existe) MockResponse(body = IaFixtures.texto(arquivo)) else MockResponse(code = 404)
            }
        }
        start()
    }

    val url: HttpUrl get() = servidor.url("/")

    override fun close() = servidor.close()
}

/** Um painel de IA local para a JVM de testes inteira. */
object IaDeTeste {
    val ia: IaLocal by lazy { IaLocal() }
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
        PainelDeTeste.painel.urlDaVersao,
        intervaloDeRecargaMs = 3_600_000,
        agendador = AgendadorDeTeste(),
        urlDeReserva = null,
        urlDaIa = IaDeTeste.ia.url,
        cofreDaIa = CofreEmMemoria(), // o Keystore do Android não existe no Robolectric
        cofreDaOrdem = CofreEmMemoria(),
    )
}
