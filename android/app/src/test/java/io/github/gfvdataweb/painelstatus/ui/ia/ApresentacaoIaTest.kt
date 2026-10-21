package io.github.gfvdataweb.painelstatus.ui.ia

import io.github.gfvdataweb.painelstatus.apoio.IaFixtures
import io.github.gfvdataweb.painelstatus.data.ia.FaixaLimite
import io.github.gfvdataweb.painelstatus.data.ia.LimitesAtuais
import io.github.gfvdataweb.painelstatus.data.ia.PontoLimite
import io.github.gfvdataweb.painelstatus.data.ia.lerCatalogo
import io.github.gfvdataweb.painelstatus.data.ia.lerLimites
import io.github.gfvdataweb.painelstatus.data.ia.lerUso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** As regras do painel-ia/docs/js/painel.js, em funções puras. */
class ApresentacaoIaTest {

    private val uso = lerUso(IaFixtures.texto("uso.json"))
    private val limites = lerLimites(IaFixtures.texto("limites.json"))
    private val catalogo = lerCatalogo(IaFixtures.texto("catalogo.json"))
    private val agoraS = IaFixtures.AGORA_MS / 1000.0
    private val hoje = hojeEm(IaFixtures.AGORA_MS, ZoneOffset.UTC)

    @Test
    fun formataTokensComoAPagina() {
        assertEquals("999", tokensCompactos(999))
        assertEquals("1,5 mil", tokensCompactos(1_500))
        assertEquals("1,2 mi", tokensCompactos(1_234_567))
        assertEquals("2,34 bi", tokensCompactos(2_340_000_000))
        assertEquals("1.234.567", inteiro(1_234_567))
        assertEquals("US$ 1.234,50", dolar(1234.5))
        assertEquals("US$ 0,05", dolar(0.05))
        assertEquals("opus-5-5", nomeModelo("claude-opus-5-5"))
        assertEquals("outro", nomeModelo("outro"))
    }

    @Test
    fun tempoSegueOsMesmosCortesDaPagina() {
        assertEquals(Tempo.MenosDeUmMinuto, tempoDe(29.0))
        assertEquals(Tempo.Minutos(1), tempoDe(30.0))
        assertEquals(Tempo.Minutos(59), tempoDe(59 * 60.0))
        assertEquals(Tempo.HorasEMinutos(1, 5), tempoDe(65 * 60.0))
        assertEquals(Tempo.HorasEMinutos(47, 59), tempoDe((48 * 60 - 1) * 60.0))
        assertEquals(Tempo.DiasEHoras(2, 1), tempoDe(49 * 3600.0))
        assertEquals(Tempo.MenosDeUmMinuto, tempoDe(-100.0))
    }

    @Test
    fun niveisDoLimite() {
        assertEquals(NivelLimite.NORMAL, nivelDoLimite(79.9))
        assertEquals(NivelLimite.ATENCAO, nivelDoLimite(80.0))
        assertEquals(NivelLimite.ATENCAO, nivelDoLimite(94.9))
        assertEquals(NivelLimite.CRITICO, nivelDoLimite(95.0))
        assertEquals(100.0, pctDaBarra(130.0), 0.0)
        assertEquals(0.0, pctDaBarra(-3.0), 0.0)
        assertEquals("18%", rotuloDoPct(18.0))
        assertEquals("82,5%", rotuloDoPct(82.5))
        assertEquals("100%", rotuloDoPct(130.0))
    }

    @Test
    fun quandoZeraEHaQuantoTempoFoiLido() {
        val atual = limites.atual!!
        val semana = atual.seteDias!!
        assertEquals(3 * 86_400.0 + 3_600.0, segundosParaZerar(semana, agoraS)!!, 0.0)
        assertEquals(600.0, idadeDaLeituraS(semana, agoraS)!!, 0.0)
        // Janela de 5 h sem zera_em: a página diz "já zerou desde a leitura".
        assertNull(segundosParaZerar(atual.cincoHoras!!, agoraS))
        assertNull(segundosParaZerar(semana.copy(zerouDesdeALeitura = true), agoraS))
        assertNull(segundosParaZerar(semana.copy(zeraEm = agoraS - 1), agoraS))
        assertNull(idadeDaLeituraS(FaixaLimite(), agoraS))
    }

    @Test
    fun leituraAntigaPassaDe24Horas() {
        val atual = limites.atual!!
        assertFalse(leituraAntiga(atual, agoraS)) // lida há 10 min
        assertFalse(leituraAntiga(atual, agoraS + 23 * 3600)) // 23 h 10 min
        assertTrue(leituraAntiga(atual, agoraS + 24 * 3600)) // 24 h 10 min
        assertFalse(leituraAntiga(LimitesAtuais(), agoraS))
    }

    @Test
    fun historicoRecenteEOsTrechosDeCadaJanela() {
        val recentes = historicoRecente(limites.historico, agoraS)
        assertEquals(4, recentes.size) // o ponto de 30 dias atrás fica de fora
        assertEquals(recentes.sortedBy { it.ts }, recentes)

        // A leitura sem a janela de 5 h (null) corta a linha em dois trechos.
        val cinco = trechosDoHistorico(recentes) { it.cincoHoras }
        assertEquals(listOf(1, 2), cinco.map { it.size })
        val sete = trechosDoHistorico(recentes) { it.seteDias }
        assertEquals(listOf(4), sete.map { it.size })
        val acima = trechosDoHistorico(listOf(PontoLimite(ts = 1.0, seteDias = 300.0))) { it.seteDias }
        assertEquals(100.0, acima[0][0].second, 0.0)
    }

    @Test
    fun somaPorModeloEmOrdemDeCusto() {
        val totais = somarPorModelo(uso.dias)
        assertEquals(listOf("claude-opus-5-5", "claude-sonnet-5", "claude-haiku-5-5"), totais.map { it.modelo })
        val sonnet = totais.first { it.modelo == "claude-sonnet-5" }
        assertEquals(1.25 + 2.0 + 0.8 + 0.1, sonnet.custoUsd, 1e-9)
        assertEquals(100L + 120 + 60 + 10, sonnet.entrada)
        assertEquals(300_000L + 500_000 + 150_000 + 10_000, sonnet.cacheLeitura)
    }

    @Test
    fun resumoDeHojeEDosUltimos7Dias() {
        val doDia = resumoDoPeriodo(diasNoPeriodo(uso.dias, hoje, hoje))
        assertEquals(listOf("claude-sonnet-5", "claude-haiku-5-5"), doDia.porModelo.map { it.modelo })
        assertEquals(0.85, doDia.custoUsd, 1e-9)
        assertEquals(152_960L + 50_820, doDia.tokens)

        // O dia 01/08 fica de fora dos 7 dias.
        val semana = resumoDoPeriodo(diasNoPeriodo(uso.dias, hoje.minusDays(6), hoje))
        assertEquals(1.25 + 3.5 + 2.0 + 7.75 + 0.8 + 0.05, semana.custoUsd, 1e-9)

        val vazio = resumoDoPeriodo(diasNoPeriodo(uso.dias, hoje.plusDays(1), hoje.plusDays(1)))
        assertTrue(vazio.porModelo.isEmpty())
        assertEquals(0L, vazio.tokens)
    }

    @Test
    fun intervaloDeCadaPeriodo() {
        assertEquals(IntervaloIa(hoje, hoje), intervaloDo(PeriodoIa.HOJE, hoje))
        assertEquals(IntervaloIa(hoje.minusDays(6), hoje), intervaloDo(PeriodoIa.SETE_DIAS, hoje))
        assertEquals(IntervaloIa(LocalDate.of(2026, 9, 16), hoje), intervaloDo(PeriodoIa.TRINTA_DIAS, hoje))
        // Desde sempre vai do primeiro dia de uso (01/08) até hoje.
        assertEquals(IntervaloIa(LocalDate.of(2026, 8, 1), hoje), intervaloDo(PeriodoIa.SEMPRE, hoje, uso.dias))
        assertEquals(IntervaloIa(hoje, hoje), intervaloDo(PeriodoIa.SEMPRE, hoje, emptyList()))

        val de = LocalDate.of(2026, 10, 10)
        val ate = LocalDate.of(2026, 10, 13)
        assertEquals(IntervaloIa(de, ate), intervaloDo(PeriodoIa.PERSONALIZADO, hoje, uso.dias, de, ate))
        assertEquals(IntervaloIa(de, ate), intervaloDo(PeriodoIa.PERSONALIZADO, hoje, uso.dias, ate, de)) // invertido
        assertEquals(IntervaloIa(de, de), intervaloDo(PeriodoIa.PERSONALIZADO, hoje, uso.dias, de, null)) // um dia só
        assertEquals(IntervaloIa(hoje, hoje), intervaloDo(PeriodoIa.PERSONALIZADO, hoje))
        assertEquals("10/10/2026", dataCurta(de))
    }

    @Test
    fun desdeSempreSeparaDe30DiasPelaLinhaDeAgosto() {
        fun custo(periodo: PeriodoIa): Double {
            val i = intervaloDo(periodo, hoje, uso.dias)
            return resumoDoPeriodo(diasNoPeriodo(uso.dias, i.de, i.ate)).custoUsd
        }
        assertEquals(1.25 + 3.5 + 2.0 + 7.75 + 0.8 + 0.05, custo(PeriodoIa.TRINTA_DIAS), 1e-9)
        assertEquals(custo(PeriodoIa.TRINTA_DIAS) + 0.1, custo(PeriodoIa.SEMPRE), 1e-9)
    }

    @Test
    fun detalheDaSessaoComEmSemAgentesDetalhe() {
        val nova = detalheDaSessao(uso.sessoes.first { it.id == "s-nova" })
        assertEquals(listOf("opus-5-5", "sonnet-5"), nova.modelos.map { it.nome })
        assertEquals(LinhaDetalhe("opus-5-5", 1_210_580, 7.75), nova.modelos.first())
        assertEquals(listOf("principal", "android-dev"), nova.agentes.map { it.nome }) // por custo
        assertEquals(0.8, nova.agentes.last().custoUsd!!, 0.0)
        assertEquals(8.55, nova.agentes.sumOf { it.custoUsd!! }, 1e-9)

        val antiga = detalheDaSessao(uso.sessoes.first { it.id == "s-antiga" })
        assertEquals(listOf("sonnet-5"), antiga.modelos.map { it.nome })
        assertTrue(antiga.agentes.isEmpty())

        // Só tokens: sem custo_usd o agente fica depois dos que têm custo.
        val soTokens = lerUso(
            """{"sessoes": [{"id": "x", "agentes_detalhe": {
                "a": {"total": 500}, "b": {"total": 10, "custo_usd": 0.5}}}]}""",
        ).sessoes.single()
        val agentes = detalheDaSessao(soTokens).agentes
        assertEquals(listOf("b", "a"), agentes.map { it.nome })
        assertNull(agentes.last().custoUsd)
    }

    @Test
    fun serieDosUltimos30DiasPorModelo() {
        val serie = serieDeTokens(uso.dias, hoje)

        assertEquals(30, serie.dias.size)
        assertEquals(LocalDate.of(2026, 9, 16), serie.dias.first())
        assertEquals(hoje, serie.dias.last())
        // Modelos em ordem alfabética, de todo o período (a cor de cada um não muda).
        assertEquals(listOf("claude-haiku-5-5", "claude-opus-5-5", "claude-sonnet-5"), serie.modelos)
        assertTrue(serie.tokens.all { it.size == 30 })
        val sonnet = serie.tokens[2]
        assertEquals(152_960L, sonnet.last())
        assertEquals(0L, sonnet[sonnet.size - 2]) // 14/10: só opus
        // 01/08 está fora da janela de 30 dias.
        val dentro = uso.dias.filter { it.dia != "2026-08-01" }.sumOf { it.total }
        assertEquals(dentro, serie.totalPorDia.sum())
    }

    @Test
    fun sessoesRecentesPrimeiroEDuracao() {
        val sessoes = sessoesRecentes(uso)
        assertEquals(listOf("s-nova", "s-sem-titulo", "s-antiga"), sessoes.map { it.id }) // pelo fim, decrescente
        assertEquals(2 * 3600L + 5 * 60, duracaoDaSessaoS(sessoes[0])) // 12:00 até 14:05
        assertEquals(20L, duracaoDaSessaoS(sessoes[1]))
        assertNull(duracaoDaSessaoS(sessoes[0].copy(fim = "lixo")))
        // O opus gastou mais que o sonnet.
        assertEquals(listOf("opus-5-5", "sonnet-5"), modelosDaSessao(sessoes[0]))
    }

    @Test
    fun agentesComparadosComOCatalogo() {
        val lista = linhasDeAgentes(uso, catalogo)
        val linhas = lista.associateBy { it.nome }

        assertEquals(listOf("principal", "android-dev", "Explore", "agente-fantasma"), lista.map { it.nome })
        // Configurado como sonnet, mas rodou também em opus.
        assertEquals("sonnet", linhas.getValue("android-dev").configurado)
        assertTrue(linhas.getValue("android-dev").diverge)
        assertEquals("claude-sonnet-5", linhas.getValue("android-dev").usados.first().first)
        // Haiku configurado e só haiku rodou.
        assertFalse(linhas.getValue("Explore").diverge)
        // Sem cadastro no catálogo: nunca diverge.
        assertNull(linhas.getValue("principal").configurado)
        assertFalse(linhas.getValue("principal").diverge)
        assertTrue(linhasDeAgentes(uso, null).none { it.configurado != null })
    }
}
