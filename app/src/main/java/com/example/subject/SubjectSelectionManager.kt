package com.example.subject

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Gestionnaire de sélection du sujet focal.
 * Permet d'enregistrer les coordonnées tactiles normalisées, d'effacer ou de modifier la sélection,
 * et de conserver cette sélection lors des changements de mode.
 */
class SubjectSelectionManager {

    private val _selectionState = MutableStateFlow(SubjectSelectionState())
    val selectionState: StateFlow<SubjectSelectionState> = _selectionState.asStateFlow()

    /**
     * Définit le point focal à partir de coordonnées tactiles réelles et des dimensions du conteneur.
     */
    fun selectTouchPoint(x: Float, y: Float, viewWidth: Float, viewHeight: Float) {
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val normX = (x / viewWidth).coerceIn(0f, 1f)
        val normY = (y / viewHeight).coerceIn(0f, 1f)

        _selectionState.update {
            it.copy(
                focalPoint = SubjectPoint(normalizedX = normX, normalizedY = normY),
                source = SubjectSelectionSource.MANUAL_TOUCH
            )
        }
    }

    /**
     * Définit directement un point normalisé.
     */
    fun setNormalizedPoint(normX: Float, normY: Float, source: SubjectSelectionSource = SubjectSelectionSource.MANUAL_TOUCH) {
        val clampedX = normX.coerceIn(0f, 1f)
        val clampedY = normY.coerceIn(0f, 1f)
        _selectionState.update {
            it.copy(
                focalPoint = SubjectPoint(clampedX, clampedY),
                source = source
            )
        }
    }

    /**
     * Efface la sélection courante.
     */
    fun clearSelection() {
        _selectionState.update {
            it.copy(focalPoint = null)
        }
    }
}
