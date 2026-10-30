package com.example.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gestionnaire coordonnant l'inférence, le suivi d'objets, le verrouillage de sujet
 * et les modes d'affichage du viseur (Jalon 2.1).
 */
class ScenePerceptionManager(
    private val context: Context,
    private val detector: LocalObjectDetector = LocalObjectDetector(context),
    private val tracker: ObjectTracker = ObjectTracker(),
    private val selectionEngine: SubjectSelectionEngine = SubjectSelectionEngine()
) : Closeable {

    companion object {
        private const val TAG = "ScenePerceptionManager"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var confidenceSettings = ConfidenceFilterSettings()
    private var displayMode = DetectionDisplayMode.ESSENTIAL

    private val _perceptionState = MutableStateFlow(PerceptionState(displayMode = displayMode))
    val perceptionState: StateFlow<PerceptionState> = _perceptionState.asStateFlow()

    private val _diagnostics = MutableStateFlow(DetectionDiagnostics(displayMode = displayMode))
    val diagnostics: StateFlow<DetectionDiagnostics> = _diagnostics.asStateFlow()

    private val isProcessingFrame = AtomicBoolean(false)
    private var fpsCounter = 0
    private var fpsWindowStartTime = 0L
    private var currentFps = 0f

    init {
        scope.launch {
            val initialized = detector.initialize()
            if (initialized) {
                _perceptionState.update {
                    it.copy(
                        state = PerceptionStateEnum.ACTIVE,
                        statusText = PerceptionStateEnum.ACTIVE.defaultMessageFr,
                        isModelReady = true,
                        displayMode = displayMode
                    )
                }
                _diagnostics.update {
                    it.copy(isModelLoaded = true, displayMode = displayMode)
                }
            } else {
                val errorMsg = detector.lastError ?: PerceptionStateEnum.ERROR.defaultMessageFr
                _perceptionState.update {
                    it.copy(
                        state = PerceptionStateEnum.ERROR,
                        statusText = "Erreur modèle : $errorMsg",
                        isModelReady = false,
                        displayMode = displayMode
                    )
                }
                _diagnostics.update {
                    it.copy(isModelLoaded = false, displayMode = displayMode)
                }
            }
        }
    }

    /**
     * Traite une nouvelle frame d'analyse issue de CameraX.
     */
    fun onImageFrameAvailable(bitmap: Bitmap) {
        if (!_perceptionState.value.isModelReady) return

        // Éviter la surcharge processeur si la frame précédente s'exécute encore
        if (!isProcessingFrame.compareAndSet(false, true)) {
            return
        }

        try {
            val now = System.currentTimeMillis()
            updateFpsCalculation(now)

            val inferenceResult = detector.detectObjects(bitmap)

            if (inferenceResult.error != null) {
                _perceptionState.update {
                    it.copy(
                        state = PerceptionStateEnum.ERROR,
                        statusText = "Détection momentanément indisponible",
                        detectedObjects = emptyList(),
                        selectedSubject = null,
                        detectionCount = 0
                    )
                }
                _diagnostics.update {
                    it.copy(
                        lastInferenceTimeMs = inferenceResult.inferenceTimeMs,
                        fps = currentFps,
                        detectionCount = 0
                    )
                }
                return
            }

            val rawDetections = inferenceResult.objects

            // 1. Suivi temporel stable & suppression des doublons (NMS)
            val trackedObjects = tracker.update(
                rawDetections = rawDetections,
                lockedTrackingId = selectionEngine.getLockedTrackingId(),
                lockedObjectId = selectionEngine.getLockedSubjectId()
            )

            // 2. Traitement de la sélection du sujet avec priorité verrouillage manuel et hystérésis
            val selectionResult = selectionEngine.processSelection(trackedObjects)

            // 3. Mesure mémoire et télémétrie
            val memoryUsageMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)

            // 4. Détermination précise du statut utilisateur
            val totalValidCount = trackedObjects.size
            val selectedSubj = selectionResult.selectedSubject

            val (newState, statusText) = when {
                selectionResult.isSubjectLost -> {
                    Pair(
                        PerceptionStateEnum.SUBJECT_LOST,
                        PerceptionStateEnum.SUBJECT_LOST.defaultMessageFr
                    )
                }
                selectionResult.isManualLocked && selectedSubj != null -> {
                    Pair(
                        PerceptionStateEnum.OBJECT_FOUND,
                        "🔒 Sujet verrouillé : ${selectedSubj.label} (${selectedSubj.confidencePercent}%)"
                    )
                }
                totalValidCount == 0 -> {
                    Pair(
                        PerceptionStateEnum.NO_OBJECT,
                        PerceptionStateEnum.NO_OBJECT.defaultMessageFr
                    )
                }
                selectedSubj != null && selectedSubj.confidence < confidenceSettings.mediumThreshold -> {
                    Pair(
                        PerceptionStateEnum.LOW_CONFIDENCE,
                        PerceptionStateEnum.LOW_CONFIDENCE.defaultMessageFr
                    )
                }
                selectionResult.isAmbiguous -> {
                    Pair(
                        PerceptionStateEnum.OBJECT_FOUND,
                        "Objets détectés ($totalValidCount) · Touchez pour choisir"
                    )
                }
                totalValidCount == 1 -> {
                    val label = selectedSubj?.label ?: "Objet"
                    val conf = selectedSubj?.confidencePercent ?: 0
                    Pair(
                        PerceptionStateEnum.OBJECT_FOUND,
                        "Objet détecté : $label ($conf%)"
                    )
                }
                else -> {
                    val label = selectedSubj?.label ?: "Objet"
                    Pair(
                        PerceptionStateEnum.OBJECT_FOUND,
                        "Objets détectés ($totalValidCount) · Sujet : $label"
                    )
                }
            }

            _perceptionState.update {
                it.copy(
                    state = newState,
                    detectedObjects = selectionResult.annotatedObjects,
                    selectedSubject = selectedSubj,
                    isManualSelection = selectionResult.isManualLocked,
                    isLocked = selectionResult.isManualLocked,
                    isAmbiguous = selectionResult.isAmbiguous,
                    statusText = statusText,
                    detectionCount = totalValidCount,
                    displayMode = displayMode
                )
            }

            _diagnostics.update {
                it.copy(
                    isModelLoaded = true,
                    detectionCount = totalValidCount,
                    activeTracksCount = trackedObjects.size,
                    lastInferenceTimeMs = inferenceResult.inferenceTimeMs,
                    fps = currentFps,
                    memoryUsageMb = memoryUsageMb,
                    selectedSubjectLabel = selectedSubj?.label,
                    selectedSubjectConfidence = selectedSubj?.confidence,
                    selectedSubjectTrackingId = selectedSubj?.trackingId,
                    isLocked = selectionResult.isManualLocked,
                    displayMode = displayMode
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans le cycle d'analyse visuelle: ${e.message}", e)
            _perceptionState.update {
                it.copy(
                    state = PerceptionStateEnum.ERROR,
                    statusText = "Détection momentanément indisponible"
                )
            }
        } finally {
            isProcessingFrame.set(false)
        }
    }

    private fun updateFpsCalculation(currentTime: Long) {
        if (fpsWindowStartTime == 0L) {
            fpsWindowStartTime = currentTime
        }
        fpsCounter++
        val elapsed = currentTime - fpsWindowStartTime
        if (elapsed >= 1000L) {
            currentFps = (fpsCounter * 1000f) / elapsed
            fpsCounter = 0
            fpsWindowStartTime = currentTime
        }
    }

    /**
     * Bascule entre le mode Essentiel et le mode Diagnostic.
     */
    fun toggleDisplayMode(): DetectionDisplayMode {
        displayMode = if (displayMode == DetectionDisplayMode.ESSENTIAL) {
            DetectionDisplayMode.DIAGNOSTIC
        } else {
            DetectionDisplayMode.ESSENTIAL
        }
        _perceptionState.update { it.copy(displayMode = displayMode) }
        _diagnostics.update { it.copy(displayMode = displayMode) }
        return displayMode
    }

    fun setDisplayMode(mode: DetectionDisplayMode) {
        displayMode = mode
        _perceptionState.update { it.copy(displayMode = mode) }
        _diagnostics.update { it.copy(displayMode = mode) }
    }

    fun updateConfidenceSettings(settings: ConfidenceFilterSettings) {
        confidenceSettings = settings
    }

    fun getConfidenceSettings(): ConfidenceFilterSettings = confidenceSettings

    /**
     * Verrouille manuellement le sujet sous le point tactile (normX, normY).
     */
    fun handleTouchSelection(normX: Float, normY: Float): DetectedObject? {
        val currentObjects = _perceptionState.value.detectedObjects
        val touched = selectionEngine.findTouchedObject(normX, normY, currentObjects)

        if (touched != null) {
            selectionEngine.lockSubjectManually(touched.id, touched.trackingId)
            val updatedSelection = selectionEngine.processSelection(currentObjects)
            _perceptionState.update {
                it.copy(
                    state = PerceptionStateEnum.OBJECT_FOUND,
                    detectedObjects = updatedSelection.annotatedObjects,
                    selectedSubject = updatedSelection.selectedSubject,
                    isManualSelection = true,
                    isLocked = true,
                    isAmbiguous = false,
                    statusText = "🔒 Sujet verrouillé : ${touched.label} (${touched.confidencePercent}%)"
                )
            }
            _diagnostics.update {
                it.copy(
                    isLocked = true,
                    selectedSubjectLabel = touched.label,
                    selectedSubjectConfidence = touched.confidence,
                    selectedSubjectTrackingId = touched.trackingId
                )
            }
            return touched
        }
        return null
    }

    /**
     * Déverrouille le sujet manuel et rétablit la sélection heuristique automatique.
     */
    fun unlockSubject() {
        selectionEngine.unlockSubject()
        val currentObjects = _perceptionState.value.detectedObjects
        val updated = selectionEngine.processSelection(currentObjects)
        _perceptionState.update {
            it.copy(
                detectedObjects = updated.annotatedObjects,
                selectedSubject = updated.selectedSubject,
                isManualSelection = false,
                isLocked = false,
                isAmbiguous = updated.isAmbiguous,
                statusText = if (updated.selectedSubject != null) {
                    "Sélection automatique : ${updated.selectedSubject.label}"
                } else {
                    PerceptionStateEnum.ACTIVE.defaultMessageFr
                }
            )
        }
        _diagnostics.update {
            it.copy(
                isLocked = false,
                selectedSubjectLabel = updated.selectedSubject?.label,
                selectedSubjectConfidence = updated.selectedSubject?.confidence,
                selectedSubjectTrackingId = updated.selectedSubject?.trackingId
            )
        }
    }

    fun clearSelection() {
        unlockSubject()
    }

    override fun close() {
        detector.close()
        tracker.reset()
        selectionEngine.unlockSubject()
    }
}
