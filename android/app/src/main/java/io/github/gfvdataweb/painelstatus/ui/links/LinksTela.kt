package io.github.gfvdataweb.painelstatus.ui.links

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.data.modelo.Status
import io.github.gfvdataweb.painelstatus.ui.comum.lembrarAbridorDeLinks
import io.github.gfvdataweb.painelstatus.ui.painel.Atalho
import io.github.gfvdataweb.painelstatus.ui.painel.atalhosDosForms
import io.github.gfvdataweb.painelstatus.ui.painel.atalhosDosSites

/** Aba Links: todos os sites e todos os Google Forms, um toque para abrir. */
@Composable
fun LinksTela(status: Status, modifier: Modifier = Modifier) {
    val sites = remember(status) { atalhosDosSites(status) }
    val forms = remember(status) { atalhosDosForms(status) }
    val padraoDoForm = stringResource(R.string.abrir_form)
    val formAvulso = stringResource(R.string.form_avulso)

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item { TituloDaSecao(stringResource(R.string.links_sites)) }
        items(sites, key = { "site:" + it.url }) { atalho ->
            LinhaDeAtalho(atalho.titulo.orEmpty(), atalho.detalhe.orEmpty(), atalho)
        }
        if (forms.isNotEmpty()) {
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TituloDaSecao(stringResource(R.string.links_forms))
            }
            items(forms, key = { "form:" + it.url }) { atalho ->
                LinhaDeAtalho(atalho.titulo ?: padraoDoForm, atalho.detalhe ?: formAvulso, atalho)
            }
        }
    }
}

@Composable
private fun TituloDaSecao(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun LinhaDeAtalho(titulo: String, detalhe: String, atalho: Atalho) {
    val abrir = lembrarAbridorDeLinks()
    ListItem(
        headlineContent = { Text(titulo) },
        supportingContent = { Text(detalhe, maxLines = 1) },
        modifier = Modifier.clickable { abrir(atalho.url) },
    )
}
