package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.subject.SubjectSelectionState
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextPrimary
import kotlin.math.roundToInt

/**
 * Superposition interactive pour la sélection manuelle du sujet et point focal.
 */
@Composable
fun SubjectSelectionOverlay(
    selectionState: SubjectSelectionState,
    onTapPoint: (x: Float, y: Float, width: Float, height: Float) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("camera_preview_touch_surface")
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onTapPoint(
                        offset.x,
                        offset.y,
                        size.width.toFloat(),
                        size.height.toFloat()
                    )
                }
            }
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val focal = selectionState.focalPoint

        // Animation de pulsation du réticule focal
        val pulseScale = remember { Animatable(1f) }
        LaunchedEffect(focal?.timestamp) {
            if (focal != null) {
                pulseScale.snapTo(1.4f)
                pulseScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 280, easing = LinearEasing)
                )
            }
        }

        if (focal != null && widthPx > 0f && heightPx > 0f) {
            val pointXPx = focal.normalizedX * widthPx
            val pointYPx = focal.normalizedY * heightPx

            // Réticule graphique
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(pointXPx, pointYPx)
                val baseRadius = 26.dp.toPx() * pulseScale.value
                val strokeWidth = 2.dp.toPx()
                val bracketLen = 10.dp.toPx()

                // Cercle focal principal
                drawCircle(
                    color = CyanAccent,
                    radius = baseRadius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )

                // Point central
                drawCircle(
                    color = CyanAccent,
                    radius = 3.dp.toPx(),
                    center = center
                )

                // Coins de cadrage
                val offsetB = baseRadius + 4.dp.toPx()
                // Haut-gauche
                drawLine(
                    color = CyanAccent,
                    start = Offset(center.x - offsetB, center.y - offsetB),
                    end = Offset(center.x - offsetB + bracketLen, center.y - offsetB),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = CyanAccent,
                    start = Offset(center.x - offsetB, center.y - offsetB),
                    end = Offset(center.x - offsetB, center.y - offsetB + bracketLen),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                // Haut-droit
                drawLine(
                    color = CyanAccent,
                    start = Offset(center.x + offsetB, center.y - offsetB),
                    end = Offset(center.x + offsetB - bracketLen, center.y - offsetB),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = CyanAccent,
                    start = Offset(center.x + offsetB, center.y - offsetB),
                    end = Offset(center.x + offsetB, center.y - offsetB + bracketLen),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                // Bas-gauche
                drawLine(
                    color = CyanAccent,
                    start = Offset(center.x - offsetB, center.y + offsetB),
                    end = Offset(center.x - offsetB + bracketLen, center.y + offsetB),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = CyanAccent,
                    start = Offset(center.x - offsetB, center.y + offsetB),
                    end = Offset(center.x - offsetB, center.y + offsetB - bracketLen),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                // Bas-droit
                drawLine(
                    color = CyanAccent,
                    start = Offset(center.x + offsetB, center.y + offsetB),
                    end = Offset(center.x + offsetB - bracketLen, center.y + offsetB),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = CyanAccent,
                    start = Offset(center.x + offsetB, center.y + offsetB),
                    end = Offset(center.x + offsetB, center.y + offsetB - bracketLen),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            // Badge d'information et bouton effacer
            // Ajuster l'offset pour que le badge ne dépasse pas de l'écran
            val density = LocalDensity.current
            val (badgeOffsetX, badgeOffsetY) = with(density) {
                val x = (pointXPx - 70.dp.toPx()).coerceIn(16.dp.toPx(), widthPx - 160.dp.toPx())
                val y = if (pointYPx > 70.dp.toPx()) {
                    pointYPx - 56.dp.toPx()
                } else {
                    pointYPx + 36.dp.toPx()
                }
                Pair(x, y)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .offset { IntOffset(badgeOffsetX.roundToInt(), badgeOffsetY.roundToInt()) }
                    .background(CharcoalBlack.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = focal.displayString,
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )

                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(18.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable { onClearSelection() }
                        .testTag("clear_subject_selection_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Effacer la sélection",
                        tint = CyanAccent,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
