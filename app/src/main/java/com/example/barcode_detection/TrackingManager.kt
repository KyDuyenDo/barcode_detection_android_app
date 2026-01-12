package com.example.barcode_detection

import android.graphics.RectF
import android.util.Log
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Tracking manager để phân biệt:
 * - Run lắc/dịch chuyển nhỏ (giữ tracking)
 * - Barcode thật sự biến mất (reset)
 */
class TrackingManager(private val config: SystemConfig) {

    private val TAG = "TrackingManager"

    data class TrackingHistory(
            val timestamp: Long,
            val box: RectF,
            val centerX: Float,
            val centerY: Float
    )

    private val history = mutableListOf<TrackingHistory>()
    private var lastDetectTime = 0L
    private var lastBox: RectF? = null
    private var missStreak = 0

    /** Update tracking với detection mới */
    fun updateDetection(box: RectF, currentTime: Long) {
        val cx = (box.left + box.right) / 2f
        val cy = (box.top + box.bottom) / 2f

        history.add(TrackingHistory(currentTime, box, cx, cy))

        // Giữ lại history trong time window
        val cutoff = currentTime - config.trackingTimeWindowMs
        history.removeAll { it.timestamp < cutoff }

        lastDetectTime = currentTime
        lastBox = box
        missStreak = 0

        Log.d(TAG, "Detection updated: center=($cx, $cy), history size=${history.size}")
    }

    /** Update khi miss detection Returns: true nếu nên reset, false nếu vẫn tracking */
    fun updateMiss(currentTime: Long): Boolean {
        missStreak++

        val timeSinceLastDetect = currentTime - lastDetectTime

        Log.d(TAG, "Miss #$missStreak, time since last: ${timeSinceLastDetect}ms")

        // Trường hợp 1: Miss ngắn (run lắc) - CHỜ THÊM
        if (timeSinceLastDetect < config.shortMissToleranceMs) {
            Log.d(TAG, "Short miss - likely vibration, continue tracking")
            return false
        }

        // Trường hợp 2: Miss trung bình - CHECK POSITION HISTORY
        if (timeSinceLastDetect < config.longMissThresholdMs) {
            // Nếu có history gần đây, check xem có pattern dịch chuyển không
            if (history.size >= 3) {
                val recentMovement = analyzeRecentMovement()

                if (recentMovement == MovementPattern.STABLE ||
                                recentMovement == MovementPattern.SMALL_DRIFT
                ) {
                    Log.d(TAG, "Medium miss but stable position - continue tracking")
                    return false
                }
            }
        }

        // Trường hợp 3: Miss quá lâu - CHẮC CHẮN BIẾN MẤT
        if (timeSinceLastDetect >= config.longMissThresholdMs) {
            Log.i(TAG, "Long miss (${timeSinceLastDetect}ms) - barcode disappeared, RESET")
            return true
        }

        // Trường hợp 4: Miss streak quá cao
        val missLimit =
                when {
                    timeSinceLastDetect < config.shortMissToleranceMs -> config.missThresholdShort
                    else -> config.missThresholdMedium
                }

        if (missStreak >= missLimit) {
            Log.i(TAG, "Miss streak too high ($missStreak >= $missLimit), RESET")
            return true
        }

        return false
    }

    /** Phân tích pattern di chuyển gần đây */
    private fun analyzeRecentMovement(): MovementPattern {
        if (history.size < 2) return MovementPattern.UNKNOWN

        // Tính variance của vị trí X và Y
        val centerXs = history.map { it.centerX }
        val centerYs = history.map { it.centerY }

        val xRange = centerXs.maxOrNull()!! - centerXs.minOrNull()!!
        val yRange = centerYs.maxOrNull()!! - centerYs.minOrNull()!!

        Log.d(TAG, "Movement analysis: xRange=$xRange, yRange=$yRange")

        return when {
            xRange < config.stablePositionThreshold && yRange < config.stablePositionThreshold -> {
                MovementPattern.STABLE
            }
            xRange < config.driftPositionThreshold -> {
                MovementPattern.SMALL_DRIFT
            }
            else -> {
                MovementPattern.LARGE_MOVEMENT
            }
        }
    }

    /**
     * Check nếu box mới có khả năng là box mới (không phải box cũ) Băng chuyền chạy NGANG → X thay
     * đổi nhiều = box mới
     */
    fun isLikelyNewBox(newBox: RectF): Boolean {
        val lastBox = this.lastBox ?: return true

        val oldCx = (lastBox.left + lastBox.right) / 2f
        val oldCy = (lastBox.top + lastBox.bottom) / 2f
        val newCx = (newBox.left + newBox.right) / 2f
        val newCy = (newBox.top + newBox.bottom) / 2f

        // Tính khoảng cách di chuyển
        val distance = hypot(newCx - oldCx, newCy - oldCy)

        // Băng chuyền NGANG → X thay đổi nhiều
        val xDiff = abs(newCx - oldCx)
        val yDiff = abs(newCy - oldCy)

        Log.d(TAG, "Box comparison: distance=$distance, xDiff=$xDiff, yDiff=$yDiff")

        // Box mới nếu:
        // 1. X thay đổi đáng kể (box mới trên băng chuyền)
        // 2. Di chuyển tổng thể quá xa
        // 3. Y thay đổi quá nhiều (bất thường, có thể camera bị lệch)
        return xDiff > config.newBoxXThreshold ||
                distance > config.newBoxDistanceThreshold ||
                yDiff > config.newBoxYThreshold
    }

    fun getMotionLevel(): String {
        if (history.size < 2) return "LOW"

        val recent = history.takeLast(5)
        val distances = mutableListOf<Float>()

        for (i in 1 until recent.size) {
            val dist =
                    hypot(
                            recent[i].centerX - recent[i - 1].centerX,
                            recent[i].centerY - recent[i - 1].centerY
                    )
            distances.add(dist)
        }

        val avgDist = distances.average().toFloat()

        return when {
            avgDist < config.motionLowThreshold -> "LOW"
            avgDist < config.motionMediumThreshold -> "MEDIUM"
            else -> "HIGH"
        }
    }

    fun reset() {
        Log.d(TAG, "Tracking reset")
        history.clear()
        lastDetectTime = 0L
        lastBox = null
        missStreak = 0
    }

    enum class MovementPattern {
        STABLE, // Gần như đứng yên
        SMALL_DRIFT, // Dịch chuyển nhỏ (run lắc)
        LARGE_MOVEMENT, // Di chuyển lớn
        UNKNOWN
    }
}
