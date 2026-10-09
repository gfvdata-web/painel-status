package io.github.gfvdataweb.painelstatus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.gfvdataweb.painelstatus.apoio.PainelPublicado
import io.github.gfvdataweb.painelstatus.data.ErroDeDados
import io.github.gfvdataweb.painelstatus.data.EstadoDados
import io.github.gfvdataweb.painelstatus.data.lerHistorico
import io.github.gfvdataweb.painelstatus.data.modelo.Etapa
import io.github.gfvdataweb.painelstatus.data.modelo.Execucao
import io.github.gfvdataweb.painelstatus.data.modelo.Status
import io.github.gfvdataweb.painelstatus.ui.bolao.BolaoTela
import io.github.gfvdataweb.painelstatus.ui.comum.ConteudoComDados
import io.github.gfvdataweb.painelstatus.ui.historico.HistoricoTela
import io.github.gfvdataweb.painelstatus.ui.links.LinksTela
import io.github.gfvdataweb.painelstatus.ui.painel.destaqueDe
import io.github.gfvdataweb.painelstatus.ui.painel.outrosSites
import io.github.gfvdataweb.painelstatus.ui.sites.SiteTela
import io.github.gfvdataweb.painelstatus.ui.sites.SitesTela
import io.github.gfvdataweb.painelstatus.ui.theme.PainelStatusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cada tela desenhada com o que o painel publica de verdade, nos temas claro
 * e escuro. Tela alta, para as listas caberem sem rolar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h4000dp")
class TelasTest {

    @get:Rule
    val compose = createComposeRule()

    private val status = PainelPublicado.status
    private val bolao = checkNotNull(destaqueDe(status))

    private fun existe(texto: String) =
        assertTrue("\"$texto\" não aparece", compose.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty())

    @Test
    fun bolaoMostraOPassoAPasso() {
        compose.setContent { PainelStatusTheme(escuro = false) { BolaoTela(bolao, aoAbrirHistorico = {}) } }
        existe(bolao.nome)
        existe("Última atualização de dados — passo a passo")
        bolao.etapasPipeline.forEach { existe(it.titulo) }
    }

    @Test
    fun bolaoComFluxoAndandoAvisaQueEstaAoVivo() {
        val andando = bolao.copy(
            ultimaExecucao = Execucao(status = "in_progress"),
            etapasPipeline = listOf(
                Etapa(chave = "forms", titulo = "Palpites enviados", estado = "ok"),
                Etapa(chave = "resultado", titulo = "Resultado oficial do quali", estado = "aguardando"),
                Etapa(chave = "pagina", titulo = "Página atualizada", estado = "pendente"),
            ),
        )
        compose.setContent { PainelStatusTheme(escuro = true) { BolaoTela(andando, aoAbrirHistorico = {}) } }
        existe("Fluxo em andamento")
        existe("Execução em andamento")
        existe("aguardando")
        existe("não iniciada")
    }

    @Test
    fun bolaoAbreOHistorico() {
        var abriu = false
        compose.setContent { PainelStatusTheme { BolaoTela(bolao, aoAbrirHistorico = { abriu = true }) } }
        compose.onNodeWithText("Histórico de execuções").performClick()
        assertTrue(abriu)
    }

    @Test
    fun sitesMostraTodosOsCardsEAbreDetalhes() {
        val sites = outrosSites(status)
        var aberto: String? = null
        compose.setContent { PainelStatusTheme(escuro = true) { SitesTela(sites, aoAbrirSite = { aberto = it }) } }
        sites.forEach { existe(it.nome) }
        compose.onAllNodesWithText("Ver detalhes")[0].performClick()
        assertEquals(sites.first { it.detalhes != null }.slug, aberto)
    }

    @Test
    fun detalhesDeUmSiteComRotina() {
        val site = outrosSites(status).first { it.detalhes?.coleta != null }
        compose.setContent {
            PainelStatusTheme { SiteTela(site, temHistorico = true, aoAbrirHistorico = {}) }
        }
        existe("Rotina de atualização de dados")
        existe(checkNotNull(site.detalhes?.coleta).workflow)
        existe("Publicação e acesso")
    }

    @Test
    fun historicoFiltraSoProblemas() {
        val historico = lerHistorico(PainelPublicado.texto(checkNotNull(bolao.historicoArquivo)))
        compose.setContent { PainelStatusTheme { HistoricoTela(historico) } }
        existe("Resumo")
        compose.onNodeWithText("Só com falha ou aviso").performClick()
        compose.onNodeWithText("Só com falha ou aviso").assertIsDisplayed()
    }

    @Test
    fun linksTemSitesEForms() {
        compose.setContent { PainelStatusTheme { LinksTela(status) } }
        status.sites.forEach { existe(it.nome) }
        status.formsAvulsos.forEach { existe(it.nome) }
    }

    @Test
    fun semDadosESemInternetOfereceTentarDeNovo() {
        var tentativas = 0
        val estado = EstadoDados<Status>(dados = null, atualizadoEm = null, atualizando = false, erro = ErroDeDados.FALHA_NO_DOWNLOAD)
        compose.setContent {
            PainelStatusTheme { ConteudoComDados(estado, aoAtualizar = { tentativas++ }) { } }
        }
        compose.onNodeWithText("Tentar de novo").performClick()
        assertEquals(1, tentativas)
    }

    @Test
    fun comDadosSalvosESemInternetAvisaNoTopo() {
        val agora = 1_800_000_000_000L
        val estado = EstadoDados(
            dados = status,
            atualizadoEm = agora - 5 * 60_000,
            atualizando = false,
            erro = ErroDeDados.FALHA_NO_DOWNLOAD,
        )
        compose.setContent {
            PainelStatusTheme { ConteudoComDados(estado, aoAtualizar = {}, agora = { agora }) { } }
        }
        compose.onNodeWithText("Sem conexão com o painel · dados salvos há 5 min").assertIsDisplayed()
    }

    @Test
    fun linhaDeAtualizacaoMostraQuandoOStatusFoiGerado() {
        val agora = 1_800_000_000_000L
        val estado = EstadoDados(dados = status, atualizadoEm = agora, atualizando = false, erro = null)
        compose.setContent {
            PainelStatusTheme {
                ConteudoComDados(estado, aoAtualizar = {}, complemento = "status de 08/10/26 12:00", agora = { agora }) { }
            }
        }
        compose.onNodeWithText("Conferido agora mesmo · status de 08/10/26 12:00").assertIsDisplayed()
    }
}
