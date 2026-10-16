package io.github.gfvdataweb.painelstatus.ui.ia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gfvdataweb.painelstatus.data.ia.DadosIa
import io.github.gfvdataweb.painelstatus.data.ia.FonteIa
import io.github.gfvdataweb.painelstatus.data.ia.LimitesIa
import io.github.gfvdataweb.painelstatus.data.ia.ResultadoIa
import io.github.gfvdataweb.painelstatus.data.ia.ResultadoLogin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Por que o login não passou. */
enum class FalhaDoLogin { SENHA_ERRADA, SEM_CONEXAO }

/** O que a aba IA mostra. */
sealed interface EstadoIa {
    /** Sem sessão: pede a senha. [entrando] = pedido em andamento; [falha] = resposta do último envio. */
    data class PrecisaEntrar(val entrando: Boolean = false, val falha: FalhaDoLogin? = null) : EstadoIa

    data object Carregando : EstadoIa

    /** [desatualizado]: sem rede, são os últimos dados guardados no aparelho. */
    data class Pronto(val dados: DadosIa, val desatualizado: Boolean) : EstadoIa

    data object Erro : EstadoIa
}

/**
 * Painel de IA: login, dados dos três JSONs e recarga a cada [intervaloMs]
 * enquanto a aba está visível ([acompanhar]). Só existe quando a pessoa abre a
 * aba IA: quem nunca usa não faz nenhum pedido.
 */
class IaViewModel(
    private val fonte: FonteIa,
    private val intervaloMs: Long,
    private val relogio: () -> Long = System::currentTimeMillis,
    /** Chamado a cada limites.json baixado da rede: a verificação em segundo plano não repete o que a tela mostrou. */
    private val aoVerLimites: suspend (LimitesIa) -> Unit = {},
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoIa>(EstadoIa.Carregando)
    val estado: StateFlow<EstadoIa> = _estado.asStateFlow()

    private val _atualizando = MutableStateFlow(false)

    /** Há uma consulta em andamento com dados já na tela ("puxar para atualizar"). */
    val atualizando: StateFlow<Boolean> = _atualizando.asStateFlow()

    private var carga: Job? = null
    private var ultimaConsulta = 0L

    init {
        carga = viewModelScope.launch {
            if (!fonte.logada) {
                _estado.value = EstadoIa.PrecisaEntrar()
                return@launch
            }
            fonte.doCache()?.let { _estado.value = EstadoIa.Pronto(it, desatualizado = true) }
            consultar()
        }
    }

    /** Manda a senha; a senha não é guardada, só o cookie que o painel devolve. */
    fun entrar(senha: String) {
        if (senha.isEmpty() || _estado.value.let { it is EstadoIa.PrecisaEntrar && it.entrando }) return
        _estado.value = EstadoIa.PrecisaEntrar(entrando = true)
        carga = viewModelScope.launch {
            val resultado = try {
                fonte.entrar(senha)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _estado.value = EstadoIa.PrecisaEntrar(falha = FalhaDoLogin.SEM_CONEXAO)
                return@launch
            }
            if (resultado == ResultadoLogin.SENHA_ERRADA) {
                _estado.value = EstadoIa.PrecisaEntrar(falha = FalhaDoLogin.SENHA_ERRADA)
            } else {
                _estado.value = EstadoIa.Carregando
                consultar()
            }
        }
    }

    /** Apaga cookie e dados guardados e volta para a tela de senha. */
    fun sair() {
        carga?.cancel()
        fonte.sair()
        _atualizando.value = false
        _estado.value = EstadoIa.PrecisaEntrar()
    }

    /** "Puxar para atualizar" / "Tentar de novo": ignora se já há consulta em andamento. */
    fun atualizar() {
        if (carga?.isActive == true) return
        if (_estado.value !is EstadoIa.Pronto) _estado.value = EstadoIa.Carregando
        carga = viewModelScope.launch { consultar() }
    }

    /** Recarga periódica, dentro de `repeatOnLifecycle(STARTED)`: para com o app em segundo plano. */
    suspend fun acompanhar() {
        carga?.join()
        while (true) {
            val espera = ultimaConsulta + intervaloMs - relogio()
            if (espera > 0) {
                delay(espera)
                continue
            }
            if (_estado.value is EstadoIa.PrecisaEntrar) {
                ultimaConsulta = relogio() // sem sessão não há o que conferir
                continue
            }
            if (carga?.isActive != true) carga = viewModelScope.launch { consultar() }
            carga?.join()
        }
    }

    private suspend fun consultar() {
        _atualizando.value = true
        try {
            _estado.value = when (val resultado = fonte.carregar()) {
                is ResultadoIa.Dados -> {
                    if (!resultado.desatualizado) resultado.dados.limites?.let { aoVerLimites(it) }
                    EstadoIa.Pronto(resultado.dados, resultado.desatualizado)
                }
                ResultadoIa.PrecisaEntrar -> EstadoIa.PrecisaEntrar()
                ResultadoIa.Erro -> EstadoIa.Erro
            }
        } finally {
            _atualizando.value = false
            ultimaConsulta = relogio()
        }
    }

    companion object {
        fun fabrica(
            fonte: FonteIa,
            intervaloMs: Long,
            aoVerLimites: suspend (LimitesIa) -> Unit = {},
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { IaViewModel(fonte, intervaloMs, aoVerLimites = aoVerLimites) }
        }
    }
}
