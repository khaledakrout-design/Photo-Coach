package com.example.detection

import android.graphics.RectF

/**
 * Niveaux de confiance de détection (Jalon 2.1).
 */
enum class ConfidenceTier(val labelFr: String) {
    HIGH("Élevée (≥ 70%)"),
    MEDIUM("Moyenne (45-69%)"),
    LOW("Faible (< 45%)")
}

/**
 * Modes d'affichage du viseur photographique (Jalon 2.1).
 */
enum class DetectionDisplayMode(val titleFr: String, val shortDescFr: String) {
    ESSENTIAL("Essentiel", "Aperçu photographique épuré, sujet principal mis en valeur"),
    DIAGNOSTIC("Diagnostic", "Télémétrie complète, toutes détections et métriques temps réel")
}

/**
 * Paramètres de filtrage de confiance configurables.
 */
data class ConfidenceFilterSettings(
    val highThreshold: Float = 0.70f,
    val mediumThreshold: Float = 0.45f,
    val minConfidence: Float = 0.30f
) {
    fun classify(confidence: Float): ConfidenceTier {
        return when {
            confidence >= highThreshold -> ConfidenceTier.HIGH
            confidence >= mediumThreshold -> ConfidenceTier.MEDIUM
            else -> ConfidenceTier.LOW
        }
    }
}

/**
 * Rôles de pertinence photographique selon la catégorie active (Jalon 2.2).
 */
enum class ObjectRelevanceRole(val labelFr: String, val scoreMultiplier: Float) {
    PRIMARY_CANDIDATE("Sujet prioritaire", 1.35f),
    SECONDARY_OBJECT("Objet secondaire", 1.0f),
    CONTEXT_OBJECT("Élément de contexte", 0.35f),
    BACKGROUND_OBJECT("Arrière-plan / Surface", 0.10f),
    UNKNOWN("Non classifié", 0.70f)
}

/**
 * Représente un objet détecté en temps réel par le moteur de perception visuelle local.
 * Sépare rigoureusement les coordonnées brutes (rawBoundingBox) des coordonnées visuellement lissées (boundingBox),
 * et préserve le label original du modèle (rawModelLabel) distinct de sa traduction française (label).
 */
data class DetectedObject(
    val id: String,
    val label: String,
    val confidence: Float,
    val boundingBox: RectF, // Coordonnées normalisées lissées (0.0f .. 1.0f)
    val rawBoundingBox: RectF = boundingBox, // Coordonnées brutes du modèle sans lissage
    val rawModelLabel: String = "", // Label d'origine du modèle COCO (ex: "cup", "dining table", "bottle")
    val relevanceRole: ObjectRelevanceRole = ObjectRelevanceRole.UNKNOWN,
    val normalizedCenterX: Float = ((boundingBox.left + boundingBox.right) / 2f).coerceIn(0f, 1f),
    val normalizedCenterY: Float = ((boundingBox.top + boundingBox.bottom) / 2f).coerceIn(0f, 1f),
    val normalizedWidth: Float = (boundingBox.right - boundingBox.left).coerceIn(0f, 1f),
    val normalizedHeight: Float = (boundingBox.bottom - boundingBox.top).coerceIn(0f, 1f),
    val trackingId: Int? = null,
    val isSelectedSubject: Boolean = false,
    val isLocked: Boolean = false, // Sujet manuellement verrouillé
    val isAmbiguous: Boolean = false,
    val consecutiveFrames: Int = 1,
    val missedFrames: Int = 0
) {
    val areaFraction: Float
        get() = (normalizedWidth * normalizedHeight).coerceIn(0f, 1f)

    val confidencePercent: Int
        get() = (confidence * 100).toInt().coerceIn(0, 100)

    val confidenceTier: ConfidenceTier
        get() = when {
            confidence >= 0.70f -> ConfidenceTier.HIGH
            confidence >= 0.45f -> ConfidenceTier.MEDIUM
            else -> ConfidenceTier.LOW
        }

    val displayLabelShort: String
        get() = if (label.isNotBlank()) label else "Objet"

    val displayLabel: String
        get() = if (label.isNotBlank()) "$label · $confidencePercent%" else "$confidencePercent%"

    /**
     * Vérifie si un point normalisé (tx, ty) est situé à l'intérieur de la boîte englobante
     * avec une tolérance tactile (slop).
     */
    fun containsNormalizedPoint(tx: Float, ty: Float, padding: Float = 0.035f): Boolean {
        val l = (boundingBox.left - padding).coerceAtLeast(0f)
        val t = (boundingBox.top - padding).coerceAtLeast(0f)
        val r = (boundingBox.right + padding).coerceAtMost(1f)
        val b = (boundingBox.bottom + padding).coerceAtMost(1f)
        return tx in l..r && ty in t..b
    }
}

/**
 * Les trois états visuels distincts de sélection du sujet (Jalon 2.2.1).
 * AMBIGUOUS : Plusieurs sujets possibles — Touchez pour choisir.
 * PROPOSED : Sujet suggéré : [Object]
 * MANUALLY_LOCKED : Sujet verrouillé : [Object]
 */
enum class SubjectVisualState {
    NONE,
    AMBIGUOUS,
    PROPOSED,
    MANUALLY_LOCKED
}

/**
 * États authentiques de perception visuelle pour l'interface utilisateur.
 */
enum class PerceptionStateEnum(val defaultMessageFr: String) {
    INITIALIZING("Initialisation du moteur visuel..."),
    ACTIVE("CAMÉRA ACTIVE · ANALYSE VISUELLE LOCALE"),
    OBJECT_FOUND("Objet détecté"),
    NO_OBJECT("Aucun objet reconnu dans cette image"),
    LOW_CONFIDENCE("Identification incertaine"),
    SUBJECT_LOST("Sujet verrouillé perdu · Touchez pour reverrouiller"),
    ERROR("Détection momentanément indisponible")
}

/**
 * État global de la perception de scène locale transmis à l'UI.
 */
data class PerceptionState(
    val state: PerceptionStateEnum = PerceptionStateEnum.INITIALIZING,
    val detectedObjects: List<DetectedObject> = emptyList(),
    val selectedSubject: DetectedObject? = null,
    val visualState: SubjectVisualState = SubjectVisualState.NONE,
    val isManualSelection: Boolean = false,
    val isLocked: Boolean = false,
    val isAmbiguous: Boolean = false,
    val statusText: String = PerceptionStateEnum.INITIALIZING.defaultMessageFr,
    val detectionCount: Int = 0,
    val isModelReady: Boolean = false,
    val displayMode: DetectionDisplayMode = DetectionDisplayMode.ESSENTIAL
)

/**
 * Données télémétriques et diagnostiques de l'inférence locale (Jalon 2.2.1).
 */
data class DetectionDiagnostics(
    val isModelLoaded: Boolean = false,
    val modelName: String = "EfficientDet-Lite0 (int8 quantifié)",
    val detectionCount: Int = 0,
    val activeTracksCount: Int = 0,
    val lastInferenceTimeMs: Long = 0L,
    val avgInferenceTimeMs: Float = 0f,
    val p95InferenceTimeMs: Long = 0L,
    val minInferenceTimeMs: Long = 0L,
    val maxInferenceTimeMs: Long = 0L,
    val fps: Float = 0f,
    val memoryUsageMb: Long = 0L,
    val selectedSubjectLabel: String? = null,
    val selectedSubjectConfidence: Float? = null,
    val selectedSubjectTrackingId: Int? = null,
    val isLocked: Boolean = false,
    val displayMode: DetectionDisplayMode = DetectionDisplayMode.ESSENTIAL,
    val hardwareDelegate: String = "CPU (optimisé ARM / NEON)"
)
