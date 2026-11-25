package co.mirthandmalice.camerademo

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.imageAnalysis
import androidx.camera.core.imageCapture
import androidx.camera.core.preview
import androidx.camera.core.takePicture
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class CameraViewModel : ViewModel() {

    companion object {
        const val TAG = "CameraViewModel"
    }

    data class CameraUI(
        val cameraReady: Boolean = false,
        val focusEnabled: Boolean = false,
        val flashEnabled: Boolean = false
    )

    private val _ui = MutableStateFlow<CameraUI>(CameraUI())
    val uiState = _ui.asStateFlow()

    private val _error = MutableStateFlow<CameraError?>(null)
    val errorEvent = _error.asStateFlow()
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest = _surfaceRequest.asStateFlow()

    val previewUseCase = preview {
        targetName = "preview_dsl"
    }

    val imageCaptureUseCase = imageCapture {
        targetName = "capture_dsl"
        captureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        flashMode = ImageCapture.FLASH_MODE_OFF
        jpegQuality = 50
    }

    val imageAnalysisUseCase = imageAnalysis {
        targetName = "analysis_dsl"
        backpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        imageQueueDepth = 5
    }

    init {
        previewUseCase.apply {
            setSurfaceProvider {
                _surfaceRequest.value = it
            }
        }
    }


    suspend fun bindToCamera(context: Context, owner: LifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)

        try {
            val camera = provider.bindToLifecycle(
                owner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                previewUseCase, imageCaptureUseCase, imageAnalysisUseCase
            )
            val cap = ImageCapture.getImageCaptureCapabilities(camera.cameraInfo)
        } catch (e: Exception) {
            if (e is IllegalStateException) {
                _error.value = CameraError.BindFailure
            } else {
                _error.value = CameraError.Unknown
            }
            if (e is CancellationException) {
                throw e
            }
        }

        try {
            awaitCancellation()
        } finally {
            provider.unbindAll()
        }
    }

    fun onFlipCamera() {
        //TODO: flip camera
    }

    fun onToggleFlash() {
        //TODO: toggle flash
    }

    fun takePicture(context: Context) {
        viewModelScope.launch {
            try {
                val name = "tbd";
                val contentValues = ContentValues()
                    .apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraDemo")
                        }
                    }
                val options = ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                    .build()
                val result = imageCaptureUseCase.takePicture(
                    outputFileOptions = options,
                    onCaptureStarted = {
                        Log.d(TAG, "takePicture: ")
                    },
                    onCaptureProcessProgressed = { prog ->
                        Log.d(TAG, "image progressed $prog")
                    },
                    onPostviewBitmapAvailable = { bitmap ->
                        Log.d(TAG, "bitmap available!")
                    }
                )
                Log.d(TAG, "takePicture: saved to ${result.savedUri}")
            } catch (e: ImageCaptureException) {
                Log.e(TAG, "takePicture: failed with exception ${e.message}",  e)
            }
        }
    }


}

// VIEWMODEL

enum class CameraError {
    Unknown,
    BindFailure
}

