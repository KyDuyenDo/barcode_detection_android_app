package com.example.barcode_detection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<BarcodeDetector.DetectionResult> = emptyList()
    private var imageW = 0
    private var imageH = 0
    private var currentState: AppState = AppState.IDLE
    private var roiRect: RectF? = null

    private val detectingPaint =
            Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 8f // Thicker for better visibility
            }

    private val detectingFillPaint =
            Paint().apply {
                color = Color.argb(40, 255, 255, 0) // Semi-transparent yellow fill
                style = Paint.Style.FILL
            }

    private val stablePaint =
            Paint().apply {
                color = Color.GREEN
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }

    private val decodedPaint =
            Paint().apply {
                color = Color.CYAN
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }

    private val roiPaint =
            Paint().apply {
                color = Color.argb(80, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 3f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }

    private val textPaint =
            Paint().apply {
                color = Color.WHITE
                textSize = 32f
                style = Paint.Style.FILL
                setShadowLayer(4f, 2f, 2f, Color.BLACK)
            }

    fun setResults(
            detections: List<BarcodeDetector.DetectionResult>,
            imgW: Int,
            imgH: Int,
            state: AppState = AppState.IDLE
    ) {
        results = detections
        imageW = imgW
        imageH = imgH
        currentState = state
        invalidate()
    }

    fun setROI(roi: RectF) {
        roiRect = roi
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (imageW == 0 || imageH == 0) return

        val scaleX = width.toFloat() / imageW
        val scaleY = height.toFloat() / imageH

        // Draw ROI
        roiRect?.let { roi ->
            canvas.drawRect(
                    roi.left * scaleX,
                    roi.top * scaleY,
                    roi.right * scaleX,
                    roi.bottom * scaleY,
                    roiPaint
            )
        }

        // Draw detections with state-based colors
        if (results.isNotEmpty()) {
            val paint =
                    when (currentState) {
                        AppState.DETECTING -> detectingPaint
                        AppState.READY_TO_DECODE -> stablePaint
                        AppState.DECODED -> decodedPaint
                        else -> detectingPaint
                    }

            for (r in results) {
                val left = r.rect.left * scaleX
                val top = r.rect.top * scaleY
                val right = r.rect.right * scaleX
                val bottom = r.rect.bottom * scaleY

                // Draw fill for DETECTING state to make it more prominent
                if (currentState == AppState.DETECTING) {
                    canvas.drawRect(left, top, right, bottom, detectingFillPaint)
                }

                // Draw border
                canvas.drawRect(left, top, right, bottom, paint)

                // Draw score
                val scoreText = "%.2f".format(r.score)
                canvas.drawText(scoreText, left + 10, top - 10, textPaint)
            }
        }
    }
}
