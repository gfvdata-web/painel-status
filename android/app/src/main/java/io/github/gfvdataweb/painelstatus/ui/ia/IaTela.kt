package io.github.gfvdataweb.painelstatus.ui.ia

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.data.ia.DadosIa
import io.github.gfvdataweb.painelstatus.ui.comum.dataHora
import java.time.ZoneId

/** As abas do painel de IA (depois do login), na ordem da página. */
private enum class AbaIa(@param:StringRes val rotulo: Int) {
    AGORA(R.string.ia_aba_agora),
    MODELOS(R.string.ia_aba_modelos),
    SESSOES(R.string.ia_aba_sessoes),
    AGENTES(R.string.ia_aba_agentes),
    CATALOGO(R.string.ia_aba_catalogo),
}

/** Aba IA ligada ao [IaViewModel]. */
@Composable
fun IaTela(viewModel: IaViewModel, modifier: Modifier = Modifier) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val atualizando by viewModel.atualizando.collectAsStateWithLifecycle()
    IaConteudo(
        estado = estado,
        atualizando = atualizando,
        aoEntrar = viewModel::entrar,
        aoAtualizar = viewModel::atualizar,
        aoSair = viewModel::sair,
        modifier = modifier,
    )
}

/** Escolhe a tela pelo [EstadoIa]; recebe tudo pronto, sem ViewModel (testável). */
@Composable
fun IaConteudo(
    estado: EstadoIa,
    atualizando: Boolean,
    aoEntrar: (String) -> Unit,
    aoAtualizar: () -> Unit,
    aoSair: () -> Unit,
    modifier: Modifier = Modifier,
    agora: () -> Long = System::currentTimeMillis,
) {
    when (estado) {
        EstadoIa.Carregando -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is EstadoIa.PrecisaEntrar -> EntrarIaTela(estado, aoEntrar, modifier)
        EstadoIa.Erro -> Column(
            modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.ia_erro), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = aoAtualizar) { Text(stringResource(R.string.tentar_de_novo)) }
        }
        is EstadoIa.Pronto -> IaLogada(estado, atualizando, aoAtualizar, aoSair, modifier, agora)
    }
}

/** Senha do painel privado. A senha só vive no campo: o app guarda o cookie que o painel devolve. */
@Composable
fun EntrarIaTela(estado: EstadoIa.PrecisaEntrar, aoEntrar: (String) -> Unit, modifier: Modifier = Modifier) {
    var senha by remember { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.ia_entrar_titulo), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ia_entrar_texto),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            label = { Text(stringResource(R.string.ia_senha)) },
            singleLine = true,
            enabled = !estado.entrando,
            isError = estado.falha != null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { aoEntrar(senha) }),
            supportingText = {
                when (estado.falha) {
                    FalhaDoLogin.SENHA_ERRADA -> Text(stringResource(R.string.ia_senha_errada))
                    FalhaDoLogin.SEM_CONEXAO -> Text(stringResource(R.string.ia_login_sem_conexao))
                    null -> Unit
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { aoEntrar(senha) }, enabled = !estado.entrando && senha.isNotEmpty()) {
            Text(stringResource(if (estado.entrando) R.string.ia_entrando else R.string.ia_entrar))
        }
    }
}

/** Painel logado: aviso de dados antigos, abas rolável e o botão de sair no rodapé. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IaLogada(
    estado: EstadoIa.Pronto,
    atualizando: Boolean,
    aoAtualizar: () -> Unit,
    aoSair: () -> Unit,
    modifier: Modifier,
    agora: () -> Long,
) {
    val dados: DadosIa = estado.dados
    var aba by rememberSaveable { mutableIntStateOf(0) }
    val abas = AbaIa.entries
    val fuso = ZoneId.systemDefault()
    Column(modifier.fillMaxSize()) {
        if (dados.vazio) {
            Text(
                stringResource(R.string.ia_sem_dados),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (estado.desatualizado) {
            Text(
                stringResource(R.string.ia_desatualizado),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        PrimaryScrollableTabRow(selectedTabIndex = aba, edgePadding = 0.dp) {
            abas.forEachIndexed { indice, item ->
                Tab(selected = indice == aba, onClick = { aba = indice }, text = { Text(stringResource(item.rotulo)) })
            }
        }
        PullToRefreshBox(isRefreshing = atualizando, onRefresh = aoAtualizar, modifier = Modifier.weight(1f)) {
            val agoraMs = agora()
            when (abas[aba]) {
                AbaIa.AGORA -> AbaAgora(dados, agoraMs, fuso)
                AbaIa.MODELOS -> AbaModelos(dados, agoraMs, fuso)
                AbaIa.SESSOES -> AbaSessoes(dados, fuso)
                AbaIa.AGENTES -> AbaAgentes(dados)
                AbaIa.CATALOGO -> AbaCatalogo(dados)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                dataHora(dados.geradoEm, fuso)?.let { stringResource(R.string.ia_gerado, it) }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = aoSair) { Text(stringResource(R.string.ia_sair)) }
        }
    }
}
