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
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
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
import io.github.gfvdataweb.painelstatus.ui.painel.ordenar
import io.github.gfvdataweb.painelstatus.ui.painel.sitesDaLista
import io.github.gfvdataweb.painelstatus.ui.painel.situacaoDoSite
import io.github.gfvdataweb.painelstatus.ui.painel.downloadsRecentes
import io.github.gfvdataweb.painelstatus.ui.painel.visitasRecentes
import io.github.gfvdataweb.painelstatus.ui.theme.LocalCoresDoPainel

/**
 * Aba Status: a lista da página principal (docs/js/lista.js). Um card por site, o
 * Bolão F1 (destaque) no topo; toque no card abre os detalhes do site.
 */
@Composable
fun StatusTela(
    status: Status,
    aoAbrirSite: (String) -> Unit,
    modifier: Modifier = Modifier,
    intervaloMin: Long = 1,
    /** Ordem dos cards no app (slugs); vazia = destaque primeiro e o resto no cadastro. */
    ordem: List<String> = emptyList(),
    /** Não nulo = modo de edição da ordem: [ordem] é o rascunho e cada card ganha os controles de mover. */
    edicao: EdicaoDaOrdem? = null,
) {
    val sites = ordenar(sitesDaLista(status.sites), ordem)
    // Slugs com o pipeline aberto; fica aqui para a recarga do status não fechá-los.
    val abertos = remember { mutableStateListOf<String>() }
    val lista = rememberLazyListState()
    var arrastando by remember { mutableStateOf<String?>(null) }
    var deslocamento by remember { mutableFloatStateOf(0f) }
    // O que o gesto vê é sempre a lista atual (o pointerInput não reinicia a cada troca).
    val sitesAgora by rememberUpdatedState(sites)
    LazyColumn(
        modifier.fillMaxSize(),
        state = lista,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (edicao != null) AvisoDeEdicao(edicao.erroAoSalvar)
                Cabecalho(sites)
            }
        }
        items(sites, key = { it.slug }) { site ->
            val aberto = site.slug in abertos
            val controles = edicao?.let { e ->
                val posicao = sites.indexOfFirst { it.slug == site.slug }
                ControlesDaOrdem(
                    nome = site.nome,
                    podeSubir = posicao > 0,
                    podeDescer = posicao < sites.lastIndex,
                    aoSubir = { e.aoMover(posicao, posicao - 1) },
                    aoDescer = { e.aoMover(posicao, posicao + 1) },
                    aoComecarArrasto = {
                        arrastando = site.slug
                        deslocamento = 0f
                    },
                    aoArrastar = { dy ->
                        deslocamento += dy
                        val troca = trocaDoArrasto(lista.layoutInfo.visibleItemsInfo, site.slug, deslocamento)
                        if (troca != null) {
                            val de = sitesAgora.indexOfFirst { it.slug == site.slug }
                            val para = sitesAgora.indexOfFirst { it.slug == troca.chave }
                            if (de >= 0 && para >= 0) {
                                e.aoMover(de, para)
                                deslocamento -= troca.ajuste
                            }
                        }
                    },
                    aoSoltar = {
                        arrastando = null
                        deslocamento = 0f
                    },
                )
            }
            val estaArrastando = arrastando == site.slug
            val animar = if (estaArrastando) Modifier else Modifier.animateItem()
            CartaoDoSite(
                site,
                pipelineAberto = aberto,
                aoAlternarPipeline = { if (aberto) abertos.remove(site.slug) else abertos.add(site.slug) },
                aoAbrir = { if (edicao == null) aoAbrirSite(site.slug) },
                intervaloMin = intervaloMin,
                controles = controles,
                modifier = animar.then(
                    if (estaArrastando) Modifier.zIndex(1f).graphicsLayer { translationY = deslocamento } else Modifier,
                ),
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
    modifier: Modifier = Modifier,
    controles: ControlesDaOrdem? = null,
) {
    val abrir = lembrarAbridorDeLinks()
    val situacao = situacaoDoSite(site)
    val categoria = categoriaDe(site.categoria)
    Card(onClick = aoAbrir, modifier = modifier.fillMaxWidth()) {
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
                downloadsRecentes(site)?.let { d ->
                    Text(
                        stringResource(R.string.downloads_linha, DIAS_VISITAS, d.recentes, d.skill, d.csv),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            if (controles != null) AlcaEBotoesDeOrdem(controles)
        }
    }
}

/** Modo de edição da ordem: [aoMover] troca de posição na lista que está na tela. */
class EdicaoDaOrdem(val aoMover: (de: Int, para: Int) -> Unit, val erroAoSalvar: Boolean = false)

/** O que um card em edição faz: setas (alternativa acessível) e o arrasto pela alça. */
class ControlesDaOrdem(
    val nome: String,
    val podeSubir: Boolean,
    val podeDescer: Boolean,
    val aoSubir: () -> Unit,
    val aoDescer: () -> Unit,
    val aoComecarArrasto: () -> Unit,
    val aoArrastar: (Float) -> Unit,
    val aoSoltar: () -> Unit,
)

/** Troca que o arrasto pede: com qual card, e quanto a posição do card arrastado mudou (para compensar o deslocamento). */
internal class TrocaDeArrasto(val chave: String, val ajuste: Float)

/**
 * O card arrastado ([chave], deslocado [deslocamento] px) passou do meio de outro card?
 * Só olha itens com chave de texto (os slugs); cabeçalho e "Outros forms" têm chave numérica.
 */
internal fun trocaDoArrasto(itens: List<LazyListItemInfo>, chave: String, deslocamento: Float): TrocaDeArrasto? {
    val arrastado = itens.firstOrNull { it.key == chave } ?: return null
    val centro = arrastado.offset + deslocamento + arrastado.size / 2f
    val alvo = itens.firstOrNull { item ->
        item.key is String && item.key != chave && centro >= item.offset && centro <= item.offset + item.size
    } ?: return null
    val novoInicio = if (alvo.offset > arrastado.offset) alvo.offset + alvo.size - arrastado.size else alvo.offset
    return TrocaDeArrasto(alvo.key as String, (novoInicio - arrastado.offset).toFloat())
}

@Composable
private fun AvisoDeEdicao(erroAoSalvar: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.ordem_editando),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (erroAoSalvar) {
            Text(
                stringResource(R.string.ordem_salvar_falhou),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Seta para cima, alça de arrastar (segurar e arrastar) e seta para baixo, na ponta do card. */
@Composable
private fun AlcaEBotoesDeOrdem(controles: ControlesDaOrdem) {
    val aoComecar by rememberUpdatedState(controles.aoComecarArrasto)
    val aoArrastar by rememberUpdatedState(controles.aoArrastar)
    val aoSoltar by rememberUpdatedState(controles.aoSoltar)
    Column(Modifier.padding(end = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = controles.aoSubir, enabled = controles.podeSubir) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.ordem_subir, controles.nome))
        }
        Box(
            Modifier
                .size(48.dp)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { aoComecar() },
                        onDrag = { mudanca, arrasto ->
                            mudanca.consume()
                            aoArrastar(arrasto.y)
                        },
                        onDragEnd = { aoSoltar() },
                        onDragCancel = { aoSoltar() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.ordem_alca, controles.nome))
        }
        IconButton(onClick = controles.aoDescer, enabled = controles.podeDescer) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.ordem_descer, controles.nome))
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
