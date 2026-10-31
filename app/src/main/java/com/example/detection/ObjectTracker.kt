package com.example.detection

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Moteur de suivi temporel des objets et de suppression des doublons (Jalon 2.1).
 * - Suppression des doublons (NMS) pour éviter les détections multiples du même objet physique
 * - Maintien stable des identifiants de suivi (trackingId) via association IoU et distance pondérée
 * - Séparation stricte des coordonnées brutes (rawBoundingBox) et des coordonnées lissées (boundingBox)
 * - Période de grâce pour les occlusions temporaires sans perte intempestive du sujet
 */
class ObjectTracker(
    private val smoothingFactor: Float = 0.65f, // Facteur de lissage exponentiel (0..1)
    private val maxMissedFrames: Int = 4,       // Tolérance aux occlusions (images avant suppression)
    private val minMatchScore: Float = 0.28f,   // Seuil minimal d'association piste-détection
    private val duplicateIouThreshold: Float = 0.50f // Seuil NMS de suppression des doublons
) {
    private var nextTrackingId = 1
    private val activeTracks = mutableListOf<DetectedObject>()

    /**
     * Supprime les détections redondantes/doublons du même objet physique (Non-Maximum Suppression et confinement).
     * Préserve scrupuleusement les objets physiques distincts même avec le même label.
     */
    fun suppressDuplicates(
        rawDetections: List<DetectedObject>,
        iouThreshold: Float = duplicateIouThreshold
    ): List<DetectedObject> {
        if (rawDetections.size <= 1) return rawDetections

        val sorted = rawDetections.sortedByDescending { it.confidence }
        val result = mutableListOf<DetectedObject>()

        for (candidate in sorted) {
            var isDuplicate = false
            for (accepted in result) {
                val iou = computeIoU(candidate.rawBoundingBox, accepted.rawBoundingBox)
                val containment = computeContainment(candidate.rawBoundingBox, accepted.rawBoundingBox)
                val sameOrGenericLabel = candidate.label.equals(accepted.label, ignoreCase = true) ||
                        candidate.label.isBlank() || accepted.label.isBlank()

                // Doublon si fort chevauchement IoU OU quasi-inclusion de la même classe
                if ((iou >= iouThreshold || containment >= 0.85f) && sameOrGenericLabel) {
                    isDuplicate = true
                    break
                }
            }
            if (!isDuplicate) {
                result.add(candidate)
            }
        }
        return result
    }

    /**
     * Calcule le taux d'inclusion (surface d'intersection / surface minimale).
     */
    fun computeContainment(b1: RectF, b2: RectF): Float {
        val interLeft = max(b1.left, b2.left)
        val interTop = max(b1.top, b2.top)
        val interRight = min(b1.right, b2.right)
        val interBottom = min(b1.bottom, b2.bottom)

        val interWidth = max(0f, interRight - interLeft)
        val interHeight = max(0f, interBottom - interTop)
        val interArea = interWidth * interHeight

        val area1 = max(0f, b1.right - b1.left) * max(0f, b1.bottom - b1.top)
        val area2 = max(0f, b2.right - b2.left) * max(0f, b2.bottom - b2.top)
        val minArea = min(area1, area2)

        return if (minArea > 0f) interArea / minArea else 0f
    }

    /**
     * Met à jour les pistes avec les nouvelles détections issues de l'inférence.
     */
    @Synchronized
    fun update(
        rawDetections: List<DetectedObject>,
        lockedTrackingId: Int? = null,
        lockedObjectId: String? = null
    ): List<DetectedObject> {
        // 1. Suppression des doublons dans les détections brutes de cette image
        val cleanDetections = suppressDuplicates(rawDetections)

        val matchedTrackIndices = mutableSetOf<Int>()
        val matchedDetectionIndices = mutableSetOf<Int>()
        val updatedTracks = mutableListOf<DetectedObject>()

        // 2. Calcul des scores de similarité entre détections et pistes actives
        data class MatchCandidate(val detIdx: Int, val trackIdx: Int, val score: Float)
        val matchCandidates = mutableListOf<MatchCandidate>()

        for ((dIdx, detection) in cleanDetections.withIndex()) {
            for ((tIdx, track) in activeTracks.withIndex()) {
                val iou = computeIoU(detection.rawBoundingBox, track.boundingBox)
                val dist = computeCenterDistance(detection.rawBoundingBox, track.boundingBox)

                // Même étiquette ou étiquette compatible
                val labelMatch = detection.label.equals(track.label, ignoreCase = true)
                val labelCompatible = labelMatch || detection.label.isBlank() || track.label.isBlank()

                if (labelCompatible) {
                    val detArea = detection.rawBoundingBox.width() * detection.rawBoundingBox.height()
                    val trackArea = track.boundingBox.width() * track.boundingBox.height()
                    val areaRatio = if (maxOf(detArea, trackArea) > 0f) minOf(detArea, trackArea) / maxOf(detArea, trackArea) else 1f

                    // Si IoU est nul et que les surfaces diffèrent trop fortement, ne pas associer
                    if (iou <= 0.02f && areaRatio < 0.25f) continue

                    // Formule d'association combinée : 65% IoU + 35% proximité du centre + bonus label
                    val spatialScore = (iou * 0.65f) + ((1f - (dist / 1.414f).coerceIn(0f, 1f)) * 0.35f)
                    val totalScore = if (labelMatch) spatialScore + 0.15f else spatialScore

                    if (totalScore >= minMatchScore && (iou > 0.05f || dist < 0.28f)) {
                        matchCandidates.add(MatchCandidate(dIdx, tIdx, totalScore))
                    }
                }
            }
        }

        // Tri par score de similarité décroissant (association gloutonne optimale)
        matchCandidates.sortByDescending { it.score }

        for (match in matchCandidates) {
            if (match.detIdx in matchedDetectionIndices || match.trackIdx in matchedTrackIndices) {
                continue
            }

            matchedDetectionIndices.add(match.detIdx)
            matchedTrackIndices.add(match.trackIdx)

            val existingTrack = activeTracks[match.trackIdx]
            val detection = cleanDetections[match.detIdx]

            // Lissage visuel des coordonnées de la boîte englobante
            val smoothedBox = smoothBoundingBox(existingTrack.boundingBox, detection.rawBoundingBox)

            val isLockedMatch = (lockedTrackingId != null && existingTrack.trackingId == lockedTrackingId) ||
                    (lockedObjectId != null && existingTrack.id == lockedObjectId)

            val updatedTrack = detection.copy(
                id = existingTrack.id,
                trackingId = existingTrack.trackingId,
                rawModelLabel = if (detection.rawModelLabel.isNotBlank()) detection.rawModelLabel else existingTrack.rawModelLabel,
                rawBoundingBox = detection.rawBoundingBox,
                boundingBox = smoothedBox,
                consecutiveFrames = existingTrack.consecutiveFrames + 1,
                missedFrames = 0,
                isSelectedSubject = isLockedMatch || existingTrack.isSelectedSubject,
                isLocked = isLockedMatch
            )
            updatedTracks.add(updatedTrack)
        }

        // 3. Traiter les nouvelles détections non associées (création de pistes avec nouvel identifiant stable)
        for ((dIdx, detection) in cleanDetections.withIndex()) {
            if (dIdx !in matchedDetectionIndices) {
                val newTrackingId = nextTrackingId++
                val newId = "track_$newTrackingId"

                val isLockedMatch = (lockedObjectId != null && detection.id == lockedObjectId)

                val newTrack = detection.copy(
                    id = newId,
                    trackingId = newTrackingId,
                    rawBoundingBox = detection.rawBoundingBox,
                    boundingBox = RectF(detection.rawBoundingBox),
                    consecutiveFrames = 1,
                    missedFrames = 0,
                    isSelectedSubject = isLockedMatch,
                    isLocked = isLockedMatch
                )
                updatedTracks.add(newTrack)
            }
        }

        // 4. Traiter les pistes existantes non détectées (tolérance aux occlusions temporaires)
        for ((tIdx, track) in activeTracks.withIndex()) {
            if (tIdx !in matchedTrackIndices) {
                val newMissed = track.missedFrames + 1
                if (newMissed <= maxMissedFrames) {
                    val isLockedMatch = (lockedTrackingId != null && track.trackingId == lockedTrackingId) ||
                            (lockedObjectId != null && track.id == lockedObjectId)

                    // Conserver la piste avec confiance en décroissance douce
                    updatedTracks.add(
                        track.copy(
                            missedFrames = newMissed,
                            confidence = (track.confidence * 0.90f).coerceAtLeast(0.15f),
                            isSelectedSubject = isLockedMatch,
                            isLocked = isLockedMatch
                        )
                    )
                }
            }
        }

        activeTracks.clear()
        activeTracks.addAll(updatedTracks)
        return activeTracks.toList()
    }

    /**
     * Réinitialise le suivi des objets.
     */
    @Synchronized
    fun reset() {
        activeTracks.clear()
        nextTrackingId = 1
    }

    /**
     * Lissage exponentiel entre l'ancienne boîte et la nouvelle boîte brute.
     */
    fun smoothBoundingBox(oldBox: RectF, newRawBox: RectF): RectF {
        val left = (smoothingFactor * newRawBox.left) + ((1f - smoothingFactor) * oldBox.left)
        val top = (smoothingFactor * newRawBox.top) + ((1f - smoothingFactor) * oldBox.top)
        val right = (smoothingFactor * newRawBox.right) + ((1f - smoothingFactor) * oldBox.right)
        val bottom = (smoothingFactor * newRawBox.bottom) + ((1f - smoothingFactor) * oldBox.bottom)

        return RectF(
            left.coerceIn(0f, 1f),
            top.coerceIn(0f, 1f),
            right.coerceIn(0f, 1f),
            bottom.coerceIn(0f, 1f)
        )
    }

    /**
     * Calcule l'Intersection over Union (IoU) entre deux boîtes englobantes normalisées.
     */
    fun computeIoU(b1: RectF, b2: RectF): Float {
        val interLeft = max(b1.left, b2.left)
        val interTop = max(b1.top, b2.top)
        val interRight = min(b1.right, b2.right)
        val interBottom = min(b1.bottom, b2.bottom)

        val interWidth = max(0f, interRight - interLeft)
        val interHeight = max(0f, interBottom - interTop)
        val interArea = interWidth * interHeight

        val area1 = max(0f, b1.right - b1.left) * max(0f, b1.bottom - b1.top)
        val area2 = max(0f, b2.right - b2.left) * max(0f, b2.bottom - b2.top)
        val unionArea = area1 + area2 - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    /**
     * Calcule la distance euclidienne normalisée entre les centres de deux boîtes englobantes.
     */
    fun computeCenterDistance(b1: RectF, b2: RectF): Float {
        val c1X = (b1.left + b1.right) / 2f
        val c1Y = (b1.top + b1.bottom) / 2f
        val c2X = (b2.left + b2.right) / 2f
        val c2Y = (b2.top + b2.bottom) / 2f

        val dx = c1X - c2X
        val dy = c1Y - c2Y
        return sqrt((dx * dx) + (dy * dy))
    }
}
