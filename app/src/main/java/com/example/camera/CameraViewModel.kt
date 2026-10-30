package com.example.camera

import android.app.Application
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.guidance.CreativeIntention
import com.example.guidance.GuidanceMode
import com.example.guidance.GuidanceSettings
import com.example.guidance.GuidanceType
import com.example.guidance.PhotographyCategory
import com.example.sensors.DeviceOrientationManager
import com.example.sensors.OrientationState
import com.example.subject.SubjectPoint
import com.example.subject.SubjectSelectionManager
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

    val hardwareState: StateFlow<CameraHardwareState> = cameraManager.hardwareState
    val subjectState: StateFlow<SubjectSelectionState> = subjectSelectionManager.selectionState
    val orientationState: StateFlow<OrientationState> = orientationManager.orientation

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
        cameraManager.bindCamera(lifecycleOwner, previewView, hardwareState.value.currentLens)
    }

    fun toggleCameraLens(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val nextLens = if (hardwareState.value.currentLens == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        cameraManager.bindCamera(lifecycleOwner, previewView, nextLens)
    }

    fun cycleFlash() {
        val nextSetting = cameraManager.cycleFlashSetting()
        showFeedback("Flash : ${nextSetting.labelFr}")
    }

    fun setZoom(ratio: Float) {
        cameraManager.setZoomRatio(ratio)
    }

    fun handlePreviewTouch(x: Float, y: Float, viewWidth: Float, viewHeight: Float, previewView: PreviewView?) {
        // Enregistrer la sélection manuelle du sujet
        subjectSelectionManager.selectTouchPoint(x, y, viewWidth, viewHeight)

        // Déclencher également l'autofocus matériel CameraX
        previewView?.let { view ->
            val factory = view.meteringPointFactory
            val point = factory.createPoint(x, y)
            cameraManager.focusAt(point)
        }

        val normX = (x / viewWidth).coerceIn(0f, 1f)
        val normY = (y / viewHeight).coerceIn(0f, 1f)
        showFeedback(String.format("Point focal fixé (%.2f, %.2f)", normX, normY))
    }

    fun clearSubjectSelection() {
        subjectSelectionManager.clearSelection()
        showFeedback("Sélection de sujet effacée")
    }

    fun setGuidanceMode(mode: GuidanceMode) {
        _settings.update { it.copy(activeMode = mode) }
    }

    fun setPhotographyCategory(category: PhotographyCategory) {
        _settings.update { it.copy(category = category) }
        showFeedback("Catégorie : ${category.labelFr}")
    }

    fun setCreativeIntention(intention: CreativeIntention) {
        _settings.update { it.copy(intention = intention) }
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
        orientationManager.stopListening()
    }
}
