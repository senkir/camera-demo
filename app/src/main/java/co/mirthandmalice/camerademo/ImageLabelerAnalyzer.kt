package co.mirthandmalice.camerademo

import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ImageLabelerAnalyzer @Inject constructor(
    private val coroutineScope: CoroutineScope,
) : ImageAnalysis.Analyzer {

    companion object {
        const val TAG = "ImageLabelerAnalyzer"
        const val ANALYZER_DELAY_M = 1L
    }

    private val _imageStarting: MutableSharedFlow<Long> = MutableSharedFlow(replay = 0)

    val imageStarting = _imageStarting.asSharedFlow()

    private val _imageEventsFlow: MutableSharedFlow<List<ProcessedLabels>> =
        MutableSharedFlow(replay = 0)

    val imageEvents = _imageEventsFlow.asSharedFlow()

    private val _errorStates: MutableSharedFlow<Throwable> = MutableSharedFlow(replay = 0)

    val errors = _errorStates.asSharedFlow()

    data class ProcessedLabels(
        val text: String,
        val priority: Priority
    )

    enum class Priority {
        High,
        Medium,
        Low
    }

    private val client = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    private fun processLabels(labels: List<ImageLabel>) {

        val out = labels.sortedByDescending { it.confidence }.map { l ->

            val pri = if (l.confidence > 0.75f) {
                Priority.High
            } else if (l.confidence > 0.5f) {
                Priority.Medium
            } else {
                Priority.Low
            }

            ProcessedLabels(
                text = l.text,
                priority = pri
            )
        }
        //succeeded
        coroutineScope.launch {
            _imageEventsFlow.emit(out)
        }

    }

    @kotlin.OptIn(ExperimentalTime::class)
    private fun notifyImageStarting() {
        coroutineScope.launch {
            //just trigger a new event happening
            _imageStarting.emit(Clock.System.now().epochSeconds)
        }
        lastEvent = Clock.System.now().epochSeconds
    }

    private var lastEvent = 0L

    @kotlin.OptIn(ExperimentalTime::class)
    private fun skipIfThrottled(): Boolean {
        val now = Clock.System.now().epochSeconds
        return now - lastEvent < ANALYZER_DELAY_M
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {

        val media = imageProxy.image
        if (media != null) {
            val inputImage = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)

            if (skipIfThrottled()) {
                imageProxy.close()
                return
            }
            notifyImageStarting()

            //pass image to MLkit vision api
            client.process(inputImage)
                .addOnSuccessListener { labels ->
                    Log.d(TAG, "analyze: found ${labels.size} labels")
                    //succeeded
                    processLabels(labels)
                }

                .addOnFailureListener { e ->
                    //failed with exception
                    coroutineScope.launch {
                        _errorStates.emit(e)
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }


        } else {
            imageProxy.close()
        }
    }

    override fun getDefaultTargetResolution(): Size? {
        return super.getDefaultTargetResolution()
    }

    override fun getTargetCoordinateSystem(): Int {
        return super.getTargetCoordinateSystem()
    }

    override fun updateTransform(matrix: Matrix?) {
        super.updateTransform(matrix)
    }

}