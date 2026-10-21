package io.github.gfvdataweb.painelstatus.ui.ia

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.gfvdataweb.painelstatus.apoio.IaFixtures
import io.github.gfvdataweb.painelstatus.data.ia.DadosIa
import io.github.gfvdataweb.painelstatus.data.ia.lerCatalogo
import io.github.gfvdataweb.painelstatus.data.ia.lerLimites
import io.github.gfvdataweb.painelstatus.data.ia.lerUso
import io.github.gfvdataweb.painelstatus.ui.theme.PainelStatusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Telas do painel de IA desenhadas com as fixtures, nos temas claro e escuro. Tela alta: as listas cabem sem rolar. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h4000dp")
class IaTelasTest {

    @get:Rule
    val compose = createComposeRule()

    private val dados = DadosIa(
        uso = lerUso(IaFixtures.texto("uso.json")),
        limites = lerLimites(IaFixtures.texto("limites.json")),
        catalogo = lerCatalogo(IaFixtures.texto("catalogo.json")),
    )

    private fun existe(texto: String, trecho: Boolean = false) = assertTrue(
        "\"$texto\" não aparece",
        compose.onAllNodesWithText(texto, substring = trecho).fetchSemanticsNodes().isNotEmpty(),
    )

    private fun mostrar(
        estado: EstadoIa,
        escuro: Boolean = false,
        aoEntrar: (String) -> Unit = {},
        aoSair: () -> Unit = {},
        agoraMs: Long = IaFixtures.AGORA_MS,
    ) = compose.setContent {
        PainelStatusTheme(escuro = escuro) {
            IaConteudo(
                estado = estado,
                atualizando = false,
                aoEntrar = aoEntrar,
                aoAtualizar = {},
                aoSair = aoSair,
                agora = { agoraMs },
            )
        }
    }

    @Test
    fun loginPedeASenhaEOBotaoSoLiberaDepoisDeDigitar() {
        val enviadas = mutableListOf<String>()
        mostrar(EstadoIa.PrecisaEntrar(), aoEntrar = { enviadas += it })

        existe("Painel de IA")
        existe("Senha")
        compose.onNodeWithText("Entrar").assertIsNotEnabled()

        compose.onNodeWithText("Senha").performTextInput("segredo")
        compose.onNodeWithText("Entrar").assertIsEnabled().performClick()

        assertEquals(listOf("segredo"), enviadas)
    }

    @Test
    fun loginMostraSenhaErradaESemConexao() {
        mostrar(EstadoIa.PrecisaEntrar(falha = FalhaDoLogin.SENHA_ERRADA))
        existe("Senha incorreta.")
    }

    @Test
    fun loginSemConexaoAvisaDaInternet() {
        mostrar(EstadoIa.PrecisaEntrar(falha = FalhaDoLogin.SEM_CONEXAO), escuro = true)
        existe("Não foi possível falar com o painel de IA", trecho = true)
    }

    @Test
    fun loginEntrandoTravaOBotao() {
        mostrar(EstadoIa.PrecisaEntrar(entrando = true))
        compose.onNodeWithText("Entrando…").assertIsNotEnabled()
    }

    @Test
    fun erroOfereceTentarDeNovo() {
        mostrar(EstadoIa.Erro)
        existe("Não foi possível baixar os dados do painel de IA", trecho = true)
        existe("Tentar de novo")
    }

    @Test
    fun abaAgoraMostraLimitesEResumos() {
        mostrar(EstadoIa.Pronto(dados, desatualizado = false))

        existe("Janela de 5 horas")
        existe("Janela semanal")
        existe("82,5%")
        existe("Perto do limite") // 82,5 % passa dos 80 %
        existe("já zerou desde a leitura", trecho = true) // a janela de 5 h veio sem zera_em
        existe("zera em 3 d 1 h", trecho = true)
        existe("lido há 10 min", trecho = true)
        existe("Limites nos últimos 7 dias")
        existe("Hoje: ", trecho = true)
        existe("Gerado em", trecho = true)
        existe("Sair do painel de IA")
    }

    @Test
    fun tocarNoChipTrocaOPeriodoETotal() {
        mostrar(EstadoIa.Pronto(dados, desatualizado = false))
        existe("Hoje: ", trecho = true)
        existe("US$ 0,85", trecho = true)

        compose.onNodeWithText("30 dias").performClick()
        existe("Últimos 30 dias: ", trecho = true)
        existe("US$ 15,35", trecho = true)

        // A linha de 01/08 só entra em "Desde sempre".
        compose.onNodeWithText("Desde sempre").performClick()
        existe("Desde sempre: ", trecho = true)
        existe("US$ 15,45", trecho = true)
    }

    @Test
    fun leituraComMaisDeUmDiaAvisa() {
        // A leitura é de 10 min antes de AGORA_MS: 24 h depois já são 24 h 10 min.
        mostrar(EstadoIa.Pronto(dados, desatualizado = false), agoraMs = IaFixtures.AGORA_MS + 24 * 3_600_000L)
        existe("Leitura antiga", trecho = true)
        existe("lido há 24 h 10 min", trecho = true)
    }

    @Test
    fun leituraDeHorasAtrasNaoAvisa() {
        mostrar(EstadoIa.Pronto(dados, desatualizado = false), agoraMs = IaFixtures.AGORA_MS + 2 * 3_600_000L)
        assertTrue(compose.onAllNodesWithText("Leitura antiga", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun leituraRecenteNaoAvisa() {
        mostrar(EstadoIa.Pronto(dados, desatualizado = false))
        assertTrue(compose.onAllNodesWithText("Leitura antiga", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun dadosGuardadosAvisamQueEstaoDesatualizados() {
        mostrar(EstadoIa.Pronto(dados, desatualizado = true))
        existe("Sem conexão: mostrando os últimos dados guardados no aparelho.")
    }

    @Test
    fun abaModelosMostraGraficoETotais() {
        mostrar(EstadoIa.Pronto(dados, desatualizado = false), escuro = true)
        compose.onNodeWithText("Modelos").performClick()

        existe("Tokens por dia, por modelo (últimos 30 dias)")
        existe("Total por modelo (todo o período coletado)")
        existe("opus-5-5")
        existe("sonnet-5")
        existe("haiku-5-5")
        existe("Maior dia: ", trecho = true)
    }

    @Test
    fun abaSessoesListaAsSessoes() {
        mostrar(EstadoIa.Pronto(dados, desatualizado = false))
        compose.onNodeWithText("Sessões").performClick()

        existe("Sessão nova de teste")
        existe("Sessão antiga de teste")
        existe("(sem título)")
        existe("ProjetoB", trecho = true)
        existe("2 h 05 min", trecho = true)
    }

    @Test
    fun tocarNaSessaoMostraCustoPorModeloEPorSubagente() {
        mostrar(EstadoIa.Pronto(dados, desatualizado = false))
        compose.onNodeWithText("Sessões").performClick()
        assertTrue(compose.onAllNodesWithText("Por modelo").fetchSemanticsNodes().isEmpty())

        compose.onNodeWithText("Sessão nova de teste").performClick()
        existe("Por modelo")
        existe("Por subagente")
        existe("android-dev · 153,0 mil tokens · US$ 0,80")

        // A sessão antiga não tem agentes_detalhe: só "Por modelo".
        compose.onNodeWithText("Sessão antiga de teste").performClick()
        assertEquals(2, compose.onAllNodesWithText("Por modelo").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("Por subagente").fetchSemanticsNodes().size)

        compose.onNodeWithText("Sessão nova de teste").performClick() // recolhe
        assertEquals(1, compose.onAllNodesWithText("Por modelo").fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodesWithText("Por subagente").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun abaAgentesComparaComOCatalogo() {
        mostrar(EstadoIa.Pronto(dados, desatualizado = false))
        compose.onNodeWithText("Agentes").performClick()

        existe("android-dev")
        existe("configurado: sonnet")
        existe("configurado: haiku")
        existe("agente-fantasma")
        existe("sem cadastro no catálogo")
    }

    @Test
    fun abaCatalogoMostraAgentesSkillsECandidatos() {
        mostrar(EstadoIa.Pronto(dados, desatualizado = false))
        compose.onNodeWithText("Catálogo").performClick()

        existe("Agentes (3)")
        existe("Skills (1)")
        existe("skill-de-teste")
        existe("Candidatos (1)")
        existe("Automatizar o passo repetido")
        existe("2x · 2026-10-13")
    }

    @Test
    fun sairChamaOCallback() {
        var saiu = false
        mostrar(EstadoIa.Pronto(dados, desatualizado = false), aoSair = { saiu = true })
        compose.onNodeWithText("Sair do painel de IA").performClick()
        assertTrue(saiu)
    }

    @Test
    fun arquivosAindaNaoPublicadosMostramAvisoEmCadaAba() {
        mostrar(EstadoIa.Pronto(DadosIa(), desatualizado = false))
        existe("Ainda sem dados: o coletor não publicou nada até agora.")
        existe("Ainda sem leitura dos limites.")
        compose.onNodeWithText("Sessões").performClick()
        existe("Ainda sem dados de uso.")
        compose.onNodeWithText("Catálogo").performClick()
        existe("Ainda sem dados do catálogo.")
    }
}
