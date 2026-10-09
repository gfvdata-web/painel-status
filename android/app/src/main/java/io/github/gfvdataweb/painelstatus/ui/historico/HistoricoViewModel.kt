package io.github.gfvdataweb.painelstatus.ui.historico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gfvdataweb.painelstatus.data.DadosDoPainel
import io.github.gfvdataweb.painelstatus.data.EstadoDados
import io.github.gfvdataweb.painelstatus.data.lerHistorico
import io.github.gfvdataweb.painelstatus.data.modelo.Historico
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Histórico acumulado das execuções de um site (`dados/historico/<slug>.json`),
 * baixado ao abrir a tela, como o "Ver histórico completo" da página.
 */
class HistoricoViewModel(private val dados: DadosDoPainel, private val arquivo: String) : ViewModel() {

    private val _estado = MutableStateFlow(
        EstadoDados<Historico>(dados = null, atualizadoEm = null, atualizando = true, erro = null),
    )
    val estado: StateFlow<EstadoDados<Historico>> = _estado.asStateFlow()

    private var carga: Job? = null

    init {
        atualizar()
    }

    fun atualizar() {
        if (carga?.isActive == true) return
        carga = viewModelScope.launch {
            dados.observar(arquivo, ::lerHistorico).collect { _estado.value = it }
        }
    }

    companion object {
        fun fabrica(dados: DadosDoPainel, arquivo: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoricoViewModel(dados, arquivo) }
        }
    }
}
