package io.github.gfvdataweb.painelstatus.ui.avisos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gfvdataweb.painelstatus.avisos.Agendador
import io.github.gfvdataweb.painelstatus.data.avisos.ArquivoDePreferencias
import io.github.gfvdataweb.painelstatus.data.avisos.PreferenciasDeAvisos
import io.github.gfvdataweb.painelstatus.data.avisos.VigiaDoPainel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Preferências dos avisos com o app fechado. Toda mudança em "ativado" liga ou
 * desliga a verificação em segundo plano na hora.
 */
class AvisosViewModel(
    private val preferencias: ArquivoDePreferencias,
    private val agendador: Agendador,
    private val vigia: VigiaDoPainel,
) : ViewModel() {

    val estado: StateFlow<PreferenciasDeAvisos> = preferencias.atuais

    private val _ultimaVerificacao = MutableStateFlow<Long?>(null)

    /** Fim da última verificação em segundo plano (epoch ms), para a tela mostrar. */
    val ultimaVerificacao: StateFlow<Long?> = _ultimaVerificacao.asStateFlow()

    init {
        // Ao abrir o app, garante que a agenda bate com o que está salvo.
        agendador.aplicar(estado.value.ativado)
        recarregarUltimaVerificacao()
    }

    fun recarregarUltimaVerificacao() {
        viewModelScope.launch { _ultimaVerificacao.value = vigia.ultimaVerificacao() }
    }

    /** Liga/desliga (depois da permissão do Android, quando ela é pedida). Responde a faixa também. */
    fun definirAtivado(ativado: Boolean) {
        preferencias.salvar(estado.value.copy(ativado = ativado, perguntado = true))
        agendador.aplicar(ativado)
    }

    /** "Agora não" na faixa: não pergunta de novo (dá para ligar na tela de avisos). */
    fun dispensar() = preferencias.salvar(estado.value.copy(perguntado = true))

    fun mudarBolao(sim: Boolean) = preferencias.salvar(estado.value.copy(bolao = sim))

    fun mudarSites(sim: Boolean) = preferencias.salvar(estado.value.copy(sites = sim))

    companion object {
        fun fabrica(
            preferencias: ArquivoDePreferencias,
            agendador: Agendador,
            vigia: VigiaDoPainel,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AvisosViewModel(preferencias, agendador, vigia) }
        }
    }
}
