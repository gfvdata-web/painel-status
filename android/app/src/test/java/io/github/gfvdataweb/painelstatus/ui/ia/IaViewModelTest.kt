package io.github.gfvdataweb.painelstatus.ui.ia

import io.github.gfvdataweb.painelstatus.apoio.IaFixtures
import io.github.gfvdataweb.painelstatus.apoio.IaLocal
import io.github.gfvdataweb.painelstatus.data.CacheDeArquivos
import io.github.gfvdataweb.painelstatus.data.ia.CofreEmMemoria
import io.github.gfvdataweb.painelstatus.data.ia.FonteIa
import io.github.gfvdataweb.painelstatus.data.ia.SessaoIa
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
import mockwebserver3.MockResponse
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Login, carga e recarga da aba IA, com um painel de IA local e o relógio do teste. */
@OptIn(ExperimentalCoroutinesApi::class)
class IaViewModelTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val agendador = TestCoroutineScheduler()
    private val principal = StandardTestDispatcher(agendador)

    private val ia = IaLocal()
    private val cofre = CofreEmMemoria()
    private val cliente = OkHttpClient()

    private fun pedidosDeUso() = ia.pedidos.count { it == "GET /dados/uso.json" }

    private fun viewModel() = IaViewModel(
        FonteIa(
            cliente = cliente,
            base = ia.url,
            sessao = SessaoIa(cliente, ia.url, cofre, relogio = { IaFixtures.AGORA_MS }, io = principal),
            cache = CacheDeArquivos(pasta.root),
            relogio = { IaFixtures.AGORA_MS },
            io = principal,
        ),
        intervaloMs = INTERVALO,
        relogio = { agendador.currentTime },
    )

    @Before
    fun prepara() = Dispatchers.setMain(principal)

    @After
    fun encerra() {
        Dispatchers.resetMain()
        ia.close()
    }

    @Test
    fun semSessaoPedeASenhaSemFazerPedido() = runTest(agendador) {
        val vm = viewModel()
        runCurrent()

        assertEquals(EstadoIa.PrecisaEntrar(), vm.estado.value)
        assertTrue(ia.pedidos.isEmpty())
    }

    @Test
    fun comSessaoCarregaOsDados() = runTest(agendador) {
        cofre.guardar(ia.cookie)
        val vm = viewModel()
        runCurrent()

        val pronto = vm.estado.value as EstadoIa.Pronto
        assertEquals(false, pronto.desatualizado)
        assertEquals(7, pronto.dados.uso!!.dias.size)
        assertEquals(false, vm.atualizando.value)
    }

    @Test
    fun senhaErradaMostraAMensagemENaoGuardaNada() = runTest(agendador) {
        val vm = viewModel()
        runCurrent()

        vm.entrar("errada")
        runCurrent()

        assertEquals(EstadoIa.PrecisaEntrar(falha = FalhaDoLogin.SENHA_ERRADA), vm.estado.value)
        assertNull(cofre.ler())
    }

    @Test
    fun senhaCertaGuardaOCookieECarrega() = runTest(agendador) {
        val vm = viewModel()
        runCurrent()

        vm.entrar("segredo")
        runCurrent()

        assertTrue(vm.estado.value is EstadoIa.Pronto)
        assertEquals(ia.cookie, cofre.ler())
        assertTrue(ia.cookiesRecebidos.all { it == "painel_ia=${ia.cookie}" })
    }

    @Test
    fun painelForaDoArNoLoginAvisaDaConexao() = runTest(agendador) {
        val vm = viewModel()
        runCurrent()
        ia.foraDoAr = true

        vm.entrar("segredo")
        runCurrent()

        assertEquals(EstadoIa.PrecisaEntrar(falha = FalhaDoLogin.SEM_CONEXAO), vm.estado.value)
    }

    @Test
    fun cookieRecusadoVoltaParaASenha() = runTest(agendador) {
        cofre.guardar(ia.cookie)
        ia.substituicoes["uso.json"] = MockResponse(code = 401)
        val vm = viewModel()
        runCurrent()

        assertEquals(EstadoIa.PrecisaEntrar(), vm.estado.value)
        assertNull(cofre.ler())
    }

    @Test
    fun semInternetAbreComOsDadosGuardadosEAvisa() = runTest(agendador) {
        cofre.guardar(ia.cookie)
        viewModel() // primeira abertura enche o cache
        runCurrent()
        ia.foraDoAr = true

        val vm = viewModel()
        runCurrent()

        val pronto = vm.estado.value as EstadoIa.Pronto
        assertTrue(pronto.desatualizado)
        assertNotNull(pronto.dados.limites)
    }

    @Test
    fun semInternetENadaGuardadoViraErro() = runTest(agendador) {
        cofre.guardar(ia.cookie)
        ia.foraDoAr = true
        val vm = viewModel()
        runCurrent()

        assertEquals(EstadoIa.Erro, vm.estado.value)
    }

    @Test
    fun sairApagaTudoEVoltaParaASenha() = runTest(agendador) {
        cofre.guardar(ia.cookie)
        val vm = viewModel()
        runCurrent()

        vm.sair()
        runCurrent()

        assertEquals(EstadoIa.PrecisaEntrar(), vm.estado.value)
        assertNull(cofre.ler())
        assertNull(CacheDeArquivos(pasta.root).ler("dados/uso.json"))
    }

    @Test
    fun acompanharConfereDeMinutoEmMinutoESoComSessao() = runTest(agendador) {
        cofre.guardar(ia.cookie)
        val vm = viewModel()
        backgroundScope.launch { vm.acompanhar() }
        runCurrent()
        assertEquals(1, pedidosDeUso())

        advanceTimeBy(INTERVALO - 1)
        runCurrent()
        assertEquals("antes do intervalo não consulta de novo", 1, pedidosDeUso())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, pedidosDeUso())

        vm.sair()
        advanceTimeBy(3 * INTERVALO)
        runCurrent()
        assertEquals("sem sessão não há recarga", 2, pedidosDeUso())
    }

    private companion object {
        const val INTERVALO = 60_000L
    }
}
