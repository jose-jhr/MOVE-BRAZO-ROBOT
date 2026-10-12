package com.aurorasoft.gympix.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraXFrameProvider(
    private val context: Context,
    //private val widthImage: Int = 640,
    //private val heightImage: Int = 480
    private val widthImage: Int = 800,
    private val heightImage: Int = 600
) {

    interface FrameListener {
        // imageProxy removed — data is already extracted into bitmap before callback fires
        fun onFrameAvailable(bitmap: Bitmap, rotationDegrees: Int)
    }

    private var frameListener: FrameListener? = null
    private lateinit var cameraExecutor: ExecutorService

    fun setFrameListener(listener: FrameListener) {
        frameListener = listener
    }

    fun initCameraExecutor() {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun setupCamera(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(widthImage, heightImage))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                // analyzeImage owns the close — do NOT close here too
                analyzeImage(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis)
            } catch (e: Exception) {
                Log.e("CameraXFrameProvider", "Error binding camera use cases", e)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeImage(imageProxy: ImageProxy) {
        if (imageProxy.image == null) {
            imageProxy.close()
            return
        }

        // Read rotation before any processing
        val degrees = imageProxy.imageInfo.rotationDegrees

        try {
            // Step 1: YUV → RGB at full sensor resolution
            val raw = imageProxy.toBitmap()

            // Close ASAP — releases the hardware camera buffer back to the pool
            imageProxy.close()

            // Step 2: Scale + rotate in a single Bitmap.createBitmap pass.
            // Using a combined Matrix avoids the intermediate scaled Bitmap entirely
            // (Skia rasterises scale and rotate in one pixel pass).
            val matrix = Matrix()
            matrix.postScale(widthImage.toFloat() / raw.width, heightImage.toFloat() / raw.height)
            if (degrees != 0) {
                // Rotate around the centre of the already-scaled space
                matrix.postRotate(degrees.toFloat(), widthImage / 2f, heightImage / 2f)
            }
            // createBitmap computes output dimensions from the bounding box of all
            // four transformed corners, so 90°/270° automatically yields heightImage×widthImage
            val output = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, false)
            raw.recycle()

            frameListener?.onFrameAvailable(output, degrees)

        } catch (e: Exception) {
            Log.e("CameraXFrameProvider", "Error processing frame", e)
            try { imageProxy.close() } catch (_: Exception) {}
        }
    }

    fun stopCamera() {
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
    }
}
