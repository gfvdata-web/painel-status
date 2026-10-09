package io.github.gfvdataweb.painelstatus.data.avisos

import io.github.gfvdataweb.painelstatus.data.CacheDeArquivos
import io.github.gfvdataweb.painelstatus.data.leitorJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** O que avisar com o app fechado. Guardado só neste celular. */
@Serializable
data class PreferenciasDeAvisos(
    /** Liga a verificação em segundo plano. Começa desligado: o app pergunta antes. */
    val ativado: Boolean = false,
    /** Passo a passo do Bolão F1 (começou, aguardando resultado, página atualizada, falhou). */
    val bolao: Boolean = true,
    /** Alertas novos dos outros sites (fora do ar, coleta falhou ou parou). */
    val sites: Boolean = true,
    /** A faixa "Receber avisos?" já foi respondida: não aparece de novo. */
    val perguntado: Boolean = false,
)

/** Preferências num arquivo JSON pequeno (gravação atômica do [CacheDeArquivos]). */
class ArquivoDePreferencias(
    private val pasta: CacheDeArquivos,
    private val relogio: () -> Long = System::currentTimeMillis,
) {
    private val _atuais = MutableStateFlow(lerDoDisco())
    val atuais: StateFlow<PreferenciasDeAvisos> = _atuais.asStateFlow()

    fun salvar(novas: PreferenciasDeAvisos) {
        pasta.salvar(ARQUIVO, leitorJson.encodeToString(PreferenciasDeAvisos.serializer(), novas), relogio())
        _atuais.value = novas
    }

    private fun lerDoDisco(): PreferenciasDeAvisos {
        val texto = pasta.ler(ARQUIVO)?.texto ?: return PreferenciasDeAvisos()
        return try {
            leitorJson.decodeFromString(PreferenciasDeAvisos.serializer(), texto)
        } catch (e: IllegalArgumentException) {
            PreferenciasDeAvisos()
        }
    }

    private companion object {
        const val ARQUIVO = "preferencias.json"
    }
}
