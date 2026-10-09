package io.github.gfvdataweb.painelstatus.ui.sobre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gfvdataweb.painelstatus.Enderecos
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.ui.comum.lembrarAbridorDeLinks

/** O que o app faz, a versão instalada e o aviso de versão ([extras]). */
@Composable
fun SobreTela(
    versao: String,
    build: Int,
    intervaloMin: Long,
    modifier: Modifier = Modifier,
    extras: @Composable () -> Unit = {},
) {
    val abrir = lembrarAbridorDeLinks()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_titulo), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.sobre_texto), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.sobre_recarga, intervaloMin),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { abrir(Enderecos.PAINEL) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.abrir_painel_web))
        }
        Text(
            stringResource(R.string.sobre_versao, versao, build),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        extras()
    }
}
