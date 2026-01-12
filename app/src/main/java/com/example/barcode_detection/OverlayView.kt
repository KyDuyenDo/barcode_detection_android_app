package com.example.barcode_detection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var bbox: RectF? = null
    private var imgW = 0
    private var imgH = 0
    private var state = AppState.IDLE
    private var motion = ""

    fun setImageInfo(w: Int, h: Int) {
        imgW = w
        imgH = h
    }

    fun update(b: RectF?, s: AppState, m: String) {
        bbox = b
        state = s
        motion = m
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bbox == null || imgW == 0) return

        canvas.save()
        canvas.scale(width.toFloat() / imgW, height.toFloat() / imgH)

        val paint =
                Paint().apply {
                    color = if (state == AppState.DECODED) Color.GREEN else Color.YELLOW
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                }

        canvas.drawRect(bbox!!, paint)
        canvas.drawText(
                "Motion: $motion",
                bbox!!.left,
                bbox!!.top - 20,
                Paint().apply {
                    color = Color.WHITE
                    textSize = 40f
                }
        )

        canvas.restore()
    }
}
