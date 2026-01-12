package com.example.barcode_detection

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class BarcodeDecoder {

    private val scanner = BarcodeScanning.getClient()

    fun scanBlocking(bitmap: Bitmap): DecodeResult? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val task = scanner.process(image)
        Tasks.await(task)

        val barcodes = task.result ?: return null
        if (barcodes.isEmpty()) return null

        val b = barcodes[0]
        val box = b.boundingBox ?: return null
        val value = b.rawValue ?: return null

        return DecodeResult(
            value = value,
            boundingBox = RectF(
                box.left.toFloat(),
                box.top.toFloat(),
                box.right.toFloat(),
                box.bottom.toFloat()
            )
        )
    }

    fun close() {
        scanner.close()
    }
}

data class DecodeResult(
    val value: String,
    val boundingBox: RectF
)
