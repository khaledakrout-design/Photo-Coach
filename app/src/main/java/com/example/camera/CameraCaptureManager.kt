package com.example.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface CaptureResult {
    data class Success(val savedUri: Uri) : CaptureResult
    data class Error(val exception: Throwable) : CaptureResult
}

/**
 * Gère la capture d'images avec CameraX et l'enregistrement dans le MediaStore Android.
 */
class CameraCaptureManager(private val context: Context) {

    private val dateFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    suspend fun capturePhoto(imageCapture: ImageCapture): CaptureResult = suspendCancellableCoroutine<CaptureResult> { continuation ->
        val filename = "PHOTO_COACH_${dateFormatter.format(System.currentTimeMillis())}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoCoach")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        val mainExecutor = ContextCompat.getMainExecutor(context)

        imageCapture.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val uri = outputFileResults.savedUri ?: Uri.EMPTY
                    if (continuation.isActive) {
                        continuation.resume(CaptureResult.Success(uri))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) {
                        continuation.resume(CaptureResult.Error(exception))
                    }
                }
            }
        )
    }
}
