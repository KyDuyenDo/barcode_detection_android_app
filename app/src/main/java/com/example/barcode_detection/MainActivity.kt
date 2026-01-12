package com.example.barcode_detection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val config = SystemConfig()

    private lateinit var state: StateController
    private lateinit var motion: MotionDetector
    private lateinit var decoder: BarcodeDecoder
    private lateinit var overlay: OverlayView

    private val executor = Executors.newSingleThreadExecutor()

    private var bbox: RectF? = null
    private var miss = 0
    private var lastScan = 0L
    private var scanStart = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlay = findViewById(R.id.overlay)

        state = StateController()
        motion = MotionDetector(config)
        decoder = BarcodeDecoder()

        startCamera()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(executor) { proxy ->
                analyze(proxy)
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(proxy: ImageProxy) {
        val bitmap = proxy.toBitmap()
        proxy.close()
        if (bitmap == null) return

        overlay.setImageInfo(bitmap.width, bitmap.height)

        when (state.getState()) {

            AppState.IDLE -> {
                if (motion.hasMotion(bitmap)) {
                    scanStart = System.currentTimeMillis()
                    state.transitionTo(AppState.SCANNING, "motion")
                }
            }

            AppState.SCANNING -> {
                val now = System.currentTimeMillis()
                if (now - scanStart > config.scanningTimeoutMs) {
                    reset()
                    return
                }
                if (now - lastScan < config.scanningIntervalMs) return

                lastScan = now
                val r = decoder.scanBlocking(bitmap)
                if (r != null) {
                    bbox = r.boundingBox
                    miss = 0
                    state.transitionTo(AppState.DECODED, "found")
                }
            }

            AppState.DECODED -> {
                val now = System.currentTimeMillis()
                if (now - lastScan < config.scanningIntervalMs) return

                lastScan = now
                val r = decoder.scanBlocking(bitmap)
                if (r != null) {
                    bbox = r.boundingBox
                    miss = 0
                } else {
                    miss++
                    if (miss >= config.exitMissThreshold) {
                        reset()
                    }
                }
            }

            AppState.RESETTING -> {}
        }

        runOnUiThread {
            overlay.setState(state.getState())
            overlay.setBbox(bbox)
        }
    }

    private fun reset() {
        bbox = null
        miss = 0
        motion.reset()
        state.transitionTo(AppState.IDLE, "reset")
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        decoder.close()
    }
}
