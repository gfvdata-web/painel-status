package io.github.gfvdataweb.painelstatus.data.ia

import io.github.gfvdataweb.painelstatus.apoio.IaFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Leitura dos três JSONs do painel de IA (fixtures sintéticas com o formato real). */
class ModelosIaTest {

    private val uso = lerUso(IaFixtures.texto("uso.json"))
    private val limites = lerLimites(IaFixtures.texto("limites.json"))
    private val catalogo = lerCatalogo(IaFixtures.texto("catalogo.json"))

    @Test
    fun usoTemDiasAgentesESessoes() {
        assertEquals("2026-10-15T14:55:00+00:00", uso.meta.geradoEm)
        assertEquals(7, uso.dias.size)
        val dia = uso.dias.first()
        assertEquals("2026-10-10", dia.dia)
        assertEquals("claude-sonnet-5", dia.modelo)
        assertEquals(300_000L, dia.cacheLeitura)
        assertEquals(5_000L, dia.cacheEscrita)
        assertEquals(1.25, dia.custoUsd, 0.0)
        assertEquals(setOf("principal", "android-dev", "Explore", "agente-fantasma"), uso.agentes.keys)
        assertEquals(3L, uso.agentes.getValue("principal").sessoes)
        assertEquals(3, uso.sessoes.size)
        val nova = uso.sessoes.first { it.id == "s-nova" }
        assertEquals(setOf("claude-opus-5-5", "claude-sonnet-5"), nova.modelos.keys)
        assertEquals(1_200_000L, nova.agentes.getValue("principal"))
    }

    @Test
    fun sessaoLeAgentesDetalheEAntigaSemOCampoContinuaLida() {
        val nova = uso.sessoes.first { it.id == "s-nova" }
        assertEquals(setOf("principal", "android-dev"), nova.agentesDetalhe.keys)
        val dev = nova.agentesDetalhe.getValue("android-dev")
        assertEquals(0.8, dev.custoUsd!!, 0.0)
        assertEquals(152_960L, dev.total)
        assertEquals(setOf("claude-sonnet-5"), dev.modelos.keys)
        assertTrue(uso.sessoes.first { it.id == "s-antiga" }.agentesDetalhe.isEmpty())

        val semCusto = lerUso("""{"sessoes": [{"id": "x", "agentes_detalhe": {"a": {"total": 7}}}]}""")
        assertNull(semCusto.sessoes.single().agentesDetalhe.getValue("a").custoUsd)
    }

    @Test
    fun limitesAceitamZeraEmNuloEPontosSemJanela() {
        val atual = limites.atual!!
        assertNull(atual.cincoHoras!!.zeraEm)
        assertFalse(atual.cincoHoras.zerouDesdeALeitura)
        assertEquals(82.5, atual.seteDias!!.usadoPct, 0.0)
        assertNotNull(atual.seteDias.zeraEm)
        assertEquals(5, limites.historico.size)
        assertTrue(limites.historico.any { it.cincoHoras == null && it.seteDias != null })
    }

    @Test
    fun catalogoTemAgentesSkillsECandidatos() {
        assertEquals(listOf("android-dev", "Explore", "principal-x"), catalogo.agentes.map { it.nome })
        assertEquals("sonnet", catalogo.agentes.first().modelo)
        assertEquals("skill-de-teste", catalogo.skills.single().nome)
        val candidato = catalogo.candidatos.single()
        assertEquals("2026-10-13", candidato.quando)
        assertEquals("2", candidato.vezes)
    }

    @Test
    fun usoLeSkillsEMcpsECatalogoLeOrigemEMcps() {
        val skill = uso.skills.getValue("skill-de-teste")
        assertEquals(7L, skill.chamadas)
        assertEquals(listOf("ProjetoA", "ProjetoB"), skill.projetos)
        assertEquals(2L, skill.agentes["android-dev"])
        val mcp = uso.mcps.getValue("servidor-x")
        assertEquals("Servidor X", mcp.rotulo)
        assertEquals(5L, mcp.ferramentas["ler"])
        assertEquals("usuario", catalogo.skills.single().origem)
        val mcpCatalogo = catalogo.mcps.single()
        assertEquals("servidor-x", mcpCatalogo.nome)
        assertEquals(listOf("ler", "gravar", "listar"), mcpCatalogo.ferramentas)
    }

    @Test
    fun jsonAntigoSemSkillsNemMcpsContinuaLido() {
        val antigo = lerUso("""{"dias": []}""")
        assertTrue(antigo.skills.isEmpty() && antigo.mcps.isEmpty())
        val catalogoAntigo = lerCatalogo("""{"skills": [{"nome": "x"}]}""")
        assertEquals("", catalogoAntigo.skills.single().origem)
        assertTrue(catalogoAntigo.mcps.isEmpty())
    }

    @Test
    fun camposAusentesEExtrasNaoQuebram() {
        val vazio = lerUso("""{"coisa_nova": 1}""")
        assertTrue(vazio.dias.isEmpty() && vazio.agentes.isEmpty() && vazio.sessoes.isEmpty())
        assertEquals("", vazio.meta.geradoEm)

        val limitesSemAtual = lerLimites("""{"meta": {"gerado_em": "2026-10-15T00:00:00+00:00", "novo": true}, "historico": []}""")
        assertNull(limitesSemAtual.atual)

        val comNulos = lerUso("""{"sessoes": [{"id": "x", "titulo": null, "projeto": null, "total": null}]}""")
        assertEquals("", comNulos.sessoes.single().titulo)
        assertEquals(0L, comNulos.sessoes.single().total)
    }

    @Test
    fun vezesAceitaTextoNumeroENulo() {
        val catalogo = lerCatalogo(
            """{"candidatos": [{"vezes": "2"}, {"vezes": 3}, {"vezes": 1.5}, {"vezes": null}, {"tarefa": "sem vezes"}]}""",
        )
        assertEquals(listOf("2", "3", "1.5", "", ""), catalogo.candidatos.map { it.vezes })
    }

    @Test(expected = IllegalArgumentException::class)
    fun jsonQuebradoLancaIllegalArgument() {
        lerCatalogo("não é json")
    }

    @Test
    fun dadosJuntosEscolhemAGeracaoMaisRecente() {
        val dados = DadosIa(uso, limites, catalogo)
        assertEquals("2026-10-15T14:55:00+00:00", dados.geradoEm)
        assertFalse(dados.vazio)
        assertTrue(DadosIa().vazio)
        assertNull(DadosIa().geradoEm)
    }
}
