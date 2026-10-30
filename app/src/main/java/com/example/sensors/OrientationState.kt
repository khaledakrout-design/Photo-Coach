package com.example.sensors

import kotlin.math.abs

/**
 * Représente l'orientation du smartphone pour le niveau d'horizon photographique.
 */
data class OrientationState(
    val rollDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val isLevel: Boolean = false,
    val isFlatLay: Boolean = false, // Viseur pointé à l'horizontale vers le bas (Flat Lay)
    val isSensorAvailable: Boolean = false
) {
    companion object {
        const val LEVEL_THRESHOLD_DEGREES = 1.0f
    }
}
