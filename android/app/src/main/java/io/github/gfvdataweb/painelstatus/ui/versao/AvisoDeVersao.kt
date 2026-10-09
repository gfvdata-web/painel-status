package io.github.gfvdataweb.painelstatus.ui.versao

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.data.VerificadorDeAtualizacao
import io.github.gfvdataweb.painelstatus.data.VersaoPublicada
import io.github.gfvdataweb.painelstatus.data.haAtualizacao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoDaVersao(
    /** Versão oficial mais recente, se a consulta funcionou. */
    val publicada: VersaoPublicada? = null,
    val buildInstalado: Int,
    val avisoDispensado: Boolean = false,
) {
    val temAtualizacao: Boolean get() = haAtualizacao(publicada, buildInstalado)
    val mostrarAviso: Boolean get() = temAtualizacao && !avisoDispensado
}

/** Consulta os Releases uma vez ao abrir o app. */
class VersaoViewModel(verificador: VerificadorDeAtualizacao, buildInstalado: Int) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaVersao(buildInstalado = buildInstalado))
    val estado: StateFlow<EstadoDaVersao> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            val publicada = verificador.maisRecente()
            _estado.update { it.copy(publicada = publicada) }
        }
    }

    /** "Agora não": esconde o aviso até o app ser aberto de novo. */
    fun dispensar() = _estado.update { it.copy(avisoDispensado = true) }

    companion object {
        fun fabrica(verificador: VerificadorDeAtualizacao, buildInstalado: Int): ViewModelProvider.Factory =
            viewModelFactory { initializer { VersaoViewModel(verificador, buildInstalado) } }
    }
}

/** Faixa no topo: "Nova versão X disponível" com Baixar / Agora não. */
@Composable
fun AvisoDeNovaVersao(
    publicada: VersaoPublicada,
    aoBaixar: () -> Unit,
    aoDispensar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(
                    stringResource(R.string.versao_nova, publicada.versao),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    stringResource(R.string.versao_nova_dica),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            TextButton(onClick = aoDispensar) {
                Text(stringResource(R.string.agora_nao), color = MaterialTheme.colorScheme.onPrimary)
            }
            TextButton(onClick = aoBaixar) {
                Text(stringResource(R.string.baixar), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

/** Linha da tela "Sobre". */
@Composable
fun SituacaoDaVersao(estado: EstadoDaVersao, aoBaixar: () -> Unit) {
    val publicada = estado.publicada
    when {
        publicada == null -> Text(
            stringResource(R.string.versao_sem_info),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        estado.temAtualizacao -> TextButton(onClick = aoBaixar) {
            Text(stringResource(R.string.versao_baixar, publicada.versao))
        }
        else -> Text(
            stringResource(R.string.versao_em_dia),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
