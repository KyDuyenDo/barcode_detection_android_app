package com.example.barcode_detection

import android.graphics.RectF
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Temporal voting and stability checking module. Ensures barcode is consistently detected and
 * stable before decoding.
 */
class TemporalVoting(private val config: SystemConfig) {

    companion object {
        private const val TAG = "TemporalVoting"
    }

    // Detection tracking
    private var detectCount = 0
    private var missCount = 0
    private var stableFrameCount = 0

    // Bounding box tracking for stability
    private var lastBbox: RectF? = null
    private var lastBboxArea: Float = 0f

    /**
     * Process a detection result and update counters.
     *
     * @param detected Whether barcode was detected in this frame
     * @param bbox Bounding box if detected, null otherwise
     * @return VotingResult indicating the decision
     */
    fun processFrame(detected: Boolean, bbox: RectF?): VotingResult {
        if (detected && bbox != null) {
            // Barcode detected
            detectCount++
            missCount = 0

            // Check stability
            val isStable = checkStability(bbox)

            if (isStable) {
                stableFrameCount++
            } else {
                stableFrameCount = 0
            }

            // Update last bbox
            lastBbox = RectF(bbox)
            lastBboxArea = bbox.width() * bbox.height()

            // Check if we have enough stable detections
            if (detectCount >= config.detectCountThreshold &&
                            stableFrameCount >= config.minStableFrames
            ) {
                Log.d(
                        TAG,
                        "Barcode STABLE (detectCount=$detectCount, stableFrames=$stableFrameCount)"
                )
                return VotingResult.STABLE
            }

            Log.v(
                    TAG,
                    "Barcode DETECTING (detectCount=$detectCount, stableFrames=$stableFrameCount)"
            )
            return VotingResult.DETECTING
        } else {
            // Barcode not detected
            missCount++
            detectCount = 0
            stableFrameCount = 0

            if (missCount >= config.missCountThreshold) {
                Log.d(TAG, "Barcode ABSENT (missCount=$missCount)")
                return VotingResult.ABSENT
            }

            Log.v(TAG, "Barcode UNCERTAIN (missCount=$missCount)")
            return VotingResult.UNCERTAIN
        }
    }

    /**
     * Check if barcode has exited (for DECODED state). Uses higher threshold than normal absence
     * detection.
     */
    fun checkExit(detected: Boolean): Boolean {
        if (!detected) {
            missCount++
            if (missCount >= config.exitMissThreshold) {
                Log.d(TAG, "Barcode EXITED (missCount=$missCount)")
                return true
            }
        } else {
            missCount = 0
        }
        return false
    }

    /** Check if bounding box is stable compared to previous frame. */
    private fun checkStability(bbox: RectF): Boolean {
        val prev = lastBbox ?: return true // First detection is considered stable

        // Check center movement
        val currCenterX = bbox.centerX()
        val currCenterY = bbox.centerY()
        val prevCenterX = prev.centerX()
        val prevCenterY = prev.centerY()

        val movement =
                kotlin.math
                        .sqrt(
                                ((currCenterX - prevCenterX) * (currCenterX - prevCenterX) +
                                                (currCenterY - prevCenterY) *
                                                        (currCenterY - prevCenterY))
                                        .toDouble()
                        )
                        .toFloat()

        if (movement > config.maxBboxMovement) {
            Log.v(TAG, "Unstable: movement=${"%.1f".format(movement)}px")
            return false
        }

        // Check scale change
        val currArea = bbox.width() * bbox.height()
        val scaleRatio =
                if (lastBboxArea > 0) {
                    max(currArea, lastBboxArea) / min(currArea, lastBboxArea)
                } else {
                    1f
                }

        if (scaleRatio - 1f > config.maxScaleChange) {
            Log.v(TAG, "Unstable: scaleRatio=${"%.2f".format(scaleRatio)}")
            return false
        }

        return true
    }

    /** Reset all counters and state */
    fun reset() {
        detectCount = 0
        missCount = 0
        stableFrameCount = 0
        lastBbox = null
        lastBboxArea = 0f
        Log.d(TAG, "Temporal voting reset")
    }

    /** Get current detection count */
    fun getDetectCount(): Int = detectCount

    /** Get current miss count */
    fun getMissCount(): Int = missCount
}

/** Result of temporal voting */
enum class VotingResult {
    /** Barcode is consistently detected and stable */
    STABLE,

    /** Barcode is being detected but not yet stable */
    DETECTING,

    /** Barcode is confirmed absent */
    ABSENT,

    /** Uncertain state (transitioning) */
    UNCERTAIN
}
