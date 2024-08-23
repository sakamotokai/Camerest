package com.example.camerest.ui.screen.viewModel

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.camera2.internal.annotation.CameraExecutor
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.example.camerest.ui.screen.CaptureModeEnum
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import javax.inject.Inject

@HiltViewModel
class CameraScreenVM @Inject constructor() : ViewModel() {

    private var recording:Recording? = null
    private var cameraExecutor: ExecutorService? = null
    var captureModeEnum: CaptureModeEnum = CaptureModeEnum.Picture
    var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var captureMode: UseCase? = null
    var captureModeFlow = MutableStateFlow(CaptureModeEnum.Picture)
        private set
    var cameraScreenState: MutableStateFlow<CameraScreenState> =
        MutableStateFlow(CameraScreenState())
        private set

    fun createCameraProvider(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        Log.e("lofi", "Inside provider")
        try {
            preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            cameraProvider = CameraProviderBuilder.Builder(
                context,
                captureModeEnum,
                lifecycleOwner,
                cameraSelector,
                preview!!
            ).build() {
                this.captureMode = it
            }
        } catch (e: Exception) {
            Log.e("lofi", "CameraScreen:createCameraProvider: ${e.message}")
        }
    }

    fun takePicture(context: Context) {
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

        val imageCapture = captureMode as ImageCapture

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

    fun recordVideo(context: Context) {
        val videoCapture = captureMode as VideoCapture<Recorder>
        manageRecordVideo(context,videoCapture, recording){
            recording = it
        }
    }

    fun analysis() {

    }

    fun setPreview(preview: Preview) {
        this.preview = preview
    }

    fun setPreviewExecutor(newSingleThreadExecutor: ExecutorService?) {
        this.cameraExecutor = newSingleThreadExecutor
    }
}

@SuppressLint("MissingPermission")
private fun manageRecordVideo(
    context: Context,
    videoCapture: VideoCapture<Recorder>,
    recording:Recording?,
    captureListener: (Recording?) -> Unit
) {
    if(recording!=null){
        recording.stop()
        captureListener(null)
        return
    }
    val name = "CameraX-recording-" +
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(System.currentTimeMillis()) + ".mp4"
    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, name)
    }
    val mediaStoreOutput = MediaStoreOutputOptions.Builder(context.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
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

data class CameraScreenState(val a: Int = 0)

private class CameraProviderBuilder private constructor(
    val context: Context,
    val captureModeEnum: CaptureModeEnum,
    val lifecycleOwner: LifecycleOwner,
    val cameraSelector: CameraSelector,
    val preview: Preview,
) {
    data class Builder(
        var context: Context,
        var captureModeEnum: CaptureModeEnum,
        var lifecycleOwner: LifecycleOwner,
        var cameraSelector: CameraSelector,
        var preview: Preview,
    ) {
        fun captureModeEnum(captureModeEnum: CaptureModeEnum) =
            apply { this.captureModeEnum = captureModeEnum }

        fun context(context: Context) = apply { this.context = context }

        @OptIn(ExperimentalCamera2Interop::class)
        fun build(block: (UseCase) -> Unit): ProcessCameraProvider {
            val cameraProviderFeature = ProcessCameraProvider.getInstance(context)
            val cameraProvider: ProcessCameraProvider = cameraProviderFeature.get()
            val captureMode = when (captureModeEnum) {
                CaptureModeEnum.Picture -> {
                    ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()
                }

                CaptureModeEnum.Video -> {
                    val cameraInfo = cameraProvider.availableCameraInfos.filter {
                        Camera2CameraInfo
                            .from(it)
                            .getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
                    }
                    val supportedQualities = QualitySelector.getSupportedQualities(cameraInfo[0])
                    val filteredQualities =
                        arrayListOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
                            .filter { supportedQualities.contains(it) }

                    val qualitySelector = QualitySelector.from(filteredQualities[1])
                    val recorder = Recorder.Builder()
                        .setQualitySelector(qualitySelector).build()
                    VideoCapture.withOutput(recorder)

                }

                CaptureModeEnum.Analysis -> {
                    ImageAnalysis.Builder()
                        .build()
                }
            }

            block(captureMode)
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                captureMode
            )

            return cameraProvider
        }
    }
}
