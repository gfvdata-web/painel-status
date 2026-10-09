package io.github.gfvdataweb.painelstatus.data

import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** De onde vêm os JSONs do painel. Nos testes, um dublê em memória. */
fun interface Fonte {
    /**
     * [caminho] relativo à raiz do painel, ex.: `dados/status.json`. Chamada
     * bloqueante: quem usa roda fora da thread principal (ver [DadosDoPainel]).
     */
    fun baixarTexto(caminho: String): String
}

/** Baixa os JSONs publicados pelo painel no GitHub Pages. */
class FonteRemota(
    private val cliente: OkHttpClient,
    private val base: HttpUrl,
) : Fonte {
    override fun baixarTexto(caminho: String): String {
        val url = base.resolve(caminho) ?: throw IOException("Caminho inválido: $caminho")
        // Sempre da rede, como o fetch "no-store" da página: a recarga de 1 em
        // 1 min só serve se cada consulta pegar o status.json mais novo.
        val pedido = Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).build()
        cliente.newCall(pedido).execute().use { resposta ->
            if (!resposta.isSuccessful) {
                throw IOException("O painel respondeu ${resposta.code} para $caminho")
            }
            return resposta.body.string()
        }
    }
}
