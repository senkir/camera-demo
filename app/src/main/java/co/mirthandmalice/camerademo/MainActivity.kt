package co.mirthandmalice.camerademo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.mirthandmalice.camerademo.ui.theme.CameraDemoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val CAMERA_REQUEST_CODE = 203
    }


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
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            )
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted = isGranted
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    if (granted) content()
    else PermissionRationale(
        onRequest = {
            launcher.launch(Manifest.permission.CAMERA)
        }
    )
}

@Composable
fun PermissionRationale(onRequest: () -> Unit) {
    //FIXME: put a rationale here
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    vm: CameraViewModel = viewModel(),
) {

    val errorEvent = vm.errorEvent.collectAsState()

    Column(modifier = modifier) {
        Text("Camera Preview")

        if (errorEvent.value != null) {
            Text("Failed to bind camera. cause={$errorEvent}")
        }

        CameraPermissionGate {

            val owner = LocalLifecycleOwner.current
            val current = LocalContext.current
            LaunchedEffect(owner) {
                vm.bindToCamera(current, owner)
            }

            Box(
                modifier = modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxSize(1.0f)
            ) {

                vm.surfaceRequest.collectAsStateWithLifecycle().value?.let {
                    CameraXViewfinder(surfaceRequest = it)
                }

                CameraWindowChrome(
                    onTakePicture = {
                        vm.takePicture(current)
                    },
                    onFlipCamera = {
                        vm.onFlipCamera()
                    }
                )

            }

        }
    }

}

@Composable
fun BoxScope.CameraWindowChrome(
    onTakePicture: () -> Unit,
    onFlipCamera: () -> Unit,
) {

    TakePictureButton(
        onTakePicture, Modifier
            .align(Alignment.BottomCenter)
            .padding(20.dp)
    )

    IconButton(
        onFlipCamera,
        modifier = Modifier
            .padding(8.dp)
        .align(Alignment.TopStart)
    ) {
        Icon(
            painterResource(R.drawable.ic_photo_flip),
            contentDescription = "Flip Camera",
        )
    }
}

@Composable
fun CameraUI() {

}

@Composable
fun TakePictureButton(onClick: () -> Unit, modifier: Modifier = Modifier) {

    Button(onClick, modifier) {
        Text("Take Picture")
    }
}