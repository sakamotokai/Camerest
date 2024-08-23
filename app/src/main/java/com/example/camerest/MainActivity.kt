package com.example.camerest

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.camera.camera2.internal.annotation.CameraExecutor
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoOutput
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat
import androidx.core.content.ContentResolverCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.util.Consumer
import com.example.camerest.ui.screen.CameraScreen
import com.example.camerest.ui.screen.viewModel.CameraScreenVM
import com.example.camerest.ui.theme.CamerestTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel:CameraScreenVM by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.camerest.checkPermission(applicationContext, this)
        enableEdgeToEdge()
        setContent {
            CamerestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    //Camera(Modifier.padding(innerPadding), applicationContext)
                    CameraScreen(modifier = Modifier.padding(innerPadding), cameraScreenVM = viewModel, context = applicationContext)
                }
            }
        }
    }
}

private class Analyzer(private val block: (Double) -> Unit) : ImageAnalysis.Analyzer {

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        val pixels = data.map { it.toInt() and 0xFF }
        val luma = pixels.average()

        block(luma)
        Log.e("lofi", "${image.height * image.width}")

        image.close()
    }

}

@Composable
fun Camera(modifier: Modifier, context: Context) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraExecutor: ExecutorService? by remember { mutableStateOf(null) }
    val cameraProviderFeature = ProcessCameraProvider.getInstance(context)
    val cameraProvider: ProcessCameraProvider = cameraProviderFeature.get()
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()
    val imageAnalyzer = ImageAnalysis.Builder()
        .build()

    var isRecord by remember { mutableStateOf(false) }

    var videoCapture: VideoCapture<Recorder>? by remember{ mutableStateOf(null)}
    createRecorder(context, cameraProvider) {
        videoCapture = VideoCapture.withOutput(it)
    }

    var recording: Recording? by remember { mutableStateOf(null) }
    if (videoCapture != null)
        recordVideo(context, videoCapture!!, { recording = it })


    if (previewView != null && videoCapture != null) {
        try {
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView!!.surfaceProvider)
            }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalyzer,
                videoCapture
            )
        } catch (e: Exception) {
        }
    }

    AndroidView(factory = { context ->
        View.inflate(context, R.layout.camera, null)
    },
        modifier.fillMaxSize(),
        update = {
            cameraExecutor = Executors.newSingleThreadExecutor()
            previewView = it.findViewById<PreviewView?>(R.id.viewFinder).also { pv ->
                /**PreviewView*/
                pv.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        })
    Column(verticalArrangement = Arrangement.Bottom, modifier = modifier.fillMaxSize()) {
        Row {
            Button(onClick = { takePicture(context, imageCapture) }) {
                Text(text = "Take a picture")
            }
            Button(onClick = {
                if (isRecord) {
                    recording!!.stop()
                    isRecord = false
                } else {
                    recordVideo(context, videoCapture!!, { recording = it })
                    isRecord = true
                }
            }) {
                Text(text = "Record a video")
            }
            Button(onClick = { imageAnalyzer.clearAnalyzer() }) {
                Text(text = "Clear analyze")
            }
            Button(onClick = {
                imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(context), Analyzer {

                })
            }) {
                Text(text = "Set analyze")
            }
        }

    }
}

@Composable
private fun pictureMode(modifier:Modifier,context: Context){
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraExecutor: ExecutorService? by remember { mutableStateOf(null) }
    val cameraProviderFeature = ProcessCameraProvider.getInstance(context)
    val cameraProvider: ProcessCameraProvider = cameraProviderFeature.get()
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()

    if (previewView != null) {
        try {
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView!!.surfaceProvider)
            }
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e("lofi", "pictureMode* ${e.message}")
        }
    }

    AndroidView(factory = { context ->
        View.inflate(context, R.layout.camera, null)
    },
        modifier.fillMaxSize(),
        update = {
            cameraExecutor = Executors.newSingleThreadExecutor()
            previewView = it.findViewById<PreviewView?>(R.id.viewFinder).also { pv ->
                pv.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        })
    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraProvider.unbindAll()
        }
    }
}

@SuppressLint("MissingPermission")
private fun recordVideo(
    context: Context,
    videoCapture: VideoCapture<Recorder>,
    captureListener: (Recording) -> Unit
) {
    var recordStatus = false
    val name = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
        }
    }
    val mediaStoreOutput = MediaStoreOutputOptions.Builder(
        context.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    )
        .setContentValues(contentValues)
        .build()

// 2. Configure Recorder and Start recording to the mediaStoreOutput.
    val pendingRecording = videoCapture.output
        .prepareRecording(context, mediaStoreOutput)
        .withAudioEnabled()
        .start(ContextCompat.getMainExecutor(context), Consumer { })
    captureListener(pendingRecording)

}

@OptIn(ExperimentalCamera2Interop::class)
private fun createRecorder(
    context: Context,
    cameraProvider: ProcessCameraProvider,
    record: (Recorder) -> Unit
) {
    val cameraInfo = cameraProvider.availableCameraInfos.filter {
        Camera2CameraInfo
            .from(it)
            .getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
    }

    val supportedQualities = QualitySelector.getSupportedQualities(cameraInfo[0])
    val filteredQualities = arrayListOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
        .filter { supportedQualities.contains(it) }

    val qualitySelector = QualitySelector.from(filteredQualities[1])

    // Create a new Recorder/VideoCapture for the new quality
    // and bind to lifecycle
    val recorder = Recorder.Builder()
        .setQualitySelector(qualitySelector).build()
    record(recorder)
    // ...
}


// A helper function to translate Quality to a string
fun Quality.qualityToString(): String {
    return when (this) {
        Quality.UHD -> "UHD"
        Quality.FHD -> "FHD"
        Quality.HD -> "HD"
        Quality.SD -> "SD"
        else -> throw IllegalArgumentException()
    }
}


private fun takePicture(context: Context, imageCapture: ImageCapture) {

    val name = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions
        .Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        .build()

    imageCapture.takePicture(outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("lofi", "Photo capture failed: ${exc.message}", exc)
            }

            override fun
                    onImageSaved(output: ImageCapture.OutputFileResults) {
                val msg = "Photo capture succeeded: ${output.savedUri}"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                Log.d("lofi", msg)
            }
        }
    )

}


/**
 * TODO("Split checkPermission and requestPermission")
 * */
private fun checkPermission(context: Context, activity: MainActivity) {
    val permissionList = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }.toTypedArray()

    val permissionsGranted = permissionList.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    if (permissionsGranted) return
    ActivityCompat.requestPermissions(activity, permissionList, 1)

}