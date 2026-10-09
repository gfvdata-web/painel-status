package io.github.gfvdataweb.painelstatus.ui

import io.github.gfvdataweb.painelstatus.data.CacheDeArquivos
import io.github.gfvdataweb.painelstatus.data.DadosDoPainel
import io.github.gfvdataweb.painelstatus.data.ErroDeDados
import io.github.gfvdataweb.painelstatus.data.Fonte
import io.github.gfvdataweb.painelstatus.ui.painel.PainelViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/** Recarga do status.json: na abertura, no "puxar para atualizar" e de minuto em minuto. */
@OptIn(ExperimentalCoroutinesApi::class)
class PainelViewModelTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val agendador = TestCoroutineScheduler()
    private val principal = StandardTestDispatcher(agendador)

    private var geradoEm = "2026-10-08T12:00:00+00:00"
    private var pedidos = 0
    private var foraDoAr = false

    private val fonte = Fonte { _ ->
        pedidos++
        if (foraDoAr) throw IOException("painel fora do ar")
        """{"meta": {"gerado_em": "$geradoEm"}, "sites": [{"slug": "bolao_f1", "nome": "Bolão F1", "destaque": true}]}"""
    }

    private fun viewModel() = PainelViewModel(
        DadosDoPainel(fonte, CacheDeArquivos(pasta.root), relogio = { agendador.currentTime }, io = principal),
        intervaloMs = INTERVALO,
        relogio = { agendador.currentTime },
    )

    @Before
    fun prepara() = Dispatchers.setMain(principal)

    @After
    fun encerra() = Dispatchers.resetMain()

    @Test
    fun abreBaixandoOStatus() = runTest(agendador) {
        val vm = viewModel()
        runCurrent()
        assertEquals(1, pedidos)
        assertEquals(geradoEm, vm.estado.value.dados?.meta?.geradoEm)
        assertFalse(vm.estado.value.atualizando)
    }

    @Test
    fun acompanharConfereDeMinutoEmMinutoEPegaOStatusNovo() = runTest(agendador) {
        val vm = viewModel()
        backgroundScope.launch { vm.acompanhar() }
        runCurrent()
        assertEquals(1, pedidos)

        // O Action publicou um status novo (ex.: uma etapa do Bolão andou).
        geradoEm = "2026-10-08T12:01:00+00:00"
        advanceTimeBy(INTERVALO - 1)
        runCurrent()
        assertEquals("antes do intervalo não consulta de novo", 1, pedidos)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, pedidos)
        assertEquals("2026-10-08T12:01:00+00:00", vm.estado.value.dados?.meta?.geradoEm)

        advanceTimeBy(INTERVALO)
        runCurrent()
        assertEquals(3, pedidos)
    }

    @Test
    fun recargaSemInternetMantemOsDadosEAvisa() = runTest(agendador) {
        val vm = viewModel()
        backgroundScope.launch { vm.acompanhar() }
        runCurrent()
        foraDoAr = true
        advanceTimeBy(INTERVALO + 1)
        runCurrent()
        assertEquals(geradoEm, vm.estado.value.dados?.meta?.geradoEm)
        assertEquals(ErroDeDados.FALHA_NO_DOWNLOAD, vm.estado.value.erro)

        // Voltou: o próximo ciclo limpa o aviso.
        foraDoAr = false
        advanceTimeBy(INTERVALO)
        runCurrent()
        assertEquals(null, vm.estado.value.erro)
    }

    @Test
    fun puxarParaAtualizarBuscaNaHoraEAdiaARecarga() = runTest(agendador) {
        val vm = viewModel()
        backgroundScope.launch { vm.acompanhar() }
        runCurrent()
        advanceTimeBy(INTERVALO / 2)
        vm.atualizar()
        runCurrent()
        assertEquals(2, pedidos)

        // A recarga periódica conta a partir do último download.
        advanceTimeBy(INTERVALO / 2 + 1)
        runCurrent()
        assertEquals(2, pedidos)
        advanceTimeBy(INTERVALO / 2)
        runCurrent()
        assertEquals(3, pedidos)
    }

    private companion object {
        const val INTERVALO = 60_000L
    }
}
