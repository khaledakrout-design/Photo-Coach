package com.example.camera

import android.app.Application
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.detection.DetectionDiagnostics
import com.example.detection.PerceptionState
import com.example.detection.ScenePerceptionManager
import com.example.guidance.CreativeIntention
import com.example.guidance.GuidanceMode
import com.example.guidance.GuidanceSettings
import com.example.guidance.GuidanceType
import com.example.guidance.PhotographyCategory
import com.example.sensors.DeviceOrientationManager
import com.example.sensors.OrientationState
import com.example.subject.SubjectPoint
import com.example.subject.SubjectSelectionManager
import com.example.subject.SubjectSelectionSource
import com.example.subject.SubjectSelectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraUiState(
    val hasCameraPermission: Boolean = false,
    val isCapturing: Boolean = false,
    val lastCapturedUri: Uri? = null,
    val selectedGalleryUri: Uri? = null,
    val activeViewerUri: Uri? = null,
    val feedbackMessage: String? = null,
    val isSettingsOpen: Boolean = false
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    val cameraManager = CameraManager(application)
    val captureManager = CameraCaptureManager(application)
    val subjectSelectionManager = SubjectSelectionManager()
    val orientationManager = DeviceOrientationManager(application)
    val scenePerceptionManager = ScenePerceptionManager(application)

    val hardwareState: StateFlow<CameraHardwareState> = cameraManager.hardwareState
    val subjectState: StateFlow<SubjectSelectionState> = subjectSelectionManager.selectionState
    val orientationState: StateFlow<OrientationState> = orientationManager.orientation
    val perceptionState: StateFlow<PerceptionState> = scenePerceptionManager.perceptionState
    val diagnostics: StateFlow<DetectionDiagnostics> = scenePerceptionManager.diagnostics

    private val _settings = MutableStateFlow(GuidanceSettings())
    val settings: StateFlow<GuidanceSettings> = _settings.asStateFlow()

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                cameraManager.getCameraProvider()
            } catch (e: Exception) {
                _uiState.update { it.copy(feedbackMessage = "Erreur CameraX: ${e.message}") }
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = isGranted) }
    }

    fun startOrientationTracking() {
        orientationManager.startListening()
    }

    fun stopOrientationTracking() {
        orientationManager.stopListening()
    }

    fun bindCameraPreview(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        cameraManager.bindCamera(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            lensFacing = hardwareState.value.currentLens,
            onFrameAnalyzed = { bitmap ->
                scenePerceptionManager.onImageFrameAvailable(bitmap)
            }
        )
    }

    fun toggleCameraLens(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val nextLens = if (hardwareState.value.currentLens == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        cameraManager.bindCamera(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            lensFacing = nextLens,
            onFrameAnalyzed = { bitmap ->
                scenePerceptionManager.onImageFrameAvailable(bitmap)
            }
        )
    }

    fun cycleFlash() {
        val nextSetting = cameraManager.cycleFlashSetting()
        showFeedback("Flash : ${nextSetting.labelFr}")
    }

    fun setZoom(ratio: Float) {
        cameraManager.setZoomRatio(ratio)
    }

    fun handlePreviewTouch(x: Float, y: Float, viewWidth: Float, viewHeight: Float, previewView: PreviewView?) {
        val normX = (x / viewWidth).coerceIn(0f, 1f)
        val normY = (y / viewHeight).coerceIn(0f, 1f)

        // 1. Déclencher également l'autofocus et l'exposition matériel CameraX
        previewView?.let { view ->
            val factory = view.meteringPointFactory
            val point = factory.createPoint(x, y)
            cameraManager.focusAt(point)
        }

        // 2. Vérifier si le toucher correspond à un objet détecté par le moteur de vision
        val touchedObject = scenePerceptionManager.handleTouchSelection(normX, normY)
        if (touchedObject != null) {
            subjectSelectionManager.setNormalizedPoint(
                touchedObject.normalizedCenterX,
                touchedObject.normalizedCenterY,
                SubjectSelectionSource.MANUAL_TOUCH
            )
            showFeedback("Sujet fixé : ${touchedObject.label} (${touchedObject.confidencePercent}%)")
        } else {
            // Toucher libre hors des objets détectés
            subjectSelectionManager.selectTouchPoint(x, y, viewWidth, viewHeight)
            showFeedback(String.format("Point focal fixé (%.2f, %.2f)", normX, normY))
        }
    }

    fun clearSubjectSelection() {
        scenePerceptionManager.clearSelection()
        subjectSelectionManager.clearSelection()
        showFeedback("Sélection de sujet réinitialisée")
    }

    fun unlockSubject() {
        scenePerceptionManager.unlockSubject()
        subjectSelectionManager.clearSelection()
        showFeedback("Sujet déverrouillé · Suivi automatique rétabli")
    }

    fun selectSubject(obj: com.example.detection.DetectedObject) {
        scenePerceptionManager.lockSubjectDirectly(obj)
        subjectSelectionManager.setNormalizedPoint(
            obj.normalizedCenterX,
            obj.normalizedCenterY,
            SubjectSelectionSource.MANUAL_TOUCH
        )
        showFeedback("Sujet verrouillé : ${obj.label} (${obj.confidencePercent}%)")
    }

    fun toggleDetectionDisplayMode(): com.example.detection.DetectionDisplayMode {
        val newMode = scenePerceptionManager.toggleDisplayMode()
        _settings.update { it.copy(displayMode = newMode) }
        val modeLabel = if (newMode == com.example.detection.DetectionDisplayMode.ESSENTIAL) "Essentiel" else "Diagnostic"
        showFeedback("Affichage détection : $modeLabel")
        return newMode
    }

    fun setDetectionDisplayMode(mode: com.example.detection.DetectionDisplayMode) {
        scenePerceptionManager.setDisplayMode(mode)
        _settings.update { it.copy(displayMode = mode) }
    }

    fun toggleDiagnostics() {
        _settings.update { it.copy(showDiagnostics = !it.showDiagnostics) }
        val status = if (_settings.value.showDiagnostics) "activé" else "masqué"
        showFeedback("Diagnostic télémétrique $status")
    }

    fun updateConfidenceThresholds(high: Float, medium: Float, min: Float = 0.30f) {
        val newSettings = com.example.detection.ConfidenceFilterSettings(
            highThreshold = high.coerceIn(0.50f, 0.95f),
            mediumThreshold = medium.coerceIn(0.20f, high - 0.05f),
            minConfidence = min.coerceIn(0.10f, medium - 0.05f)
        )
        scenePerceptionManager.updateConfidenceSettings(newSettings)
        _settings.update { it.copy(confidenceSettings = newSettings) }
        showFeedback("Seuils confiance : Haut ${(newSettings.highThreshold * 100).toInt()}% • Moyen ${(newSettings.mediumThreshold * 100).toInt()}%")
    }

    fun resetConfidenceThresholds() {
        val defaultSettings = com.example.detection.ConfidenceFilterSettings(
            highThreshold = 0.70f,
            mediumThreshold = 0.45f,
            minConfidence = 0.30f
        )
        scenePerceptionManager.updateConfidenceSettings(defaultSettings)
        _settings.update { it.copy(confidenceSettings = defaultSettings) }
        showFeedback("Seuils de confiance réinitialisés par défaut (70% / 45%)")
    }

    fun setGuidanceMode(mode: GuidanceMode) {
        _settings.update { it.copy(activeMode = mode) }
        val isAuto = mode == GuidanceMode.AUTOMATIC
        scenePerceptionManager.updatePhotographyContext(
            category = if (isAuto) null else _settings.value.category,
            isAutomaticMode = isAuto,
            intention = _settings.value.intention
        )
    }

    fun setPhotographyCategory(category: PhotographyCategory) {
        _settings.update { it.copy(category = category) }
        val isAuto = _settings.value.activeMode == GuidanceMode.AUTOMATIC
        scenePerceptionManager.updatePhotographyContext(
            category = if (isAuto) null else category,
            isAutomaticMode = isAuto,
            intention = _settings.value.intention
        )
        showFeedback("Catégorie : ${category.labelFr}")
    }

    fun setCreativeIntention(intention: CreativeIntention) {
        _settings.update { it.copy(intention = intention) }
        val isAuto = _settings.value.activeMode == GuidanceMode.AUTOMATIC
        scenePerceptionManager.updatePhotographyContext(
            category = if (isAuto) null else _settings.value.category,
            isAutomaticMode = isAuto,
            intention = intention
        )
        showFeedback("Intention : ${intention.labelFr}")
    }

    fun setGuidanceType(type: GuidanceType) {
        _settings.update { it.copy(guidanceType = type) }
        showFeedback("Style : ${type.labelFr}")
    }

    fun toggleRuleOfThirds() {
        _settings.update { it.copy(showRuleOfThirds = !it.showRuleOfThirds) }
        val status = if (_settings.value.showRuleOfThirds) "activée" else "désactivée"
        showFeedback("Grille des tiers $status")
    }

    fun toggleHorizonLevel() {
        _settings.update { it.copy(showHorizonLevel = !it.showHorizonLevel) }
        val status = if (_settings.value.showHorizonLevel) "activé" else "désactivé"
        showFeedback("Niveau d'horizon $status")
    }

    fun capturePhoto() {
        val imageCapture = cameraManager.getImageCapture()
        if (imageCapture == null) {
            showFeedback("Appareil photo non prêt")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true) }
            when (val result = captureManager.capturePhoto(imageCapture)) {
                is CaptureResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            lastCapturedUri = result.savedUri,
                            activeViewerUri = result.savedUri,
                            feedbackMessage = "Photographie enregistrée dans la galerie !"
                        )
                    }
                }
                is CaptureResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            feedbackMessage = "Erreur de capture : ${result.exception.localizedMessage}"
                        )
                    }
                }
            }
        }
    }

    fun onGalleryImageSelected(uri: Uri?) {
        if (uri != null) {
            _uiState.update {
                it.copy(
                    selectedGalleryUri = uri,
                    activeViewerUri = uri,
                    feedbackMessage = "Photo importée depuis la galerie"
                )
            }
        }
    }

    fun openViewer(uri: Uri?) {
        _uiState.update { it.copy(activeViewerUri = uri) }
    }

    fun closeViewer() {
        _uiState.update { it.copy(activeViewerUri = null) }
    }

    fun setSettingsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    fun showFeedback(msg: String) {
        _uiState.update { it.copy(feedbackMessage = msg) }
    }

    fun dismissFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.release()
        scenePerceptionManager.close()
        orientationManager.stopListening()
    }
}
