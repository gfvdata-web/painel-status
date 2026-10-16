package io.github.gfvdataweb.painelstatus.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.data.modelo.Site
import io.github.gfvdataweb.painelstatus.data.modelo.Status
import io.github.gfvdataweb.painelstatus.ui.comum.Bolinha
import io.github.gfvdataweb.painelstatus.ui.comum.LinhaDaEtapa
import io.github.gfvdataweb.painelstatus.ui.comum.coresDoTom
import io.github.gfvdataweb.painelstatus.ui.comum.lembrarAbridorDeLinks
import io.github.gfvdataweb.painelstatus.ui.painel.Categoria
import io.github.gfvdataweb.painelstatus.ui.painel.DIAS_VISITAS
import io.github.gfvdataweb.painelstatus.ui.painel.PipelineDoSite
import io.github.gfvdataweb.painelstatus.ui.painel.SubLinha
import io.github.gfvdataweb.painelstatus.ui.painel.Tom
import io.github.gfvdataweb.painelstatus.ui.painel.categoriaDe
import io.github.gfvdataweb.painelstatus.ui.painel.fluxoEmAndamento
import io.github.gfvdataweb.painelstatus.ui.painel.pipelineDoSite
import io.github.gfvdataweb.painelstatus.ui.painel.categoriasUsadas
import io.github.gfvdataweb.painelstatus.ui.painel.corDaCategoria
import io.github.gfvdataweb.painelstatus.ui.painel.linkSeguro
import io.github.gfvdataweb.painelstatus.ui.painel.resumoDosSites
import io.github.gfvdataweb.painelstatus.ui.painel.rotuloDaCategoria
import io.github.gfvdataweb.painelstatus.ui.painel.situacaoDoSite
import io.github.gfvdataweb.painelstatus.ui.painel.sitesNaOrdemDaLista
import io.github.gfvdataweb.painelstatus.ui.painel.visitasRecentes
import io.github.gfvdataweb.painelstatus.ui.theme.LocalCoresDoPainel

/**
 * Aba Status: a lista da página principal (docs/js/lista.js). Um card por site, o
 * Bolão F1 (destaque) no topo; toque no card abre os detalhes do site.
 */
@Composable
fun StatusTela(status: Status, aoAbrirSite: (String) -> Unit, modifier: Modifier = Modifier, intervaloMin: Long = 1) {
    val sites = sitesNaOrdemDaLista(status)
    // Slugs com o pipeline aberto; fica aqui para a recarga do status não fechá-los.
    val abertos = remember { mutableStateListOf<String>() }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Cabecalho(sites) }
        items(sites, key = { it.slug }) { site ->
            val aberto = site.slug in abertos
            CartaoDoSite(
                site,
                pipelineAberto = aberto,
                aoAlternarPipeline = { if (aberto) abertos.remove(site.slug) else abertos.add(site.slug) },
                aoAbrir = { aoAbrirSite(site.slug) },
                intervaloMin = intervaloMin,
            )
        }
        if (status.formsAvulsos.isNotEmpty()) {
            item { OutrosForms(status) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
private fun Cabecalho(sites: List<Site>) {
    val resumo = resumoDosSites(sites)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (resumo.foraDoAr == 0) {
                stringResource(R.string.resumo_todos_no_ar, resumo.total)
            } else {
                stringResource(R.string.resumo_com_problema, resumo.total, resumo.foraDoAr)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        // Legenda: só aparece depois que alguma categoria estiver atribuída.
        val usadas = categoriasUsadas(sites)
        if (usadas.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                usadas.forEach { categoria ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(corDe(categoria), RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(rotuloDaCategoria(categoria)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun corDe(categoria: Categoria) = Color(corDaCategoria(categoria))

@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
private fun CartaoDoSite(
    site: Site,
    pipelineAberto: Boolean,
    aoAlternarPipeline: () -> Unit,
    aoAbrir: () -> Unit,
    intervaloMin: Long,
) {
    val abrir = lembrarAbridorDeLinks()
    val situacao = situacaoDoSite(site)
    val categoria = categoriaDe(site.categoria)
    Card(onClick = aoAbrir, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(corDe(categoria)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            site.nome,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextoDaSubLinha(situacao.subLinha, situacao.tom)
                    }
                    Spacer(Modifier.width(12.dp))
                    Visitas(site)
                    Spacer(Modifier.width(12.dp))
                    val rotulo = stringResource(R.string.par, stringResource(rotuloDaBolinha(situacao.tom)), stringResource(rotuloDaCategoria(categoria)))
                    Bolinha(situacao.tom, Modifier.semantics { contentDescription = rotulo })
                }
                if (site.destaque && fluxoEmAndamento(site)) AvisoAoVivo(intervaloMin)
                val pipeline = pipelineDoSite(site)
                val temPipeline = pipeline != null
                val temForm = linkSeguro(site.formUrl) != null
                if (temPipeline || temForm) {
                    FlowRow(Modifier.fillMaxWidth()) {
                        if (temForm) {
                            TextButton(onClick = { abrir(site.formUrl) }) {
                                Text(site.formRotulo ?: stringResource(R.string.abrir_form))
                            }
                        }
                        if (temPipeline) {
                            TextButton(onClick = aoAlternarPipeline) {
                                Text(stringResource(if (pipelineAberto) R.string.pipeline_ocultar else R.string.pipeline_mostrar))
                            }
                        }
                    }
                }
                if (pipeline != null && pipelineAberto) {
                    Pipeline(pipeline)
                }
            }
        }
    }
}

/** Faixa enquanto o fluxo do Bolão anda: o app (aberto) confere o painel no mesmo ritmo da página. */
@Composable
private fun AvisoAoVivo(intervaloMin: Long) {
    val cores = LocalCoresDoPainel.current
    Card(
        colors = CardDefaults.cardColors(containerColor = cores.f1Fundo),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
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
private fun TextoDaSubLinha(subLinha: SubLinha, tom: Tom) {
    val texto = when (subLinha) {
        is SubLinha.Endereco -> subLinha.texto
        is SubLinha.ForaDoAr ->
            if (subLinha.statusHttp != null) {
                stringResource(R.string.sub_fora_do_ar_http, subLinha.statusHttp)
            } else {
                stringResource(R.string.sub_fora_do_ar)
            }
        is SubLinha.Alertas -> subLinha.texto
        SubLinha.Vigilia -> stringResource(R.string.sub_vigilia)
    }
    val cor = if (subLinha is SubLinha.Endereco) MaterialTheme.colorScheme.onSurfaceVariant else coresDoTom(tom).first
    Text(texto, style = MaterialTheme.typography.bodySmall, color = cor, maxLines = 3, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Visitas(site: Site) {
    val visitas = visitasRecentes(site)
    val semRastreio = stringResource(R.string.visitas_sem_rastreio)
    Column(
        Modifier.semantics { if (visitas == null) contentDescription = semRastreio },
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            visitas?.toString() ?: stringResource(R.string.sem_valor),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.visitas_valor, DIAS_VISITAS),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun rotuloDaBolinha(tom: Tom): Int = when (tom) {
    Tom.AVISO -> R.string.bolinha_aviso
    Tom.ERRO -> R.string.bolinha_erro
    Tom.OK, Tom.NEUTRO -> R.string.bolinha_ok
}

/** Passo a passo da última atualização de dados, com o link da execução no GitHub. */
@Composable
private fun Pipeline(pipeline: PipelineDoSite) {
    val abrir = lembrarAbridorDeLinks()
    val url = pipeline.urlDaExecucao
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            stringResource(if (pipeline.doForms) R.string.pipeline_titulo_forms else R.string.pipeline_titulo),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        pipeline.etapas.forEachIndexed { indice, etapa -> LinhaDaEtapa(etapa, indice + 1) }
        if (linkSeguro(url) != null) {
            TextButton(onClick = { abrir(url) }) { Text(stringResource(R.string.ver_execucao)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
private fun OutrosForms(status: Status) {
    val abrir = lembrarAbridorDeLinks()
    FlowRow(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.outros_forms),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        status.formsAvulsos.filter { linkSeguro(it.url) != null }.forEach { form ->
            TextButton(onClick = { abrir(form.url) }) { Text(stringResource(R.string.form_avulso_link, form.nome)) }
        }
    }
}
