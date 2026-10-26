package io.github.gfvdataweb.painelstatus

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.gfvdataweb.painelstatus.apoio.PainelAppDeTeste
import io.github.gfvdataweb.painelstatus.apoio.PainelPublicado
import io.github.gfvdataweb.painelstatus.ui.painel.outrosSites
import io.github.gfvdataweb.painelstatus.ui.painel.sitesDaLista
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Abre o app de verdade (manifest, tema, Activity, ViewModel, rede e cache)
 * num Android simulado, lendo de um painel local com os dados reais. Se o app
 * fecharia sozinho ao abrir no celular, este teste falha no CI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PainelAppDeTeste::class, qualifiers = "w411dp-h4000dp")
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val status = PainelPublicado.status

    private fun esperarTexto(texto: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun abreNoStatusComOsSites() {
        sitesDaLista(status.sites).forEach { esperarTexto(it.nome) }
    }

    @Test
    fun cardAbreOsDetalhesDoSite() {
        val site = outrosSites(status).first()
        esperarTexto(site.nome)
        compose.onAllNodesWithText(site.nome)[0].performClick()
        esperarTexto("Publicação e acesso")
    }

    @Test
    fun abaIaPedeASenhaEEntraComAsenhaCertaESai() {
        esperarTexto(status.sites.first().nome)
        compose.onNodeWithText("IA").performClick()
        esperarTexto("Painel de IA")
        compose.onNodeWithText("Senha").performTextInput("segredo")
        compose.onNodeWithText("Entrar").performClick()
        esperarTexto("Sair do painel de IA")
        esperarTexto("Janela semanal")

        compose.onNodeWithText("Sair do painel de IA").performClick()
        esperarTexto("Entrar")
    }

    @Test
    fun abaIaComSenhaErradaMostraAMensagem() {
        esperarTexto(status.sites.first().nome)
        compose.onNodeWithText("IA").performClick()
        compose.onNodeWithText("Senha").performTextInput("errada")
        compose.onNodeWithText("Entrar").performClick()
        esperarTexto("Senha incorreta.")
    }

    @Test
    fun sinoAbreOsAvisosEAFaixaSomeComAgoraNao() {
        esperarTexto("Avisar com o app fechado?")
        compose.onNodeWithText("Agora não").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Avisar com o app fechado?").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription("Avisos").performClick()
        esperarTexto("Avisar com o app fechado")
        esperarTexto("Bolão F1 — passo a passo")
    }

    @Test
    fun sobreMostraAVersaoInstalada() {
        esperarTexto(status.sites.first().nome)
        compose.onNodeWithContentDescription("Sobre").performClick()
        esperarTexto("Versão ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}")
    }
}
