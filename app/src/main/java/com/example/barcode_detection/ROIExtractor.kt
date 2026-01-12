package com.example.barcode_detection

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * ROI (Region of Interest) extraction module. Crops frames to the configured ROI to reduce
 * inference cost.
 */
class ROIExtractor(private val config: SystemConfig) {

    /**
     * Extract ROI from bitmap based on configuration.
     *
     * @param bitmap Source bitmap
     * @return Cropped bitmap containing only the ROI
     */
    fun extractROI(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Convert normalized coordinates to pixel coordinates
        val left = (config.roiLeft * width).toInt().coerceIn(0, width - 1)
        val top = (config.roiTop * height).toInt().coerceIn(0, height - 1)
        val right = (config.roiRight * width).toInt().coerceIn(left + 1, width)
        val bottom = (config.roiBottom * height).toInt().coerceIn(top + 1, height)

        val roiWidth = right - left
        val roiHeight = bottom - top

        return Bitmap.createBitmap(bitmap, left, top, roiWidth, roiHeight)
    }

    /**
     * Get ROI rectangle in pixel coordinates for a given frame size.
     *
     * @param frameWidth Frame width in pixels
     * @param frameHeight Frame height in pixels
     * @return RectF with pixel coordinates
     */
    fun getROIRect(frameWidth: Int, frameHeight: Int): RectF {
        return RectF(
                config.roiLeft * frameWidth,
                config.roiTop * frameHeight,
                config.roiRight * frameWidth,
                config.roiBottom * frameHeight
        )
    }

    /**
     * Map coordinates from ROI space back to full frame space.
     *
     * @param roiRect Rectangle in ROI coordinates
     * @param frameWidth Full frame width
     * @param frameHeight Full frame height
     * @return Rectangle in full frame coordinates
     */
    fun mapROIToFrame(roiRect: RectF, frameWidth: Int, frameHeight: Int): RectF {
        val roiPixelRect = getROIRect(frameWidth, frameHeight)

        return RectF(
                roiPixelRect.left + roiRect.left,
                roiPixelRect.top + roiRect.top,
                roiPixelRect.left + roiRect.right,
                roiPixelRect.top + roiRect.bottom
        )
    }

    /**
     * Check if a rectangle is fully outside the ROI.
     *
     * @param rect Rectangle in full frame coordinates
     * @param frameWidth Full frame width
     * @param frameHeight Full frame height
     * @return True if rectangle is completely outside ROI
     */
    fun isOutsideROI(rect: RectF, frameWidth: Int, frameHeight: Int): Boolean {
        val roiRect = getROIRect(frameWidth, frameHeight)
        return !RectF.intersects(roiRect, rect)
    }
}
