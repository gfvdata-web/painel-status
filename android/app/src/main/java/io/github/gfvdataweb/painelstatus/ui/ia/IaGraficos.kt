package io.github.gfvdataweb.painelstatus.ui.ia

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Cores das séries de modelos (as `--serie-N` da página); o modelo `i` usa a `i % 7`. */
val CORES_DAS_SERIES = listOf(
    Color(0xFF2563EB),
    Color(0xFFD97706),
    Color(0xFF059669),
    Color(0xFF7C3AED),
    Color(0xFFDB2777),
    Color(0xFF0891B2),
    Color(0xFF65A30D),
)

fun corDaSerie(indice: Int): Color = CORES_DAS_SERIES[indice % CORES_DAS_SERIES.size]

/** Duas linhas (janela de 5 h e semanal) de 0 a 100 %, do histórico de leituras dos limites. */
@Composable
fun GraficoLimites(
    cincoHoras: List<List<Pair<Double, Double>>>,
    seteDias: List<List<Pair<Double, Double>>>,
    desdeS: Double,
    ateS: Double,
    corCincoHoras: Color,
    corSeteDias: Color,
    corGrade: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val margem = 2.dp.toPx()
        val altura = size.height - 2 * margem
        val periodo = (ateS - desdeS).coerceAtLeast(1.0)
        fun x(ts: Double) = (((ts - desdeS) / periodo).coerceIn(0.0, 1.0) * size.width).toFloat()
        fun y(pct: Double) = (margem + altura - pct / 100.0 * altura).toFloat()

        listOf(0.0, 50.0, 100.0).forEach { pct ->
            drawLine(corGrade, Offset(0f, y(pct)), Offset(size.width, y(pct)), strokeWidth = 1.dp.toPx())
        }
        fun linha(trechos: List<List<Pair<Double, Double>>>, cor: Color) {
            trechos.forEach { trecho ->
                if (trecho.size == 1) {
                    drawCircle(cor, radius = 2.dp.toPx(), center = Offset(x(trecho[0].first), y(trecho[0].second)))
                    return@forEach
                }
                val caminho = Path()
                trecho.forEachIndexed { i, (ts, pct) ->
                    if (i == 0) caminho.moveTo(x(ts), y(pct)) else caminho.lineTo(x(ts), y(pct))
                }
                drawPath(caminho, cor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        linha(seteDias, corSeteDias)
        linha(cincoHoras, corCincoHoras)
    }
}

/** Barras empilhadas de tokens por dia, uma cor por modelo (a ordem é a de [SerieModelos.modelos]). */
@Composable
fun GraficoModelos(serie: SerieModelos, corGrade: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val dias = serie.dias.size
        if (dias == 0) return@Canvas
        val maximo = serie.totalPorDia.max().coerceAtLeast(1).toFloat()
        val faixa = size.width / dias
        val largura = faixa * 0.7f
        drawLine(corGrade, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        for (dia in 0 until dias) {
            var base = size.height
            serie.tokens.forEachIndexed { modelo, valores ->
                val altura = valores[dia] / maximo * size.height
                if (altura > 0f) {
                    drawRect(
                        corDaSerie(modelo),
                        topLeft = Offset(dia * faixa + (faixa - largura) / 2, base - altura),
                        size = Size(largura, altura),
                    )
                    base -= altura
                }
            }
        }
    }
}
