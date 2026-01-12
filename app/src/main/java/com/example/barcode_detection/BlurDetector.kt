package com.example.barcode_detection

import android.graphics.Bitmap
import kotlin.math.abs

class BlurDetector(private val config: SystemConfig) {

    /** Very lightweight blur check. Returns true ONLY if image is extremely blurry. */
    fun isBlurry(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height

        // Sample step (downsample aggressively)
        val step = 8

        var edgeSum = 0L
        var count = 0

        for (y in step until h step step) {
            for (x in step until w step step) {
                val c = bitmap.getPixel(x, y)
                val l = bitmap.getPixel(x - step, y)
                val u = bitmap.getPixel(x, y - step)

                val gC = gray(c)
                val gL = gray(l)
                val gU = gray(u)

                edgeSum += abs(gC - gL) + abs(gC - gU)
                count++
            }
        }

        if (count == 0) return true

        val score = edgeSum.toDouble() / count
        return score < config.blurThreshold
    }

    private fun gray(c: Int): Int {
        val r = (c shr 16) and 0xff
        val g = (c shr 8) and 0xff
        val b = c and 0xff
        return (r + g + b) / 3
    }
}
