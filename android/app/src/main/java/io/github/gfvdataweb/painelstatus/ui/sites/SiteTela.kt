package io.github.gfvdataweb.painelstatus.ui.sites

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.data.modelo.Coleta
import io.github.gfvdataweb.painelstatus.data.modelo.Execucao
import io.github.gfvdataweb.painelstatus.data.modelo.Site
import io.github.gfvdataweb.painelstatus.ui.bolao.urlDoRepositorio
import io.github.gfvdataweb.painelstatus.ui.comum.Bolinha
import io.github.gfvdataweb.painelstatus.ui.comum.CaixaDeMotivo
import io.github.gfvdataweb.painelstatus.ui.comum.LinhaDeAlerta
import io.github.gfvdataweb.painelstatus.ui.comum.LinhaDeDetalhe
import io.github.gfvdataweb.painelstatus.ui.comum.Secao
import io.github.gfvdataweb.painelstatus.ui.comum.TextoComLink
import io.github.gfvdataweb.painelstatus.ui.comum.coresDoTom
import io.github.gfvdataweb.painelstatus.ui.comum.dataHora
import io.github.gfvdataweb.painelstatus.ui.comum.lembrarAbridorDeLinks
import io.github.gfvdataweb.painelstatus.ui.comum.textoDataComDias
import io.github.gfvdataweb.painelstatus.ui.comum.textoDoResultado
import io.github.gfvdataweb.painelstatus.ui.painel.Tom
import io.github.gfvdataweb.painelstatus.ui.painel.linkSeguro
import io.github.gfvdataweb.painelstatus.ui.painel.resultadoDe
import io.github.gfvdataweb.painelstatus.ui.painel.tomDoResultado
import java.time.Instant
import java.time.ZoneId

/** Detalhes de um site (o pop-up do card na página): rotina de coleta, dados e publicação. */
@Composable
fun SiteTela(
    site: Site,
    temHistorico: Boolean,
    aoAbrirHistorico: () -> Unit,
    modifier: Modifier = Modifier,
    agora: Instant = Instant.now(),
) {
    val detalhes = site.detalhes
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (site.alertas.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { site.alertas.forEach { LinhaDeAlerta(it) } }
            }
        }
        item { SecaoDaRotina(detalhes?.coleta, temHistorico, aoAbrirHistorico, agora) }
        item { SecaoDosDados(site, agora) }
        item { SecaoDaPublicacao(site, detalhes?.deployPages, agora) }
        item { Links(site) }
    }
}

@Composable
private fun SecaoDaRotina(coleta: Coleta?, temHistorico: Boolean, aoAbrirHistorico: () -> Unit, agora: Instant) {
    Secao(stringResource(R.string.secao_rotina)) {
        if (coleta == null) {
            Text(
                stringResource(R.string.sem_rotina),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RotinaDeColeta(coleta, temHistorico, aoAbrirHistorico, agora)
        }
    }
}

@Composable
private fun RotinaDeColeta(coleta: Coleta, temHistorico: Boolean, aoAbrirHistorico: () -> Unit, agora: Instant) {
    val (corDeErro, _) = coresDoTom(Tom.ERRO)
    val total = coleta.historico.size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        coleta.erro?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = corDeErro) }
        LinhaDeDetalhe(stringResource(R.string.det_workflow)) { TextoComLink(coleta.workflow, coleta.urlWorkflow) }
        LinhaDeDetalhe(stringResource(R.string.det_agenda)) {
            if (coleta.agenda.isEmpty()) {
                Text(stringResource(R.string.sem_agenda), style = MaterialTheme.typography.bodyMedium)
            } else {
                coleta.agenda.forEach {
                    Text(stringResource(R.string.agenda_item, it.descricao, it.cron), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        LinhaDeDetalhe(stringResource(R.string.det_ultimo_sucesso)) {
            val sucesso = coleta.ultimoSucesso
            if (sucesso != null) {
                TextoComLink(textoDataComDias(sucesso.criadoEm, agora), sucesso.url)
            } else {
                Text(
                    stringResource(R.string.nenhum_sucesso, total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = corDeErro,
                )
            }
        }
        LinhaDeDetalhe(stringResource(R.string.det_ultima_falha)) {
            val falha = coleta.ultimaFalha
            if (falha != null) {
                TextoComLink(
                    stringResource(R.string.par, textoDataComDias(falha.criadoEm, agora), textoDoResultado(falha.conclusao)),
                    falha.url,
                )
                falha.motivo?.let { CaixaDeMotivo(it, Modifier.padding(top = 4.dp)) }
            } else {
                Text(stringResource(R.string.nenhuma_falha, total), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (coleta.historico.isNotEmpty()) {
            LinhaDeDetalhe(stringResource(R.string.det_historico)) {
                Bolinhas(coleta.historico)
                val concluidas = coleta.historico.filter { it.status == "completed" }
                Text(
                    stringResource(
                        R.string.historico_resumo,
                        concluidas.count { it.conclusao == "success" },
                        concluidas.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (temHistorico) {
                    TextButton(onClick = aoAbrirHistorico) { Text(stringResource(R.string.ver_historico_completo)) }
                }
            }
        }
        if (coleta.passosUltima.isNotEmpty()) {
            LinhaDeDetalhe(stringResource(R.string.det_passos)) {
                coleta.passosUltima.forEach { passo ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Bolinha(tomDoResultado(passo.conclusao))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.par, passo.nome, textoDoResultado(passo.conclusao)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/** Execuções recentes em bolinhas, da mais antiga (esquerda) para a mais recente; toque abre no GitHub. */
@Composable
private fun Bolinhas(historico: List<Execucao>) {
    val abrir = lembrarAbridorDeLinks()
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        historico.asReversed().forEach { execucao ->
            val tom = tomDoResultado(resultadoDe(execucao))
            val url = linkSeguro(execucao.url)
            Bolinha(tom, if (url != null) Modifier.clickable { abrir(url) } else Modifier)
        }
    }
}

@Composable
private fun SecaoDosDados(site: Site, agora: Instant) {
    val ultimo = site.ultimoCommitDados
    val commits = site.detalhes?.commitsDados.orEmpty()
    Secao(stringResource(R.string.secao_dados)) {
        LinhaDeDetalhe(stringResource(R.string.det_ultima_atualizacao)) {
            val data = ultimo?.data
            val erro = ultimo?.erro
            val texto = when {
                data != null -> textoDataComDias(data, agora)
                erro != null -> erro
                else -> stringResource(R.string.sem_registro)
            }
            Text(texto, style = MaterialTheme.typography.bodyMedium)
        }
        val inicio = site.periodoPublicado?.inicio
        val fim = site.periodoPublicado?.fim
        if (inicio != null && fim != null) {
            LinhaDeDetalhe(stringResource(R.string.periodo_coberto)) {
                Text(stringResource(R.string.periodo, inicio, fim), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (commits.isNotEmpty()) {
            LinhaDeDetalhe(stringResource(R.string.commits_recentes)) {
                commits.forEach { commit ->
                    val quando = dataHora(commit.data, ZoneId.systemDefault()) ?: stringResource(R.string.sem_valor)
                    TextoComLink(
                        stringResource(R.string.commit_item, commit.sha.orEmpty(), commit.mensagem.orEmpty(), quando),
                        commit.url,
                        Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SecaoDaPublicacao(site: Site, deploy: Execucao?, agora: Instant) {
    val noAr = site.siteNoAr
    Secao(stringResource(R.string.secao_publicacao)) {
        LinhaDeDetalhe(stringResource(R.string.det_site)) {
            if (noAr != null && noAr.ok) {
                Text(
                    stringResource(R.string.site_no_ar_http, noAr.statusHttp ?: 0),
                    style = MaterialTheme.typography.bodyMedium,
                    color = coresDoTom(Tom.OK).first,
                )
            } else {
                val motivo = noAr?.statusHttp?.toString() ?: noAr?.erro ?: stringResource(R.string.sem_valor)
                Text(
                    stringResource(R.string.site_fora_do_ar, motivo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = coresDoTom(Tom.ERRO).first,
                )
            }
        }
        LinhaDeDetalhe(stringResource(R.string.det_deploy)) {
            if (deploy != null) {
                TextoComLink(
                    stringResource(R.string.par, textoDataComDias(deploy.criadoEm, agora), textoDoResultado(resultadoDe(deploy))),
                    deploy.url,
                )
            } else {
                Text(stringResource(R.string.sem_deploy), style = MaterialTheme.typography.bodyMedium)
            }
        }
        LinhaDeDetalhe(stringResource(R.string.kpi_visitantes)) {
            Text(textoDosVisitantes(site), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Links(site: Site) {
    val abrir = lembrarAbridorDeLinks()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (linkSeguro(site.pagesUrl) != null) {
            OutlinedButton(onClick = { abrir(site.pagesUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.abrir_site))
            }
        }
        if (site.repo.isNotBlank()) {
            OutlinedButton(onClick = { abrir(urlDoRepositorio(site)) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.abrir_repositorio))
            }
        }
        if (linkSeguro(site.formUrl) != null) {
            OutlinedButton(onClick = { abrir(site.formUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text(site.formRotulo ?: stringResource(R.string.abrir_form))
            }
        }
    }
}
