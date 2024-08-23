package com.example.camerest.ui.screen

import android.content.Context
import android.util.Log
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.example.camerest.R
import com.example.camerest.ui.screen.viewModel.CameraScreenVM
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    modifier: Modifier,
    cameraScreenVM: CameraScreenVM,
    context: Context
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var preview: Preview? by remember { mutableStateOf(null) }
    var cameraSelector: CameraSelector? by remember { mutableStateOf(null) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    CameraPreview(modifier, cameraScreenVM) {
        previewView = it
    }
    if(previewView!=null)
    CameraUI(
        modifier = modifier.fillMaxSize(),
        cameraScreenVM = cameraScreenVM,
        context = context,
        lifecycleOwner = lifecycleOwner,
        previewView = previewView!!)
    if (previewView != null)
        cameraScreenVM.createCameraProvider(context, lifecycleOwner, previewView!!)
}

@Composable
private fun CameraPreview(
    modifier: Modifier,
    cameraScreenVM: CameraScreenVM,
    block: (PreviewView) -> Unit
) {
    AndroidView(factory = { context ->
        View.inflate(context, R.layout.camera, null)
    },
        modifier.fillMaxSize(),
        update = {
            cameraScreenVM.setPreviewExecutor(Executors.newSingleThreadExecutor())
            val previewView = it.findViewById<PreviewView?>(R.id.viewFinder).also { pv ->
                pv.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            block(previewView)
            cameraScreenVM.setPreview(androidx.camera.core.Preview.Builder().build().also { pv ->
                pv.setSurfaceProvider(previewView!!.surfaceProvider)
            }
            )
        })
}

@Composable
private fun CameraUI(
    modifier: Modifier,
    cameraScreenVM: CameraScreenVM,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView
) {
    Column(modifier.padding(bottom = 20.dp), verticalArrangement = Arrangement.Bottom) {
        LaunchedEffect(Unit) {
        }
        Row(Modifier.fillMaxWidth()) {
            Button(onClick = {
                cameraScreenVM.captureModeEnum = CaptureModeEnum.Picture
                cameraScreenVM.createCameraProvider(context, lifecycleOwner,previewView)
            }) {
                Text(text = "ImageMode")
            }
            Button(onClick = {
                cameraScreenVM.captureModeEnum = CaptureModeEnum.Analysis
                cameraScreenVM.createCameraProvider(context, lifecycleOwner, previewView)
            }) {
                Text(text = "Analysis")
            }
            Button(onClick = {
                cameraScreenVM.captureModeEnum = CaptureModeEnum.Video
                cameraScreenVM.createCameraProvider(context, lifecycleOwner, previewView)
            }) {
                Text(text = "VideoMode")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Button(onClick = {
                cameraScreenVM.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            }) {
                Text(text = "Back")
            }
            Button(onClick = {
                cameraScreenVM.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            }) {
                Text(text = "Front")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            var buttonName by remember{ mutableStateOf("") }
            val action = {
                when (cameraScreenVM.captureModeEnum) {
                    CaptureModeEnum.Picture -> {
                        cameraScreenVM.takePicture(context)
                        buttonName = "Take a picture"
                    }

                    CaptureModeEnum.Video -> {
                        cameraScreenVM.recordVideo(context)
                        buttonName = "Record a video"
                    }

                    CaptureModeEnum.Analysis -> {
                        cameraScreenVM.analysis()
                        buttonName = "Analysis"
                    }
                }
            }
            Button(onClick = { action() }) {
                Text(text = buttonName)
                Log.e("lofi", buttonName)
            }
        }
    }
}

@Composable
private fun CameraSwitcher() {

}

@Composable
private fun CaptureMode() {

}

enum class CaptureModeEnum {
    Picture, Video, Analysis
}