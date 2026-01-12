package com.example.barcode_detection

import android.graphics.RectF

/**
 * Centralized configuration for the industrial barcode box counting system. All thresholds and
 * parameters can be tuned per installation.
 */
data class SystemConfig(
        // ============================================================
        // ROI (Region of Interest) Configuration
        // ============================================================
        /** ROI where barcode is expected (normalized 0.0-1.0) */
        val roiLeft: Float = 0.2f,
        val roiTop: Float = 0.3f,
        val roiRight: Float = 0.8f,
        val roiBottom: Float = 0.7f,

        // ============================================================
        // Motion Detection Gate
        // ============================================================
        /** Minimum motion score to trigger detection (0.0-1.0) */
        val motionThreshold: Float = 0.02f, // Lowered for more sensitive detection

        // ============================================================
        // Temporal Voting Parameters
        // ============================================================
        /** Number of consecutive detections to confirm barcode presence */
        val detectCountThreshold: Int = 2,

        /** Number of consecutive misses to confirm barcode absence */
        val missCountThreshold: Int = 3,

        /** Number of consecutive misses to trigger exit (for DECODED state) */
        val exitMissThreshold: Int = 5,

        // ============================================================
        // Detection Timeout
        // ============================================================
        /** Maximum time to stay in DETECTING state without finding barcode (milliseconds) */
        val detectingTimeoutMs: Long = 5000L, // 5 seconds

        /** Cooldown period after reset before checking motion again (milliseconds) */
        val postResetCooldownMs: Long = 1000L, // 1 second

        /** Lower detection threshold for DETECTING state (0.0-1.0) */
        val detectingThreshold: Float = 0.3f, // Easier to activate decoding than normal 0.5

        /** Lower detection threshold for exit detection in DECODED state (0.0-1.0) */
        val exitDetectionThreshold: Float = 0.3f, // More lenient than normal 0.5

        // ============================================================
        // Stability Thresholds
        // ============================================================
        /** Maximum allowed bbox center movement (pixels) between frames */
        val maxBboxMovement: Float = 20f,

        /** Maximum allowed bbox scale change ratio (0.0-1.0) */
        val maxScaleChange: Float = 0.2f,

        /** Minimum number of stable frames before READY_TO_DECODE */
        val minStableFrames: Int = 2,

        // ============================================================
        // Barcode Decoding
        // ============================================================
        /** Timeout for ML Kit barcode decoding (milliseconds) */
        val decodeTimeoutMs: Long = 500L,

        // ============================================================
        // PO OCR Configuration
        // ============================================================
        /** Vertical offset below barcode bbox for PO OCR (pixels) */
        val poOcrOffsetY: Float = 10f,

        /** Height of PO OCR region (pixels) */
        val poOcrHeight: Float = 60f,

        /** Regex pattern for PO validation */
        val poRegexPattern: String = "^[A-Z0-9\\-]{5,20}$",

        // ============================================================
        // Performance & Thermal Management
        // ============================================================
        /** Target FPS for vision processing (5-10 for thermal stability) */
        val targetFps: Int = 8,

        /** Minimum interval between detections (milliseconds) */
        val minDetectionIntervalMs: Long = 125L, // 1000ms / 8fps

        // ============================================================
        // Data Logging
        // ============================================================
        /** Camera identifier for logging */
        val cameraId: String = "CAMERA_01"
) {
    /** Get ROI as RectF */
    fun getRoiRect(): RectF = RectF(roiLeft, roiTop, roiRight, roiBottom)

    /** Calculate frame interval in milliseconds based on target FPS */
    fun getFrameIntervalMs(): Long = 1000L / targetFps
}
