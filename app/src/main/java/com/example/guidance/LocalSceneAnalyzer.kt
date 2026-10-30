package com.example.guidance

import android.graphics.Bitmap
import com.example.subject.SubjectPoint

/**
 * État d'observation de la scène pour le mode automatique.
 */
sealed interface SceneAnalysisStatus {
    object Idle : SceneAnalysisStatus
    object ReadyForLocalDetector : SceneAnalysisStatus
    data class Observing(val timestamp: Long = System.currentTimeMillis()) : SceneAnalysisStatus
}

/**
 * Interface de contrat prête pour l'intégration future du moteur de vision par ordinateur local (Jalon 02).
 * Aucun résultat factice ou simulé n'est produit dans ce jalon (Milestone 01).
 */
interface LocalSceneAnalyzer {
    /**
     * Analyse un flux d'images en local pour la détection automatique du sujet.
     * Prévu pour intégration TFLite/MLKit dans un jalon ultérieur.
     */
    suspend fun analyzeFrame(bitmap: Bitmap): SubjectPoint?

    /**
     * Évalue l'alignement de composition par rapport aux intentions de guidage.
     */
    fun evaluateComposition(
        subjectPoint: SubjectPoint?,
        settings: GuidanceSettings
    ): SceneAnalysisStatus
}

/**
 * Implémentation de base pour le Jalon 01 : en attente du moteur de vision locale.
 */
class MilestoneOneSceneAnalyzer : LocalSceneAnalyzer {
    override suspend fun analyzeFrame(bitmap: Bitmap): SubjectPoint? {
        // En attente d'implémentation du moteur de vision par ordinateur local (Jalon 02)
        return null
    }

    override fun evaluateComposition(
        subjectPoint: SubjectPoint?,
        settings: GuidanceSettings
    ): SceneAnalysisStatus {
        return SceneAnalysisStatus.ReadyForLocalDetector
    }
}
