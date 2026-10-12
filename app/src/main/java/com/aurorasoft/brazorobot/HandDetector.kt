package com.aurorasoft.brazorobot

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions

class HandDetector(
    private val context: Context,
    // Agregamos el listener obligatorio para recibir los datos de la cámara en vivo
    private val resultListener: (HandLandmarkerResult) -> Unit
) {

    private var handLandmarker: HandLandmarker? = null

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .setDelegate(Delegate.GPU)
                .build()

            val options = HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setNumHands(1)

                // 1. Asegurar el modo LIVE_STREAM si usas cámara
                .setRunningMode(RunningMode.LIVE_STREAM)
                // 2. Proporcionar el ResultListener requerido que soluciona el crash
                .setResultListener { result, _ ->
                    resultListener(result)
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Envía los cuadros de la cámara a procesar de manera asíncrona.
     * Importante: Se requiere una marca de tiempo (timestamp) única por cuadro.
     */
    fun detectLiveStream(bmp: Bitmap, frameTimeMs: Long) {
        val mpImage: MPImage = BitmapImageBuilder(bmp).build()
        handLandmarker?.detectAsync(mpImage, frameTimeMs)
    }

    fun close() {
        handLandmarker?.close()
    }
}
