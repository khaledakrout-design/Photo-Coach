package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanAccent

/**
 * Grille de composition photographique de la règle des tiers avec points de force.
 */
@Composable
fun RuleOfThirdsOverlay(
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0x55FFFFFF),
    intersectionColor: Color = CyanAccent.copy(alpha = 0.6f)
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val strokeWidthPx = 1.dp.toPx()
        val markerLength = 12.dp.toPx()

        val oneThirdX = width / 3f
        val twoThirdsX = (width * 2f) / 3f
        val oneThirdY = height / 3f
        val twoThirdsY = (height * 2f) / 3f

        // Lignes verticales
        drawLine(
            color = lineColor,
            start = Offset(oneThirdX, 0f),
            end = Offset(oneThirdX, height),
            strokeWidth = strokeWidthPx
        )
        drawLine(
            color = lineColor,
            start = Offset(twoThirdsX, 0f),
            end = Offset(twoThirdsX, height),
            strokeWidth = strokeWidthPx
        )

        // Lignes horizontales
        drawLine(
            color = lineColor,
            start = Offset(0f, oneThirdY),
            end = Offset(width, oneThirdY),
            strokeWidth = strokeWidthPx
        )
        drawLine(
            color = lineColor,
            start = Offset(0f, twoThirdsY),
            end = Offset(width, twoThirdsY),
            strokeWidth = strokeWidthPx
        )

        // Points d'intersection (Power points)
        val points = listOf(
            Offset(oneThirdX, oneThirdY),
            Offset(twoThirdsX, oneThirdY),
            Offset(oneThirdX, twoThirdsY),
            Offset(twoThirdsX, twoThirdsY)
        )

        val markerStroke = 1.5.dp.toPx()
        for (p in points) {
            // Petite croix sur chaque intersection
            drawLine(
                color = intersectionColor,
                start = Offset(p.x - markerLength, p.y),
                end = Offset(p.x + markerLength, p.y),
                strokeWidth = markerStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = intersectionColor,
                start = Offset(p.x, p.y - markerLength),
                end = Offset(p.x, p.y + markerLength),
                strokeWidth = markerStroke,
                cap = StrokeCap.Round
            )
        }
    }
}
