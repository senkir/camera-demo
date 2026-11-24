package co.mirthandmalice.camerademo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import co.mirthandmalice.camerademo.ui.theme.CameraDemoTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    CameraPreview(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    vm: CameraViewModel = viewModel(),
    modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    val current = LocalContext.current
    LaunchedEffect(owner) {
        vm.bindToCamera(current, owner)
    }
}

// VIEWMODEL
class CameraViewModel: ViewModel() {
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest = _surfaceRequest.asStateFlow()

    private val preview = androidx.camera.core.Preview.Builder()
        .build().apply {
            setSurfaceProvider { _surfaceRequest.value = it }
        }

    suspend fun bindToCamera(context: Context, owner: LifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA)
        try {
            awaitCancellation()
        } finally {
            provider.unbindAll()
        }
    }

}