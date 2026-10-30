package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensors.OrientationState
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.LevelTilted
import kotlin.math.abs

/**
 * Indicateur de niveau et d'horizon électronique professionnel.
 * S'ajuste dynamiquement en fonction du gyroscope/accéléromètre du smartphone.
 */
@Composable
fun HorizonLevelOverlay(
    orientation: OrientationState,
    modifier: Modifier = Modifier
) {
    if (!orientation.isSensorAvailable) return

    val isLevel = orientation.isLevel
    val levelColor by animateColorAsState(
        targetValue = if (isLevel) CyanAccent else LevelTilted.copy(alpha = 0.85f),
        label = "horizonLevelColor"
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f

            if (orientation.isFlatLay) {
                // Mode Flat Lay (vue plongeante à 90°) : cible circulaire zénithale
                val outerRadius = 36.dp.toPx()
                val innerRadius = 14.dp.toPx()
                val targetStroke = 1.5.dp.toPx()

                drawCircle(
                    color = levelColor.copy(alpha = 0.5f),
                    radius = outerRadius,
                    center = Offset(centerX, centerY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = targetStroke)
                )

                drawCircle(
                    color = levelColor,
                    radius = innerRadius,
                    center = Offset(centerX, centerY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = targetStroke)
                )

                // Réticule central
                val crossLength = 10.dp.toPx()
                drawLine(
                    color = levelColor,
                    start = Offset(centerX - crossLength, centerY),
                    end = Offset(centerX + crossLength, centerY),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = levelColor,
                    start = Offset(centerX, centerY - crossLength),
                    end = Offset(centerX, centerY + crossLength),
                    strokeWidth = 2.dp.toPx()
                )
            } else {
                // Mode standard : niveau d'horizon avec ligne de référence pivotante
                val lineHalfWidth = 90.dp.toPx()
                val centerGap = 24.dp.toPx()
                val tickHeight = 6.dp.toPx()
                val strokeWidth = 2.dp.toPx()

                // Repères fixes au centre
                val fixedColor = Color.White.copy(alpha = 0.35f)
                drawLine(
                    color = fixedColor,
                    start = Offset(centerX - 10.dp.toPx(), centerY),
                    end = Offset(centerX + 10.dp.toPx(), centerY),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = fixedColor,
                    start = Offset(centerX, centerY - 10.dp.toPx()),
                    end = Offset(centerX, centerY + 10.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Ligne d'horizon mobile (inversée pour compenser le roulis de l'appareil)
                rotate(degrees = -orientation.rollDegrees, pivot = Offset(centerX, centerY)) {
                    // Segment gauche
                    drawLine(
                        color = levelColor,
                        start = Offset(centerX - lineHalfWidth, centerY),
                        end = Offset(centerX - centerGap, centerY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    // Petit taquet gauche
                    drawLine(
                        color = levelColor,
                        start = Offset(centerX - lineHalfWidth, centerY - tickHeight),
                        end = Offset(centerX - lineHalfWidth, centerY + tickHeight),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )

                    // Segment droit
                    drawLine(
                        color = levelColor,
                        start = Offset(centerX + centerGap, centerY),
                        end = Offset(centerX + lineHalfWidth, centerY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    // Petit taquet droit
                    drawLine(
                        color = levelColor,
                        start = Offset(centerX + lineHalfWidth, centerY - tickHeight),
                        end = Offset(centerX + lineHalfWidth, centerY + tickHeight),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Affichage numérique des degrés sous l'horizon
        val angleText = if (orientation.isFlatLay) {
            "FLAT LAY ${String.format("%.1f°", abs(orientation.pitchDegrees))}"
        } else {
            String.format("%+.1f°", -orientation.rollDegrees)
        }

        Text(
            text = angleText,
            color = levelColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 48.dp)
        )
    }
}
