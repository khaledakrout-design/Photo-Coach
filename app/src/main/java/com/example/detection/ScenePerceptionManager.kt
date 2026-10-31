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

    // Historique des latences d'inférence sur une fenêtre glissante d'au moins 10 secondes (Jalon 2.2.1)
    private val latencyHistory = java.util.ArrayDeque<Pair<Long, Long>>()
    private val latencyWindowMs = 10000L

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

            // 2. Traitement de la sélection du sujet avec priorité absolue au verrouillage manuel et hystérésis
            val selectionResult = selectionEngine.processSelection(trackedObjects)

            // 3. Mesure mémoire et télémétrie de performance sur fenêtre glissante (>= 10s)
            val memoryUsageMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
            val latencyMs = inferenceResult.inferenceTimeMs

            synchronized(latencyHistory) {
                latencyHistory.addLast(Pair(now, latencyMs))
                while (latencyHistory.isNotEmpty() && (now - latencyHistory.first().first) > latencyWindowMs) {
                    latencyHistory.removeFirst()
                }
            }

            val latenciesSnapshot = synchronized(latencyHistory) { latencyHistory.map { it.second } }
            val avgLatency = if (latenciesSnapshot.isNotEmpty()) latenciesSnapshot.average().toFloat() else latencyMs.toFloat()
            val sortedLatencies = latenciesSnapshot.sorted()
            val p95Index = if (sortedLatencies.isNotEmpty()) ((sortedLatencies.size * 95) / 100).coerceIn(0, sortedLatencies.size - 1) else 0
            val p95Latency = if (sortedLatencies.isNotEmpty()) sortedLatencies[p95Index] else latencyMs
            val minLatency = if (sortedLatencies.isNotEmpty()) sortedLatencies.first() else latencyMs
            val maxLatency = if (sortedLatencies.isNotEmpty()) sortedLatencies.last() else latencyMs

            // 4. Détermination précise des trois états visuels (Jalon 2.2.1)
            // - MANUALLY LOCKED : Sujet verrouillé : [Object]
            // - AMBIGUOUS : Plusieurs sujets possibles — Touchez pour choisir.
            // - PROPOSED : Sujet suggéré : [Object]
            // Règle stricte : L'UI ne doit jamais communiquer simultanément une ambiguïté et un verrouillage manuel.
            val totalValidCount = trackedObjects.size
            val selectedSubj = selectionResult.selectedSubject
            val isManuallyLocked = selectionResult.isManualLocked && selectedSubj != null

            val (visualState, newState, statusText) = when {
                selectionResult.isSubjectLost -> {
                    Triple(
                        SubjectVisualState.NONE,
                        PerceptionStateEnum.SUBJECT_LOST,
                        "Sujet verrouillé perdu · Touchez pour reverrouiller"
                    )
                }
                isManuallyLocked -> {
                    // Priorité absolue : verrouillage manuel
                    Triple(
                        SubjectVisualState.MANUALLY_LOCKED,
                        PerceptionStateEnum.OBJECT_FOUND,
                        "Sujet verrouillé : ${selectedSubj!!.label}"
                    )
                }
                selectionResult.isAmbiguous -> {
                    // Ambiguïté sans verrouillage manuel
                    Triple(
                        SubjectVisualState.AMBIGUOUS,
                        PerceptionStateEnum.OBJECT_FOUND,
                        "Plusieurs sujets possibles — Touchez pour choisir."
                    )
                }
                selectedSubj != null -> {
                    // Proposition automatique
                    Triple(
                        SubjectVisualState.PROPOSED,
                        PerceptionStateEnum.OBJECT_FOUND,
                        "Sujet suggéré : ${selectedSubj.label}"
                    )
                }
                totalValidCount == 0 -> {
                    Triple(
                        SubjectVisualState.NONE,
                        PerceptionStateEnum.NO_OBJECT,
                        PerceptionStateEnum.NO_OBJECT.defaultMessageFr
                    )
                }
                else -> {
                    Triple(
                        SubjectVisualState.NONE,
                        PerceptionStateEnum.ACTIVE,
                        PerceptionStateEnum.ACTIVE.defaultMessageFr
                    )
                }
            }

            _perceptionState.update {
                it.copy(
                    state = newState,
                    detectedObjects = selectionResult.annotatedObjects,
                    selectedSubject = selectedSubj,
                    visualState = visualState,
                    isManualSelection = isManuallyLocked,
                    isLocked = isManuallyLocked,
                    isAmbiguous = (visualState == SubjectVisualState.AMBIGUOUS),
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
                    lastInferenceTimeMs = latencyMs,
                    avgInferenceTimeMs = avgLatency,
                    p95InferenceTimeMs = p95Latency,
                    minInferenceTimeMs = minLatency,
                    maxInferenceTimeMs = maxLatency,
                    fps = currentFps,
                    memoryUsageMb = memoryUsageMb,
                    selectedSubjectLabel = selectedSubj?.label,
                    selectedSubjectConfidence = selectedSubj?.confidence,
                    selectedSubjectTrackingId = selectedSubj?.trackingId,
                    isLocked = isManuallyLocked,
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

    fun updatePhotographyContext(
        category: com.example.guidance.PhotographyCategory?,
        isAutomaticMode: Boolean,
        intention: com.example.guidance.CreativeIntention? = null
    ) {
        selectionEngine.updatePhotographyContext(category, isAutomaticMode, intention)
    }

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
                    visualState = SubjectVisualState.MANUALLY_LOCKED,
                    isManualSelection = true,
                    isLocked = true,
                    isAmbiguous = false,
                    statusText = "Sujet verrouillé : ${touched.label}"
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
     * Verrouille directement un objet spécifique sans passer par les coordonnées tactiles.
     */
    fun lockSubjectDirectly(obj: DetectedObject) {
        selectionEngine.lockSubjectManually(obj.id, obj.trackingId)
        val currentObjects = _perceptionState.value.detectedObjects
        val updatedSelection = selectionEngine.processSelection(currentObjects)
        _perceptionState.update {
            it.copy(
                state = PerceptionStateEnum.OBJECT_FOUND,
                detectedObjects = updatedSelection.annotatedObjects,
                selectedSubject = updatedSelection.selectedSubject,
                visualState = SubjectVisualState.MANUALLY_LOCKED,
                isManualSelection = true,
                isLocked = true,
                isAmbiguous = false,
                statusText = "Sujet verrouillé : ${obj.label}"
            )
        }
        _diagnostics.update {
            it.copy(
                isLocked = true,
                selectedSubjectLabel = obj.label,
                selectedSubjectConfidence = obj.confidence,
                selectedSubjectTrackingId = obj.trackingId
            )
        }
    }

    /**
     * Déverrouille le sujet manuel et rétablit la sélection heuristique automatique.
     */
    fun unlockSubject() {
        selectionEngine.unlockSubject()
        val currentObjects = _perceptionState.value.detectedObjects
        val updated = selectionEngine.processSelection(currentObjects)
        val newVisualState = when {
            updated.isAmbiguous -> SubjectVisualState.AMBIGUOUS
            updated.selectedSubject != null -> SubjectVisualState.PROPOSED
            else -> SubjectVisualState.NONE
        }
        val newStatus = when (newVisualState) {
            SubjectVisualState.AMBIGUOUS -> "Plusieurs sujets possibles — Touchez pour choisir."
            SubjectVisualState.PROPOSED -> "Sujet suggéré : ${updated.selectedSubject?.label}"
            else -> if (updated.annotatedObjects.isEmpty()) {
                PerceptionStateEnum.NO_OBJECT.defaultMessageFr
            } else {
                PerceptionStateEnum.ACTIVE.defaultMessageFr
            }
        }
        _perceptionState.update {
            it.copy(
                detectedObjects = updated.annotatedObjects,
                selectedSubject = updated.selectedSubject,
                visualState = newVisualState,
                isManualSelection = false,
                isLocked = false,
                isAmbiguous = updated.isAmbiguous,
                statusText = newStatus
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
