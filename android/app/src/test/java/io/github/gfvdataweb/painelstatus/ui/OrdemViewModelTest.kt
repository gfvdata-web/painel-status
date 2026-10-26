package io.github.gfvdataweb.painelstatus.ui

import io.github.gfvdataweb.painelstatus.data.ia.CofreEmMemoria
import io.github.gfvdataweb.painelstatus.data.ordem.FonteDeOrdem
import io.github.gfvdataweb.painelstatus.ui.painel.ErroDeLogin
import io.github.gfvdataweb.painelstatus.ui.painel.OrdemViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Login, edição e salvamento da ordem dos cards, contra um Worker local. */
@OptIn(ExperimentalCoroutinesApi::class)
class OrdemViewModelTest {

    private val servidor = MockWebServer().apply { start() }
    private val cofre = CofreEmMemoria()

    @Before
    fun prepara() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun encerra() {
        Dispatchers.resetMain()
        servidor.close()
    }

    private fun viewModel(): OrdemViewModel {
        val ordemSalva = MockResponse(code = 200, body = """{"slugs":["b","a"],"atualizado_em":null}""")
        servidor.enqueue(ordemSalva)
        return OrdemViewModel(FonteDeOrdem(OkHttpClient(), servidor.url("/"), cofre, io = Dispatchers.Unconfined))
    }

    @Test
    fun abreComAOrdemSalvaESemLogin() {
        val vm = viewModel()

        assertEquals(listOf("b", "a"), vm.estado.value.slugs)
        assertFalse(vm.estado.value.logada)
    }

    @Test
    fun editarSemLoginPedeLogin() {
        val vm = viewModel()

        vm.editar(listOf("b", "a"))

        assertTrue(vm.estado.value.pedindoLogin)
        assertFalse(vm.estado.value.editando)
    }

    @Test
    fun senhaErradaMostraErroELoginCertoLibera() {
        val vm = viewModel()
        vm.abrirLogin()

        servidor.enqueue(MockResponse(code = 401))
        vm.entrar("gui", "errada")
        assertEquals(ErroDeLogin.INCORRETO, vm.estado.value.erroDeLogin)
        assertTrue(vm.estado.value.pedindoLogin)

        servidor.enqueue(MockResponse(code = 200, body = """{"ok":true,"token":"1.a","expira":"x"}"""))
        vm.entrar("gui", "certa")
        assertTrue(vm.estado.value.logada)
        assertFalse(vm.estado.value.pedindoLogin)
    }

    @Test
    fun editarMoverESalvarGuardaANovaOrdem() {
        cofre.guardar("1.a")
        val vm = viewModel()
        vm.editar(listOf("b", "a", "c"))

        vm.mover(2, 0)
        assertEquals(listOf("c", "b", "a"), vm.estado.value.rascunho)

        servidor.enqueue(MockResponse(code = 200, body = """{"ok":true}"""))
        vm.salvar()

        assertFalse(vm.estado.value.editando)
        assertEquals(listOf("c", "b", "a"), vm.estado.value.slugs)
    }

    @Test
    fun salvarComTokenVencidoPedeLoginEMantemORascunho() {
        cofre.guardar("1.a")
        val vm = viewModel()
        vm.editar(listOf("a", "b"))
        vm.mover(1, 0)

        servidor.enqueue(MockResponse(code = 401))
        vm.salvar()

        assertTrue(vm.estado.value.pedindoLogin)
        assertFalse(vm.estado.value.logada)
        assertTrue(vm.estado.value.editando)
        assertEquals(listOf("b", "a"), vm.estado.value.rascunho)
        assertNull(cofre.ler())
    }

    @Test
    fun cancelarDescartaORascunho() {
        cofre.guardar("1.a")
        val vm = viewModel()
        vm.editar(listOf("a", "b"))
        vm.mover(0, 1)

        vm.cancelar()

        assertFalse(vm.estado.value.editando)
        assertEquals(listOf("b", "a"), vm.estado.value.slugs)
    }
}
