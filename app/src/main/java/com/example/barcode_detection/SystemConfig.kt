package com.example.barcode_detection

data class SystemConfig(

    // ===== Motion =====
    val motionThreshold: Float = 0.02f,

    // ===== Scanning =====
    val scanningTimeoutMs: Long = 5_000,
    val scanningIntervalMs: Long = 120,

    // ===== Exit detection =====
    val exitMissThreshold: Int = 5,

    // ===== Cooldown =====
    val postResetCooldownMs: Long = 800,

    // ===== PO OCR (BỔ SUNG) =====
    val poCodePattern: String = "PO[0-9]{6}",
    val ocrRegionHeightRatio: Float = 0.5f
)
