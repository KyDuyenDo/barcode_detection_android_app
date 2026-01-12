package com.example.barcode_detection

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import java.nio.FloatBuffer
import java.util.Collections

class BarcodeDetector(private val context: Context) {

    companion object {
        private const val INPUT_SIZE = 320
        private const val SCORE_THRESH = 0.5f
        private const val TAG = "BarcodeDetector"
    }

    /** Bounding box ở TOẠ ĐỘ ẢNH GỐC */
    data class DetectionResult(
            val rect: RectF,
            val score: Float,
            val timestamp: Long = System.currentTimeMillis()
    )

    private lateinit var env: OrtEnvironment
    private lateinit var session: OrtSession
    private var lastDetectionTime: Long = 0
    private var frameCount: Long = 0

    fun init() {
        env = OrtEnvironment.getEnvironment()
        val modelBytes = context.assets.open("barcode_ssd_mobilenet.onnx").readBytes()
        session = env.createSession(modelBytes)
        Log.i(TAG, "ONNX model initialized")
    }

    /** Check if enough time has passed since last detection (FPS throttling) */
    fun shouldProcess(minIntervalMs: Long): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastDetectionTime) >= minIntervalMs
    }

    fun detect(bitmap: Bitmap, scoreThreshold: Float = SCORE_THRESH): List<DetectionResult> {
        frameCount++
        lastDetectionTime = System.currentTimeMillis()

        val origW = bitmap.width
        val origH = bitmap.height

        // ======================
        // 1. LETTERBOX → 320x320
        // ======================
        val scale = minOf(INPUT_SIZE.toFloat() / origW, INPUT_SIZE.toFloat() / origH)
        val resizedW = (origW * scale).toInt()
        val resizedH = (origH * scale).toInt()
        val padX = (INPUT_SIZE - resizedW) / 2f
        val padY = (INPUT_SIZE - resizedH) / 2f

        val resized = Bitmap.createScaledBitmap(bitmap, resizedW, resizedH, true)
        val inputBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)

        Canvas(inputBitmap).apply {
            drawColor(Color.BLACK)
            drawBitmap(resized, padX, padY, null)
        }

        // ======================
        // 2. BITMAP → FLOAT
        // ======================
        val inputBuffer = bitmapToFloatBuffer(inputBitmap)
        val inputTensor =
                OnnxTensor.createTensor(
                        env,
                        inputBuffer,
                        longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
                )

        val results = mutableListOf<DetectionResult>()
        var outputs: OrtSession.Result? = null

        try {
            outputs = session.run(Collections.singletonMap("input", inputTensor))

            val boxes = outputs[0].value as Array<FloatArray>
            val scores = outputs[1].value as FloatArray
            val labels = outputs[2].value as LongArray

            for (i in scores.indices) {
                val score = scores[i]
                if (score < scoreThreshold) continue // Use dynamic threshold
                if (labels[i] != 1L) continue

                val b = boxes[i]

                // ======================
                // 3. UNDO LETTERBOX
                // ======================
                var x1 = (b[0] - padX) / scale
                var y1 = (b[1] - padY) / scale
                var x2 = (b[2] - padX) / scale
                var y2 = (b[3] - padY) / scale

                x1 = x1.coerceIn(0f, origW.toFloat())
                y1 = y1.coerceIn(0f, origH.toFloat())
                x2 = x2.coerceIn(0f, origW.toFloat())
                y2 = y2.coerceIn(0f, origH.toFloat())

                if (x2 <= x1 || y2 <= y1) continue

                results.add(DetectionResult(RectF(x1, y1, x2, y2), score))
            }
        } catch (e: Exception) {
            Log.e("BarcodeDetector", "ONNX error", e)
        } finally {
            outputs?.close()
            inputTensor.close()
        }

        return results
    }

    private fun bitmapToFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val buffer = FloatBuffer.allocate(1 * 3 * INPUT_SIZE * INPUT_SIZE)

        for (p in pixels) buffer.put(((p shr 16) and 0xFF) / 255f)
        for (p in pixels) buffer.put(((p shr 8) and 0xFF) / 255f)
        for (p in pixels) buffer.put((p and 0xFF) / 255f)

        buffer.rewind()
        return buffer
    }
}
