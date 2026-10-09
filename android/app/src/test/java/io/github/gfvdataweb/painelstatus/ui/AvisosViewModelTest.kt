package io.github.gfvdataweb.painelstatus.ui

import io.github.gfvdataweb.painelstatus.apoio.AgendadorDeTeste
import io.github.gfvdataweb.painelstatus.data.CacheDeArquivos
import io.github.gfvdataweb.painelstatus.data.DadosDoPainel
import io.github.gfvdataweb.painelstatus.data.avisos.ArquivoDePreferencias
import io.github.gfvdataweb.painelstatus.data.avisos.PreferenciasDeAvisos
import io.github.gfvdataweb.painelstatus.data.avisos.VigiaDoPainel
import io.github.gfvdataweb.painelstatus.ui.avisos.AvisosViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** O ViewModel dos avisos liga/desliga a verificação em segundo plano junto com a preferência. */
@OptIn(ExperimentalCoroutinesApi::class)
class AvisosViewModelTest {

    @get:Rule
    val pasta = TemporaryFolder()

    @Before
    fun prepara() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun encerra() = Dispatchers.resetMain()

    private fun viewModel(agendador: AgendadorDeTeste): Pair<AvisosViewModel, ArquivoDePreferencias> {
        val avisos = CacheDeArquivos(pasta.newFolder())
        val preferencias = ArquivoDePreferencias(avisos)
        val dados = DadosDoPainel({ error("sem rede no teste") }, CacheDeArquivos(pasta.newFolder()), io = Dispatchers.Unconfined)
        val vigia = VigiaDoPainel(dados, avisos, io = Dispatchers.Unconfined)
        return AvisosViewModel(preferencias, agendador, vigia) to preferencias
    }

    @Test
    fun viewModelLigaEDesligaAAgenda() {
        val agendador = AgendadorDeTeste()
        val (vm, preferencias) = viewModel(agendador)
        assertEquals("ao abrir, aplica o que está salvo (desligado)", listOf(false), agendador.aplicados)

        vm.definirAtivado(true)
        assertTrue(preferencias.atuais.value.ativado)
        assertTrue("ligar responde a faixa", preferencias.atuais.value.perguntado)
        assertEquals(listOf(false, true), agendador.aplicados)

        vm.mudarSites(false)
        assertFalse(preferencias.atuais.value.sites)
        vm.definirAtivado(false)
        assertEquals(listOf(false, true, false), agendador.aplicados)
    }

    @Test
    fun agoraNaoSoEscondeAFaixa() {
        val agendador = AgendadorDeTeste()
        val (vm, preferencias) = viewModel(agendador)
        vm.dispensar()
        assertEquals(PreferenciasDeAvisos(perguntado = true), preferencias.atuais.value)
        assertEquals(listOf(false), agendador.aplicados)
    }
}
