package io.github.gfvdataweb.painelstatus.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.gfvdataweb.painelstatus.data.avisos.PreferenciasDeAvisos
import io.github.gfvdataweb.painelstatus.ui.avisos.AvisosTela
import io.github.gfvdataweb.painelstatus.ui.avisos.FaixaDeAvisos
import io.github.gfvdataweb.painelstatus.ui.theme.PainelStatusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tela e faixa de avisos. */
@RunWith(RobolectricTestRunner::class)
class AvisosTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun faixaChamaAtivarEAgoraNao() {
        var ativou = 0
        var dispensou = 0
        compose.setContent { PainelStatusTheme { FaixaDeAvisos(aoAtivar = { ativou++ }, aoDispensar = { dispensou++ }) } }
        compose.onNodeWithText("Ativar").performClick()
        compose.onNodeWithText("Agora não").performClick()
        assertEquals(1, ativou)
        assertEquals(1, dispensou)
    }

    @Test
    fun telaComAvisosDesligadosNaoDeixaMudarOsTipos() {
        var pedidoDeAtivar: Boolean? = null
        compose.setContent {
            PainelStatusTheme {
                AvisosTela(
                    preferencias = PreferenciasDeAvisos(),
                    permitido = true,
                    ultimaVerificacao = null,
                    aoAtivar = { pedidoDeAtivar = it },
                    aoMudarBolao = {},
                    aoMudarSites = {},
                    aoTestar = {},
                    aoAbrirConfiguracoesDoAndroid = {},
                )
            }
        }
        compose.onNodeWithText("Bolão F1 — passo a passo").assertIsNotEnabled()
        compose.onNodeWithText("Ainda não houve verificação em segundo plano.").assertExists()
        compose.onNodeWithText("Avisar com o app fechado").performClick()
        assertEquals(true, pedidoDeAtivar)
    }

    @Test
    fun telaAvisaQuandoOAndroidBloqueia() {
        compose.setContent {
            PainelStatusTheme {
                AvisosTela(
                    preferencias = PreferenciasDeAvisos(ativado = true),
                    permitido = false,
                    ultimaVerificacao = 1_800_000_000_000L - 3 * 60_000,
                    aoAtivar = {},
                    aoMudarBolao = {},
                    aoMudarSites = {},
                    aoTestar = {},
                    aoAbrirConfiguracoesDoAndroid = {},
                    agora = { 1_800_000_000_000L },
                )
            }
        }
        compose.onNodeWithText("Abrir configurações de notificação").assertExists()
        compose.onNodeWithText("Enviar uma notificação de teste").assertIsNotEnabled()
        compose.onNodeWithText("Última verificação em segundo plano: há 3 min").assertExists()
    }
}
