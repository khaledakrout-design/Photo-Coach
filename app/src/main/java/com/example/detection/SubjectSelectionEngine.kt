package com.example.detection

import com.example.guidance.CreativeIntention
import com.example.guidance.PhotographyCategory
import kotlin.math.sqrt

/**
 * Moteur intelligent de sélection et de verrouillage du sujet principal (Jalon 2.2).
 * - Intègre les profils de pertinence photographique (FOOD, PRODUCT, STILL_LIFE, FLAT_LAY, AUTOMATIC)
 * - Évite catégoriquement la promotion de surfaces ou mobilier d'arrière-plan (table, chaise, téléviseur)
 * - Priorité absolue au verrouillage manuel (Manual Subject Locking)
 * - Gestion explicite de la perte du sujet verrouillé (Subject Loss)
 * - Hystérésis de sélection automatique et détection d'ambiguïté ("Plusieurs sujets possibles — Touchez pour choisir")
 */
class SubjectSelectionEngine(
    private val hysteresisDelta: Float = 0.08f,
    private val maxMissedLockedFrames: Int = 1,
    private val minSelectionConfidence: Float = 0.30f,
    var currentCategory: PhotographyCategory? = null,
    var isAutomaticMode: Boolean = true,
    var currentIntention: CreativeIntention? = null
) {
    private var lockedSubjectId: String? = null
    private var lockedTrackingId: Int? = null
    private var isManuallyLocked: Boolean = false
    private var missedLockedFrames: Int = 0

    // Maintien de l'état pour l'hystérésis automatique
    private var currentAutoSubjectId: String? = null
    private var currentAutoSubjectScore: Float = 0f

    /**
     * Met à jour le contexte photographique actif pour orienter la pertinence des objets.
     */
    fun updatePhotographyContext(
        category: PhotographyCategory?,
        automaticMode: Boolean,
        intention: CreativeIntention? = null
    ) {
        currentCategory = category
        isAutomaticMode = automaticMode
        currentIntention = intention
    }

    /**
     * Surcharge contextuelle compatible avec GuidanceMode.
     */
    fun updateContext(
        category: PhotographyCategory?,
        guidanceMode: com.example.guidance.GuidanceMode
    ) {
        updatePhotographyContext(category, guidanceMode != com.example.guidance.GuidanceMode.GUIDED)
    }

    /**
     * Alias d'évaluation pour compatibilité.
     */
    fun evaluate(objects: List<DetectedObject>): SelectionResult = processSelection(objects)

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
        // Annotation systématique du rôle de pertinence de chaque objet
        val annotatedWithRoles = objects.map { obj ->
            val modelLabel = if (obj.rawModelLabel.isNotBlank()) obj.rawModelLabel else obj.label
            val role = PhotographyRelevanceProfile.determineRole(
                rawModelLabel = modelLabel,
                category = currentCategory,
                isAutomaticMode = isAutomaticMode
            )
            obj.copy(relevanceRole = role)
        }

        if (annotatedWithRoles.isEmpty()) {
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
            val lockedMatch = annotatedWithRoles.find { obj ->
                (lockedTrackingId != null && obj.trackingId == lockedTrackingId) ||
                        (lockedSubjectId != null && obj.id == lockedSubjectId)
            }

            if (lockedMatch != null) {
                missedLockedFrames = 0
                lockedSubjectId = lockedMatch.id
                lockedTrackingId = lockedMatch.trackingId

                val annotated = annotatedWithRoles.map { obj ->
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
                missedLockedFrames++
                val isLost = missedLockedFrames >= maxMissedLockedFrames

                val annotated = annotatedWithRoles.map { obj ->
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

        // 2. SÉLECTION AUTOMATIQUE INTELLIGENTE AVEC HYSTÉRÉSIS & REJET D'ARRIÈRE-PLAN
        val scoredObjects = annotatedWithRoles.map { obj ->
            Pair(obj, computeSubjectScore(obj))
        }.sortedByDescending { it.second }

        val topCandidate = scoredObjects.firstOrNull()
        val secondCandidate = scoredObjects.getOrNull(1)

        // Détection d'ambiguïté entre deux sujets potentiels d'importance très proche
        val isAmbiguous = if (topCandidate != null && secondCandidate != null) {
            val scoreDiff = topCandidate.second - secondCandidate.second
            scoreDiff < 0.08f &&
                    topCandidate.first.confidence >= 0.40f &&
                    secondCandidate.first.confidence >= 0.40f &&
                    topCandidate.first.relevanceRole != ObjectRelevanceRole.BACKGROUND_OBJECT &&
                    secondCandidate.first.relevanceRole != ObjectRelevanceRole.BACKGROUND_OBJECT
        } else {
            false
        }

        // Si le meilleur candidat est un arrière-plan (ex: table vide) avec un score insuffisant, aucun sujet n'est promu
        val isTopCandidateViable = topCandidate != null &&
                (topCandidate.first.relevanceRole != ObjectRelevanceRole.BACKGROUND_OBJECT || topCandidate.second > 0.45f) &&
                topCandidate.second >= 0.20f

        // Hystérésis : empêcher le basculement automatique fréquent
        val chosenSubject: DetectedObject? = if (!isTopCandidateViable) {
            currentAutoSubjectId = null
            currentAutoSubjectScore = 0f
            null
        } else if (currentAutoSubjectId != null) {
            val existingInCandidates = scoredObjects.find { it.first.id == currentAutoSubjectId }
            if (existingInCandidates != null && topCandidate != null && topCandidate.first.id != currentAutoSubjectId) {
                val scoreAdvantage = topCandidate.second - existingInCandidates.second
                if (scoreAdvantage < hysteresisDelta) {
                    existingInCandidates.first
                } else {
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

        val annotated = annotatedWithRoles.map { obj ->
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
     * Score multicritère intelligent d'un objet candidat (Jalon 2.2) :
     * Prend en compte la pertinence contextuelle, la taille adaptée (rejet des surfaces géantes),
     * la centralité, la position en avant-plan et la stabilité temporelle.
     */
    fun computeSubjectScore(obj: DetectedObject): Float {
        // 1. Seuil minimal de confiance
        if (obj.confidence < minSelectionConfidence) {
            return (obj.confidence * 0.15f)
        }
        val confidenceScore = obj.confidence.coerceIn(0f, 1f)

        // 2. Rôle de pertinence photographique
        val role = if (obj.relevanceRole != ObjectRelevanceRole.UNKNOWN) {
            obj.relevanceRole
        } else {
            val modelLabel = if (obj.rawModelLabel.isNotBlank()) obj.rawModelLabel else obj.label
            PhotographyRelevanceProfile.determineRole(modelLabel, currentCategory, isAutomaticMode)
        }

        // 3. Surface : pénaliser drastiquement les surfaces envahissantes d'arrière-plan (> 50%)
        val area = obj.areaFraction
        val areaScore = when {
            area < 0.02f -> (area / 0.02f) * 0.4f // Trop minuscule
            area in 0.02f..0.38f -> 1.0f            // Surface idéale pour un sujet photographique
            area in 0.38f..0.70f -> (1.0f - ((area - 0.38f) / 0.32f) * 0.7f).coerceAtLeast(0.15f)
            else -> 0.08f // Fond complet ou surface géante (ex. table entière recouvrant l'écran)
        }.coerceIn(0f, 1f)

        // 4. Centralité par rapport au centre de cadrage (0.5, 0.5)
        val distCenter = sqrt(
            (obj.normalizedCenterX - 0.5f) * (obj.normalizedCenterX - 0.5f) +
                    (obj.normalizedCenterY - 0.5f) * (obj.normalizedCenterY - 0.5f)
        )
        val maxDist = 0.7071f
        val centralityScore = (1.0f - (distCenter / maxDist)).coerceIn(0f, 1f)

        // 5. Avant-plan : privilégier les objets situés dans les deux tiers inférieurs plutôt que le plafond
        val foregroundScore = if (obj.normalizedCenterY in 0.30f..0.85f) 1.0f else 0.65f

        // 6. Stabilité temporelle (jusqu'à 6 images consécutives)
        val stabilityScore = (obj.consecutiveFrames.coerceAtMost(6) / 6f)

        // Calcul du score de base pondéré
        val baseScore = (confidenceScore * 0.28f) +
                (areaScore * 0.26f) +
                (centralityScore * 0.22f) +
                (foregroundScore * 0.12f) +
                (stabilityScore * 0.12f)

        // Pondération finale par le rôle photographique (bonus pour objets d'intérêt, malus fort pour surfaces)
        return (baseScore * role.scoreMultiplier).coerceAtLeast(0f)
    }

    /**
     * Détecte si un toucher normalisé correspond à une boîte englobante.
     * En cas de chevauchement, sélectionne l'objet le plus spécifique (plus petite surface).
     */
    fun findTouchedObject(touchX: Float, touchY: Float, objects: List<DetectedObject>): DetectedObject? {
        val candidates = objects.filter { it.containsNormalizedPoint(touchX, touchY, padding = 0.035f) }
        return candidates.minByOrNull { it.areaFraction }
    }

    /**
     * Réinitialise le moteur de sélection.
     */
    fun reset() {
        lockedSubjectId = null
        lockedTrackingId = null
        isManuallyLocked = false
        missedLockedFrames = 0
        currentAutoSubjectId = null
        currentAutoSubjectScore = 0f
    }
}

data class SelectionResult(
    val annotatedObjects: List<DetectedObject>,
    val selectedSubject: DetectedObject?,
    val isManualLocked: Boolean,
    val isSubjectLost: Boolean,
    val isAmbiguous: Boolean,
    val visualState: SubjectVisualState = when {
        isManualLocked && selectedSubject != null -> SubjectVisualState.MANUALLY_LOCKED
        isAmbiguous -> SubjectVisualState.AMBIGUOUS
        selectedSubject != null -> SubjectVisualState.PROPOSED
        else -> SubjectVisualState.NONE
    }
) {
    val isManualSelection: Boolean get() = isManualLocked
    val ambiguityMessage: String get() = "Plusieurs sujets possibles — Touchez pour choisir."
}
