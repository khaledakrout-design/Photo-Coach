package com.example.subject

/**
 * Coordonnées normalisées (0.0f .. 1.0f) d'un point focal sélectionné sur le viseur.
 */
data class SubjectPoint(
    val normalizedX: Float,
    val normalizedY: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    init {
        require(normalizedX in 0f..1f) { "normalizedX doit être compris entre 0 et 1 : $normalizedX" }
        require(normalizedY in 0f..1f) { "normalizedY doit être compris entre 0 et 1 : $normalizedY" }
    }

    val displayString: String
        get() = String.format("X: %.2f | Y: %.2f", normalizedX, normalizedY)
}

/**
 * Source de sélection du sujet.
 * Jalon 01 : sélection manuelle au toucher.
 * Jalon 02 : détection automatique locale.
 */
enum class SubjectSelectionSource {
    MANUAL_TOUCH,
    AUTOMATIC_DETECTOR_RESERVED
}

/**
 * État de sélection du sujet dans le viseur.
 */
data class SubjectSelectionState(
    val focalPoint: SubjectPoint? = null,
    val source: SubjectSelectionSource = SubjectSelectionSource.MANUAL_TOUCH
) {
    val isActive: Boolean
        get() = focalPoint != null
}
