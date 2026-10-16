package io.github.gfvdataweb.painelstatus.data.ia

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Resultado de [SessaoIa.entrar]. */
enum class ResultadoLogin { ENTROU, SENHA_ERRADA }

/**
 * Login do painel de IA (ia.gfvdata.com, privado). O Worker confere a senha em
 * `POST /entrar` e responde 303 com `Set-Cookie: painel_ia=<expira em ms>.<hmac>`
 * (senha certa) ou 303 para `/?erro` sem cookie (senha errada). Aqui só o
 * cookie fica guardado (cifrado, no [cofre]); a senha nunca é gravada.
 */
class SessaoIa(
    cliente: OkHttpClient,
    private val base: HttpUrl,
    private val cofre: CofreDeSegredo,
    private val relogio: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    // Sem seguir redirecionamento: o 303 é a resposta que interessa (e o cookie vem nele).
    private val clienteSemRedirecionar = cliente.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** Lança IOException se o painel não responde (sem internet, resposta inesperada). */
    suspend fun entrar(senha: String): ResultadoLogin = withContext(io) {
        val url = base.resolve("entrar") ?: throw IOException("Endereço inválido")
        val pedido = Request.Builder().url(url).post(FormBody.Builder().add("senha", senha).build()).build()
        clienteSemRedirecionar.newCall(pedido).execute().use { resposta ->
            if (resposta.code != 303) throw IOException("O painel de IA respondeu ${resposta.code}")
            val valor = resposta.headers("Set-Cookie").firstNotNullOfOrNull { valorDoCookie(it) }
                ?: return@use ResultadoLogin.SENHA_ERRADA
            if (expiraEm(valor) == null) throw IOException("Cookie do painel de IA em formato inesperado")
            cofre.guardar(valor)
            ResultadoLogin.ENTROU
        }
    }

    /** Valor do cookie (`<expira>.<hmac>`) enquanto não venceu; vencido ou ausente, apaga e devolve null. */
    fun cookie(): String? {
        val valor = cofre.ler() ?: return null
        val expira = expiraEm(valor)
        if (expira == null || expira <= relogio()) {
            cofre.apagar()
            return null
        }
        return valor
    }

    val logada: Boolean get() = cookie() != null

    /** Cabeçalho `Cookie` para os pedidos de dados, ou null sem sessão. */
    fun cabecalhoCookie(): String? = cookie()?.let { "$NOME_DO_COOKIE=$it" }

    /** O Worker recusou o cookie (401) ou a pessoa saiu: esquece. */
    fun sair() = cofre.apagar()

    companion object {
        const val NOME_DO_COOKIE = "painel_ia"

        /** `painel_ia=<valor>; Path=/; ...` → `<valor>`; null para outro cookie ou cookie vazio (logout). */
        fun valorDoCookie(setCookie: String): String? {
            val par = setCookie.substringBefore(';').trim()
            if (!par.startsWith("$NOME_DO_COOKIE=")) return null
            return par.removePrefix("$NOME_DO_COOKIE=").ifEmpty { null }
        }

        /** Expiração (epoch ms) que vem antes do ponto no valor do cookie; null se não for do formato `<ms>.<hmac>`. */
        fun expiraEm(valor: String): Long? {
            val (expira, assinatura) = valor.split('.', limit = 2).let { it[0] to it.getOrNull(1) }
            return if (assinatura.isNullOrEmpty()) null else expira.toLongOrNull()
        }
    }
}
