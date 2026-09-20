package io.github.sceneview.demo.state

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.sceneview.demo.demos.CaptureContext
import io.github.sceneview.demo.demos.SplatCapturePipeline
import io.github.sceneview.demo.storage.CaptureProject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Retained state holder for AR Splat Capture.
 * Lives across tab switches so captured frames, anchor poses, JNI pipeline state,
 * and generation/export progress are never lost when navigating between tabs.
 */
object SplatCaptureStateHolder {
    var pipelineHandle by mutableStateOf(0L)
    var initError by mutableStateOf<String?>(null)

    var isCapturing by mutableStateOf(false)
    var isGenerating by mutableStateOf(false)
    var isExportReady by mutableStateOf(false)
    var isExporting by mutableStateOf(false)

    val internalFrameCount = AtomicInteger(0)
    var displayFrameCount by mutableIntStateOf(0)
    var displayPointCount by mutableIntStateOf(0)
    var pendingFrames by mutableIntStateOf(0)
    var processedFrames by mutableIntStateOf(0)
    var totalFramesToProcess by mutableIntStateOf(0)
    var gpuStatus by mutableIntStateOf(0)
    var processingPhase by mutableIntStateOf(0)

    val captureContext = CaptureContext()

    var resolutionIndex by mutableFloatStateOf(1f)
    var isAutoFocus by mutableStateOf(true)
    var activeCameraFps by mutableIntStateOf(30)
    var isMovingTooFast by mutableStateOf(false)
    var motionSensitivity by mutableFloatStateOf(1.0f)
    var warmupFrameCount by mutableIntStateOf(0)

    var projectNameInput by mutableStateOf(
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).let { "Scan_${it.format(Date())}" }
    )
    var savedProject by mutableStateOf<CaptureProject?>(null)
    var isSavingCapture by mutableStateOf(false)

    private var receiverRegistered = false

    fun getTempDir(context: Context): File {
        val dir = File(context.cacheDir, "splat_capture")
        if (!dir.exists()) {
            dir.mkdirs()
            File(dir, "images").mkdirs()
        }
        return dir
    }

    fun initReceiver(context: Context) {
        if (receiverRegistered) return
        receiverRegistered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == "io.github.sceneview.demo.SPLAT_PROCESSING_COMPLETE") {
                    isGenerating = false
                    isExportReady = true
                }
            }
        }
        val filter = IntentFilter("io.github.sceneview.demo.SPLAT_PROCESSING_COMPLETE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.applicationContext.registerReceiver(receiver, filter)
        }
    }

    fun resetCaptureState(context: Context) {
        if (pipelineHandle != 0L) {
            SplatCapturePipeline.clearPipeline(pipelineHandle)
        }
        try {
            context.stopService(Intent(context, io.github.sceneview.demo.service.SplatProcessService::class.java))
        } catch (_: Exception) {}
        internalFrameCount.set(0)
        displayFrameCount = 0
        displayPointCount = 0
        processingPhase = 0
        pendingFrames = 0
        processedFrames = 0
        warmupFrameCount = 0
        isMovingTooFast = false
        captureContext.reset()
        val tempDir = File(context.cacheDir, "splat_capture")
        tempDir.deleteRecursively()
        tempDir.mkdirs()
        File(tempDir, "images").mkdirs()
        val zipFile = File(context.cacheDir, "export.zip")
        if (zipFile.exists()) {
            zipFile.delete()
        }
        isGenerating = false
        isExportReady = false
        isCapturing = false
        savedProject = null
        projectNameInput = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).let { "Scan_${it.format(Date())}" }
        isSavingCapture = false
        System.gc()
    }
}
