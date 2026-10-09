package io.github.gfvdataweb.painelstatus.ui.comum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.data.ErroDeDados
import io.github.gfvdataweb.painelstatus.data.EstadoDados
import io.github.gfvdataweb.painelstatus.data.modelo.Alerta
import io.github.gfvdataweb.painelstatus.data.modelo.Execucao
import io.github.gfvdataweb.painelstatus.data.modelo.Motivo
import io.github.gfvdataweb.painelstatus.data.modelo.Site
import io.github.gfvdataweb.painelstatus.ui.painel.SituacaoDaExecucao
import io.github.gfvdataweb.painelstatus.ui.painel.Tom
import io.github.gfvdataweb.painelstatus.ui.painel.linkSeguro
import io.github.gfvdataweb.painelstatus.ui.painel.rotuloDoEvento
import io.github.gfvdataweb.painelstatus.ui.painel.rotuloDoResultado
import io.github.gfvdataweb.painelstatus.ui.painel.situacaoDaExecucao
import io.github.gfvdataweb.painelstatus.ui.painel.tomDoAlerta
import io.github.gfvdataweb.painelstatus.ui.theme.LocalCoresDoPainel
import java.time.Instant
import java.time.ZoneId

/**
 * Moldura comum das telas: carregando, erro sem dados, ou o conteúdo com
 * "puxar para atualizar" e a linha de quando os dados foram conferidos
 * ([complemento] entra no fim dela, ex.: quando o status foi gerado).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> ConteudoComDados(
    estado: EstadoDados<T>,
    aoAtualizar: () -> Unit,
    modifier: Modifier = Modifier,
    complemento: String? = null,
    agora: () -> Long = System::currentTimeMillis,
    conteudo: @Composable (T) -> Unit,
) {
    val dados = estado.dados
    when {
        dados == null && estado.atualizando -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        dados == null -> SemDados(estado.erro, aoAtualizar, modifier)
        else -> PullToRefreshBox(
            isRefreshing = estado.atualizando,
            onRefresh = aoAtualizar,
            modifier = modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                LinhaDeAtualizacao(estado, agora(), complemento)
                Box(Modifier.weight(1f)) { conteudo(dados) }
            }
        }
    }
}

@Composable
private fun SemDados(erro: ErroDeDados?, aoTentarDeNovo: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                if (erro == ErroDeDados.FORMATO_INESPERADO) R.string.erro_formato else R.string.erro_sem_dados,
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = aoTentarDeNovo) { Text(stringResource(R.string.tentar_de_novo)) }
    }
}

@Composable
private fun LinhaDeAtualizacao(estado: EstadoDados<*>, agora: Long, complemento: String?) {
    val quando = estado.atualizadoEm ?: return
    val idade = when (val idade = idadeDosDados(agora, quando, ZoneId.systemDefault())) {
        Idade.AgoraMesmo -> stringResource(R.string.idade_agora)
        is Idade.Minutos -> stringResource(R.string.idade_minutos, idade.quantos)
        is Idade.Horas -> stringResource(R.string.idade_horas, idade.quantas)
        is Idade.EmData -> stringResource(R.string.idade_data, idade.texto)
    }
    val (texto, cor) = when (estado.erro) {
        null -> {
            val conferido = stringResource(R.string.conferido, idade)
            val linha = if (complemento != null) stringResource(R.string.par, conferido, complemento) else conferido
            linha to MaterialTheme.colorScheme.onSurfaceVariant
        }
        ErroDeDados.FALHA_NO_DOWNLOAD -> stringResource(R.string.offline_dados_salvos, idade) to MaterialTheme.colorScheme.error
        ErroDeDados.FORMATO_INESPERADO -> stringResource(R.string.formato_dados_salvos, idade) to MaterialTheme.colorScheme.error
    }
    Text(
        text = texto,
        style = MaterialTheme.typography.labelSmall,
        color = cor,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Cor do texto e do fundo de um [Tom], na paleta do painel. */
@Composable
fun coresDoTom(tom: Tom): Pair<Color, Color> {
    val cores = LocalCoresDoPainel.current
    return when (tom) {
        Tom.OK -> cores.ok to cores.okFundo
        Tom.AVISO -> cores.aviso to cores.avisoFundo
        Tom.ERRO -> cores.erro to cores.erroFundo
        Tom.NEUTRO -> cores.neutro to cores.neutroFundo
    }
}

/** Selo arredondado ("Site no ar", "Última execução OK"…), como os badges da página. */
@Composable
fun Selo(texto: String, tom: Tom, modifier: Modifier = Modifier) {
    val (frente, fundo) = coresDoTom(tom)
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = frente,
        modifier = modifier
            .background(fundo, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SeloNoAr(site: Site, modifier: Modifier = Modifier) {
    if (site.siteNoAr?.ok == true) {
        Selo(stringResource(R.string.selo_no_ar), Tom.OK, modifier)
    } else {
        Selo(stringResource(R.string.selo_fora_do_ar), Tom.ERRO, modifier)
    }
}

@Composable
fun SeloDaExecucao(execucao: Execucao?, modifier: Modifier = Modifier, emVigilia: Boolean = false) {
    when (situacaoDaExecucao(execucao, emVigilia)) {
        SituacaoDaExecucao.VIGILIA -> Selo(stringResource(R.string.selo_vigilia), Tom.AVISO, modifier)
        SituacaoDaExecucao.SEM_REGISTRO -> Selo(stringResource(R.string.selo_execucao_sem_registro), Tom.NEUTRO, modifier)
        SituacaoDaExecucao.OK -> Selo(stringResource(R.string.selo_execucao_ok), Tom.OK, modifier)
        SituacaoDaExecucao.FALHOU -> Selo(stringResource(R.string.selo_execucao_falhou), Tom.ERRO, modifier)
        SituacaoDaExecucao.EM_ANDAMENTO -> Selo(stringResource(R.string.selo_execucao_andamento), Tom.AVISO, modifier)
        SituacaoDaExecucao.OUTRA -> Selo(
            stringResource(R.string.selo_execucao_outra, textoDoResultado(execucao?.conclusao)),
            Tom.NEUTRO,
            modifier,
        )
    }
}

/** Bolinha colorida do histórico de execuções. */
@Composable
fun Bolinha(tom: Tom, modifier: Modifier = Modifier) {
    val (frente, _) = coresDoTom(tom)
    Box(modifier.size(12.dp).background(frente, CircleShape))
}

/** Alerta do painel (src/detalhes_site.py) numa faixa colorida. */
@Composable
fun LinhaDeAlerta(alerta: Alerta, modifier: Modifier = Modifier) {
    val (frente, fundo) = coresDoTom(tomDoAlerta(alerta))
    Text(
        alerta.texto,
        style = MaterialTheme.typography.bodySmall,
        color = frente,
        modifier = modifier
            .fillMaxWidth()
            .background(fundo, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Card com título, usado nas seções das telas de detalhe. */
@Composable
fun Secao(titulo: String, modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            conteudo()
        }
    }
}

/** Rótulo pequeno em cima e o valor embaixo (linhas do pop-up de detalhes da página). */
@Composable
fun LinhaDeDetalhe(rotulo: String, modifier: Modifier = Modifier, valor: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        valor()
    }
}

/** "Rótulo ........ valor" numa linha só (cards da lista de sites). */
@Composable
fun LinhaChaveValor(rotulo: String, valor: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            rotulo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Texto na cor de link que abre [url] no navegador (sem link válido, texto comum). */
@Composable
fun TextoComLink(texto: String, url: String?, modifier: Modifier = Modifier) {
    val abrir = lembrarAbridorDeLinks()
    val destino = linkSeguro(url)
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = if (destino != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = if (destino != null) modifier.clickable { abrir(destino) } else modifier,
    )
}

/** Abre um endereço no navegador; ignora o que não for http(s) e celular sem navegador. */
@Composable
fun lembrarAbridorDeLinks(): (String?) -> Unit {
    val navegador = LocalUriHandler.current
    return remember(navegador) {
        { url -> linkSeguro(url)?.let { runCatching { navegador.openUri(it) } } }
    }
}

/** Caixa com o motivo de uma falha (passo e linha de erro do log). */
@Composable
fun CaixaDeMotivo(motivo: Motivo, modifier: Modifier = Modifier) {
    val (frente, fundo) = coresDoTom(Tom.ERRO)
    Column(
        modifier
            .fillMaxWidth()
            .background(fundo, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val passo = motivo.passo
        Text(
            if (passo != null) stringResource(R.string.motivo_passo, passo) else stringResource(R.string.motivo),
            style = MaterialTheme.typography.labelLarge,
            color = frente,
        )
        val mensagem = motivo.mensagem
        Text(
            mensagem ?: stringResource(R.string.motivo_indisponivel),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mensagem != null) FontFamily.Monospace else null,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Linha de visitantes únicos por dia (série do GoatCounter). */
@Composable
fun Sparkline(valores: List<Int>, cor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (valores.size < 2) return@Canvas
        val maximo = valores.max().coerceAtLeast(1)
        val passo = size.width / (valores.size - 1)
        val margem = 2.dp.toPx()
        val altura = size.height - 2 * margem
        val caminho = Path()
        valores.forEachIndexed { indice, valor ->
            val x = indice * passo
            val y = margem + altura - valor.toFloat() / maximo * altura
            if (indice == 0) caminho.moveTo(x, y) else caminho.lineTo(x, y)
        }
        drawPath(caminho, cor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---------- Textos que dependem de recursos ----------

/** "sucesso", "falhou"…; resultado desconhecido aparece como veio. */
@Composable
fun textoDoResultado(resultado: String?): String {
    val rotulo = rotuloDoResultado(resultado)
    return when {
        rotulo != null -> stringResource(rotulo)
        resultado != null -> resultado
        else -> stringResource(R.string.sem_valor)
    }
}

@Composable
fun textoDoEvento(evento: String?): String {
    val rotulo = rotuloDoEvento(evento)
    return when {
        rotulo != null -> stringResource(rotulo)
        evento != null -> evento
        else -> stringResource(R.string.sem_valor)
    }
}

/** "hoje", "há 1 dia", "há 5 dias" (como a página); "sem registro" sem data. */
@Composable
fun textoDosDias(iso: String?, agora: Instant): String = when (val dias = diasDesde(iso, agora)) {
    null -> stringResource(R.string.sem_registro)
    0L -> stringResource(R.string.dias_hoje)
    1L -> stringResource(R.string.dias_um)
    else -> stringResource(R.string.dias_varios, dias)
}

/** "03/10/26 09:31 (há 5 dias)". */
@Composable
fun textoDataComDias(iso: String?, agora: Instant): String {
    val data = dataHora(iso, ZoneId.systemDefault())
    return if (data != null) {
        stringResource(R.string.data_com_dias, data, textoDosDias(iso, agora))
    } else {
        stringResource(R.string.sem_registro)
    }
}

@Composable
fun textoDaDuracao(segundos: Long?): String {
    val partes = segundos?.let(::duracao)
    return when {
        partes == null -> stringResource(R.string.sem_valor)
        partes.horas > 0 -> stringResource(R.string.duracao_horas, partes.horas, partes.minutos)
        partes.minutos > 0 -> stringResource(R.string.duracao_minutos, partes.minutos, partes.segundos)
        else -> stringResource(R.string.duracao_segundos, partes.segundos)
    }
}
