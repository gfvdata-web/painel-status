package io.github.gfvdataweb.painelstatus.ui.ia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.gfvdataweb.painelstatus.R

/** Aba IA: por ora só o aviso; o painel de uso do Claude entra no lugar (bloco B). */
@Composable
fun IaTela(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.ia_em_breve), style = MaterialTheme.typography.bodyLarge)
    }
}
