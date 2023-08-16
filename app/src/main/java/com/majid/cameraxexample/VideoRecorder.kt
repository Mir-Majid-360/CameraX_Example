    package com.majid.cameraxexample

    import android.Manifest
    import android.annotation.SuppressLint
    import android.content.Context
    import android.content.pm.PackageManager
    import android.util.Log
    import androidx.camera.core.CameraSelector
    import androidx.camera.core.Preview
    import androidx.camera.core.VideoCapture
    import androidx.camera.lifecycle.ProcessCameraProvider
    import androidx.camera.view.PreviewView
    import androidx.core.app.ActivityCompat
    import androidx.core.content.ContextCompat
    import androidx.lifecycle.LifecycleOwner
    import java.io.File
    import java.text.SimpleDateFormat
    import java.util.Locale
    import java.util.concurrent.ExecutorService
    import java.util.concurrent.Executors

    class VideoRecorder(
        private val lifecycleOwner: LifecycleOwner,
        private val previewView: PreviewView,
        private val context: Context
    ) {
        private var cameraProvider: ProcessCameraProvider? = null
        private var videoCapture: VideoCapture? = null
        private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

        init {
            startCamera()
        }

        private fun startCamera() {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
            cameraProviderFuture.addListener({
                cameraProvider = cameraProviderFuture.get()
                bindPreview()
            }, ContextCompat.getMainExecutor(previewView.context))
        }

        private fun bindPreview() {
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }

        @SuppressLint("RestrictedApi")
        fun startRecording(outputDirectory: File) {
            val videoFile = File(
                outputDirectory,
                "video_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())}.mp4"
            )

            val outputOptions = VideoCapture.OutputFileOptions.Builder(videoFile).build()

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            videoCapture?.startRecording(outputOptions, cameraExecutor, @SuppressLint("RestrictedApi")
            object : VideoCapture.OnVideoSavedCallback {
                override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                    Log.d(TAG, "Video file: ${videoFile.absolutePath}")
                }

                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    Log.e(TAG, "Video capture error: $message", cause)
                }
            })
        }

        @SuppressLint("RestrictedApi")
        fun stopRecording() {
            videoCapture?.stopRecording()
            videoCapture = null
        }

        fun onDestroy() {
            cameraExecutor.shutdown()
        }

        companion object {
            private const val TAG = "VideoRecorder"
        }
    }

