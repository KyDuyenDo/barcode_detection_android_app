package com.example.barcode_detection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private val TAG = "BarcodeApp"
    private val config = SystemConfig()

    private lateinit var state: StateController
    private lateinit var motion: MotionDetector
    private lateinit var blur: BlurDetector
    private lateinit var decoder: BarcodeDecoder
    private lateinit var ocr: POOCRProcessor
    private lateinit var overlay: OverlayView
    private lateinit var logger: DataLogger
    private lateinit var tracker: TrackingManager

    // UI elements
    private lateinit var tvState: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvBarcode: TextView
    private lateinit var tvPO: TextView
    private lateinit var tvDebug: TextView

    private val executor = Executors.newSingleThreadExecutor()

    private var bbox: RectF? = null
    private var lastCenterX = 0f
    private var lastCenterY = 0f
    private var motionLevel = "LOW"

    private var missCount = 0
    private var lastScanTime = 0L
    private var frameCount = 0

    private var currentBarcode: String? = null
    private var currentPO: String? = null
    private var boxCount = 0
    private var conveyorDirection = "" // "→" or "←" hoặc ""

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                    isGranted: Boolean ->
                if (isGranted) {
                    Log.i(TAG, "Camera permission granted")
                    startCamera()
                } else {
                    Log.e(TAG, "Camera permission denied")
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind UI elements
        overlay = findViewById(R.id.overlay)
        tvState = findViewById(R.id.tv_state)
        tvCount = findViewById(R.id.tv_count)
        tvBarcode = findViewById(R.id.tv_barcode)
        tvPO = findViewById(R.id.tv_po)
        tvDebug = findViewById(R.id.tv_debug)

        // Initialize components
        state = StateController()
        motion = MotionDetector(config)
        blur = BlurDetector(config)
        decoder = BarcodeDecoder()
        ocr = POOCRProcessor(config)
        tracker = TrackingManager(config)

        // Initialize logger in background to avoid ANR
        executor.execute {
            val logDir = File(getExternalFilesDir(null), "box_logs")
            logger = DataLogger(logDir)
            Log.i(TAG, "DataLogger initialized at: ${logDir.absolutePath}")
        }

        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "App started - Config:")
        Log.i(TAG, "  motionThreshold: ${config.motionThreshold}")
        Log.i(TAG, "  blurThreshold: ${config.blurThreshold}")
        Log.i(TAG, "  scanningIntervalMs: ${config.scanningIntervalMs}")
        Log.i(TAG, "  Initial state: ${state.getState()}")
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        updateUI()
        checkCameraPermission()
    }

    override fun onResume() {
        super.onResume()
        // Force reset to IDLE when app resumes
        Log.i(TAG, "🔄 onResume - Resetting to IDLE")
        resetSystem()
    }

    // =========================================================
    // Camera setup
    // =========================================================
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        Log.d(TAG, "Starting camera...")
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener(
                {
                    val provider = providerFuture.get()
                    Log.d(TAG, "Camera provider obtained")

                    val preview =
                            Preview.Builder().build().apply {
                                setSurfaceProvider(
                                        findViewById<PreviewView>(R.id.viewFinder).surfaceProvider
                                )
                            }

                    val analysis =
                            ImageAnalysis.Builder()
                                    .setBackpressureStrategy(
                                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                    )
                                    .build()

                    analysis.setAnalyzer(executor) { proxy -> analyzeFrame(proxy) }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                            this,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                    )

                    Log.i(TAG, "✅ Camera started successfully")
                },
                ContextCompat.getMainExecutor(this)
        )
    }

    // =========================================================
    // Frame analysis (FULL DEBUG)
    // =========================================================
    private fun analyzeFrame(proxy: ImageProxy) {
        try {
            frameCount++
            val currentState = state.getState()

            // Log every 30 frames to avoid spam
            if (frameCount % 30 == 0) {
                Log.d(TAG, "═══ Frame #$frameCount ═══ State: $currentState")
            }

            // 1️⃣ MOTION DETECTION (Y plane, BEFORE bitmap conversion)
            if (currentState == AppState.IDLE) {
                val hasMotion = motion.hasMotion(proxy)

                // Always log motion detection attempts in IDLE
                Log.d(TAG, "[IDLE] Motion check: $hasMotion")

                if (hasMotion) {
                    Log.i(TAG, "🔥 MOTION DETECTED! IDLE → SCANNING")
                    state.transitionTo(AppState.SCANNING, "motion")
                }
            } else {
                // Log if we're NOT in IDLE (this is the problem!)
                if (frameCount % 30 == 0) {
                    Log.w(
                            TAG,
                            "⚠️ NOT in IDLE state, skipping motion detection (current: $currentState)"
                    )
                }
            }

            // 2️⃣ Convert to bitmap ONCE (for barcode / blur / OCR)
            val bitmap = proxy.toBitmapCorrect()
            Log.d(TAG, "[BITMAP] Size: ${bitmap.width}x${bitmap.height}")

            overlay.setImageInfo(bitmap.width, bitmap.height)

            when (state.getState()) {
                AppState.SCANNING -> {
                    Log.d(TAG, "[SCANNING] Processing frame...")

                    // Check blur (but don't return early)
                    val isBlurry = blur.isBlurry(bitmap)
                    Log.d(TAG, "[SCANNING] Blur check: $isBlurry")

                    if (isBlurry) {
                        Log.w(TAG, "[SCANNING] ❌ Frame too blurry, skipping")
                        runOnUiThread { overlay.update(bbox, state.getState(), motionLevel) }
                        return@analyzeFrame
                    }

                    // Check interval
                    val now = System.currentTimeMillis()
                    val timeSinceLastScan = now - lastScanTime

                    if (timeSinceLastScan < config.scanningIntervalMs) {
                        Log.d(
                                TAG,
                                "[SCANNING] ⏱️ Too soon (${timeSinceLastScan}ms < ${config.scanningIntervalMs}ms)"
                        )
                        runOnUiThread { overlay.update(bbox, state.getState(), motionLevel) }
                        return@analyzeFrame
                    }

                    Log.d(TAG, "[SCANNING] 🔍 Attempting barcode scan...")
                    lastScanTime = now
                    val box = decoder.scan(bitmap)

                    if (box != null) {
                        Log.i(TAG, "[SCANNING] ✅✅✅ BARCODE FOUND! Box: $box")
                        bbox = box
                        lastCenterX = (box.left + box.right) / 2f
                        lastCenterY = (box.top + box.bottom) / 2f
                        missCount = 0

                        Log.d(TAG, "[SCANNING] 📄 Extracting PO code...")
                        val po = ocr.extractPO(bitmap, box)
                        Log.i(TAG, "[SCANNING] PO result: ${po ?: "NOT FOUND"}")

                        // Get barcode value (need to scan again to get raw value)
                        val barcodeValue = getBarcodeValue(bitmap)
                        currentBarcode = barcodeValue
                        currentPO = po

                        if (barcodeValue != null) {
                            boxCount++

                            // Log the box record
                            if (::logger.isInitialized) {
                                val record =
                                        logger.createRecord(
                                                barcode = barcodeValue,
                                                po = po,
                                                poStatus =
                                                        if (po != null) POStatus.CONFIRMED
                                                        else POStatus.MISSING,
                                                cameraId = "0",
                                                frameId = "frame_$frameCount"
                                        )
                                logger.logRecord(record)
                            } else {
                                Log.w(TAG, "Logger not initialized yet, skipping log")
                            }

                            Log.i(TAG, "📦 BOX #$boxCount: $barcodeValue (PO: ${po ?: "N/A"})")
                        }

                        state.transitionTo(AppState.DECODED, "barcode detected")
                        runOnUiThread { updateUI() }
                    } else {
                        Log.w(TAG, "[SCANNING] ❌ No barcode detected in this frame")
                    }
                }
                AppState.DECODED -> {
                    val now = System.currentTimeMillis()
                    val timeSinceLastScan = now - lastScanTime

                    if (timeSinceLastScan < config.scanningIntervalMs) {
                        if (frameCount % 30 == 0) {
                            Log.d(TAG, "[DECODED] Waiting interval (${timeSinceLastScan}ms)")
                        }
                        runOnUiThread { overlay.update(bbox, state.getState(), motionLevel) }
                        return@analyzeFrame
                    }

                    lastScanTime = now

                    val box = decoder.scan(bitmap)
                    if (box != null) {
                        // Barcode vẫn còn - update tracking
                        tracker.updateDetection(box, now)

                        // Check xem có phải box mới không
                        if (tracker.isLikelyNewBox(box)) {
                            val oldCx = lastCenterX
                            val newCx = (box.left + box.right) / 2f
                            val xDiff = newCx - oldCx
                            val direction = if (xDiff > 0) "→" else "←"

                            Log.i(
                                    TAG,
                                    "[DECODED] 🆕 NEW BOX DETECTED! Direction: $direction, xDiff=${abs(xDiff).toInt()}px"
                            )

                            // Log box cũ trước khi reset (nếu có)
                            if (currentBarcode != null) {
                                Log.i(TAG, "[DECODED] ✅ Previous box completed: $currentBarcode")
                            }

                            resetSystem()
                            return@analyzeFrame
                        }

                        // Cùng box - update vị trí
                        val cx = (box.left + box.right) / 2f
                        val cy = (box.top + box.bottom) / 2f

                        // Detect conveyor direction
                        if (lastCenterX > 0) {
                            val xDiff = cx - lastCenterX
                            if (abs(xDiff) > 2f) { // Chỉ update nếu di chuyển rõ ràng
                                conveyorDirection = if (xDiff > 0) "→" else "←"
                            }
                        }

                        motionLevel = tracker.getMotionLevel()

                        if (frameCount % 30 == 0) {
                            Log.d(
                                    TAG,
                                    "[DECODED] Tracking OK, motion=$motionLevel, dir=$conveyorDirection"
                            )
                        }

                        lastCenterX = cx
                        lastCenterY = cy
                        bbox = box
                        missCount = 0
                    } else {
                        // Miss detection - check với tracking manager
                        val shouldReset = tracker.updateMiss(now)
                        missCount++

                        Log.w(TAG, "[DECODED] Miss #$missCount, shouldReset=$shouldReset")

                        if (shouldReset) {
                            Log.e(TAG, "[DECODED] ⚠️ Barcode truly disappeared! Resetting...")
                            resetSystem()
                        }
                    }
                }
                else -> {
                    if (frameCount % 30 == 0) {
                        Log.d(TAG, "[${state.getState()}] No action in this state")
                    }
                }
            }

            runOnUiThread {
                overlay.update(bbox, state.getState(), motionLevel)
                updateUI()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR in analyzeFrame", e)
        } finally {
            // Always close proxy
            proxy.close()
        }
    }

    // =========================================================
    // HELPER: Get barcode raw value
    // =========================================================
    private fun getBarcodeValue(bitmap: Bitmap): String? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val scanner = BarcodeScanning.getClient()
            val task = scanner.process(image)
            Tasks.await(task)
            val barcodes = task.result
            barcodes?.firstOrNull()?.rawValue
        } catch (e: Exception) {
            Log.e(TAG, "Error getting barcode value", e)
            null
        }
    }

    // =========================================================
    // UI UPDATE
    // =========================================================
    private fun updateUI() {
        val currentState = state.getState()

        // Update state with color
        tvState.text = currentState.name
        tvState.setBackgroundColor(
                when (currentState) {
                    AppState.IDLE -> Color.parseColor("#80808080") // Gray
                    AppState.SCANNING -> Color.parseColor("#80FFA500") // Orange
                    AppState.DECODED -> Color.parseColor("#8000FF00") // Green
                    else -> Color.parseColor("#80000000") // Black
                }
        )

        // Update count
        tvCount.text = "Count: $boxCount"

        // Update barcode
        tvBarcode.text = "Barcode: ${currentBarcode ?: "-"}"

        // Update PO
        tvPO.text = "PO: ${currentPO ?: "-"}"
        tvPO.setTextColor(if (currentPO != null) Color.GREEN else Color.RED)

        // Update debug info
        val debugText =
                when (currentState) {
                    AppState.IDLE -> "Waiting for motion..."
                    AppState.SCANNING -> "Scanning for barcode..."
                    AppState.DECODED -> {
                        val dir = if (conveyorDirection.isNotEmpty()) " $conveyorDirection" else ""
                        "Tracking$dir (Motion: $motionLevel, Miss: $missCount)"
                    }
                    else -> "Unknown state"
                }
        tvDebug.text = debugText
    }

    // =========================================================
    // RESET
    // =========================================================
    private fun resetSystem() {
        Log.i(TAG, "🔄 RESET → IDLE")
        bbox = null
        missCount = 0
        currentBarcode = null
        currentPO = null
        conveyorDirection = ""
        motion.reset()
        tracker.reset()
        state.transitionTo(AppState.IDLE, "barcode lost")
        runOnUiThread { updateUI() }
    }

    // =========================================================
    // YUV → RGB (JPEG-based, safe)
    // =========================================================
    private fun ImageProxy.toBitmapCorrect(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
        val jpegBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "App destroyed - Total boxes: $boxCount")
        if (::logger.isInitialized) {
            logger.flushToFile()
        }
        executor.shutdown()
        decoder.close()
    }
}
