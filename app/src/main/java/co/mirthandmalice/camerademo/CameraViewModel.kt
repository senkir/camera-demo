package co.mirthandmalice.camerademo

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.imageAnalysis
import androidx.camera.core.imageCapture
import androidx.camera.core.preview
import androidx.camera.core.takePicture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.mirthandmalice.camerademo.ImageLabelerAnalyzer.ProcessedLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val analyzer: ImageLabelerAnalyzer
) : ViewModel() {

    companion object {
        const val TAG = "CameraViewModel"
    }

    data class CameraUI(
        val hasOneCamera: Boolean = false,
        val hasTorch: Boolean = false,
        val cameraReady: Boolean = false,
        val focusEnabled: Boolean = false,
        val flashEnabled: Boolean = false,
        val changeIndex: Long = 0L,
        val labels: List<LabelData> = emptyList(),
        val newestLabel: String = "",
        val newImageProcessing: Long = 0L,
    )

    data class LabelData(
        val text: String,
        val priorityInt: Int,
    )

    private val analysis: Executor = Executors.newSingleThreadExecutor()

    private var processedLabels = listOf<LabelData>()

    private var camera: Camera? = null

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing = _lensFacing.asStateFlow()

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
        imageQueueDepth = 3
    }

    init {
        previewUseCase.apply {
            setSurfaceProvider {
                _surfaceRequest.value = it
            }
        }
        imageAnalysisUseCase.setAnalyzer(
            analysis,
            analyzer
        )

        viewModelScope.launch {
            analyzer.errors.collect { e ->
                //errors in analyzer
                Log.e(TAG, "takePicture: error in analyzer ${e.message}", e)
            }
        }
        viewModelScope.launch {
            analyzer.imageEvents.distinctUntilChanged().collect { events ->
                updateLabels(events)
            }
        }
        viewModelScope.launch {
            analyzer.imageStarting.collect { newTime ->
                onNewImage(newTime)
            }
        }
    }

    fun onNewImage(newTime: Long) {
//        clearRecords()
        _ui.update {
            it.copy(newImageProcessing = newTime)
        }
    }


    suspend fun bindToCamera(context: Context, owner: LifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)

        if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            _ui.update { it.copy(hasOneCamera = true) }
        }

            try {
        lensFacing.collectLatest { facing ->
                val selector = CameraSelector.Builder()
                    .requireLensFacing(facing)
                    .build()
                if (!provider.hasCamera(selector)) {
                    return@collectLatest
                }
                stopProcessing()
                provider.unbindAll()
                try {
                    camera = provider.bindToLifecycle(
                        owner,
                        selector,
                        previewUseCase, imageCaptureUseCase, imageAnalysisUseCase
                    )
                    startProcessing()
                } catch (e: IllegalStateException) {
                    _error.value = CameraError.BindFailure
                } catch (e: IllegalArgumentException) {
                    _error.value = CameraError.BindFailure
                }
        }
            } finally {
//                stopProcessing()
//                provider.unbindAll()
            }
        _ui.update { ui ->
            ui.copy(
                cameraReady = true,
                flashEnabled = false
            )
        }

        try {
            awaitCancellation()
        } finally {
            camera = null
            provider.unbindAll()
        }
    }

    fun onFlipCamera() {
        //attempt to swap
        _lensFacing.update {
            if (it == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
        }
        //rebind

    }

    fun onToggleTorch() {
        val torchEnabled = (camera?.cameraInfo?.torchState?.value ?: 0) > 0
        camera?.cameraControl?.enableTorch(!torchEnabled)
        _ui.update { ui ->
            ui.copy(flashEnabled = !torchEnabled)
        }
    }

    fun takePicture(context: Context) {
        startProcessing()
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
                Log.e(TAG, "takePicture: failed with exception ${e.message}", e)
            }
        }
    }

    private fun startProcessing() {
        imageAnalysisUseCase.setAnalyzer(analysis, analyzer)
    }

    private fun stopProcessing() {
        imageAnalysisUseCase.clearAnalyzer()
    }

    private fun updateLabels(produced: List<ProcessedLabels>) {
        Log.d(TAG, "updateLabels: $produced")
        val out = produced.map {
            LabelData(
                text = it.text,
                priorityInt = it.priority.ordinal
            )
        }
        processedLabels = out
        _ui.update { ui ->
            ui.copy(
                labels = processedLabels,
                changeIndex = ui.changeIndex + 1,
            )
        }
    }

    fun clearRecords() {
        if (processedLabels.isEmpty()) {
            return;
        }
        processedLabels = emptyList()
        _ui.update { ui ->
            ui.copy(
                labels = processedLabels,
                changeIndex = ui.changeIndex + 1
            )
        }
    }

}


// VIEWMODEL

enum class CameraError {
    Unknown,
    BindFailure
}

