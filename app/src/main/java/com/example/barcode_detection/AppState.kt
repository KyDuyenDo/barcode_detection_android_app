package com.example.barcode_detection

enum class AppState {
    IDLE,       // chỉ phát hiện chuyển động
    SCANNING,   // bật ML Kit để tìm barcode
    DECODED,    // barcode đang còn trong khung
    RESETTING   // reset hệ thống
}
