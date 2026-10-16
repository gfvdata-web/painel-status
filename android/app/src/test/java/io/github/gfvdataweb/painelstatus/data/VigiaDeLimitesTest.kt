package io.github.gfvdataweb.painelstatus.data

import io.github.gfvdataweb.painelstatus.apoio.IaFixtures
import io.github.gfvdataweb.painelstatus.apoio.IaLocal
import io.github.gfvdataweb.painelstatus.data.avisos.JanelaLimite
import io.github.gfvdataweb.painelstatus.data.avisos.NovidadeIa
import io.github.gfvdataweb.painelstatus.data.avisos.VigiaDeLimites
import io.github.gfvdataweb.painelstatus.data.ia.CofreEmMemoria
import io.github.gfvdataweb.painelstatus.data.ia.FonteIa
import io.github.gfvdataweb.painelstatus.data.ia.SessaoIa
import io.github.gfvdataweb.painelstatus.data.ia.lerLimites
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Verificação em segundo plano dos limites do Claude contra um painel de IA local. */
class VigiaDeLimitesTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val ia = IaLocal()
    private val cofre = CofreEmMemoria()
    private val cliente = OkHttpClient()
    private val vigia by lazy {
        val avisos = CacheDeArquivos(pasta.newFolder("avisos"))
        val fonte = FonteIa(
            cliente = cliente,
            base = ia.url,
            sessao = SessaoIa(cliente, ia.url, cofre, relogio = { IaFixtures.AGORA_MS }),
            cache = CacheDeArquivos(pasta.newFolder("ia")),
            relogio = { IaFixtures.AGORA_MS },
        )
        VigiaDeLimites(fonte, avisos, relogio = { IaFixtures.AGORA_MS })
    }

    @After
    fun fecha() = ia.close()

    private fun limites(pctSemana: Double, zeraEm: Long = 1_792_252_800) = """
        {"meta": {"gerado_em": "2026-10-15T14:00:00+00:00"},
         "atual": {"seven_day": {"usado_pct": $pctSemana, "zera_em": $zeraEm, "lido_em": 1792076000, "zerou_desde_a_leitura": false}},
         "historico": []}
    """.trimIndent()

    private fun servir(pctSemana: Double, zeraEm: Long = 1_792_252_800) {
        ia.substituicoes["limites.json"] = MockResponse(body = limites(pctSemana, zeraEm))
    }

    @Test
    fun semSessaoNemFazPedido() = runTest {
        assertTrue(vigia.verificar().isEmpty())
        assertTrue(ia.pedidos.isEmpty())
    }

    @Test
    fun primeiraVerificacaoSoGuardaALinhaDeBaseEDepoisAvisaAoCruzar() = runTest {
        cofre.guardar(ia.cookie)
        servir(70.0)
        assertTrue(vigia.verificar().isEmpty())
        assertEquals(listOf("GET /dados/limites.json"), ia.pedidos.toList()) // só o limites.json

        servir(85.0)
        assertEquals(listOf(NovidadeIa.LimitePassou80(JanelaLimite.SETE_DIAS)), vigia.verificar())

        // O mesmo ciclo não avisa de novo.
        servir(90.0)
        assertTrue(vigia.verificar().isEmpty())

        // Zerou (ciclo novo).
        servir(4.0, zeraEm = 1_792_857_600)
        assertEquals(listOf(NovidadeIa.LimiteZerou(JanelaLimite.SETE_DIAS)), vigia.verificar())
    }

    @Test
    fun oQueATelaJaMostrouNaoViraNotificacao() = runTest {
        cofre.guardar(ia.cookie)
        servir(70.0)
        vigia.verificar()

        // A pessoa abriu a aba IA e viu 85%.
        vigia.marcarComoVisto(lerLimites(limites(85.0)))

        servir(86.0)
        assertTrue(vigia.verificar().isEmpty())
    }

    @Test
    fun cookieRecusadoNaoAvisaNemLancaEApagaASessao() = runTest {
        cofre.guardar(ia.cookie)
        ia.substituicoes["limites.json"] = MockResponse(code = 401)

        assertTrue(vigia.verificar().isEmpty())
        assertNull(cofre.ler())
    }

    @Test
    fun semInternetNaoAvisaENaoMudaALinhaDeBase() = runTest {
        cofre.guardar(ia.cookie)
        servir(70.0)
        vigia.verificar()

        ia.foraDoAr = true
        assertTrue(vigia.verificar().isEmpty())
        ia.foraDoAr = false

        servir(85.0)
        assertEquals(listOf(NovidadeIa.LimitePassou80(JanelaLimite.SETE_DIAS)), vigia.verificar())
    }
}
