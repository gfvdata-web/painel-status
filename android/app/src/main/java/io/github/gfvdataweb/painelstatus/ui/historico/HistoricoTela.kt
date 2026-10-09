package io.github.gfvdataweb.painelstatus.ui.historico

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.data.modelo.Execucao
import io.github.gfvdataweb.painelstatus.data.modelo.Historico
import io.github.gfvdataweb.painelstatus.ui.comum.Bolinha
import io.github.gfvdataweb.painelstatus.ui.comum.CaixaDeMotivo
import io.github.gfvdataweb.painelstatus.ui.comum.LinhaDeDetalhe
import io.github.gfvdataweb.painelstatus.ui.comum.Secao
import io.github.gfvdataweb.painelstatus.ui.comum.TextoComLink
import io.github.gfvdataweb.painelstatus.ui.comum.coresDoTom
import io.github.gfvdataweb.painelstatus.ui.comum.dataHora
import io.github.gfvdataweb.painelstatus.ui.comum.textoDaDuracao
import io.github.gfvdataweb.painelstatus.ui.comum.textoDoEvento
import io.github.gfvdataweb.painelstatus.ui.comum.textoDoResultado
import io.github.gfvdataweb.painelstatus.ui.painel.Tom
import io.github.gfvdataweb.painelstatus.ui.painel.avisosProprios
import io.github.gfvdataweb.painelstatus.ui.painel.resumir
import io.github.gfvdataweb.painelstatus.ui.painel.semProblemas
import io.github.gfvdataweb.painelstatus.ui.painel.tomDoResultado
import java.time.ZoneId

/** Histórico completo das execuções de um site, com o filtro "só com falha ou aviso". */
@Composable
fun HistoricoTela(historico: Historico, modifier: Modifier = Modifier) {
    val resumo = remember(historico) { resumir(historico) }
    var soProblemas by rememberSaveable { mutableStateOf(false) }
    var verRecorrentes by rememberSaveable { mutableStateOf(false) }
    val visiveis = remember(historico, resumo, soProblemas) {
        if (soProblemas) historico.execucoes.filterNot { semProblemas(it, resumo.recorrentes) } else historico.execucoes
    }
    val fuso = ZoneId.systemDefault()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Secao(stringResource(R.string.hist_resumo)) {
                LinhaDeDetalhe(stringResource(R.string.hist_execucoes)) {
                    val desde = dataHora(resumo.maisAntiga, fuso)
                    Text(
                        if (desde != null) {
                            stringResource(R.string.hist_execucoes_desde, resumo.total, desde)
                        } else {
                            resumo.total.toString()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                LinhaDeDetalhe(stringResource(R.string.hist_sucesso)) {
                    Text(
                        stringResource(R.string.hist_sucesso_valor, resumo.sucessos, resumo.percentualDeSucesso),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                LinhaDeDetalhe(stringResource(R.string.hist_duracao)) {
                    Text(
                        stringResource(R.string.hist_duracao_valor, textoDaDuracao(resumo.duracaoTipicaS)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                LinhaDeDetalhe(stringResource(R.string.det_workflow)) {
                    TextoComLink(historico.workflow, historico.urlWorkflow)
                }
                if (resumo.recorrentes.isNotEmpty()) {
                    TextButton(onClick = { verRecorrentes = !verRecorrentes }) {
                        Text(stringResource(R.string.hist_recorrentes, resumo.recorrentes.size))
                    }
                    if (verRecorrentes) {
                        resumo.recorrentes.forEach { (mensagem, vezes) ->
                            Text(
                                stringResource(R.string.hist_recorrente_item, mensagem, vezes, resumo.lidas),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = soProblemas, role = Role.Checkbox, onValueChange = { soProblemas = it }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = soProblemas, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.hist_filtro), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (visiveis.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.hist_vazio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(visiveis) { execucao -> ItemDoHistorico(execucao, resumo.recorrentes, fuso) }
    }
}

@Composable
private fun ItemDoHistorico(execucao: Execucao, recorrentes: Map<String, Int>, fuso: ZoneId) {
    val tom = tomDoResultado(execucao.conclusao)
    val avisos = avisosProprios(execucao, recorrentes)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Bolinha(tom)
                Spacer(Modifier.width(8.dp))
                TextoComLink(dataHora(execucao.criadoEm, fuso) ?: stringResource(R.string.sem_valor), execucao.url)
                Spacer(Modifier.width(8.dp))
                Text(
                    textoDoResultado(execucao.conclusao),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = coresDoTom(tom).first,
                )
            }
            Text(
                stringResource(R.string.par, textoDoEvento(execucao.evento), textoDaDuracao(execucao.duracaoS)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            execucao.motivo?.let { CaixaDeMotivo(it) }
            avisos.forEach { aviso ->
                val tomDoAviso = if (aviso.nivel == "failure") Tom.ERRO else Tom.AVISO
                Text(
                    aviso.mensagem,
                    style = MaterialTheme.typography.bodySmall,
                    color = coresDoTom(tomDoAviso).first,
                )
            }
            if (execucao.avisos == null) {
                Text(
                    stringResource(R.string.hist_avisos_nao_lidos),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
