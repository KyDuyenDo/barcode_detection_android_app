package com.example.barcode_detection

/**
 * State machine for the industrial barcode box counting system.
 *
 * State transitions: IDLE -> DETECTING (motion detected) DETECTING -> READY_TO_DECODE (barcode
 * detected consistently) READY_TO_DECODE -> DECODED (ML Kit decode success) READY_TO_DECODE ->
 * DETECTING (timeout or barcode lost) DECODED -> RESETTING (barcode exits camera view) RESETTING ->
 * IDLE (cleanup completed)
 */
enum class AppState {
    /** Waiting for motion to start detection */
    IDLE,

    /** Running MobileNetV2 detection, building temporal confidence */
    DETECTING,

    /** Barcode detected and stable, ready for ML Kit decoding */
    READY_TO_DECODE,

    /** Barcode successfully decoded, locked until box exits */
    DECODED,

    /** Box has exited, performing cleanup before returning to IDLE */
    RESETTING
}
