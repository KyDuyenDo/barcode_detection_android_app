package com.example.barcode_detection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.*

/**
 * Main activity orchestrating the industrial barcode box counting system. Implements event-driven
 * state machine with thermal stability for 7-8 hour operation.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // Configuration
    private val config = SystemConfig()

    // Core components
    private lateinit var stateController: StateController
    private lateinit var barcodeDetector: BarcodeDetector
    private lateinit var motionDetector: MotionDetector
    private lateinit var roiExtractor: ROIExtractor
    private lateinit var temporalVoting: TemporalVoting
    private lateinit var barcodeDecoder: BarcodeDecoder
    private lateinit var poOcrProcessor: POOCRProcessor
    private lateinit var dataLogger: DataLogger

    // UI components
    private lateinit var overlayView: OverlayView
    private lateinit var tvState: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvBarcode: TextView
    private lateinit var tvPo: TextView
    private lateinit var tvDebug: TextView

    // State tracking
    private var boxCount = 0
    private var currentBarcode: String? = null
    private var currentPO: String? = null
    private var currentBbox: android.graphics.RectF? = null
    private var frameId = 0L
    private var detectingStartTime: Long = 0L // Track when DETECTING state started
    private var lastResetTime: Long = 0L // Track when system was last reset

    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) startCamera() else tvDebug.text = "Camera permission denied"
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize UI components
        val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
        overlayView = findViewById(R.id.overlay)
        tvState = findViewById(R.id.tv_state)
        tvCount = findViewById(R.id.tv_count)
        tvBarcode = findViewById(R.id.tv_barcode)
        tvPo = findViewById(R.id.tv_po)
        tvDebug = findViewById(R.id.tv_debug)

        // Initialize core components
        initializeComponents()

        // Set up state change callback
        stateController.setStateChangeCallback { oldState, newState ->
            onStateChanged(oldState, newState)
        }

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /** Initialize all system components */
    private fun initializeComponents() {
        stateController = StateController(config)
        barcodeDetector = BarcodeDetector(this)
        barcodeDetector.init()
        motionDetector = MotionDetector(config)
        roiExtractor = ROIExtractor(config)
        temporalVoting = TemporalVoting(config)
        barcodeDecoder = BarcodeDecoder(config)
        poOcrProcessor = POOCRProcessor(config)

        // Initialize data logger
        val logDir = File(getExternalFilesDir(null), "box_logs")
        dataLogger = DataLogger(logDir)

        Log.i(TAG, "All components initialized")
    }

    /** Start camera and image analysis */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()

                    val preview =
                            Preview.Builder().build().also {
                                it.setSurfaceProvider(
                                        findViewById<PreviewView>(R.id.viewFinder).surfaceProvider
                                )
                            }

                    val imageAnalysis =
                            ImageAnalysis.Builder()
                                    .setBackpressureStrategy(
                                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                    )
                                    .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                            this,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                    )
                },
                ContextCompat.getMainExecutor(this)
        )
    }

    /** Main frame processing pipeline */
    @OptIn(ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        try {
            frameId++

            // Convert to bitmap
            val bitmap = imageProxy.toRotatedBitmap() ?: return

            // Process based on current state
            when (stateController.getState()) {
                AppState.IDLE -> {
                    // FPS throttling for IDLE state
                    if (!barcodeDetector.shouldProcess(config.minDetectionIntervalMs)) {
                        return
                    }

                    // Check cooldown period after reset to prevent immediate re-trigger
                    val timeSinceReset = System.currentTimeMillis() - lastResetTime
                    if (timeSinceReset < config.postResetCooldownMs) {
                        // Still in cooldown period, skip motion detection
                        return
                    }

                    // Check for motion to start detection
                    if (motionDetector.hasMotion(bitmap)) {
                        stateController.transitionTo(AppState.DETECTING, "Motion detected")
                    }
                }
                AppState.DETECTING -> {
                    // FPS throttling for DETECTING state
                    if (!barcodeDetector.shouldProcess(config.minDetectionIntervalMs)) {
                        return
                    }
                    processDetecting(bitmap)
                }
                AppState.READY_TO_DECODE -> {
                    // Trigger barcode decoding (once)
                    scope.launch { processDecode(bitmap) }
                }
                AppState.DECODED -> {
                    // NO FPS throttling in DECODED state - check every frame for maximum
                    // reliability
                    // This prevents false exits when barcode is still present
                    processDecoded(bitmap)
                }
                AppState.RESETTING -> {
                    // Cleanup and return to IDLE
                    resetSystem()
                    stateController.transitionTo(AppState.IDLE, "Reset complete")
                }
            }

            // Update UI
            updateUI(bitmap.width, bitmap.height)
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error", e)
        } finally {
            imageProxy.close()
        }
    }

    /** Process frame in DETECTING state */
    private fun processDetecting(bitmap: Bitmap) {
        // Check for timeout - if we've been detecting for too long without finding barcode
        val detectingDuration = System.currentTimeMillis() - detectingStartTime
        if (detectingDuration > config.detectingTimeoutMs) {
            Log.w(TAG, "Detection timeout after ${detectingDuration}ms - returning to IDLE")
            stateController.transitionTo(AppState.IDLE, "Detection timeout")
            return
        }

        // Run MobileNetV2 detection with LOWER threshold for easier activation
        // This makes it easier to transition to READY_TO_DECODE state
        val detections = barcodeDetector.detect(bitmap, config.detectingThreshold)

        // Temporal voting
        val result =
                if (detections.isNotEmpty()) {
                    currentBbox = detections[0].rect
                    temporalVoting.processFrame(true, detections[0].rect)
                } else {
                    currentBbox = null // Clear bbox when no detection
                    temporalVoting.processFrame(false, null)
                }

        // Check voting result
        when (result) {
            VotingResult.STABLE -> {
                stateController.transitionTo(
                        AppState.READY_TO_DECODE,
                        "Barcode stable (detect=${temporalVoting.getDetectCount()})"
                )
            }
            VotingResult.ABSENT -> {
                stateController.transitionTo(AppState.IDLE, "No barcode detected")
            }
            else -> {
                // Continue detecting
            }
        }
    }

    /** Process barcode decoding (called once in READY_TO_DECODE state) */
    private suspend fun processDecode(bitmap: Bitmap) {
        // Prevent multiple decode calls
        if (stateController.getState() != AppState.READY_TO_DECODE) {
            return
        }

        try {
            // Decode barcode
            val decodeResult = barcodeDecoder.decode(bitmap, currentBbox)

            if (decodeResult != null) {
                currentBarcode = decodeResult.value
                currentBbox = decodeResult.boundingBox ?: currentBbox

                // Transition to DECODED (locks the state)
                stateController.transitionTo(
                        AppState.DECODED,
                        "Barcode decoded: ${decodeResult.value}"
                )

                // Run PO OCR
                currentBbox?.let { bbox ->
                    val poResult = poOcrProcessor.extractPO(bitmap, bbox)
                    currentPO = poResult?.poCode

                    // Log the box record
                    val record =
                            dataLogger.createRecord(
                                    barcode = currentBarcode!!,
                                    po = currentPO,
                                    poStatus = poResult?.status ?: POStatus.MISSING,
                                    cameraId = config.cameraId,
                                    frameId = frameId.toString()
                            )
                    dataLogger.logRecord(record)

                    // Increment count
                    boxCount++
                }
            } else {
                // Decode failed - return to DETECTING
                Log.w(TAG, "Barcode decode failed")
                stateController.transitionTo(AppState.DETECTING, "Decode failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decode error", e)
            stateController.transitionTo(AppState.DETECTING, "Decode error")
        }
    }

    /** Process frame in DECODED state (check for exit) */
    private fun processDecoded(bitmap: Bitmap) {
        // Run detection with LOWER threshold to be more lenient in exit detection
        // This prevents premature exits when barcode is still visible but at challenging
        // angle/distance
        val detections = barcodeDetector.detect(bitmap, config.exitDetectionThreshold)
        val hasBarcode = detections.isNotEmpty()

        // Log detection status for debugging
        Log.d(
                TAG,
                "DECODED state: hasBarcode=$hasBarcode, detections=${detections.size}, missCount=${temporalVoting.getMissCount()}, threshold=${config.exitDetectionThreshold}"
        )

        if (hasBarcode && detections.isNotEmpty()) {
            Log.d(
                    TAG,
                    "  → Barcode detected: score=${detections[0].score}, bbox=${detections[0].rect}"
            )
        } else {
            Log.w(TAG, "  → NO barcode detected! missCount will increment")
        }

        // Check for exit - pass true when barcode IS detected, false when NOT detected
        // checkExit will increment missCount when detected=false (barcode disappeared)
        if (temporalVoting.checkExit(hasBarcode)) {
            Log.w(
                    TAG,
                    "EXIT TRIGGERED! Barcode exited after ${temporalVoting.getMissCount()} misses"
            )
            stateController.transitionTo(AppState.RESETTING, "Barcode exited")
        }
    }

    /** Reset system for next box */
    private fun resetSystem() {
        temporalVoting.reset()
        motionDetector.reset()
        currentBarcode = null
        currentPO = null
        currentBbox = null
        lastResetTime = System.currentTimeMillis() // Record reset time for cooldown
        Log.i(TAG, "System reset complete")
    }

    /** Handle state changes */
    private fun onStateChanged(oldState: AppState, newState: AppState) {
        Log.i(TAG, "State: $oldState -> $newState")

        // Record time when entering DETECTING state
        if (newState == AppState.DETECTING) {
            detectingStartTime = System.currentTimeMillis()
        }

        runOnUiThread {
            tvState.text = newState.name

            // Update state indicator color
            val color =
                    when (newState) {
                        AppState.IDLE -> android.graphics.Color.GRAY
                        AppState.DETECTING -> android.graphics.Color.YELLOW
                        AppState.READY_TO_DECODE -> android.graphics.Color.GREEN
                        AppState.DECODED -> android.graphics.Color.CYAN
                        AppState.RESETTING -> android.graphics.Color.MAGENTA
                    }
            tvState.setTextColor(color)
        }
    }

    /** Update UI with current state */
    private fun updateUI(frameWidth: Int, frameHeight: Int) {
        runOnUiThread {
            // Update count
            tvCount.text = "Count: $boxCount"

            // Update barcode/PO display
            tvBarcode.text = "Barcode: ${currentBarcode ?: "-"}"
            tvPo.text = "PO: ${currentPO ?: "-"}"

            // Update debug info
            val state = stateController.getState()
            val debugInfo =
                    when (state) {
                        AppState.IDLE -> "Waiting for motion..."
                        AppState.DETECTING ->
                                "Detecting... (${temporalVoting.getDetectCount()}/${config.detectCountThreshold})"
                        AppState.READY_TO_DECODE -> "Decoding barcode..."
                        AppState.DECODED -> "Box counted! Waiting for exit..."
                        AppState.RESETTING -> "Resetting..."
                    }
            tvDebug.text = debugInfo

            // Update overlay
            val detections =
                    if (state == AppState.DETECTING ||
                                    state == AppState.READY_TO_DECODE ||
                                    state == AppState.DECODED
                    ) {
                        currentBbox?.let { listOf(BarcodeDetector.DetectionResult(it, 1.0f)) }
                                ?: emptyList()
                    } else {
                        emptyList()
                    }

            overlayView.setResults(detections, frameWidth, frameHeight, state)
            overlayView.setROI(roiExtractor.getROIRect(frameWidth, frameHeight))
        }
    }

    /** Convert ImageProxy to rotated Bitmap */
    @ExperimentalGetImage
    private fun ImageProxy.toRotatedBitmap(): Bitmap? {
        val bitmap = toBitmap() ?: return null
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) return bitmap

        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scope.cancel()
        barcodeDecoder.close()
        poOcrProcessor.close()
        dataLogger.flushToFile()
        Log.i(TAG, "Total boxes counted: $boxCount")
    }
}
