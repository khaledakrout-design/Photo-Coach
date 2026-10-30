package com.example.guidance

import android.graphics.Bitmap
import com.example.detection.DetectedObject
import com.example.detection.LocalObjectDetector
import com.example.detection.SubjectSelectionEngine
import com.example.subject.SubjectPoint

/**
 * État d'observation de la scène pour le mode automatique et guidé.
 */
sealed interface SceneAnalysisStatus {
    object Idle : SceneAnalysisStatus
    object ReadyForLocalDetector : SceneAnalysisStatus
    data class Observing(val timestamp: Long = System.currentTimeMillis()) : SceneAnalysisStatus
    data class ObjectDetected(
        val detectedObject: DetectedObject,
        val totalCount: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : SceneAnalysisStatus
}

/**
 * Interface de contrat du moteur de vision par ordinateur local (Jalon 02).
 * Exécute l'inférence sur l'appareil (on-device) sans aucune dépendance cloud.
 */
interface LocalSceneAnalyzer {
    /**
     * Analyse une image du flux caméra pour détecter et localiser les objets réels.
     */
    suspend fun analyzeFrame(bitmap: Bitmap): SubjectPoint?

    /**
     * Évalue l'état de composition de la scène par rapport aux paramètres de cadrage.
     */
    fun evaluateComposition(
        subjectPoint: SubjectPoint?,
        settings: GuidanceSettings
    ): SceneAnalysisStatus
}

/**
 * Implémentation du moteur de perception locale pour le Jalon 02.
 */
class MilestoneTwoSceneAnalyzer(
    private val detector: LocalObjectDetector? = null,
    private val selectionEngine: SubjectSelectionEngine = SubjectSelectionEngine()
) : LocalSceneAnalyzer {

    override suspend fun analyzeFrame(bitmap: Bitmap): SubjectPoint? {
        val det = detector ?: return null
        val result = det.detectObjects(bitmap)
        val selection = selectionEngine.processSelection(result.objects)
        val subject = selection.selectedSubject ?: return null

        return SubjectPoint(
            normalizedX = subject.normalizedCenterX,
            normalizedY = subject.normalizedCenterY
        )
    }

    override fun evaluateComposition(
        subjectPoint: SubjectPoint?,
        settings: GuidanceSettings
    ): SceneAnalysisStatus {
        return if (subjectPoint != null) {
            SceneAnalysisStatus.Observing()
        } else {
            SceneAnalysisStatus.ReadyForLocalDetector
        }
    }
}
