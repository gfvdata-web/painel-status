package io.github.gfvdataweb.painelstatus.ui.painel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gfvdataweb.painelstatus.data.ordem.FonteDeOrdem
import io.github.gfvdataweb.painelstatus.data.ordem.ResultadoEntrada
import io.github.gfvdataweb.painelstatus.data.ordem.ResultadoSalvar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Por que o login não entrou. */
enum class ErroDeLogin { INCORRETO, FALHOU }

data class EstadoDaOrdem(
    /** Ordem salva no Worker (vazia = nenhuma). */
    val slugs: List<String> = emptyList(),
    val logada: Boolean = false,
    val editando: Boolean = false,
    /** Ordem em edição (só vale com [editando]). */
    val rascunho: List<String> = emptyList(),
    val pedindoLogin: Boolean = false,
    val erroDeLogin: ErroDeLogin? = null,
    val salvando: Boolean = false,
    val erroAoSalvar: Boolean = false,
    val entrando: Boolean = false,
)

/**
 * Ordem dos cards do app: lê a ordem salva ao abrir, faz o login (token no aparelho)
 * e controla o modo de edição. Quem desenha a lista usa [ordenar] com [EstadoDaOrdem.slugs]
 * (ou o rascunho, enquanto edita).
 */
class OrdemViewModel(private val fonte: FonteDeOrdem) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaOrdem(logada = fonte.logada))
    val estado: StateFlow<EstadoDaOrdem> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            val salvos = fonte.carregar()
            _estado.update { it.copy(slugs = salvos) }
        }
    }

    fun abrirLogin() = _estado.update { it.copy(pedindoLogin = true, erroDeLogin = null) }

    fun fecharLogin() = _estado.update { it.copy(pedindoLogin = false, erroDeLogin = null) }

    fun entrar(usuario: String, senha: String) {
        if (_estado.value.entrando) return
        _estado.update { it.copy(entrando = true, erroDeLogin = null) }
        viewModelScope.launch {
            val resultado = fonte.entrar(usuario.trim(), senha)
            _estado.update {
                when (resultado) {
                    ResultadoEntrada.ENTROU -> it.copy(entrando = false, logada = true, pedindoLogin = false)
                    ResultadoEntrada.INCORRETO -> it.copy(entrando = false, erroDeLogin = ErroDeLogin.INCORRETO)
                    ResultadoEntrada.FALHOU -> it.copy(entrando = false, erroDeLogin = ErroDeLogin.FALHOU)
                }
            }
        }
    }

    fun sair() {
        fonte.sair()
        _estado.update { it.copy(logada = false, editando = false, rascunho = emptyList(), erroAoSalvar = false) }
    }

    /** Começa a editar a partir da ordem que está na tela ([slugsNaTela]). */
    fun editar(slugsNaTela: List<String>) {
        if (!_estado.value.logada) return abrirLogin()
        _estado.update { it.copy(editando = true, rascunho = slugsNaTela, erroAoSalvar = false) }
    }

    fun cancelar() = _estado.update { it.copy(editando = false, rascunho = emptyList(), erroAoSalvar = false) }

    fun mover(de: Int, para: Int) = _estado.update { it.copy(rascunho = moverSlug(it.rascunho, de, para)) }

    fun salvar() {
        val atual = _estado.value
        if (!atual.editando || atual.salvando) return
        _estado.update { it.copy(salvando = true, erroAoSalvar = false) }
        viewModelScope.launch {
            val resultado = fonte.salvar(atual.rascunho)
            _estado.update {
                when (resultado) {
                    ResultadoSalvar.SALVOU ->
                        it.copy(salvando = false, editando = false, slugs = atual.rascunho, rascunho = emptyList())
                    // Token vencido ou recusado: já foi apagado; o rascunho fica para salvar depois do login.
                    ResultadoSalvar.SESSAO_EXPIRADA ->
                        it.copy(salvando = false, logada = false, pedindoLogin = true, erroDeLogin = null)
                    ResultadoSalvar.FALHOU -> it.copy(salvando = false, erroAoSalvar = true)
                }
            }
        }
    }

    companion object {
        fun fabrica(fonte: FonteDeOrdem): ViewModelProvider.Factory = viewModelFactory {
            initializer { OrdemViewModel(fonte) }
        }
    }
}
