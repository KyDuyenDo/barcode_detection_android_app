package com.example.barcode_detection

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** PO (Purchase Order) OCR processor. Reads text below the barcode and validates it. */
class POOCRProcessor(private val config: SystemConfig) {

    companion object {
        private const val TAG = "POOCRProcessor"
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val poRegex = Regex(config.poCodePattern)

    /**
     * Extract and validate PO code from region below barcode.
     *
     * @param bitmap Full frame bitmap
     * @param barcodeBbox Barcode bounding box
     * @return POResult with PO code if successful, null otherwise
     */
    suspend fun extractPO(bitmap: Bitmap, barcodeBbox: RectF): POResult? {
        try {
            // Calculate OCR ROI below barcode
            val ocrRect = calculateOCRRect(barcodeBbox, bitmap.width, bitmap.height)

            // Crop to OCR region
            val ocrBitmap = cropBitmap(bitmap, ocrRect)

            // Run OCR
            val image = InputImage.fromBitmap(ocrBitmap, 0)
            val text =
                    suspendCancellableCoroutine<String> { continuation ->
                        recognizer
                                .process(image)
                                .addOnSuccessListener { visionText ->
                                    continuation.resume(visionText.text)
                                }
                                .addOnFailureListener { e -> continuation.resumeWithException(e) }
                    }

            // Post-process and validate
            val processedPO = postProcessPO(text)

            if (processedPO != null) {
                Log.i(TAG, "PO extracted: $processedPO")
                return POResult(processedPO, POStatus.CONFIRMED)
            } else {
                Log.w(TAG, "PO validation failed. Raw text: '$text'")
                return POResult(null, POStatus.MISSING)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR error", e)
            return POResult(null, POStatus.MISSING)
        }
    }

    /** Calculate OCR rectangle below barcode */
    private fun calculateOCRRect(barcodeBbox: RectF, frameWidth: Int, frameHeight: Int): RectF {
        val top = (barcodeBbox.bottom).coerceIn(0f, frameHeight.toFloat())
        val height = barcodeBbox.height() * config.ocrRegionHeightRatio
        val bottom = (top + height).coerceIn(top, frameHeight.toFloat())

        return RectF(
                barcodeBbox.left.coerceIn(0f, frameWidth.toFloat()),
                top,
                barcodeBbox.right.coerceIn(0f, frameWidth.toFloat()),
                bottom
        )
    }

    /** Crop bitmap to specified rectangle */
    private fun cropBitmap(bitmap: Bitmap, rect: RectF): Bitmap {
        val left = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val width = (rect.width().toInt()).coerceIn(1, bitmap.width - left)
        val height = (rect.height().toInt()).coerceIn(1, bitmap.height - top)

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    /**
     * Post-process OCR text and validate against regex.
     *
     * Steps:
     * 1. Trim whitespace
     * 2. Convert to uppercase
     * 3. Remove illegal characters
     * 4. Validate against regex pattern
     *
     * @return Validated PO code or null if invalid
     */
    private fun postProcessPO(rawText: String): String? {
        // Trim and uppercase
        var processed = rawText.trim().uppercase()

        // Remove common OCR noise characters
        processed = processed.replace(Regex("[^A-Z0-9\\-]"), "")

        // Split by lines and try each line
        val lines = processed.split("\n", "\r\n", "\r")
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.matches(poRegex)) {
                return trimmedLine
            }
        }

        // Try the full processed text
        if (processed.matches(poRegex)) {
            return processed
        }

        return null
    }

    /** Release resources */
    fun close() {
        recognizer.close()
    }
}

/** Result of PO OCR */
data class POResult(val poCode: String?, val status: POStatus)
