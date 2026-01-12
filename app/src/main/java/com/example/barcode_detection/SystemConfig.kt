package com.example.barcode_detection

data class SystemConfig(

        // Motion detection
        val motionThreshold: Float = 0.0005f,

        // Scan timing
        val scanningIntervalMs: Long = 150,
        val scanningTimeoutMs: Long = 6_000,

        // === TRACKING TIME WINDOWS ===

        // Time window để giữ history (ms)
        val trackingTimeWindowMs: Long = 2000,

        // Miss tolerance ngắn - cho phép miss do run lắc (ms)
        val shortMissToleranceMs: Long = 500,

        // Threshold miss dài - chắc chắn barcode biến mất (ms)
        val longMissThresholdMs: Long = 1500,

        // === MISS THRESHOLDS (adaptive) ===

        // Miss threshold cho giai đoạn ngắn (run lắc)
        val missThresholdShort: Int = 5, // ~750ms với 150ms interval

        // Miss threshold cho giai đoạn trung bình
        val missThresholdMedium: Int = 8, // ~1200ms

        // Legacy thresholds (không dùng nữa nhưng giữ cho tương thích)
        val missThresholdLow: Int = 3,
        val missThresholdHigh: Int = 15,

        // === POSITION THRESHOLDS ===

        // Threshold để coi như vị trí "stable" (pixels)
        val stablePositionThreshold: Float = 10f,

        // Threshold cho "drift nhỏ" - run lắc (pixels)
        val driftPositionThreshold: Float = 30f,

        // Khoảng cách để coi như box mới (pixels)
        val newBoxDistanceThreshold: Float = 100f,

        // X threshold - băng chuyền NGANG, nếu X thay đổi nhiều = box mới (pixels)
        val newBoxXThreshold: Float = 80f,

        // Y threshold - nếu Y thay đổi bất thường (camera lệch hoặc box nhảy) (pixels)
        val newBoxYThreshold: Float = 60f,

        // Motion level classification (pixels)
        val motionLowThreshold: Float = 5f,
        val motionMediumThreshold: Float = 20f,

        // Blur
        val blurThreshold: Double = 15.0,

        // PO OCR
        val poCodePattern: String = "PO[0-9]{6}",
        val ocrRegionHeightRatio: Float = 0.5f,

        // Reset cooldown
        val postResetCooldownMs: Long = 500
)
