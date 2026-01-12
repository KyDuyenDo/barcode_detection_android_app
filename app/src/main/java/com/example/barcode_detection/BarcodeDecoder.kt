package com.example.barcode_detection

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.*

/**
 * ML Kit barcode decoder with single-shot execution and timeout. Decodes barcode exactly once per
 * box.
 */
class BarcodeDecoder(private val config: SystemConfig) {

    companion object {
        private const val TAG = "BarcodeDecoder"
    }

    private val scanner = BarcodeScanning.getClient()

    /**
     * Decode barcode from bitmap with timeout. This is a single-shot operation - should only be
     * called once per box.
     *
     * @param bitmap Image containing the barcode
     * @param bboxHint Optional bounding box hint for better accuracy
     * @return DecodeResult with barcode value and format, or null if failed
     */
    suspend fun decode(bitmap: Bitmap, bboxHint: RectF? = null): DecodeResult? {
        return withTimeoutOrNull(config.decodeTimeoutMs) {
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val barcodes =
                        suspendCoroutine<List<Barcode>> { continuation ->
                            scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        continuation.resume(barcodes)
                                    }
                                    .addOnFailureListener { e ->
                                        continuation.resumeWithException(e)
                                    }
                        }

                if (barcodes.isEmpty()) {
                    Log.w(TAG, "No barcode found")
                    return@withTimeoutOrNull null
                }

                // If we have a bbox hint, prefer barcode closest to it
                val barcode =
                        if (bboxHint != null && barcodes.size > 1) {
                            selectBarcodeNearHint(barcodes, bboxHint)
                        } else {
                            barcodes[0]
                        }

                val rawValue = barcode.rawValue
                if (rawValue.isNullOrEmpty()) {
                    Log.w(TAG, "Barcode has no value")
                    return@withTimeoutOrNull null
                }

                val result =
                        DecodeResult(
                                value = rawValue,
                                format = getBarcodeFormatName(barcode.format),
                                boundingBox =
                                        barcode.boundingBox?.let {
                                            RectF(
                                                    it.left.toFloat(),
                                                    it.top.toFloat(),
                                                    it.right.toFloat(),
                                                    it.bottom.toFloat()
                                            )
                                        }
                        )

                Log.i(TAG, "Barcode decoded: ${result.value} (${result.format})")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Decode error", e)
                null
            }
        }
    }

    /** Select barcode closest to the hint bounding box */
    private fun selectBarcodeNearHint(barcodes: List<Barcode>, hint: RectF): Barcode {
        return barcodes.minByOrNull { barcode ->
            val bbox = barcode.boundingBox ?: return@minByOrNull Float.MAX_VALUE
            val centerX = (bbox.left + bbox.right) / 2f
            val centerY = (bbox.top + bbox.bottom) / 2f
            val hintCenterX = hint.centerX()
            val hintCenterY = hint.centerY()

            // Distance from hint center
            val dx = centerX - hintCenterX
            val dy = centerY - hintCenterY
            kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }
                ?: barcodes[0]
    }

    /** Get human-readable barcode format name */
    private fun getBarcodeFormatName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_CODE_128 -> "CODE_128"
            Barcode.FORMAT_CODE_39 -> "CODE_39"
            Barcode.FORMAT_CODE_93 -> "CODE_93"
            Barcode.FORMAT_CODABAR -> "CODABAR"
            Barcode.FORMAT_EAN_13 -> "EAN_13"
            Barcode.FORMAT_EAN_8 -> "EAN_8"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_UPC_A -> "UPC_A"
            Barcode.FORMAT_UPC_E -> "UPC_E"
            Barcode.FORMAT_QR_CODE -> "QR_CODE"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_AZTEC -> "AZTEC"
            Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
            else -> "UNKNOWN"
        }
    }

    /** Release resources */
    fun close() {
        scanner.close()
    }
}

/** Result of barcode decoding */
data class DecodeResult(val value: String, val format: String, val boundingBox: RectF?)
