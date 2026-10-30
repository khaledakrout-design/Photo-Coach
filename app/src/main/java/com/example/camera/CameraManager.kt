package com.example.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

enum class FlashSetting(val labelFr: String) {
    OFF("Désactivé"),
    AUTO("Auto"),
    ON("Activé")
}

data class CameraHardwareState(
    val isInitialized: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val currentLens: Int = CameraSelector.LENS_FACING_BACK,
    val flashSetting: FlashSetting = FlashSetting.AUTO,
    val currentZoomRatio: Float = 1.0f,
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 5.0f,
    val isFlashSupported: Boolean = false
)

/**
 * Gère le cycle de vie CameraX, la prévisualisation et les contrôles matériels
 * (zoom, flash, mise au point tactile).
 */
class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var analysisExecutor: ExecutorService? = null
    private val isAnalyzing = AtomicBoolean(false)
    private var lastAnalysisTimestampMs = 0L
    private var adaptiveThrottleIntervalMs = 250L

    private val _hardwareState = MutableStateFlow(CameraHardwareState())
    val hardwareState: StateFlow<CameraHardwareState> = _hardwareState.asStateFlow()

    suspend fun getCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                cont.resume(provider)
            } catch (e: Exception) {
                cont.resumeWith(Result.failure(e))
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Lie la prévisualisation et la capture à la vue PreviewView et au LifecycleOwner.
     */
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        onFrameAnalyzed: ((Bitmap) -> Unit)? = null
    ) {
        _hardwareState.update { it.copy(isLoading = true, errorMessage = null) }

        val provider = cameraProvider
        if (provider == null) {
            _hardwareState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Fournisseur de caméra non prêt."
                )
            }
            return
        }

        try {
            provider.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            val previewUseCase = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            preview = previewUseCase

            val flashMode = when (_hardwareState.value.flashSetting) {
                FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
                FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
                FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
            }

            val captureUseCase = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                .build()
            imageCapture = captureUseCase

            // Configuration sécurisée de l'analyse d'image locale (CameraX ImageAnalysis)
            val analysisUseCase = if (onFrameAnalyzed != null) {
                analysisExecutor?.shutdown()
                val executor = Executors.newSingleThreadExecutor()
                analysisExecutor = executor

                ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build().also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            val now = SystemClock.elapsedRealtime()
                            // Throttling adaptatif pour préserver le CPU et la batterie
                            if (now - lastAnalysisTimestampMs < adaptiveThrottleIntervalMs ||
                                !isAnalyzing.compareAndSet(false, true)
                            ) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            lastAnalysisTimestampMs = now
                            val startAnalysis = SystemClock.elapsedRealtime()
                            try {
                                val rawBitmap = imageProxy.toBitmap()
                                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                val orientedBitmap = if (rotationDegrees != 0) {
                                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                    Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                                } else {
                                    rawBitmap
                                }
                                onFrameAnalyzed(orientedBitmap)

                                val duration = SystemClock.elapsedRealtime() - startAnalysis
                                // Régulation adaptative : espacer les analyses selon la latence mesurée (2-5 FPS cible)
                                adaptiveThrottleIntervalMs = (duration * 2L).coerceIn(200L, 500L)
                            } catch (e: Exception) {
                                Log.e("CameraManager", "Erreur lors de l'extraction de l'image d'analyse: ${e.message}")
                            } finally {
                                imageProxy.close()
                                isAnalyzing.set(false)
                            }
                        }
                    }
            } else {
                null
            }
            imageAnalysis = analysisUseCase

            val boundCamera = if (analysisUseCase != null) {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    captureUseCase,
                    analysisUseCase
                )
            } else {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    captureUseCase
                )
            }
            camera = boundCamera

            // Observer l'état du zoom
            boundCamera.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
                if (zoomState != null) {
                    _hardwareState.update {
                        it.copy(
                            currentZoomRatio = zoomState.zoomRatio,
                            minZoomRatio = zoomState.minZoomRatio,
                            maxZoomRatio = zoomState.maxZoomRatio.coerceAtMost(8.0f)
                        )
                    }
                }
            }

            val hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit()

            _hardwareState.update {
                it.copy(
                    isInitialized = true,
                    isLoading = false,
                    currentLens = lensFacing,
                    isFlashSupported = hasFlashUnit,
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            _hardwareState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Erreur lors de l'initialisation de la caméra : ${e.localizedMessage}"
                )
            }
        }
    }

    fun getImageCapture(): ImageCapture? = imageCapture

    fun setFlashSetting(newSetting: FlashSetting) {
        val flashMode = when (newSetting) {
            FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
        }
        imageCapture?.flashMode = flashMode
        _hardwareState.update { it.copy(flashSetting = newSetting) }
    }

    fun cycleFlashSetting(): FlashSetting {
        val next = when (_hardwareState.value.flashSetting) {
            FlashSetting.OFF -> FlashSetting.AUTO
            FlashSetting.AUTO -> FlashSetting.ON
            FlashSetting.ON -> FlashSetting.OFF
        }
        setFlashSetting(next)
        return next
    }

    fun setZoomRatio(ratio: Float) {
        val clamped = ratio.coerceIn(
            _hardwareState.value.minZoomRatio,
            _hardwareState.value.maxZoomRatio
        )
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    fun setLinearZoom(linear: Float) {
        camera?.cameraControl?.setLinearZoom(linear.coerceIn(0f, 1f))
    }

    /**
     * Effectue la mise au point et le calcul d'exposition au point tactile spécifié.
     */
    fun focusAt(point: MeteringPoint) {
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun release() {
        try {
            cameraProvider?.unbindAll()
            analysisExecutor?.shutdown()
        } catch (e: Exception) {
            Log.w("CameraManager", "Erreur lors de la libération des ressources: ${e.message}")
        }
    }
}
