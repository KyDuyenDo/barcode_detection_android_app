package com.example.barcode_detection

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class BarcodeDecoder {

    private val TAG = "BarcodeDecoder"
    private val scanner = BarcodeScanning.getClient()
    private var scanCount = 0

    fun scan(bitmap: Bitmap): RectF? {
        scanCount++

        try {
            Log.d(TAG, "Scan #$scanCount - Processing ${bitmap.width}x${bitmap.height} bitmap")

            val image = InputImage.fromBitmap(bitmap, 0)
            val task = scanner.process(image)
            Tasks.await(task)

            val barcodes = task.result

            if (barcodes == null) {
                Log.w(TAG, "Scan #$scanCount - Result is null!")
                return null
            }

            Log.d(TAG, "Scan #$scanCount - Found ${barcodes.size} barcode(s)")

            if (barcodes.isEmpty()) {
                return null
            }

            val barcode = barcodes[0]
            Log.i(
                    TAG,
                    "Scan #$scanCount - Barcode: ${barcode.rawValue} (format: ${barcode.format})"
            )

            val box = barcode.boundingBox
            if (box == null) {
                Log.w(TAG, "Scan #$scanCount - No bounding box!")
                return null
            }

            val rectF =
                    RectF(
                            box.left.toFloat(),
                            box.top.toFloat(),
                            box.right.toFloat(),
                            box.bottom.toFloat()
                    )

            Log.i(TAG, "Scan #$scanCount - Box: $rectF")
            return rectF
        } catch (e: Exception) {
            Log.e(TAG, "Scan #$scanCount - ERROR", e)
            return null
        }
    }

    fun close() {
        Log.d(TAG, "Closing scanner (total scans: $scanCount)")
        scanner.close()
    }
}
