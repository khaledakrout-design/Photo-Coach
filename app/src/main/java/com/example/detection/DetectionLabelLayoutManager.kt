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

        // 1. Filtrer les objets à étiqueter selon le mode d'affichage (Jalon 2.2.1)
        val candidates = when (displayMode) {
            DetectionDisplayMode.ESSENTIAL -> {
                // En mode Essentiel : le sujet sélectionné en priorité absolue.
                // Objets secondaires : uniquement rôles pertinents (ni CONTEXT_OBJECT ni BACKGROUND_OBJECT),
                // confiance >= MEDIUM, et maximum 2 étiquettes secondaires.
                val selected = detectedObjects.filter { it.isSelectedSubject }
                val secondary = detectedObjects.filter {
                    !it.isSelectedSubject &&
                    it.confidenceTier != ConfidenceTier.LOW &&
                    it.relevanceRole != ObjectRelevanceRole.CONTEXT_OBJECT &&
                    it.relevanceRole != ObjectRelevanceRole.BACKGROUND_OBJECT
                }.sortedByDescending { it.confidence }
                 .take(2)
                selected + secondary
            }
            DetectionDisplayMode.DIAGNOSTIC -> {
                // En mode Diagnostic : étiquettes pour toutes les détections valides (triées par sélection puis confiance)
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
                val trackSuffix = obj.trackingId?.let { " [#$it]" } ?: ""
                "${obj.label}$trackSuffix · ${obj.confidencePercent}%"
            }

            // Options horizontales pour chaque positionnement vertical
            val centerX = ((box.left + box.right - labelW) / 2f)
                .coerceIn(screenMarginHorizontal, screenWidth - labelW - screenMarginHorizontal)
            val leftX = box.left
                .coerceIn(screenMarginHorizontal, screenWidth - labelW - screenMarginHorizontal)
            val rightX = (box.right - labelW)
                .coerceIn(screenMarginHorizontal, screenWidth - labelW - screenMarginHorizontal)

            val xCandidates = listOf(centerX, leftX, rightX).distinct()

            var placedX = centerX
            var placedY = box.top - labelH - 4f
            var isPlaced = false
            var requiresConnector = false
            var connStartX = 0f
            var connStartY = 0f
            var connEndX = 0f
            var connEndY = 0f

            // Stratégie 1 : Au-dessus de la boîte (position préférée)
            val yAbove = box.top - labelH - 4f
            if (yAbove >= safeTop && yAbove + labelH <= safeBottom) {
                for (x in xCandidates) {
                    val rect = RectF(x, yAbove, x + labelW, yAbove + labelH)
                    if (!collidesWithAny(rect, placedRects)) {
                        placedX = x
                        placedY = yAbove
                        isPlaced = true
                        break
                    }
                }
            }

            // Stratégie 2 : À l'intérieur haut de la boîte (si la boîte est suffisamment haute)
            if (!isPlaced && (box.bottom - box.top) >= labelH * 1.6f) {
                val yInside = box.top + 6f
                if (yInside >= safeTop && yInside + labelH <= box.bottom && yInside + labelH <= safeBottom) {
                    for (x in xCandidates) {
                        val rect = RectF(x, yInside, x + labelW, yInside + labelH)
                        if (!collidesWithAny(rect, placedRects)) {
                            placedX = x
                            placedY = yInside
                            isPlaced = true
                            break
                        }
                    }
                }
            }

            // Stratégie 3 : En dessous de la boîte
            if (!isPlaced) {
                val yBelow = box.bottom + 6f
                if (yBelow >= safeTop && yBelow + labelH <= safeBottom) {
                    for (x in xCandidates) {
                        val rect = RectF(x, yBelow, x + labelW, yBelow + labelH)
                        if (!collidesWithAny(rect, placedRects)) {
                            placedX = x
                            placedY = yBelow
                            isPlaced = true
                            break
                        }
                    }
                }
            }

            // Stratégie 4 : Déport latéral / vertical avec ligne de liaison
            if (!isPlaced) {
                var foundSlot = false
                var testY = safeTop
                while (testY + labelH <= safeBottom) {
                    val rect = RectF(placedX, testY, placedX + labelW, testY + labelH)
                    if (!collidesWithAny(rect, placedRects)) {
                        placedY = testY
                        foundSlot = true
                        requiresConnector = true
                        connStartX = placedX + (labelW / 2f)
                        connStartY = if (placedY < box.top) placedY + labelH else placedY
                        connEndX = (box.left + box.right) / 2f
                        connEndY = if (placedY < box.top) box.top else box.bottom
                        isPlaced = true
                        break
                    }
                    testY += labelH + labelSpacing
                }

                if (!foundSlot) {
                    // Masquer toute étiquette secondaire si aucun emplacement sans collision n'est disponible
                    // afin de ne jamais obstruer le contenu visuel ou les contrôles de la caméra
                    if (!obj.isSelectedSubject) {
                        continue
                    }

                    // Dernier recours pour le sujet sélectionné ou en mode Diagnostic : décalage avec ligne de liaison
                    placedY = (box.top - labelH - 4f).coerceIn(safeTop, safeBottom - labelH)
                    if (placedRects.isNotEmpty()) {
                        placedY = (placedRects.last().bottom + 4f).coerceIn(safeTop, safeBottom - labelH)
                    }
                    requiresConnector = true
                    connStartX = placedX + (labelW / 2f)
                    connStartY = placedY + (labelH / 2f)
                    connEndX = (box.left + box.right) / 2f
                    connEndY = (box.top + box.bottom) / 2f
                }
            }

            // Clamping strict dans les bornes d'écran autorisées
            placedX = placedX.coerceIn(screenMarginHorizontal, screenWidth - labelW - screenMarginHorizontal)
            placedY = placedY.coerceIn(safeTop, safeBottom - labelH)

            val finalRect = RectF(placedX, placedY, placedX + labelW, placedY + labelH)
            placedRects.add(finalRect)

            placedLabels.add(
                PositionedLabel(
                    objectId = obj.id,
                    displayLabel = labelText,
                    isSelectedSubject = obj.isSelectedSubject,
                    isLocked = obj.isSelectedSubject && obj.isLocked,
                    confidenceTier = obj.confidenceTier,
                    labelX = placedX,
                    labelY = placedY,
                    labelWidth = labelW,
                    labelHeight = labelH,
                    hasConnectorLine = requiresConnector,
                    connectorStartX = connStartX,
                    connectorStartY = connStartY,
                    connectorEndX = connEndX,
                    connectorEndY = connEndY
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
