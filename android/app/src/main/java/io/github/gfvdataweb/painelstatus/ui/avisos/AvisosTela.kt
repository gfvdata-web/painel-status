package io.github.gfvdataweb.painelstatus.ui.avisos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.avisos.AgendadorWorkManager
import io.github.gfvdataweb.painelstatus.data.avisos.PreferenciasDeAvisos
import io.github.gfvdataweb.painelstatus.ui.comum.Idade
import io.github.gfvdataweb.painelstatus.ui.comum.coresDoTom
import io.github.gfvdataweb.painelstatus.ui.comum.idadeDosDados
import io.github.gfvdataweb.painelstatus.ui.painel.Tom
import java.time.ZoneId

/**
 * Avisos com o app fechado: liga/desliga, o que avisar, situação da
 * permissão do Android e um botão de teste.
 */
@Composable
fun AvisosTela(
    preferencias: PreferenciasDeAvisos,
    permitido: Boolean,
    ultimaVerificacao: Long?,
    aoAtivar: (Boolean) -> Unit,
    aoMudarBolao: (Boolean) -> Unit,
    aoMudarSites: (Boolean) -> Unit,
    aoTestar: () -> Unit,
    aoAbrirConfiguracoesDoAndroid: () -> Unit,
    modifier: Modifier = Modifier,
    agora: () -> Long = System::currentTimeMillis,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(
                R.string.avisos_explicacao,
                AgendadorWorkManager.INTERVALO_PERIODICO_MIN,
                AgendadorWorkManager.INTERVALO_RAPIDO_MIN,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        LinhaDeChave(
            titulo = stringResource(R.string.avisos_ativar),
            descricao = null,
            ligado = preferencias.ativado,
            habilitado = true,
            aoMudar = aoAtivar,
        )
        LinhaDeChave(
            titulo = stringResource(R.string.avisos_bolao),
            descricao = stringResource(R.string.avisos_bolao_descricao),
            ligado = preferencias.bolao,
            habilitado = preferencias.ativado,
            aoMudar = aoMudarBolao,
        )
        LinhaDeChave(
            titulo = stringResource(R.string.avisos_sites),
            descricao = stringResource(R.string.avisos_sites_descricao),
            ligado = preferencias.sites,
            habilitado = preferencias.ativado,
            aoMudar = aoMudarSites,
        )
        if (preferencias.ativado && !permitido) {
            val (frente, fundo) = coresDoTom(Tom.ERRO)
            Card(colors = CardDefaults.cardColors(containerColor = fundo)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.avisos_bloqueados),
                        style = MaterialTheme.typography.bodyMedium,
                        color = frente,
                    )
                    TextButton(onClick = aoAbrirConfiguracoesDoAndroid) {
                        Text(stringResource(R.string.avisos_abrir_configuracoes))
                    }
                }
            }
        }
        Text(
            textoDaUltimaVerificacao(ultimaVerificacao, agora()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = aoTestar, enabled = permitido, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.avisos_testar))
        }
        Text(
            stringResource(R.string.avisos_bateria),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LinhaDeChave(
    titulo: String,
    descricao: String?,
    ligado: Boolean,
    habilitado: Boolean,
    aoMudar: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = ligado, enabled = habilitado, role = Role.Switch, onValueChange = aoMudar)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                titulo,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (habilitado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (descricao != null) {
                Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = ligado, onCheckedChange = null, enabled = habilitado)
    }
}

@Composable
private fun textoDaUltimaVerificacao(quando: Long?, agora: Long): String {
    val idade = quando?.let { idadeDosDados(agora, it, ZoneId.systemDefault()) }
    return when (idade) {
        null -> stringResource(R.string.avisos_sem_verificacao)
        Idade.AgoraMesmo -> stringResource(R.string.avisos_ultima_verificacao, stringResource(R.string.idade_agora))
        is Idade.Minutos -> stringResource(R.string.avisos_ultima_verificacao, stringResource(R.string.idade_minutos, idade.quantos))
        is Idade.Horas -> stringResource(R.string.avisos_ultima_verificacao, stringResource(R.string.idade_horas, idade.quantas))
        is Idade.EmData -> stringResource(R.string.avisos_ultima_verificacao, stringResource(R.string.idade_data, idade.texto))
    }
}

/** Faixa no topo, uma vez só: "Avisar com o app fechado?" com Agora não / Ativar. */
@Composable
fun FaixaDeAvisos(aoAtivar: () -> Unit, aoDispensar: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Text(
                stringResource(R.string.faixa_avisos_titulo),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.faixa_avisos_texto),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = aoDispensar) { Text(stringResource(R.string.agora_nao)) }
                TextButton(onClick = aoAtivar) { Text(stringResource(R.string.faixa_avisos_ativar)) }
            }
        }
    }
}
