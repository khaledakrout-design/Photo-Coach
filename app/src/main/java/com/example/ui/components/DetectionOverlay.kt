package com.example.ui.components

import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.detection.ConfidenceTier
import com.example.detection.DetectionDiagnostics
import com.example.detection.DetectionDisplayMode
import com.example.detection.DetectionLabelLayoutManager
import com.example.detection.DetectedObject
import com.example.detection.PerceptionState
import com.example.detection.PositionedLabel
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanAccentSubtle
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Superposition visuelle des boîtes englobantes réelles et étiquettes intelligentes anti-chevauchement (Jalon 2.1).
 * Intègre le DetectionLabelLayoutManager, le lissage temporel, la gestion de confiance et les modes Essentiel / Diagnostic.
 */
@Composable
fun DetectionOverlay(
    perceptionState: PerceptionState,
    onSubjectSelected: (DetectedObject) -> Unit,
    onUnlockSubject: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("detection_overlay_root")
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        if (widthPx <= 0f || heightPx <= 0f || perceptionState.detectedObjects.isEmpty()) {
            return@BoxWithConstraints
        }

        val density = LocalDensity.current
        val layoutManager = remember {
            DetectionLabelLayoutManager(
                screenMarginHorizontal = with(density) { 14.dp.toPx() },
                topExclusionBoundary = with(density) { 145.dp.toPx() },
                bottomExclusionBoundary = with(density) { 190.dp.toPx() },
                labelSpacing = with(density) { 6.dp.toPx() }
            )
        }

        // Calcul de projection des coordonnées normalisées (upright) vers les pixels de l'écran (FILL_CENTER)
        val cameraAspect = 3f / 4f
        val screenAspect = widthPx / heightPx

        val (scaleX, scaleY, offsetX, offsetY) = if (screenAspect < cameraAspect) {
            val renderedWidth = heightPx * cameraAspect
            val cropOffsetX = -(renderedWidth - widthPx) / 2f
            Quad(renderedWidth, heightPx, cropOffsetX, 0f)
        } else {
            val renderedHeight = widthPx / cameraAspect
            val cropOffsetY = -(renderedHeight - heightPx) / 2f
            Quad(widthPx, renderedHeight, 0f, cropOffsetY)
        }

        fun mapRect(box: RectF): RectF {
            val left = (offsetX + (box.left * scaleX)).coerceIn(0f, widthPx)
            val top = (offsetY + (box.top * scaleY)).coerceIn(0f, heightPx)
            val right = (offsetX + (box.right * scaleX)).coerceIn(0f, widthPx)
            val bottom = (offsetY + (box.bottom * scaleY)).coerceIn(0f, heightPx)
            return RectF(left, top, right, bottom)
        }

        // Cartographie des boîtes projetées en pixels
        val projectedBoxes = remember(perceptionState.detectedObjects, widthPx, heightPx) {
            perceptionState.detectedObjects.associate { obj ->
                obj.id to mapRect(obj.boundingBox)
            }
        }

        val estimatedLabelHeightPx = with(density) { 26.dp.toPx() }
        val charWidthPx = with(density) { 7.5.dp.toPx() }
        val paddingHorizontalPx = with(density) { 18.dp.toPx() }

        // Calcul de la disposition optimisée anti-collision des étiquettes
        val positionedLabels: List<PositionedLabel> = remember(
            perceptionState.detectedObjects,
            perceptionState.displayMode,
            projectedBoxes,
            widthPx,
            heightPx
        ) {
            layoutManager.layoutLabels(
                detectedObjects = perceptionState.detectedObjects,
                projectedBoxes = projectedBoxes,
                screenWidth = widthPx,
                screenHeight = heightPx,
                estimatedLabelWidth = { obj ->
                    val textLen = if (perceptionState.displayMode == DetectionDisplayMode.ESSENTIAL) {
                        (if (obj.isSelectedSubject) obj.displayLabelShort else obj.label).length
                    } else {
                        obj.displayLabel.length
                    }
                    val lockBonus = if (obj.isLocked) with(density) { 16.dp.toPx() } else 0f
                    (textLen * charWidthPx) + paddingHorizontalPx + lockBonus
                },
                estimatedLabelHeight = estimatedLabelHeightPx,
                displayMode = perceptionState.displayMode,
                maxLabelsInEssentialMode = 3
            )
        }

        // 1. DESSIN CANVAS : Boîtes englobantes, réticules et lignes de liaison des étiquettes déportées
        Canvas(modifier = Modifier.fillMaxSize()) {
            val isDiagnostic = perceptionState.displayMode == DetectionDisplayMode.DIAGNOSTIC

            perceptionState.detectedObjects.forEach { obj ->
                val pRect = projectedBoxes[obj.id] ?: return@forEach
                val isSelected = obj.isSelectedSubject

                // En mode Essentiel : masquer les boîtes de faible confiance sauf si c'est le sujet sélectionné
                if (!isDiagnostic && obj.confidenceTier == ConfidenceTier.LOW && !isSelected) {
                    return@forEach
                }

                val rectW = (pRect.right - pRect.left).coerceAtLeast(0f)
                val rectH = (pRect.bottom - pRect.top).coerceAtLeast(0f)

                // En mode diagnostic, dessiner la boîte brute (non lissée) en pointillés discrets
                if (isDiagnostic) {
                    val rawRect = mapRect(obj.rawBoundingBox)
                    val rawW = (rawRect.right - rawRect.left).coerceAtLeast(0f)
                    val rawH = (rawRect.bottom - rawRect.top).coerceAtLeast(0f)
                    drawRect(
                        color = Color.White.copy(alpha = 0.18f),
                        topLeft = Offset(rawRect.left, rawRect.top),
                        size = Size(rawW, rawH),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    )
                }

                // Couleur et épaisseur de contour adaptées au niveau de confiance
                val (strokeColor, strokeWidth) = when {
                    isSelected -> Pair(CyanAccent, 2.5.dp.toPx())
                    obj.confidenceTier == ConfidenceTier.HIGH -> Pair(Color.White.copy(alpha = 0.65f), 1.4.dp.toPx())
                    obj.confidenceTier == ConfidenceTier.MEDIUM -> Pair(Color.White.copy(alpha = 0.28f), 1.0.dp.toPx())
                    else -> Pair(Color.White.copy(alpha = 0.15f), 0.8.dp.toPx())
                }

                // Boîte principale (lissée temporellement)
                drawRect(
                    color = strokeColor,
                    topLeft = Offset(pRect.left, pRect.top),
                    size = Size(rectW, rectH),
                    style = Stroke(width = strokeWidth)
                )

                // Réticules de coin pour le sujet sélectionné
                if (isSelected) {
                    val cornerLen = (14.dp.toPx()).coerceAtMost(minOf(rectW, rectH) / 3f)
                    if (cornerLen > 2f) {
                        val cornerColor = CyanAccent
                        val cornerStroke = 3.dp.toPx()

                        // Coin haut-gauche
                        drawLine(cornerColor, Offset(pRect.left, pRect.top), Offset(pRect.left + cornerLen, pRect.top), cornerStroke, StrokeCap.Round)
                        drawLine(cornerColor, Offset(pRect.left, pRect.top), Offset(pRect.left, pRect.top + cornerLen), cornerStroke, StrokeCap.Round)

                        // Coin haut-droit
                        drawLine(cornerColor, Offset(pRect.right, pRect.top), Offset(pRect.right - cornerLen, pRect.top), cornerStroke, StrokeCap.Round)
                        drawLine(cornerColor, Offset(pRect.right, pRect.top), Offset(pRect.right, pRect.top + cornerLen), cornerStroke, StrokeCap.Round)

                        // Coin bas-gauche
                        drawLine(cornerColor, Offset(pRect.left, pRect.bottom), Offset(pRect.left + cornerLen, pRect.bottom), cornerStroke, StrokeCap.Round)
                        drawLine(cornerColor, Offset(pRect.left, pRect.bottom), Offset(pRect.left, pRect.bottom - cornerLen), cornerStroke, StrokeCap.Round)

                        // Coin bas-droit
                        drawLine(cornerColor, Offset(pRect.right, pRect.bottom), Offset(pRect.right - cornerLen, pRect.bottom), cornerStroke, StrokeCap.Round)
                        drawLine(cornerColor, Offset(pRect.right, pRect.bottom), Offset(pRect.right, pRect.bottom + cornerLen), cornerStroke, StrokeCap.Round)

                        // Point focal central
                        val cx = (pRect.left + pRect.right) / 2f
                        val cy = (pRect.top + pRect.bottom) / 2f
                        drawCircle(CyanAccent, radius = 2.5.dp.toPx(), center = Offset(cx, cy))
                    }
                }
            }

            // Dessin des lignes de liaison des étiquettes déportées (si déport nécessaire)
            positionedLabels.forEach { label ->
                if (label.hasConnectorLine) {
                    val connectorColor = if (label.isSelectedSubject) CyanAccent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.45f)
                    drawLine(
                        color = connectorColor,
                        start = Offset(label.connectorStartX, label.connectorStartY),
                        end = Offset(label.connectorEndX, label.connectorEndY),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                    // Petit point d'ancrage sur le bord de la boîte
                    drawCircle(
                        color = connectorColor,
                        radius = 2.dp.toPx(),
                        center = Offset(label.connectorEndX, label.connectorEndY)
                    )
                }
            }
        }

        // 2. ÉTIQUETTES POSITIONNÉES SANS CHEVAUCHEMENT (Composables interactifs)
        positionedLabels.forEach { label ->
            val isSelected = label.isSelectedSubject
            val isLocked = label.isLocked
            val obj = perceptionState.detectedObjects.find { it.id == label.objectId }

            Box(
                modifier = Modifier
                    .offset { IntOffset(label.labelX.roundToInt(), label.labelY.roundToInt()) }
                    .clickable {
                        if (obj != null) {
                            if (isSelected && isLocked) {
                                onUnlockSubject()
                            } else {
                                onSubjectSelected(obj)
                            }
                        }
                    }
                    .background(
                        color = if (isSelected) CyanAccent else CharcoalBlack.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) PureWhite else if (label.confidenceTier == ConfidenceTier.HIGH) CharcoalBorder else CharcoalBorder.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .testTag("detected_object_pill_${label.objectId}")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        if (isLocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Sujet verrouillé",
                                tint = CharcoalBlack,
                                modifier = Modifier.size(10.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CenterFocusStrong,
                                contentDescription = null,
                                tint = CharcoalBlack,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        text = if (isSelected && !isLocked) "★ ${label.displayLabel}" else label.displayLabel,
                        color = if (isSelected) CharcoalBlack else if (label.confidenceTier == ConfidenceTier.HIGH) TextPrimary else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Diagnostic télémétrique en temps réel (Jalon 02 & Jalon 2.1).
 * Affiche la latence d'inférence, le taux de rafraîchissement (FPS), l'empreinte mémoire, le modèle et le sujet verrouillé.
 */
@Composable
fun DetectionDiagnosticsHUD(
    diagnostics: DetectionDiagnostics,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(CharcoalBlack.copy(alpha = 0.90f), RoundedCornerShape(10.dp))
            .border(1.dp, CyanAccent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("detection_diagnostics_hud")
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "DIAGNOSTIC PERCEPTION LOCALE",
                    color = CyanAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
            Text(
                text = "Modèle : ${diagnostics.modelName}",
                color = TextSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Inférence : ${diagnostics.lastInferenceTimeMs} ms • Taux : ${String.format("%.1f", diagnostics.fps)} FPS • Mémoire : ${diagnostics.memoryUsageMb} Mo",
                color = TextPrimary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Objets : ${diagnostics.detectionCount} (Pistes : ${diagnostics.activeTracksCount}) • Mode : ${if (diagnostics.displayMode == DetectionDisplayMode.ESSENTIAL) "Essentiel" else "Diagnostic"}",
                color = TextSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
            if (diagnostics.selectedSubjectLabel != null) {
                val lockStatus = if (diagnostics.isLocked) "🔒 VERROUILLÉ" else "AUTO"
                val trackInfo = diagnostics.selectedSubjectTrackingId?.let { " [#$it]" } ?: ""
                Text(
                    text = "Sujet : ${diagnostics.selectedSubjectLabel}$trackInfo (${((diagnostics.selectedSubjectConfidence ?: 0f) * 100).toInt()}%) • $lockStatus",
                    color = CyanAccent,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private data class Quad(val scaleX: Float, val scaleY: Float, val offsetX: Float, val offsetY: Float)
