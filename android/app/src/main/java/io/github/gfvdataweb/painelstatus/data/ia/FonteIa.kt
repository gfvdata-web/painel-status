package io.github.gfvdataweb.painelstatus.data.ia

import io.github.gfvdataweb.painelstatus.data.CacheDeArquivos
import io.github.gfvdataweb.painelstatus.data.leitorJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** O que a tela de IA recebe de [FonteIa.carregar]. */
sealed interface ResultadoIa {
    /** [desatualizado]: a rede falhou e estes são os últimos dados guardados no aparelho. */
    data class Dados(val dados: DadosIa, val desatualizado: Boolean) : ResultadoIa

    /** Sem sessão, sessão vencida ou recusada pelo painel (401): é preciso digitar a senha. */
    data object PrecisaEntrar : ResultadoIa

    /** Rede ou formato falharam e não há nada guardado. */
    data object Erro : ResultadoIa
}

fun lerUso(texto: String): UsoIa = leitorJson.decodeFromString(texto)

fun lerLimites(texto: String): LimitesIa = leitorJson.decodeFromString(texto)

fun lerCatalogo(texto: String): CatalogoIa = leitorJson.decodeFromString(texto)

/**
 * Baixa `dados/{uso,limites,catalogo}.json` do painel de IA com o cookie da
 * [sessao]. Só o domínio do próprio painel, sem reserva. Cada arquivo bom vai
 * para o cache offline (são dados de uso, sem senha; só aparecem com sessão
 * válida e somem ao sair).
 */
class FonteIa(
    private val cliente: OkHttpClient,
    private val base: HttpUrl,
    private val sessao: SessaoIa,
    private val cache: CacheDeArquivos,
    private val relogio: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    val logada: Boolean get() = sessao.logada

    suspend fun entrar(senha: String): ResultadoLogin = sessao.entrar(senha)

    /** Apaga o cookie e os dados guardados. */
    fun sair() {
        sessao.sair()
        cache.limpar()
    }

    /** Último conjunto guardado (só com sessão válida), ou null. */
    suspend fun doCache(): DadosIa? = withContext(io) {
        if (!sessao.logada) null else dadosGuardados()
    }

    suspend fun carregar(): ResultadoIa = withContext(io) {
        val cookie = sessao.cabecalhoCookie() ?: return@withContext ResultadoIa.PrecisaEntrar
        try {
            val dados = DadosIa(
                uso = baixar("uso.json", cookie, ::lerUso),
                limites = baixar("limites.json", cookie, ::lerLimites),
                catalogo = baixar("catalogo.json", cookie, ::lerCatalogo),
            )
            ResultadoIa.Dados(dados, desatualizado = false)
        } catch (e: SessaoRecusada) {
            sair()
            ResultadoIa.PrecisaEntrar
        } catch (e: IOException) {
            guardadoOuErro()
        } catch (e: IllegalArgumentException) { // JSON que o app não entende (SerializationException)
            guardadoOuErro()
        }
    }

    /**
     * Só o limites.json, para a verificação em segundo plano. Sem sessão ou com 401 →
     * [ResultadoIa.PrecisaEntrar]; rede ou formato falhando → [ResultadoIa.Erro] (sem cache).
     */
    suspend fun carregarLimites(): ResultadoIa = withContext(io) {
        val cookie = sessao.cabecalhoCookie() ?: return@withContext ResultadoIa.PrecisaEntrar
        try {
            ResultadoIa.Dados(DadosIa(limites = baixar("limites.json", cookie, ::lerLimites)), desatualizado = false)
        } catch (e: SessaoRecusada) {
            sair()
            ResultadoIa.PrecisaEntrar
        } catch (e: IOException) {
            ResultadoIa.Erro
        } catch (e: IllegalArgumentException) {
            ResultadoIa.Erro
        }
    }

    private fun guardadoOuErro(): ResultadoIa =
        dadosGuardados()?.let { ResultadoIa.Dados(it, desatualizado = true) } ?: ResultadoIa.Erro

    private fun dadosGuardados(): DadosIa? {
        val dados = DadosIa(
            uso = lerGuardado("uso.json", ::lerUso),
            limites = lerGuardado("limites.json", ::lerLimites),
            catalogo = lerGuardado("catalogo.json", ::lerCatalogo),
        )
        return if (dados.vazio) null else dados
    }

    private fun <T> lerGuardado(nome: String, ler: (String) -> T): T? {
        val guardado = cache.ler("dados/$nome") ?: return null
        return try {
            ler(guardado.texto)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** null = 404 (o coletor ainda não publicou esse arquivo). */
    private fun <T> baixar(nome: String, cookie: String, ler: (String) -> T): T? {
        val url = base.resolve("dados/$nome") ?: throw IOException("Caminho inválido: $nome")
        val pedido = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        cliente.newCall(pedido).execute().use { resposta ->
            if (resposta.code == 401) throw SessaoRecusada()
            if (resposta.code == 404) return null
            if (!resposta.isSuccessful) throw IOException("O painel de IA respondeu ${resposta.code} para $nome")
            val texto = resposta.body.string()
            val valor = ler(texto) // confere antes de mexer no cache
            cache.salvar("dados/$nome", texto, relogio())
            return valor
        }
    }

    /** 401: o Worker não aceitou o cookie. Fora da hierarquia de IOException para não cair no cache. */
    private class SessaoRecusada : Exception()
}
