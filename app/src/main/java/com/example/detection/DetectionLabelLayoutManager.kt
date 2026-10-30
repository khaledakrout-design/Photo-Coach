package com.example.detection

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Informations de positionnement d'une étiquette d'objet détecté.
 */
data class PositionedLabel(
    val objectId: String,
    val displayLabel: String,
    val isSelectedSubject: Boolean,
    val isLocked: Boolean,
    val confidenceTier: ConfidenceTier,
    // Coordonnées absolues à l'écran (pixels)
    val labelX: Float,
    val labelY: Float,
    val labelWidth: Float,
    val labelHeight: Float,
    // Si l'étiquette a dû être déportée, point d'ancrage sur la boîte englobante pour la ligne de liaison
    val hasConnectorLine: Boolean = false,
    val connectorStartX: Float = 0f,
    val connectorStartY: Float = 0f,
    val connectorEndX: Float = 0f,
    val connectorEndY: Float = 0f
)

/**
 * Gestionnaire dédié au calcul des positions des étiquettes de détection.
 * Résout les collisions, maintient les étiquettes dans l'écran et évite les contrôles vitaux de la caméra.
 */
class DetectionLabelLayoutManager(
    val screenMarginHorizontal: Float = 16f,
    val topExclusionBoundary: Float = 90f,      // Marge sous la barre d'outils supérieure
    val bottomExclusionBoundary: Float = 175f,  // Marge au-dessus des contrôles et déclencheur
    val labelSpacing: Float = 8f
) {

    /**
     * Calcule la disposition anti-chevauchement des étiquettes pour une liste d'objets détectés.
     */
    fun layoutLabels(
        detectedObjects: List<DetectedObject>,
        projectedBoxes: Map<String, RectF>,
        screenWidth: Float,
        screenHeight: Float,
        estimatedLabelWidth: (DetectedObject) -> Float,
        estimatedLabelHeight: Float,
        displayMode: DetectionDisplayMode = DetectionDisplayMode.ESSENTIAL,
        maxLabelsInEssentialMode: Int = 3
    ): List<PositionedLabel> {
        if (detectedObjects.isEmpty() || screenWidth <= 0f || screenHeight <= 0f) {
            return emptyList()
        }

        // 1. Filtrer les objets à étiqueter selon le mode d'affichage
        val candidates = when (displayMode) {
            DetectionDisplayMode.ESSENTIAL -> {
                // En mode Essentiel : le sujet sélectionné en priorité absolue, puis au maximum
                // les 2 objets secondaires les plus confiants (confiance >= MEDIUM)
                val selected = detectedObjects.filter { it.isSelectedSubject }
                val secondary = detectedObjects.filter { !it.isSelectedSubject && it.confidenceTier != ConfidenceTier.LOW }
                    .sortedByDescending { it.confidence }
                    .take(max(0, maxLabelsInEssentialMode - selected.size))
                selected + secondary
            }
            DetectionDisplayMode.DIAGNOSTIC -> {
                // En mode Diagnostic : étiquette pour toutes les détections valides (triées par sélection puis confiance)
                detectedObjects.sortedWith(
                    compareByDescending<DetectedObject> { it.isSelectedSubject }
                        .thenByDescending { it.confidence }
                )
            }
        }

        val placedLabels = mutableListOf<PositionedLabel>()
        val placedRects = mutableListOf<RectF>()

        val safeTop = topExclusionBoundary
        val safeBottom = (screenHeight - bottomExclusionBoundary).coerceAtLeast(safeTop + 50f)

        for (obj in candidates) {
            val box = projectedBoxes[obj.id] ?: continue
            val labelW = estimatedLabelWidth(obj).coerceIn(40f, screenWidth - (screenMarginHorizontal * 2f))
            val labelH = estimatedLabelHeight

            val labelText = if (displayMode == DetectionDisplayMode.ESSENTIAL) {
                if (obj.isSelectedSubject) obj.displayLabelShort else obj.label
            } else {
                obj.displayLabel
            }

            // Stratégies de placement successives :
            // 1. Au-dessus de la boîte (stratégie préférée)
            // 2. À l'intérieur haut de la boîte
            // 3. En dessous de la boîte
            // 4. Déport latéral avec ligne de liaison

            var finalX = (box.left).coerceIn(screenMarginHorizontal, screenWidth - labelW - screenMarginHorizontal)
            var finalY = box.top - labelH - 4f
            var requiresConnector = false
            var connectorStartX = 0f
            var connectorStartY = 0f
            var connectorEndX = 0f
            var connectorEndY = 0f

            val candidateRectAbove = RectF(finalX, finalY, finalX + labelW, finalY + labelH)

            if (finalY >= safeTop && !collidesWithAny(candidateRectAbove, placedRects)) {
                // Placement 1 réussi : au-dessus
                finalY = candidateRectAbove.top
            } else {
                // Placement 2 : à l'intérieur haut de la boîte
                val candidateRectInside = RectF(finalX, box.top + 6f, finalX + labelW, box.top + 6f + labelH)
                if (candidateRectInside.bottom <= box.bottom &&
                    candidateRectInside.bottom <= safeBottom &&
                    !collidesWithAny(candidateRectInside, placedRects)
                ) {
                    finalY = candidateRectInside.top
                } else {
                    // Placement 3 : en dessous de la boîte
                    val candidateRectBelow = RectF(finalX, box.bottom + 6f, finalX + labelW, box.bottom + 6f + labelH)
                    if (candidateRectBelow.bottom <= safeBottom &&
                        !collidesWithAny(candidateRectBelow, placedRects)
                    ) {
                        finalY = candidateRectBelow.top
                    } else {
                        // Placement 4 : recherche d'un emplacement libre dans l'espace sécurisé avec déport
                        var foundSlot = false
                        var testY = safeTop
                        while (testY + labelH <= safeBottom) {
                            val candidateRectFallback = RectF(finalX, testY, finalX + labelW, testY + labelH)
                            if (!collidesWithAny(candidateRectFallback, placedRects)) {
                                finalY = testY
                                foundSlot = true
                                requiresConnector = true
                                connectorStartX = finalX + (labelW / 2f)
                                connectorStartY = if (finalY < box.top) finalY + labelH else finalY
                                connectorEndX = (box.left + box.right) / 2f
                                connectorEndY = if (finalY < box.top) box.top else box.bottom
                                break
                            }
                            testY += labelH + labelSpacing
                        }

                        if (!foundSlot) {
                            // Forcer un décalage vertical minimal pour éviter un empilement exact
                            finalY = (box.top - labelH - 4f).coerceIn(safeTop, safeBottom - labelH)
                            if (placedRects.isNotEmpty()) {
                                finalY = (placedRects.last().bottom + 4f).coerceIn(safeTop, safeBottom - labelH)
                            }
                        }
                    }
                }
            }

            // Validation finale des bornes d'écran strictes
            finalX = finalX.coerceIn(screenMarginHorizontal, screenWidth - labelW - screenMarginHorizontal)
            finalY = finalY.coerceIn(safeTop, safeBottom - labelH)

            val placedRect = RectF(finalX, finalY, finalX + labelW, finalY + labelH)
            placedRects.add(placedRect)

            placedLabels.add(
                PositionedLabel(
                    objectId = obj.id,
                    displayLabel = labelText,
                    isSelectedSubject = obj.isSelectedSubject,
                    isLocked = obj.isSelectedSubject && obj.isLocked,
                    confidenceTier = obj.confidenceTier,
                    labelX = finalX,
                    labelY = finalY,
                    labelWidth = labelW,
                    labelHeight = labelH,
                    hasConnectorLine = requiresConnector,
                    connectorStartX = connectorStartX,
                    connectorStartY = connectorStartY,
                    connectorEndX = connectorEndX,
                    connectorEndY = connectorEndY
                )
            )
        }

        return placedLabels
    }

    private fun collidesWithAny(candidate: RectF, placed: List<RectF>): Boolean {
        for (rect in placed) {
            if (RectF.intersects(candidate, rect)) {
                return true
            }
        }
        return false
    }
}
