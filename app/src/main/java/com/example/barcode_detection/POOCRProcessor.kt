package com.example.barcode_detection

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class POOCRProcessor(private val config: SystemConfig) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun extractPO(bitmap: Bitmap, bbox: RectF): String? {
        val y = bbox.bottom.toInt()
        val h = ((bitmap.height - y) * config.ocrRegionHeightRatio).toInt()
        if (h <= 0) return null

        val crop = Bitmap.createBitmap(bitmap, 0, y, bitmap.width, h)

        val image = InputImage.fromBitmap(crop, 0)
        val result = Tasks.await(recognizer.process(image))

        val regex = Regex(config.poCodePattern)
        return regex.find(result.text)?.value
    }
}
