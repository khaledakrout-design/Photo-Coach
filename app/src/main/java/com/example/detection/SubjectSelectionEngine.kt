package com.example.detection

import kotlin.math.sqrt

/**
 * Moteur de sélection et de verrouillage du sujet principal (Jalon 2.1).
 * - Priorité absolue au verrouillage manuel (Manual Subject Locking)
 * - Persistance du verrouillage basée sur l'identifiant de suivi stable (trackingId)
 * - Gestion explicite de la perte du sujet verrouillé (Subject Loss) sans saut intempestif
 * - Hystérésis de sélection automatique pour éliminer le clignotement entre sujets équivalents
 */
class SubjectSelectionEngine(
    private val hysteresisDelta: Float = 0.12f,
    private val maxMissedLockedFrames: Int = 4
) {
    private var lockedSubjectId: String? = null
    private var lockedTrackingId: Int? = null
    private var isManuallyLocked: Boolean = false
    private var missedLockedFrames: Int = 0

    // Maintien de l'état pour l'hystérésis automatique
    private var currentAutoSubjectId: String? = null
    private var currentAutoSubjectScore: Float = 0f

    /**
     * Verrouille manuellement un objet spécifique comme sujet principal.
     */
    fun lockSubjectManually(objectId: String, trackingId: Int? = null) {
        lockedSubjectId = objectId
        lockedTrackingId = trackingId
        isManuallyLocked = true
        missedLockedFrames = 0
    }

    /**
     * Déverrouille le sujet manuel et réactive la sélection automatique par heuristique.
     */
    fun unlockSubject() {
        lockedSubjectId = null
        lockedTrackingId = null
        isManuallyLocked = false
        missedLockedFrames = 0
    }

    fun isSubjectLocked(): Boolean = isManuallyLocked

    fun getLockedSubjectId(): String? = if (isManuallyLocked) lockedSubjectId else null

    fun getLockedTrackingId(): Int? = if (isManuallyLocked) lockedTrackingId else null

    /**
     * Traite la sélection du sujet principal sur la liste des objets suivis.
     */
    fun processSelection(objects: List<DetectedObject>): SelectionResult {
        if (objects.isEmpty()) {
            if (isManuallyLocked) {
                missedLockedFrames++
                val isLost = missedLockedFrames >= maxMissedLockedFrames
                return SelectionResult(
                    annotatedObjects = emptyList(),
                    selectedSubject = null,
                    isManualLocked = true,
                    isSubjectLost = isLost,
                    isAmbiguous = false
                )
            }
            currentAutoSubjectId = null
            currentAutoSubjectScore = 0f
            return SelectionResult(
                annotatedObjects = emptyList(),
                selectedSubject = null,
                isManualLocked = false,
                isSubjectLost = false,
                isAmbiguous = false
            )
        }

        // 1. PRIORITÉ ABSOLUE : Verrouillage manuel (Manual Subject Lock)
        if (isManuallyLocked) {
            val lockedMatch = objects.find { obj ->
                (lockedTrackingId != null && obj.trackingId == lockedTrackingId) ||
                        (lockedSubjectId != null && obj.id == lockedSubjectId)
            }

            if (lockedMatch != null) {
                missedLockedFrames = 0
                // Mettre à jour les références stables
                lockedSubjectId = lockedMatch.id
                lockedTrackingId = lockedMatch.trackingId

                val annotated = objects.map { obj ->
                    val isThis = obj.id == lockedMatch.id
                    obj.copy(isSelectedSubject = isThis, isLocked = isThis, isAmbiguous = false)
                }

                return SelectionResult(
                    annotatedObjects = annotated,
                    selectedSubject = lockedMatch.copy(isSelectedSubject = true, isLocked = true),
                    isManualLocked = true,
                    isSubjectLost = false,
                    isAmbiguous = false
                )
            } else {
                // Le sujet verrouillé est momentanément ou définitivement absent
                missedLockedFrames++
                val isLost = missedLockedFrames >= maxMissedLockedFrames

                val annotated = objects.map { obj ->
                    obj.copy(isSelectedSubject = false, isLocked = false, isAmbiguous = false)
                }

                return SelectionResult(
                    annotatedObjects = annotated,
                    selectedSubject = null,
                    isManualLocked = true,
                    isSubjectLost = isLost,
                    isAmbiguous = false
                )
            }
        }

        // 2. SÉLECTION AUTOMATIQUE AVEC HYSTÉRÉSIS
        val scoredObjects = objects.map { obj ->
            Pair(obj, computeSubjectScore(obj))
        }.sortedByDescending { it.second }

        val topCandidate = scoredObjects.firstOrNull()
        val secondCandidate = scoredObjects.getOrNull(1)

        // Détection d'ambiguïté entre les deux meilleurs candidats
        val isAmbiguous = if (topCandidate != null && secondCandidate != null) {
            val scoreDiff = topCandidate.second - secondCandidate.second
            scoreDiff < 0.08f && topCandidate.first.confidence >= 0.40f && secondCandidate.first.confidence >= 0.40f
        } else {
            false
        }

        // Hystérésis : empêcher le basculement automatique fréquent
        val chosenSubject: DetectedObject? = if (currentAutoSubjectId != null) {
            val existingInCandidates = scoredObjects.find { it.first.id == currentAutoSubjectId }
            if (existingInCandidates != null && topCandidate != null && topCandidate.first.id != currentAutoSubjectId) {
                val scoreAdvantage = topCandidate.second - existingInCandidates.second
                if (scoreAdvantage < hysteresisDelta) {
                    // Conserver le sujet automatique précédent (stabilisation temporelle)
                    existingInCandidates.first
                } else {
                    // Nouveau sujet dominant avec une avance significative
                    currentAutoSubjectId = topCandidate.first.id
                    currentAutoSubjectScore = topCandidate.second
                    topCandidate.first
                }
            } else {
                currentAutoSubjectId = topCandidate?.first?.id
                currentAutoSubjectScore = topCandidate?.second ?: 0f
                topCandidate?.first
            }
        } else {
            currentAutoSubjectId = topCandidate?.first?.id
            currentAutoSubjectScore = topCandidate?.second ?: 0f
            topCandidate?.first
        }

        val annotated = objects.map { obj ->
            val isSelected = (chosenSubject != null && obj.id == chosenSubject.id)
            obj.copy(isSelectedSubject = isSelected, isLocked = false, isAmbiguous = isAmbiguous && isSelected)
        }

        return SelectionResult(
            annotatedObjects = annotated,
            selectedSubject = chosenSubject?.copy(isSelectedSubject = true, isLocked = false, isAmbiguous = isAmbiguous),
            isManualLocked = false,
            isSubjectLost = false,
            isAmbiguous = isAmbiguous
        )
    }

    /**
     * Score multicritère d'un objet candidat :
     * Score = (Confiance * 0.35) + (Surface adaptée * 0.30) + (Centralité * 0.25) + (Stabilité * 0.10)
     */
    fun computeSubjectScore(obj: DetectedObject): Float {
        // 1. Confiance (0..1)
        val confidenceScore = obj.confidence.coerceIn(0f, 1f)

        // 2. Surface : la surface idéale d'un sujet est entre 5% et 45% de l'image
        val area = obj.areaFraction
        val areaScore = when {
            area < 0.03f -> (area / 0.03f) * 0.5f // Trop petit
            area in 0.03f..0.45f -> 1.0f            // Surface idéale pour un sujet photographique
            area in 0.45f..0.85f -> 1.0f - ((area - 0.45f) / 0.40f) * 0.5f // Objet envahissant
            else -> 0.25f // Fond complet
        }.coerceIn(0f, 1f)

        // 3. Centralité par rapport au centre de cadrage (0.5, 0.5)
        val distCenter = sqrt(
            (obj.normalizedCenterX - 0.5f) * (obj.normalizedCenterX - 0.5f) +
                    (obj.normalizedCenterY - 0.5f) * (obj.normalizedCenterY - 0.5f)
        )
        val maxDist = 0.7071f
        val centralityScore = (1.0f - (distCenter / maxDist)).coerceIn(0f, 1f)

        // 4. Stabilité temporelle (jusqu'à 6 images consécutives)
        val stabilityScore = (obj.consecutiveFrames.coerceAtMost(6) / 6f)

        return (confidenceScore * 0.35f) +
                (areaScore * 0.30f) +
                (centralityScore * 0.25f) +
                (stabilityScore * 0.10f)
    }

    /**
     * Détecte si un toucher normalisé correspond à une boîte englobante.
     * En cas de chevauchement, sélectionne l'objet le plus spécifique (plus petite surface).
     */
    fun findTouchedObject(touchX: Float, touchY: Float, objects: List<DetectedObject>): DetectedObject? {
        val candidates = objects.filter { it.containsNormalizedPoint(touchX, touchY, padding = 0.035f) }
        return candidates.minByOrNull { it.areaFraction }
    }
}

data class SelectionResult(
    val annotatedObjects: List<DetectedObject>,
    val selectedSubject: DetectedObject?,
    val isManualLocked: Boolean,
    val isSubjectLost: Boolean,
    val isAmbiguous: Boolean
)
