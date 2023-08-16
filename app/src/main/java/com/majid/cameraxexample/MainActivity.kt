package com.majid.cameraxexample


import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.majid.cameraxexample.databinding.ActivityMainBinding
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var countDownTimer: CountDownTimer? = null
    private var recordingTimer: CountDownTimer? = null
    private var remainingRecordingTimeMillis = 0L

    private lateinit var cameraExecutor: ExecutorService
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
        countDownTimer = createCountDownTimer(10 * 1000) // 30 seconds
        recordingTimer = createRecordingTimer(Long.MAX_VALUE)


        // Set up the listeners for take photo and video capture buttons
//        viewBinding.ibCamera.setOnClickListener { takePhoto() }


        viewBinding.ibCamera.setOnClickListener {
            captureVideo()
            countDownTimer?.start()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        viewBinding.ibRotate.setOnClickListener {
            rotateCamera()
        }

        viewBinding.ibPause.setOnClickListener {
            pauseRecording()
        }

        viewBinding.ibPlay.setOnClickListener {
            resumeRecording()
        }
        viewBinding.ibRetake.setOnClickListener {
            recording?.close()

        }
    }

    private fun pauseRecording() {
        recording?.pause()

    }

    private fun resumeRecording() {
        recording?.resume()
    }

    private fun createRecordingTimer(duration: Long): CountDownTimer {
        remainingRecordingTimeMillis = duration
        return object : CountDownTimer(remainingRecordingTimeMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsElapsed = (Long.MAX_VALUE - millisUntilFinished) / 1000
                val minutes = secondsElapsed / 60
                val seconds = secondsElapsed % 60
                val formattedTime = String.format("%02d:%02d", minutes, seconds)
                viewBinding.durationTextView.text = formattedTime
                viewBinding.durationTextView.visibility = View.VISIBLE

            }


            override fun onFinish() {
                // Timer finished
                viewBinding.durationTextView.visibility = View.GONE
            }
        }
    }

    private fun createCountDownTimer(duration: Long): CountDownTimer {
        return object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Update UI with remaining time
                val secondsRemaining = millisUntilFinished / 1000
                // Update the countdown text view with secondsRemaining
                viewBinding.countdownTextView.visibility = View.VISIBLE
                viewBinding.countdownTextView.text = secondsRemaining.toString()
                // For majid: countdownTextView.text = secondsRemaining.toString()
            }
            //   videoTimer?.start()

            override fun onFinish() {
                // Stop video recording when countdown finishes
                //    stopRecording(null) // Pass a dummy View
                viewBinding.countdownTextView.visibility = View.GONE
            }
        }
    }


    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()

            recording = null
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) ==
                    PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
//
                        Log.w("VideoRecording", "Video recording Started")
                        countDownTimer?.start()
                        viewBinding.durationTextView.visibility = View.VISIBLE
                        recordingTimer?.start()
//                        recordingTimer?.start()
                        viewBinding.ibRotate.visibility = View.GONE
                        viewBinding.ibCamera.setImageDrawable(getDrawable(R.drawable.baseline_pause_24))
                        viewBinding.ibPause.visibility = View.VISIBLE
                        viewBinding.ibPlay.visibility = View.GONE
                        viewBinding.ibRetake.visibility = View.VISIBLE

                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            viewBinding.ibRotate.visibility = View.VISIBLE
                            Log.w("VideoRecording" ,"Video Duration Saved at : ${recordEvent.recordingStats.recordedDurationNanos/ 1_000_000_000} Seconds")

                            Log.w("VideoRecording", "Video recording Stopped with SUCCESS")

                            val uri = recordEvent.outputResults.outputUri
                            Log.d("DancerMatch", uri.path.toString())
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                            viewBinding.ibPause.visibility = View.GONE
                            viewBinding.ibRetake.visibility = View.GONE
                            viewBinding.ibPlay.visibility = View.GONE
                            viewBinding.ibCamera.setImageDrawable(getDrawable(R.drawable.baseline_videocam_24))
                            countDownTimer?.cancel()
                            recordingTimer?.cancel()
                            viewBinding.durationTextView.visibility = View.GONE

                        } else {
                            Log.w("VideoRecording", "Video recording Stopper with ERROR")

                            recording?.close()
                            recording = null
                            Log.e(
                                TAG, "Video capture ends with error: " +
                                        "${recordEvent.error}"
                            )


                            viewBinding.ibPause.visibility = View.GONE
                            viewBinding.ibRetake.visibility = View.GONE
                            viewBinding.ibPlay.visibility = View.GONE
                            viewBinding.ibCamera.setImageDrawable(getDrawable(R.drawable.baseline_videocam_24))
                            countDownTimer?.cancel()
//                            recordingTimer?.cancel()
                            countDownTimer?.onFinish()
//                            recordingTimer?.onFinish()
                        }

                    }

                    is VideoRecordEvent.Pause -> {

                      Log.w("VideoRecording" ,"Video Duration Paused at : ${recordEvent.recordingStats.recordedDurationNanos /1_000_000_000}")
                        Log.w("VideoRecording", "Video recording Paused")
                        countDownTimer?.cancel()
                        recordingTimer?.cancel()
                        viewBinding.ibPause.visibility = View.GONE
                        viewBinding.ibPlay.visibility = View.VISIBLE

                    }

                    is VideoRecordEvent.Resume -> {
                        Log.w("VideoRecording" ,"Video Duration resumed at : ${recordEvent.recordingStats.recordedDurationNanos /1_000_000_000}")

                        Log.w("VideoRecording", "Video recording Resumed")


                        countDownTimer?.start()
                        recordingTimer?.onTick(remainingRecordingTimeMillis)
                        recordingTimer?.start()
//                        recordingTimer?.start()
                        viewBinding.ibPause.visibility = View.VISIBLE
                        viewBinding.ibPlay.visibility = View.GONE

                    }


                }
            }

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HIGHEST,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            imageCapture = ImageCapture.Builder().build()

            /*
            val imageAnalyzer = ImageAnalysis.Builder().build()
                .also {
                    setAnalyzer(
                        cameraExecutor,
                        LuminosityAnalyzer { luma ->
                            Log.d(TAG, "Average luminosity: $luma")
                        }
                    )
                }
            */

            // Select back camera as a default
//            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun rotateCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCamera()
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

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

            listener(luma)

            image.close()
        }
    }
}