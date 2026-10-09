package io.github.gfvdataweb.painelstatus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Paleta alinhada aos tokens da página (docs/css/estilo.css). Sem "dynamic
// color" do Android 12+: o app mantém a identidade do painel em qualquer celular.

private val CoresClaras = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF4F6FB),
    onSurface = Color(0xFF0F172A),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
)

private val CoresEscuras = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1120),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFDBEAFE),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFE8EDF7),
    surface = Color(0xFF0B1120),
    onSurface = Color(0xFFE8EDF7),
    surfaceContainer = Color(0xFF131C31),
    surfaceContainerLow = Color(0xFF131C31),
    surfaceContainerHigh = Color(0xFF131C31),
    surfaceContainerHighest = Color(0xFF131C31),
    onSurfaceVariant = Color(0xFF93A1BD),
    outline = Color(0xFF24304A),
    outlineVariant = Color(0xFF24304A),
    error = Color(0xFFF87171),
)

/**
 * Cores de situação (selos, etapas, bolinhas do histórico), as mesmas da
 * página (`--positivo`, `--aviso`, `--negativo` e `--f1` do Bolão).
 */
@Immutable
data class CoresDoPainel(
    val ok: Color,
    val okFundo: Color,
    val aviso: Color,
    val avisoFundo: Color,
    val erro: Color,
    val erroFundo: Color,
    val neutro: Color,
    val neutroFundo: Color,
    val f1: Color,
    val f1Fundo: Color,
)

private val PainelClaro = CoresDoPainel(
    ok = Color(0xFF047857),
    okFundo = Color(0xFFD1FAE5),
    aviso = Color(0xFFB45309),
    avisoFundo = Color(0xFFFEF3C7),
    erro = Color(0xFFDC2626),
    erroFundo = Color(0xFFFEE2E2),
    neutro = Color(0xFF64748B),
    neutroFundo = Color(0xFFE2E8F0),
    f1 = Color(0xFFE10600),
    f1Fundo = Color(0xFFFDE2E1),
)

private val PainelEscuro = CoresDoPainel(
    ok = Color(0xFF34D399),
    okFundo = Color(0xFF0B3324),
    aviso = Color(0xFFFBBF24),
    avisoFundo = Color(0xFF3A2A0A),
    erro = Color(0xFFF87171),
    erroFundo = Color(0xFF3A1414),
    neutro = Color(0xFF93A1BD),
    neutroFundo = Color(0xFF24304A),
    f1 = Color(0xFFFF5A4F),
    f1Fundo = Color(0xFF3A1210),
)

val LocalCoresDoPainel = staticCompositionLocalOf { PainelClaro }

/** Tema do app: claro/escuro seguindo o sistema. */
@Composable
fun PainelStatusTheme(
    escuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalCoresDoPainel provides if (escuro) PainelEscuro else PainelClaro) {
        MaterialTheme(
            colorScheme = if (escuro) CoresEscuras else CoresClaras,
            content = content,
        )
    }
}
