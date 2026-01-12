package com.example.barcode_detection

import androidx.camera.core.ImageProxy
import kotlin.math.abs

class MotionDetector(private val config: SystemConfig) {

    private var prevY: ByteArray? = null
    private var prevW = 0
    private var prevH = 0

    fun hasMotion(proxy: ImageProxy): Boolean {
        val yBuffer = proxy.planes[0].buffer
        val w = proxy.width
        val h = proxy.height

        val ySize = yBuffer.remaining()
        val y = ByteArray(ySize)
        yBuffer.get(y)

        if (prevY == null || prevW != w || prevH != h) {
            prevY = y
            prevW = w
            prevH = h
            return false
        }

        var diff = 0L
        val step = 16 // VERY important: skip aggressively

        for (i in y.indices step step) {
            diff += abs((y[i].toInt() and 0xff) - (prevY!![i].toInt() and 0xff))
        }

        prevY = y

        val score = diff.toFloat() / ((ySize / step) * 255f)
        return score > config.motionThreshold
    }

    fun reset() {
        prevY = null
    }
}
