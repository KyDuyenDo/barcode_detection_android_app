package com.example.barcode_detection

import android.graphics.Bitmap
import kotlin.math.abs

class MotionDetector(private val config: SystemConfig) {

    private var prev: IntArray? = null
    private var w = 0
    private var h = 0

    fun hasMotion(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        if (prev == null || w != width || h != height) {
            prev = pixels
            w = width
            h = height
            return false
        }

        var diffSum = 0L
        for (i in pixels.indices step 4) {
            val c = pixels[i]
            val p = prev!![i]

            val g1 = ((c shr 16) and 0xff + (c shr 8) and 0xff + (c and 0xff)) / 3
            val g2 = ((p shr 16) and 0xff + (p shr 8) and 0xff + (p and 0xff)) / 3
            diffSum += abs(g1 - g2)
        }

        prev = pixels
        val max = (pixels.size / 4) * 255f
        val score = diffSum / max
        return score >= config.motionThreshold
    }

    fun reset() {
        prev = null
    }
}
