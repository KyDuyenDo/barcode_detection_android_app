package com.example.barcode_detection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var bbox: RectF? = null
    private var imgW = 0
    private var imgH = 0
    private var state: AppState = AppState.IDLE

    private val scanPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val decodedPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    fun setImageInfo(w: Int, h: Int) {
        imgW = w
        imgH = h
    }

    fun setBbox(b: RectF?) {
        bbox = b
        invalidate()
    }

    fun setState(s: AppState) {
        state = s
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bbox == null || imgW == 0 || imgH == 0) return

        // ⚠️ scale canvas – KHÔNG scale bbox
        val sx = width.toFloat() / imgW
        val sy = height.toFloat() / imgH
        canvas.save()
        canvas.scale(sx, sy)

        val paint = if (state == AppState.DECODED) decodedPaint else scanPaint
        canvas.drawRect(bbox!!, paint)

        canvas.restore()
    }
}
