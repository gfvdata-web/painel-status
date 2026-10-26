package io.github.gfvdataweb.painelstatus.ui

import io.github.gfvdataweb.painelstatus.data.modelo.Site
import io.github.gfvdataweb.painelstatus.ui.painel.moverSlug
import io.github.gfvdataweb.painelstatus.ui.painel.ordenar
import io.github.gfvdataweb.painelstatus.ui.painel.sitesDaLista
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ordem dos cards do app: a salva primeiro, o resto no cadastro. */
class OrdenarTest {

    private val bolao = Site(slug = "bolao", nome = "Bolão", destaque = true)
    private val a = Site(slug = "a", nome = "A")
    private val b = Site(slug = "b", nome = "B")
    private val c = Site(slug = "c", nome = "C")

    private fun slugs(sites: List<Site>) = sites.map { it.slug }

    @Test
    fun semOrdemSalvaDestaquePrimeiroEORestoNoCadastro() {
        assertEquals(listOf("bolao", "a", "b", "c"), slugs(ordenar(listOf(a, b, bolao, c), emptyList())))
    }

    @Test
    fun ordemSalvaVemPrimeiraNaOrdemSalva() {
        assertEquals(listOf("c", "a", "b", "bolao"), slugs(ordenar(listOf(bolao, a, b, c), listOf("c", "a", "b", "bolao"))))
    }

    @Test
    fun siteForaDaListaVaiParaOFimNaOrdemDoCadastro() {
        assertEquals(listOf("c", "bolao", "a", "b"), slugs(ordenar(listOf(bolao, a, b, c), listOf("c"))))
    }

    @Test
    fun slugQueNaoExisteMaisEIgnoradoERepetidoValeOPrimeiro() {
        assertEquals(listOf("b", "a"), slugs(ordenar(listOf(a, b), listOf("b", "fantasma", "a", "b"))))
    }

    @Test
    fun siteComNaListaFalsoNaoGanhaCardEAusenteContaComoVerdadeiro() {
        val ia = Site(slug = "painel_ia", nome = "Painel IA", naLista = false)
        assertEquals(listOf("a", "b"), slugs(sitesDaLista(listOf(a, ia, b))))
        assertEquals(true, a.naLista)
    }

    @Test
    fun moverSlugTrocaDePosicaoEIgnoraLimites() {
        val lista = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moverSlug(lista, 1, 0))
        assertEquals(listOf("a", "c", "b"), moverSlug(lista, 1, 2))
        assertEquals(listOf("b", "c", "a"), moverSlug(lista, 0, 2))
        assertEquals(lista, moverSlug(lista, 0, -1))
        assertEquals(lista, moverSlug(lista, 2, 3))
        assertEquals(lista, moverSlug(lista, 1, 1))
    }
}
