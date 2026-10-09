package io.github.gfvdataweb.painelstatus

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.gfvdataweb.painelstatus.apoio.PainelAppDeTeste
import io.github.gfvdataweb.painelstatus.apoio.PainelPublicado
import io.github.gfvdataweb.painelstatus.ui.painel.destaqueDe
import io.github.gfvdataweb.painelstatus.ui.painel.outrosSites
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
    fun abreNoBolaoComOPassoAPasso() {
        esperarTexto("Última atualização de dados — passo a passo")
        checkNotNull(destaqueDe(status)).etapasPipeline.forEach { esperarTexto(it.titulo) }
    }

    @Test
    fun trocaParaAAbaSitesEAbreUmSite() {
        esperarTexto("Última atualização de dados — passo a passo")
        compose.onNodeWithText("Sites").performClick()
        val site = outrosSites(status).first { it.detalhes != null }
        esperarTexto(site.nome)
        compose.onAllNodesWithText("Ver detalhes")[0].performClick()
        esperarTexto("Publicação e acesso")
    }

    @Test
    fun abaLinksTemOsForms() {
        esperarTexto("Última atualização de dados — passo a passo")
        compose.onNodeWithText("Links").performClick()
        status.formsAvulsos.forEach { esperarTexto(it.nome) }
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
        esperarTexto("Última atualização de dados — passo a passo")
        compose.onNodeWithContentDescription("Sobre").performClick()
        esperarTexto("Versão ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}")
    }
}
