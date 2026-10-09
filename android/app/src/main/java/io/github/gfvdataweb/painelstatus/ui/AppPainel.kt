package io.github.gfvdataweb.painelstatus.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.gfvdataweb.painelstatus.AppContainer
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.avisos.AbaDoAviso
import io.github.gfvdataweb.painelstatus.avisos.podeNotificar
import io.github.gfvdataweb.painelstatus.data.DadosDoPainel
import io.github.gfvdataweb.painelstatus.data.modelo.Status
import io.github.gfvdataweb.painelstatus.ui.avisos.AvisosTela
import io.github.gfvdataweb.painelstatus.ui.avisos.AvisosViewModel
import io.github.gfvdataweb.painelstatus.ui.avisos.FaixaDeAvisos
import io.github.gfvdataweb.painelstatus.ui.bolao.BolaoTela
import io.github.gfvdataweb.painelstatus.ui.comum.ConteudoComDados
import io.github.gfvdataweb.painelstatus.ui.comum.dataHora
import io.github.gfvdataweb.painelstatus.ui.historico.HistoricoTela
import io.github.gfvdataweb.painelstatus.ui.historico.HistoricoViewModel
import io.github.gfvdataweb.painelstatus.ui.links.LinksTela
import io.github.gfvdataweb.painelstatus.ui.painel.PainelViewModel
import io.github.gfvdataweb.painelstatus.ui.painel.destaqueDe
import io.github.gfvdataweb.painelstatus.ui.painel.outrosSites
import io.github.gfvdataweb.painelstatus.ui.sites.SiteTela
import io.github.gfvdataweb.painelstatus.ui.sites.SitesTela
import io.github.gfvdataweb.painelstatus.ui.sobre.SobreTela
import io.github.gfvdataweb.painelstatus.ui.versao.AvisoDeNovaVersao
import io.github.gfvdataweb.painelstatus.ui.versao.SituacaoDaVersao
import io.github.gfvdataweb.painelstatus.ui.versao.VersaoViewModel
import kotlinx.serialization.Serializable
import java.time.ZoneId

// Rotas da navegação (type-safe: cada tela é um objeto ou classe serializável).
@Serializable
object RotaBolao

@Serializable
object RotaSites

@Serializable
object RotaLinks

@Serializable
data class RotaSite(val slug: String)

@Serializable
data class RotaHistorico(val slug: String)

@Serializable
object RotaSobre

@Serializable
object RotaAvisos

private data class Aba(val rota: Any, @param:StringRes val rotulo: Int, val icone: ImageVector)

private val ABAS = listOf(
    Aba(RotaBolao, R.string.aba_bolao, Icons.Filled.Star),
    Aba(RotaSites, R.string.aba_sites, Icons.AutoMirrored.Filled.List),
    Aba(RotaLinks, R.string.aba_links, Icons.AutoMirrored.Filled.ExitToApp),
)

/** Raiz da interface: barra superior, abas embaixo e a tela escolhida. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPainel(container: AppContainer, versao: String, build: Int, abaInicial: String? = null) {
    val navegacao = rememberNavController()
    // Escopo da Activity: todas as telas dividem o mesmo status.json.
    val painel: PainelViewModel = viewModel(
        factory = PainelViewModel.fabrica(container.dados, container.intervaloDeRecargaMs, container::aoVerStatus),
    )
    val estado by painel.estado.collectAsStateWithLifecycle()
    val versaoViewModel: VersaoViewModel = viewModel(factory = VersaoViewModel.fabrica(container.verificadorDeAtualizacao, build))
    val estadoDaVersao by versaoViewModel.estado.collectAsStateWithLifecycle()
    val entradaAtual by navegacao.currentBackStackEntryAsState()
    val naAba = ABAS.firstOrNull { aba -> entradaAtual?.destination?.hasRoute(aba.rota::class) == true }
    val navegador = LocalUriHandler.current
    val baixarNovaVersao = { estadoDaVersao.publicada?.let { runCatching { navegador.openUri(it.urlDoApk) } } }
    val intervaloMin = (container.intervaloDeRecargaMs / 60_000).coerceAtLeast(1)

    // Avisos com o app fechado: preferências e a permissão do Android 13+.
    val avisos: AvisosViewModel = viewModel(
        factory = AvisosViewModel.fabrica(container.preferencias, container.agendador, container.vigia),
    )
    val preferenciasDeAvisos by avisos.estado.collectAsStateWithLifecycle()
    val ultimaVerificacao by avisos.ultimaVerificacao.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var permitido by remember { mutableStateOf(podeNotificar(contexto)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permitido = podeNotificar(contexto)
        avisos.recarregarUltimaVerificacao()
    }
    val pedirPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        permitido = concedida
        avisos.definirAtivado(concedida)
    }
    val ativarAvisos = { ligar: Boolean ->
        if (ligar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !podeNotificar(contexto)) {
            pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            avisos.definirAtivado(ligar)
        }
    }

    // Com o app visível, confere o painel a cada intervalo (como a página
    // aberta); em segundo plano, para.
    val ciclo = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(ciclo, painel) {
        ciclo.repeatOnLifecycle(Lifecycle.State.STARTED) { painel.acompanhar() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        tituloDaTela(entradaAtual, estado.dados),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (naAba == null && entradaAtual != null) {
                        IconButton(onClick = { navegacao.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.voltar))
                        }
                    }
                },
                actions = {
                    if (naAba != null) {
                        IconButton(onClick = { navegacao.navigate(RotaAvisos) { launchSingleTop = true } }) {
                            Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.avisos_titulo))
                        }
                        IconButton(onClick = { navegacao.navigate(RotaSobre) { launchSingleTop = true } }) {
                            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.sobre_titulo))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (naAba != null) {
                NavigationBar {
                    ABAS.forEach { aba ->
                        NavigationBarItem(
                            selected = aba == naAba,
                            onClick = { navegacao.irParaAba(aba.rota) },
                            icon = { Icon(aba.icone, contentDescription = null) },
                            label = { Text(stringResource(aba.rotulo)) },
                        )
                    }
                }
            }
        },
    ) { espaco ->
        Column(Modifier.padding(espaco)) {
            val publicada = estadoDaVersao.publicada
            if (estadoDaVersao.mostrarAviso && publicada != null) {
                AvisoDeNovaVersao(publicada, aoBaixar = { baixarNovaVersao() }, aoDispensar = versaoViewModel::dispensar)
            }
            if (!preferenciasDeAvisos.perguntado && naAba != null) {
                FaixaDeAvisos(aoAtivar = { ativarAvisos(true) }, aoDispensar = avisos::dispensar)
            }
            val complemento = dataHora(estado.dados?.meta?.geradoEm, ZoneId.systemDefault())
                ?.let { stringResource(R.string.status_gerado, it) }
            // Aberto por uma notificação dos sites: começa na aba Sites.
            val inicio: Any = if (abaInicial == AbaDoAviso.SITES.name) RotaSites else RotaBolao
            NavHost(navegacao, startDestination = inicio, modifier = Modifier.weight(1f)) {
                composable<RotaBolao> {
                    ConteudoComDados(estado, painel::atualizar, complemento = complemento) { status ->
                        val destaque = destaqueDe(status)
                        if (destaque != null) {
                            BolaoTela(
                                destaque,
                                aoAbrirHistorico = { navegacao.navigate(RotaHistorico(destaque.slug)) },
                                intervaloMin = intervaloMin,
                            )
                        } else {
                            MensagemSimples(stringResource(R.string.sem_destaque))
                        }
                    }
                }
                composable<RotaSites> {
                    ConteudoComDados(estado, painel::atualizar, complemento = complemento) { status ->
                        SitesTela(outrosSites(status), aoAbrirSite = { slug -> navegacao.navigate(RotaSite(slug)) })
                    }
                }
                composable<RotaLinks> {
                    ConteudoComDados(estado, painel::atualizar, complemento = complemento) { status ->
                        LinksTela(status)
                    }
                }
                composable<RotaSite> { entrada ->
                    val slug = entrada.toRoute<RotaSite>().slug
                    ConteudoComDados(estado, painel::atualizar, complemento = complemento) { status ->
                        val site = status.sites.firstOrNull { it.slug == slug }
                        if (site != null) {
                            SiteTela(
                                site,
                                temHistorico = DadosDoPainel.historicoValido(site.historicoArquivo),
                                aoAbrirHistorico = { navegacao.navigate(RotaHistorico(slug)) },
                            )
                        } else {
                            MensagemSimples(stringResource(R.string.site_nao_encontrado))
                        }
                    }
                }
                composable<RotaHistorico> { entrada ->
                    val slug = entrada.toRoute<RotaHistorico>().slug
                    val arquivo = estado.dados?.sites?.firstOrNull { it.slug == slug }?.historicoArquivo
                    if (arquivo != null && DadosDoPainel.historicoValido(arquivo)) {
                        val historico: HistoricoViewModel = viewModel(factory = HistoricoViewModel.fabrica(container.dados, arquivo))
                        val estadoDoHistorico by historico.estado.collectAsStateWithLifecycle()
                        ConteudoComDados(estadoDoHistorico, historico::atualizar) { HistoricoTela(it) }
                    } else {
                        MensagemSimples(stringResource(R.string.sem_historico))
                    }
                }
                composable<RotaAvisos> {
                    AvisosTela(
                        preferencias = preferenciasDeAvisos,
                        permitido = permitido,
                        ultimaVerificacao = ultimaVerificacao,
                        aoAtivar = ativarAvisos,
                        aoMudarBolao = avisos::mudarBolao,
                        aoMudarSites = avisos::mudarSites,
                        aoTestar = container.avisador::testar,
                        aoAbrirConfiguracoesDoAndroid = {
                            val abrir = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName)
                            runCatching { contexto.startActivity(abrir) }
                        },
                    )
                }
                composable<RotaSobre> {
                    SobreTela(
                        versao = versao,
                        build = build,
                        intervaloMin = intervaloMin,
                        extras = { SituacaoDaVersao(estadoDaVersao, aoBaixar = { baixarNovaVersao() }) },
                    )
                }
            }
        }
    }
}

/** Título da barra superior: o do app nas abas, o do site/histórico nas telas de detalhe. */
@Composable
private fun tituloDaTela(entrada: NavBackStackEntry?, status: Status?): String {
    val destino = entrada?.destination
    val nomeDoSite = { slug: String -> status?.sites?.firstOrNull { it.slug == slug }?.nome ?: slug }
    return when {
        entrada == null || destino == null -> stringResource(R.string.app_titulo)
        destino.hasRoute(RotaSite::class) -> nomeDoSite(entrada.toRoute<RotaSite>().slug)
        destino.hasRoute(RotaHistorico::class) ->
            stringResource(R.string.historico_titulo, nomeDoSite(entrada.toRoute<RotaHistorico>().slug))
        destino.hasRoute(RotaSobre::class) -> stringResource(R.string.sobre_titulo)
        destino.hasRoute(RotaAvisos::class) -> stringResource(R.string.avisos_titulo)
        else -> stringResource(R.string.app_titulo)
    }
}

@Composable
private fun MensagemSimples(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    )
}

/** Troca de aba sem empilhar telas (o "voltar" sai do app a partir de qualquer aba). */
private fun NavHostController.irParaAba(rota: Any) {
    navigate(rota) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
