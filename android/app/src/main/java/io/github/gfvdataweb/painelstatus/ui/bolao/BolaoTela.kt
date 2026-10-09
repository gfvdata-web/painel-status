package io.github.gfvdataweb.painelstatus.ui.bolao

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.data.DadosDoPainel
import io.github.gfvdataweb.painelstatus.data.modelo.Etapa
import io.github.gfvdataweb.painelstatus.data.modelo.Site
import io.github.gfvdataweb.painelstatus.ui.comum.SeloDaExecucao
import io.github.gfvdataweb.painelstatus.ui.comum.SeloNoAr
import io.github.gfvdataweb.painelstatus.ui.comum.Sparkline
import io.github.gfvdataweb.painelstatus.ui.comum.TextoComLink
import io.github.gfvdataweb.painelstatus.ui.comum.coresDoTom
import io.github.gfvdataweb.painelstatus.ui.comum.dataHora
import io.github.gfvdataweb.painelstatus.ui.comum.lembrarAbridorDeLinks
import io.github.gfvdataweb.painelstatus.ui.comum.textoDosDias
import io.github.gfvdataweb.painelstatus.ui.painel.EstadoDaEtapa
import io.github.gfvdataweb.painelstatus.ui.painel.estadoDaEtapa
import io.github.gfvdataweb.painelstatus.ui.painel.fluxoEmAndamento
import io.github.gfvdataweb.painelstatus.ui.painel.linkSeguro
import io.github.gfvdataweb.painelstatus.ui.painel.rotuloDaEtapa
import io.github.gfvdataweb.painelstatus.ui.painel.tomDaEtapa
import io.github.gfvdataweb.painelstatus.ui.theme.LocalCoresDoPainel
import java.time.Instant
import java.time.ZoneId

/**
 * Aba do Bolão F1 (card de destaque da página): situação do site e do
 * pipeline, o passo a passo da última atualização de dados e os atalhos.
 */
@Composable
fun BolaoTela(
    site: Site,
    aoAbrirHistorico: () -> Unit,
    modifier: Modifier = Modifier,
    intervaloMin: Long = 1,
    agora: Instant = Instant.now(),
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Cabecalho(site, agora) }
        if (fluxoEmAndamento(site)) {
            item { AvisoAoVivo(intervaloMin) }
        }
        if (site.etapasPipeline.isNotEmpty()) {
            item { CartaoDeEtapas(site.etapasPipeline, site.ultimaExecucao?.url) }
        }
        item { Atalhos(site, aoAbrirHistorico) }
    }
}

@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
private fun Cabecalho(site: Site, agora: Instant) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(LocalCoresDoPainel.current.f1, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(site.nome, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SeloNoAr(site)
                if (site.ultimaExecucao != null) {
                    SeloDaExecucao(site.ultimaExecucao, emVigilia = site.emVigilia)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Indicador(
                    stringResource(R.string.kpi_rodada),
                    site.rodadaMaisRecente?.toString() ?: stringResource(R.string.sem_valor),
                    Modifier.weight(1f),
                )
                Indicador(
                    stringResource(R.string.kpi_visitantes),
                    site.acesso?.visitantesUnicos?.toString() ?: stringResource(R.string.sem_valor),
                    Modifier.weight(1f),
                )
                Indicador(
                    stringResource(R.string.kpi_dados),
                    textoDosDias(site.ultimoCommitDados?.data, agora),
                    Modifier.weight(1f),
                )
            }
            val commit = site.ultimoCommitDados
            val mensagem = commit?.mensagem
            if (commit != null && mensagem != null) {
                TextoComLink(
                    stringResource(R.string.ultimo_commit, mensagem, commit.sha.orEmpty()),
                    commit.url,
                )
            }
            val serie = site.acesso?.serieDiaria.orEmpty()
            if (serie.size >= 2) {
                Text(
                    stringResource(R.string.visitas_por_dia, serie.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Sparkline(
                    serie.map { it.visitantes },
                    LocalCoresDoPainel.current.f1,
                    Modifier.fillMaxWidth().height(48.dp),
                )
            }
        }
    }
}

@Composable
private fun Indicador(rotulo: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = 8.dp)) {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** Faixa enquanto o fluxo anda: o app (aberto) confere o painel no mesmo ritmo da página. */
@Composable
private fun AvisoAoVivo(intervaloMin: Long) {
    val cores = LocalCoresDoPainel.current
    Card(
        colors = CardDefaults.cardColors(containerColor = cores.f1Fundo),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.ao_vivo_titulo),
                style = MaterialTheme.typography.titleSmall,
                color = cores.f1,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.ao_vivo_texto, intervaloMin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CartaoDeEtapas(etapas: List<Etapa>, urlDaExecucao: String?) {
    val abrir = lembrarAbridorDeLinks()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.etapas_titulo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            etapas.forEachIndexed { indice, etapa -> LinhaDaEtapa(etapa, indice + 1) }
            if (linkSeguro(urlDaExecucao) != null) {
                TextButton(onClick = { abrir(urlDaExecucao) }) { Text(stringResource(R.string.ver_execucao)) }
            }
        }
    }
}

@Composable
private fun LinhaDaEtapa(etapa: Etapa, numero: Int) {
    val estado = estadoDaEtapa(etapa.estado)
    val (frente, fundo) = coresDoTom(tomDaEtapa(estado))
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).background(fundo, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                marcaDaEtapa(estado, numero),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = frente,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(etapa.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val rotulo = rotuloDaEtapa(estado)?.let { stringResource(it) } ?: etapa.estado
            val quando = dataHora(etapa.quando, ZoneId.systemDefault())
            Text(
                if (quando != null) stringResource(R.string.par, rotulo, quando) else rotulo,
                style = MaterialTheme.typography.bodySmall,
                color = frente,
            )
            if (etapa.detalhe.isNotBlank()) {
                Text(
                    etapa.detalhe,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** ✓ … ⏳ ✕ – como na página; etapa ainda não iniciada mostra o número. */
@Composable
private fun marcaDaEtapa(estado: EstadoDaEtapa, numero: Int): String = when (estado) {
    EstadoDaEtapa.OK -> stringResource(R.string.marca_ok)
    EstadoDaEtapa.ANDAMENTO -> stringResource(R.string.marca_andamento)
    EstadoDaEtapa.AGUARDANDO -> stringResource(R.string.marca_aguardando)
    EstadoDaEtapa.ERRO -> stringResource(R.string.marca_erro)
    EstadoDaEtapa.PULADO -> stringResource(R.string.marca_pulado)
    EstadoDaEtapa.PENDENTE, EstadoDaEtapa.DESCONHECIDO -> numero.toString()
}

@Composable
private fun Atalhos(site: Site, aoAbrirHistorico: () -> Unit) {
    val abrir = lembrarAbridorDeLinks()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (linkSeguro(site.pagesUrl) != null) {
            OutlinedButton(onClick = { abrir(site.pagesUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.abrir_site_nome, site.nome))
            }
        }
        if (linkSeguro(site.formUrl) != null) {
            OutlinedButton(onClick = { abrir(site.formUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text(site.formRotulo ?: stringResource(R.string.abrir_form))
            }
        }
        if (DadosDoPainel.historicoValido(site.historicoArquivo)) {
            OutlinedButton(onClick = aoAbrirHistorico, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ver_historico))
            }
        }
        if (site.repo.isNotBlank()) {
            OutlinedButton(onClick = { abrir(urlDoRepositorio(site)) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.abrir_repositorio))
            }
        }
    }
}

/** Repositório do site na conta gfvdata-web (mesmo link do pop-up da página). */
fun urlDoRepositorio(site: Site): String = "https://github.com/gfvdata-web/${site.repo}"
