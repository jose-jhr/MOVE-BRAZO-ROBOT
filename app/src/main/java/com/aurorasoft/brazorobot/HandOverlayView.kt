package com.aurorasoft.brazorobot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.hypot

class HandOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: HandLandmarkerResult? = null

    // Pincel para los micro-núcleos (Blanco brillante)
    private val corePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Pincel para los micro-anillos (Cian Neón)
    private val ringPaint = Paint().apply {
        color = Color.parseColor("#00F5FF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    // Pincel para la bruma de resplandor de los puntos
    private val glowPaint = Paint().apply {
        color = Color.parseColor("#2600F5FF") // 15% de opacidad cian
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Pincel dinámico para la línea elástica de la pinza
    private val gripperLinePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    // Dimensiones Micro-Elegantes para los 21 puntos
    private val coreRadius = 3f
    private val ringRadius = 6f
    private val glowRadius = 10f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setResults(handLandmarkerResult: HandLandmarkerResult) {
        this.results = handLandmarkerResult
        postInvalidate()
    }

    fun clear() {
        results = null
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentResults = results ?: return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        for (landmarks in currentResults.landmarks()) {
            if (landmarks.size < 21) continue

            // 1. EXTRAER COORDENADAS DE LA PINZA: Pulgar (4) e Índice (8)
            val thumb = landmarks[4]
            val indexFinger = landmarks[8]

            val tX = thumb.x() * viewWidth
            val tY = thumb.y() * viewHeight
            val iX = indexFinger.x() * viewWidth
            val iY = indexFinger.y() * viewHeight

            // Calcular distancia geométrica para definir el estado de apertura
            val distance = hypot(iX - tX, iY - tY)
            val isClosed = distance < 65f // Umbral de cierre (ajustable)

            // Asignar color reactivo: Rojo Neón (Cerrado/Agarre) o Verde Neón (Abierto/Libre)
            gripperLinePaint.color = if (isClosed) Color.parseColor("#FF3366") else Color.parseColor("#00FF66")

            // 2. DIBUJAR LA LÍNEA ELÁSTICA DE LA PINZA
            canvas.drawLine(tX, tY, iX, iY, gripperLinePaint)


            // 3. DIBUJAR LOS 21 MICRO-PUNTOS CON PASADAS OPTIMIZADAS
            // Pasada A: Auras de fondo
            for (landmark in landmarks) {
                val cx = landmark.x() * viewWidth
                val cy = landmark.y() * viewHeight
                canvas.drawCircle(cx, cy, glowRadius, glowPaint)
            }

            // Pasada B: Anillos cian
            for (landmark in landmarks) {
                val cx = landmark.x() * viewWidth
                val cy = landmark.y() * viewHeight
                canvas.drawCircle(cx, cy, ringRadius, ringPaint)
            }

            // Pasada C: Núcleos blancos brillantes
            for (landmark in landmarks) {
                val cx = landmark.x() * viewWidth
                val cy = landmark.y() * viewHeight
                canvas.drawCircle(cx, cy, coreRadius, corePaint)
            }

            // Pasada D: Resaltar los dos nodos de la pinza con un aro exterior extra del color del estado
            canvas.drawCircle(tX, tY, ringRadius + 4f, gripperLinePaint)
            canvas.drawCircle(iX, iY, ringRadius + 4f, gripperLinePaint)
        }
    }
}
