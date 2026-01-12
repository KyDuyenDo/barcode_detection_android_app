package com.example.barcode_detection

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs

/**
 * Lightweight motion detection gate to prevent unnecessary inference. Uses frame difference to
 * detect motion in the scene.
 */
class MotionDetector(private val config: SystemConfig) {

    companion object {
        private const val TAG = "MotionDetector"
    }

    private var previousFrame: IntArray? = null
    private var previousWidth: Int = 0
    private var previousHeight: Int = 0

    /**
     * Compute motion score between current and previous frame. Returns a value between 0.0 (no
     * motion) and 1.0 (maximum motion).
     *
     * Enhanced to detect horizontal movement (X-axis) which is critical for conveyor belt
     * scenarios.
     *
     * @param bitmap Current frame
     * @return Motion score, or 1.0 if no previous frame exists
     */
    fun computeMotionScore(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // First frame - no comparison possible
        if (previousFrame == null || previousWidth != width || previousHeight != height) {
            previousFrame = pixels
            previousWidth = width
            previousHeight = height
            return 1.0f // Allow processing on first frame
        }

        // Compute frame difference
        var totalDiff = 0L
        var horizontalDiff = 0L // Track horizontal motion specifically
        val prev = previousFrame!!

        // Analyze motion in horizontal strips to detect X-axis movement
        val numStrips = 8 // Divide frame into horizontal strips
        val stripHeight = height / numStrips

        for (stripIndex in 0 until numStrips) {
            val stripStart = stripIndex * stripHeight
            val stripEnd = minOf((stripIndex + 1) * stripHeight, height)
            var stripDiff = 0L

            // Sample pixels in this horizontal strip
            for (y in stripStart until stripEnd step 2) {
                for (x in 0 until width step 4) {
                    val i = y * width + x
                    if (i >= pixels.size) continue

                    val currPixel = pixels[i]
                    val prevPixel = prev[i]

                    // Compute grayscale difference
                    val currGray =
                            ((currPixel shr 16 and 0xFF) +
                                    (currPixel shr 8 and 0xFF) +
                                    (currPixel and 0xFF)) / 3
                    val prevGray =
                            ((prevPixel shr 16 and 0xFF) +
                                    (prevPixel shr 8 and 0xFF) +
                                    (prevPixel and 0xFF)) / 3

                    val diff = abs(currGray - prevGray)
                    totalDiff += diff
                    stripDiff += diff
                }
            }

            // If any horizontal strip shows significant motion, it indicates horizontal movement
            horizontalDiff = maxOf(horizontalDiff, stripDiff)
        }

        // Update previous frame
        previousFrame = pixels

        // Compute both overall motion and horizontal motion scores
        val sampledPixels = (width / 4) * (height / 2)
        val maxPossibleDiff = sampledPixels * 255L
        val overallMotion = (totalDiff.toFloat() / maxPossibleDiff).coerceIn(0f, 1f)

        // Horizontal motion score (normalized per strip)
        val stripSampledPixels = (width / 4) * (stripHeight / 2)
        val maxStripDiff = stripSampledPixels * 255L
        val horizontalMotion = (horizontalDiff.toFloat() / maxStripDiff).coerceIn(0f, 1f)

        // Use the maximum of overall and horizontal motion to ensure horizontal movement is
        // detected
        val motionScore = maxOf(overallMotion, horizontalMotion * 1.2f).coerceIn(0f, 1f)

        Log.v(
                TAG,
                "Motion score: ${"%.3f".format(motionScore)} (overall: ${"%.3f".format(overallMotion)}, horizontal: ${"%.3f".format(horizontalMotion)})"
        )
        return motionScore
    }

    /** Check if motion score exceeds threshold */
    fun hasMotion(bitmap: Bitmap): Boolean {
        val score = computeMotionScore(bitmap)
        return score >= config.motionThreshold
    }

    /** Reset motion detector (clear previous frame) */
    fun reset() {
        previousFrame = null
        previousWidth = 0
        previousHeight = 0
        Log.d(TAG, "Motion detector reset")
    }
}
