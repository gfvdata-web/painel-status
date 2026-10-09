package io.github.gfvdataweb.painelstatus.ui.painel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gfvdataweb.painelstatus.data.DadosDoPainel
import io.github.gfvdataweb.painelstatus.data.ErroDeDados
import io.github.gfvdataweb.painelstatus.data.EstadoDados
import io.github.gfvdataweb.painelstatus.data.lerStatus
import io.github.gfvdataweb.painelstatus.data.modelo.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * O status.json do painel, compartilhado por todas as telas (um download só).
 * Abre com o cache e baixa na hora; depois, enquanto o app está visível,
 * [acompanhar] confere de novo a cada [intervaloMs] — o mesmo ritmo da
 * página, para o passo a passo do Bolão F1 andar junto.
 */
class PainelViewModel(
    private val dados: DadosDoPainel,
    private val intervaloMs: Long,
    private val relogio: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _estado = MutableStateFlow(
        EstadoDados<Status>(dados = null, atualizadoEm = null, atualizando = true, erro = null),
    )
    val estado: StateFlow<EstadoDados<Status>> = _estado.asStateFlow()

    private var carga: Job? = null

    /** Fim da última consulta ao painel, com ou sem sucesso (relógio [relogio]). */
    private var ultimaConsulta = 0L

    init {
        carga = viewModelScope.launch {
            dados.doCache(DadosDoPainel.STATUS, ::lerStatus)?.let { guardado ->
                _estado.value = EstadoDados(guardado.valor, guardado.salvoEm, atualizando = true, erro = null)
            }
            consultar()
        }
    }

    /** "Puxar para atualizar": busca agora; ignora se já há uma busca em andamento. */
    fun atualizar() {
        if (carga?.isActive == true) return
        _estado.update { it.copy(atualizando = true) }
        carga = viewModelScope.launch { consultar() }
    }

    /**
     * Recarga periódica e silenciosa (sem indicador de "carregando"). Roda até
     * ser cancelada: a tela chama dentro de `repeatOnLifecycle(STARTED)`, então
     * para com o app em segundo plano e, ao voltar, confere na hora se o último
     * download já tem mais de [intervaloMs].
     */
    suspend fun acompanhar() {
        carga?.join()
        while (true) {
            val espera = ultimaConsulta + intervaloMs - relogio()
            if (espera > 0) delay(espera)
            if (carga?.isActive != true && relogio() - ultimaConsulta >= intervaloMs) {
                carga = viewModelScope.launch { consultar() }
            }
            carga?.join()
        }
    }

    private suspend fun consultar() {
        val novo = try {
            val baixado = dados.baixar(DadosDoPainel.STATUS, ::lerStatus)
            EstadoDados(baixado.valor, baixado.salvoEm, atualizando = false, erro = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val erro = ErroDeDados.de(e) ?: throw e
            // Mantém o que já está na tela (cache ou download anterior).
            _estado.value.copy(atualizando = false, erro = erro)
        } finally {
            ultimaConsulta = relogio()
        }
        _estado.value = novo
    }

    companion object {
        fun fabrica(dados: DadosDoPainel, intervaloMs: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { PainelViewModel(dados, intervaloMs) }
        }
    }
}
