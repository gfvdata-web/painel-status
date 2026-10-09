package io.github.gfvdataweb.painelstatus.ui.sites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.data.modelo.Site
import io.github.gfvdataweb.painelstatus.ui.comum.LinhaChaveValor
import io.github.gfvdataweb.painelstatus.ui.comum.LinhaDeAlerta
import io.github.gfvdataweb.painelstatus.ui.comum.SeloDaExecucao
import io.github.gfvdataweb.painelstatus.ui.comum.SeloNoAr
import io.github.gfvdataweb.painelstatus.ui.comum.coresDoTom
import io.github.gfvdataweb.painelstatus.ui.comum.lembrarAbridorDeLinks
import io.github.gfvdataweb.painelstatus.ui.comum.textoDosDias
import io.github.gfvdataweb.painelstatus.ui.painel.linkSeguro
import io.github.gfvdataweb.painelstatus.ui.painel.tomDosAlertas
import java.time.Instant

/** Aba Sites: um card por site da grade da página (todos menos o destaque). */
@Composable
fun SitesTela(
    sites: List<Site>,
    aoAbrirSite: (String) -> Unit,
    modifier: Modifier = Modifier,
    agora: Instant = Instant.now(),
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (sites.isEmpty()) {
            item { Text(stringResource(R.string.sem_sites), style = MaterialTheme.typography.bodyMedium) }
        }
        items(sites, key = { it.slug }) { site ->
            CartaoDoSite(site, agora, aoAbrir = if (site.detalhes != null) ({ aoAbrirSite(site.slug) }) else null)
        }
    }
}

@Composable
private fun CartaoDoSite(site: Site, agora: Instant, aoAbrir: (() -> Unit)?) {
    if (aoAbrir != null) {
        Card(onClick = aoAbrir, modifier = Modifier.fillMaxWidth()) { ConteudoDoCartao(site, agora, aoAbrir) }
    } else {
        Card(Modifier.fillMaxWidth()) { ConteudoDoCartao(site, agora, null) }
    }
}

@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
private fun ConteudoDoCartao(site: Site, agora: Instant, aoAbrir: (() -> Unit)?) {
    val abrir = lembrarAbridorDeLinks()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                site.nome,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            val tom = tomDosAlertas(site.alertas)
            if (tom != null) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = stringResource(R.string.alerta_descricao),
                    tint = coresDoTom(tom).first,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SeloNoAr(site)
            if (site.ultimaExecucao != null) SeloDaExecucao(site.ultimaExecucao)
        }
        site.alertas.forEach { LinhaDeAlerta(it) }
        LinhaChaveValor(stringResource(R.string.kpi_dados), textoDosDias(site.ultimoCommitDados?.data, agora))
        val inicio = site.periodoPublicado?.inicio
        val fim = site.periodoPublicado?.fim
        if (inicio != null && fim != null) {
            LinhaChaveValor(stringResource(R.string.periodo_coberto), stringResource(R.string.periodo, inicio, fim))
        }
        LinhaChaveValor(stringResource(R.string.kpi_visitantes), textoDosVisitantes(site))
        FlowRow(Modifier.fillMaxWidth()) {
            if (linkSeguro(site.pagesUrl) != null) {
                TextButton(onClick = { abrir(site.pagesUrl) }) { Text(stringResource(R.string.abrir_site)) }
            }
            if (linkSeguro(site.formUrl) != null) {
                TextButton(onClick = { abrir(site.formUrl) }) { Text(site.formRotulo ?: stringResource(R.string.abrir_form)) }
            }
            if (aoAbrir != null) {
                TextButton(onClick = aoAbrir) { Text(stringResource(R.string.ver_detalhes)) }
            }
        }
    }
}

/** Visitantes únicos; "sem rastreio" quando o GoatCounter não cobre o site. */
@Composable
fun textoDosVisitantes(site: Site): String {
    val acesso = site.acesso
    return when {
        acesso == null -> stringResource(R.string.sem_rastreio)
        acesso.visitantesUnicos != null -> acesso.visitantesUnicos.toString()
        else -> stringResource(R.string.sem_valor)
    }
}
