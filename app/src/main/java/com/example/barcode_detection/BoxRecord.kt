package com.example.barcode_detection

/** Status of PO (Purchase Order) OCR result */
enum class POStatus {
    /** PO successfully read and validated */
    CONFIRMED,

    /** PO OCR failed or validation failed */
    MISSING
}

/** Record of a successfully counted box. This is the primary data structure logged for each box. */
data class BoxRecord(
        /** Decoded barcode value */
        val barcode: String,

        /** Purchase Order code (if successfully read) */
        val po: String?,

        /** Status of PO OCR */
        val poStatus: POStatus,

        /** ISO8601 timestamp when box was counted */
        val timestamp: String,

        /** Camera identifier */
        val cameraId: String,

        /** Frame ID for debugging */
        val frameId: String
)
