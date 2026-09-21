package io.github.sceneview.demo.demos

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import kotlinx.coroutines.joinAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.PlayArrow
import io.github.sceneview.demo.storage.CaptureProject
import io.github.sceneview.demo.storage.CaptureStorageManager
import io.github.sceneview.demo.state.SplatCaptureStateHolder
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.isActive
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.ARSessionFailure
import io.github.sceneview.ar.arcore.cameraImage
import io.github.sceneview.ar.rememberARCameraStream
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.R
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.sqrt
import kotlin.math.roundToInt
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.WindowManager

object SplatCapturePipeline {
    init {
        System.loadLibrary("splat_capture_jni")
    }

    external fun initPipeline(modelPath: String): Long
    external fun freePipeline(handle: Long)
    external fun processFrame(
        handle: Long,
        yBuf: java.nio.ByteBuffer, yRowStride: Int,
        uBuf: java.nio.ByteBuffer, uRowStride: Int, uPixelStride: Int,
        vBuf: java.nio.ByteBuffer, vRowStride: Int, vPixelStride: Int,
        width: Int, height: Int,
        depthBuf: java.nio.ByteBuffer, depthWidth: Int, depthHeight: Int,
        confBuf: java.nio.ByteBuffer,
        pointCloudBuf: java.nio.FloatBuffer, pointCount: Int,
        poseMatrix: FloatArray,
        fx: Float, fy: Float, cx: Float, cy: Float,
        imageFilePath: String, imageRelativePath: String
    ): Boolean

    external fun processDataset(handle: Long, manifestPath: String)
    external fun getPointCount(handle: Long): Int
    external fun clearPipeline(handle: Long)
    external fun getPendingFrames(handle: Long): Int
    external fun getProcessedFrames(handle: Long): Int
    external fun getGpuStatus(handle: Long): Int
    external fun getProcessingPhase(handle: Long): Int
}

data class CapturedFrameMetadata(
    val imageFile: File,
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    val camW: Int,
    val camH: Int,
    val anchor: Anchor?,
    val initialPoseMatrix: FloatArray,
    val isRollingShutterRisk: Int
)

class CaptureContext {
    var lastPose: Pose? = null
    var lastTimestampNs: Long = 0L
    var prevFramePose: Pose? = null
    var prevFrameTimestampNs: Long = 0L
    var smoothedAngularVelocity: Float = 0f
    var smoothedLinearVelocity: Float = 0f
    val capturedFrames = mutableListOf<CapturedFrameMetadata>()
    val pendingWriteJobs = java.util.Collections.synchronizedSet(mutableSetOf<kotlinx.coroutines.Job>())

    fun clearAnchors() {
        synchronized(capturedFrames) {
            capturedFrames.forEach {
                try {
                    it.anchor?.detach()
                } catch (_: Exception) {}
            }
            capturedFrames.clear()
        }
    }

    fun reset() {
        lastPose = null
        lastTimestampNs = 0L
        prevFramePose = null
        prevFrameTimestampNs = 0L
        smoothedAngularVelocity = 0f
        smoothedLinearVelocity = 0f
        clearAnchors()
        val jobs = synchronized(pendingWriteJobs) { pendingWriteJobs.toList() }
        jobs.forEach { it.cancel() }
        pendingWriteJobs.clear()
    }
}

private fun copyAssetToFile(context: Context, assetPath: String, outFile: File) {
    if (outFile.exists() && outFile.length() > 0) return
    context.assets.open(assetPath).use { inputStream ->
        FileOutputStream(outFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }
}

enum class CaptureResolution(val label: String, val height: Int, val width: Int) {
    P480("480p", 480, 640),
    P720("720p", 720, 1280),
    P1080("1080p", 1080, 1920)
}

private fun areConfigsEqual(a: CameraConfig?, b: CameraConfig?): Boolean {
    if (a === b) return true
    if (a == null || b == null) return false
    return a.imageSize == b.imageSize &&
           a.textureSize == b.textureSize &&
           a.fpsRange == b.fpsRange &&
           a.facingDirection == b.facingDirection
}

private fun getAllBackCameraConfigs(session: com.google.ar.core.Session): List<CameraConfig> {
    return runCatching {
        session.getSupportedCameraConfigs(
            CameraConfigFilter(session)
                .setFacingDirection(CameraConfig.FacingDirection.BACK)
        )
    }.getOrDefault(emptyList()).ifEmpty {
        session.getSupportedCameraConfigs(CameraConfigFilter(session))
    }.distinctBy {
        "${it.imageSize.width}x${it.imageSize.height}_${it.textureSize.width}x${it.textureSize.height}_${it.fpsRange.lower}-${it.fpsRange.upper}"
    }
}

private fun selectCameraConfig(
    session: com.google.ar.core.Session,
    targetResolution: CaptureResolution
): com.google.ar.core.CameraConfig {
    val configs = getAllBackCameraConfigs(session)
    if (configs.isEmpty()) return session.cameraConfig

    Log.i("SplatCapture", "Supported camera configs count: ${configs.size}")
    configs.forEach { c ->
        Log.i("SplatCapture", " - img=${c.imageSize.width}x${c.imageSize.height}, tex=${c.textureSize.width}x${c.textureSize.height}, fps=${c.fpsRange.lower}-${c.fpsRange.upper}")
    }

    val targetH = targetResolution.height
    val targetAspect = targetResolution.width.toFloat() / targetResolution.height.toFloat()

    fun imageAspectRatio(config: CameraConfig): Float {
        val w = maxOf(config.imageSize.width, config.imageSize.height).toFloat()
        val h = minOf(config.imageSize.width, config.imageSize.height).toFloat()
        return if (h > 0f) w / h else targetAspect
    }

    fun textureAspectRatio(config: CameraConfig): Float {
        val w = maxOf(config.textureSize.width, config.textureSize.height).toFloat()
        val h = minOf(config.textureSize.width, config.textureSize.height).toFloat()
        return if (h > 0f) w / h else targetAspect
    }

    // 1. Filter configs matching target CPU image height (the resolution of frame.cameraImage())
    val matchingConfigs = configs.filter { config ->
        val imgH = minOf(config.imageSize.width, config.imageSize.height)
        imgH == targetH
    }.ifEmpty {
        // Fallback to configs with CPU image height closest to targetH
        val minDiff = configs.minOf { config ->
            val imgH = minOf(config.imageSize.width, config.imageSize.height)
            Math.abs(imgH - targetH)
        }
        configs.filter { config ->
            val imgH = minOf(config.imageSize.width, config.imageSize.height)
            Math.abs(imgH - targetH) == minDiff
        }
    }

    // Rank candidates:
    // 1. CPU image height exact match (diff == 0)
    // 2. GPU preview texture height closest match to targetH (so preview respects selected resolution!)
    // 3. Highest max FPS (60 FPS over 30 FPS)
    // 4. CPU image aspect ratio closest to target aspect (4:3 for 480p, 16:9 for 720p/1080p)
    // 5. GPU texture aspect ratio closest to target aspect
    // 6. Highest min FPS
    val selected = matchingConfigs.minWithOrNull(
        compareBy<CameraConfig> { config ->
            val imgH = minOf(config.imageSize.width, config.imageSize.height)
            Math.abs(imgH - targetH)
        }
        .thenBy { config ->
            val texH = minOf(config.textureSize.width, config.textureSize.height)
            Math.abs(texH - targetH)
        }
        .thenByDescending { it.fpsRange.upper }
        .thenBy { config ->
            val aspectDiff = Math.abs(imageAspectRatio(config) - targetAspect)
            if (aspectDiff < 0.05f) 0f else aspectDiff * 1000f
        }
        .thenBy { config ->
            val aspectDiff = Math.abs(textureAspectRatio(config) - targetAspect)
            if (aspectDiff < 0.05f) 0f else aspectDiff * 1000f
        }
        .thenByDescending { it.fpsRange.lower }
    ) ?: matchingConfigs.first()

    Log.i("SplatCapture", "Selected config for ${targetResolution.label}: img=${selected.imageSize.width}x${selected.imageSize.height}, tex=${selected.textureSize.width}x${selected.textureSize.height}, fps=${selected.fpsRange.lower}-${selected.fpsRange.upper}")
    return selected
}

private fun getSupportedResolutions(session: com.google.ar.core.Session): Set<CaptureResolution> {
    val allConfigs = getAllBackCameraConfigs(session)

    val result = mutableSetOf<CaptureResolution>()
    for (res in CaptureResolution.values()) {
        val targetH = res.height
        // Only mark resolution as supported if the device actually provides a camera config for this CPU image size
        val hasMatch = allConfigs.any { c ->
            val imgH = minOf(c.imageSize.width, c.imageSize.height)
            imgH == targetH
        }
        if (hasMatch) {
            result.add(res)
        }
    }
    return if (result.isEmpty()) setOf(CaptureResolution.P480) else result
}

@Composable
fun ArSplatCaptureDemo(
    onBack: (() -> Unit)? = null,
    onSendToTraining: ((CaptureProject) -> Unit)? = null
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val coroutineScope = rememberCoroutineScope()

    val activity = context as? Activity
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    var supportedResolutions by remember {
        mutableStateOf(setOf(CaptureResolution.P480, CaptureResolution.P720, CaptureResolution.P1080))
    }
    var arErrorMessage by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(activity) {
        if (activity == null) return@LaunchedEffect
        runCatching {
            var availability = ArCoreApk.getInstance().checkAvailability(activity)
            var attempts = 0
            while (availability == ArCoreApk.Availability.UNKNOWN_CHECKING && attempts < 10) {
                kotlinx.coroutines.delay(200)
                availability = ArCoreApk.getInstance().checkAvailability(activity)
                attempts++
            }
            if (availability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
                arErrorMessage = Pair(
                    "AR Not Supported",
                    "This device does not meet the hardware requirements for ARCore motion tracking. 3D Gaussian Splatting dataset capture cannot be performed on this device."
                )
            }
        }
    }

    var resolutionIndex by SplatCaptureStateHolder::resolutionIndex
    val selectedResolution = when (resolutionIndex.roundToInt()) {
        0 -> CaptureResolution.P480
        1 -> CaptureResolution.P720
        else -> CaptureResolution.P1080
    }

    var isCapturing by SplatCaptureStateHolder::isCapturing
    var isGenerating by SplatCaptureStateHolder::isGenerating

    val shouldKeepScreenOn = isCapturing || isGenerating
    DisposableEffect(activity, shouldKeepScreenOn) {
        val window = activity?.window
        if (shouldKeepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, shouldKeepScreenOn) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && shouldKeepScreenOn) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    var isMovingTooFast by SplatCaptureStateHolder::isMovingTooFast
    var motionSensitivity by SplatCaptureStateHolder::motionSensitivity
    var warmupFrameCount by SplatCaptureStateHolder::warmupFrameCount
    var isExporting by SplatCaptureStateHolder::isExporting
    var isAutoFocus by SplatCaptureStateHolder::isAutoFocus
    var showDiscardDialog by remember { mutableStateOf(false) }
    var arSession by remember { mutableStateOf<com.google.ar.core.Session?>(null) }
    var activeCameraFps by SplatCaptureStateHolder::activeCameraFps

    val internalFrameCount = SplatCaptureStateHolder.internalFrameCount
    var displayFrameCount by SplatCaptureStateHolder::displayFrameCount
    var displayPointCount by SplatCaptureStateHolder::displayPointCount
    var pendingFrames by SplatCaptureStateHolder::pendingFrames
    var processedFrames by SplatCaptureStateHolder::processedFrames
    var totalFramesToProcess by SplatCaptureStateHolder::totalFramesToProcess
    var gpuStatus by SplatCaptureStateHolder::gpuStatus
    var processingPhase by SplatCaptureStateHolder::processingPhase
    var isExportReady by SplatCaptureStateHolder::isExportReady
    var projectNameInput by SplatCaptureStateHolder::projectNameInput
    var savedProject by SplatCaptureStateHolder::savedProject
    var isSavingCapture by SplatCaptureStateHolder::isSavingCapture
    val captureContext = SplatCaptureStateHolder.captureContext

    // Request Notification permission for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    val tempDir = SplatCaptureStateHolder.getTempDir(context)

    var pipelineHandle by SplatCaptureStateHolder::pipelineHandle
    var initError by SplatCaptureStateHolder::initError
    val controlsExpanded = rememberSaveable { mutableStateOf(false) }

    fun resetCaptureState() {
        SplatCaptureStateHolder.resetCaptureState(context)
    }

    LaunchedEffect(Unit) {
        SplatCaptureStateHolder.initReceiver(context)
        if (pipelineHandle == 0L) {
            withContext(Dispatchers.IO) {
                try {
                    // Copy xfeat_fp16.tflite from assets to cache
                    val modelFile = File(context.cacheDir, "xfeat_fp16.tflite")
                    if (!modelFile.exists()) {
                        context.assets.open("xfeat_fp16.tflite").use { input ->
                            FileOutputStream(modelFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    
                    val handle = SplatCapturePipeline.initPipeline(modelFile.absolutePath)
                    if (handle == 0L) {
                        throw RuntimeException("JNI pipeline handle initialization failed.")
                    }
                    withContext(Dispatchers.Main) {
                        pipelineHandle = handle
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        initError = "Error initializing JNI: ${e.message}"
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // When navigating away to another tab, pause live frame recording if active,
            // but preserve all captured frames, anchors, JNI pipeline, and files.
            isCapturing = false
        }
    }

    LaunchedEffect(isAutoFocus, arSession) {
        arSession?.let { session ->
            val config = session.config
            val newMode = if (isAutoFocus) Config.FocusMode.AUTO else Config.FocusMode.FIXED
            if (config.focusMode != newMode) {
                config.focusMode = newMode
                session.configure(config)
            }
        }
    }

    LaunchedEffect(arSession) {
        arSession?.let { session ->
            val detected = getSupportedResolutions(session)
            supportedResolutions = detected
            val maxIdx = when {
                detected.contains(CaptureResolution.P1080) -> 2f
                detected.contains(CaptureResolution.P720) -> 1f
                else -> 0f
            }
            if (resolutionIndex > maxIdx) {
                resolutionIndex = maxIdx
            }
        }
    }

    LaunchedEffect(isGenerating) {
        try {
            if (isGenerating) {
                arSession?.pause()
                arSession = null
            } else {
                arSession?.resume()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(isCapturing) {
        if (isCapturing) {
            warmupFrameCount = 0
        }
    }

    LaunchedEffect(isCapturing, isGenerating, pipelineHandle) {
        while (isActive) {
            displayFrameCount = internalFrameCount.get()
            if (pipelineHandle != 0L) {
                pendingFrames = SplatCapturePipeline.getPendingFrames(pipelineHandle)
                processedFrames = SplatCapturePipeline.getProcessedFrames(pipelineHandle)
                gpuStatus = SplatCapturePipeline.getGpuStatus(pipelineHandle)
                processingPhase = SplatCapturePipeline.getProcessingPhase(pipelineHandle)
                
                if (isGenerating && processingPhase >= 8) {
                    isGenerating = false
                    isExportReady = true
                }
                
                if (!isCapturing && !isGenerating) {
                    displayPointCount = SplatCapturePipeline.getPointCount(pipelineHandle)
                } else {
                    displayPointCount = 0
                }
            }
            kotlinx.coroutines.delay(200)
        }
    }

    LaunchedEffect(isExportReady) {
        if (isExportReady && savedProject == null && tempDir.exists()) {
            isSavingCapture = true
            try {
                val pointCount = SplatCapturePipeline.getPointCount(pipelineHandle)
                val project = CaptureStorageManager.saveCaptureZip(
                    context = context,
                    projectName = projectNameInput,
                    sourceDir = tempDir,
                    frameCount = displayFrameCount,
                    pointCount = pointCount
                )
                savedProject = project
            } catch (e: Exception) {
                android.util.Log.e("ARSplatCaptureDemo", "Auto-save capture failed: ${e.message}")
            } finally {
                isSavingCapture = false
            }
        }
    }

    var lastW by remember { mutableIntStateOf(0) }
    var lastH by remember { mutableIntStateOf(0) }

    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val zipFile = File(context.cacheDir, "export.zip")
                if (zipFile.exists()) {
                    context.contentResolver.openOutputStream(uri)?.use { outStream ->
                        zipFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                }
            }
        }
    }

    DemoScaffold(
        title = stringResource(R.string.demo_ar_splat_capture_title),
        onBack = onBack,
        controlsExpanded = controlsExpanded,
        controls = {
            Text(
                text = "Capture a dataset for Gaussian Splatting. Move the camera slowly around an object. " +
                    "Frames are automatically captured when you move 4cm or rotate 6 degrees.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Switch(
                    checked = isAutoFocus,
                    onCheckedChange = { isAutoFocus = it },
                    enabled = !isCapturing
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Auto Focus", style = MaterialTheme.typography.labelLarge)
                    if (isAutoFocus) {
                        Text("Warning: Not recommended for Gaussian Splats!", style = MaterialTheme.typography.labelSmall, color = Color.Red)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Capture Resolution", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "${selectedResolution.label} (${activeCameraFps}fps)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                val sliderEnabled = !isCapturing && !isGenerating && internalFrameCount.get() == 0 && supportedResolutions.size > 1

                Slider(
                    value = resolutionIndex,
                    onValueChange = { newVal ->
                        val candidate = when (newVal.roundToInt()) {
                            0 -> CaptureResolution.P480
                            1 -> CaptureResolution.P720
                            else -> CaptureResolution.P1080
                        }
                        if (supportedResolutions.contains(candidate)) {
                            resolutionIndex = newVal.roundToInt().toFloat()
                        } else {
                            val nearestIdx = listOf(0, 1, 2)
                                .filter { idx ->
                                    val res = when (idx) {
                                        0 -> CaptureResolution.P480
                                        1 -> CaptureResolution.P720
                                        else -> CaptureResolution.P1080
                                    }
                                    supportedResolutions.contains(res)
                                }
                                .minByOrNull { Math.abs(it - newVal.roundToInt()) } ?: 0
                            resolutionIndex = nearestIdx.toFloat()
                        }
                    },
                    valueRange = 0f..2f,
                    steps = 1,
                    enabled = sliderEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val resList = listOf(
                        CaptureResolution.P480 to 0,
                        CaptureResolution.P720 to 1,
                        CaptureResolution.P1080 to 2
                    )
                    resList.forEach { (res, idx) ->
                        val isSupported = supportedResolutions.contains(res)
                        val isSelected = resolutionIndex.roundToInt() == idx
                        Text(
                            text = if (isSupported) res.label else "${res.label} (N/A)",
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                !isSupported -> Color.Gray.copy(alpha = 0.38f)
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> Color.Gray
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.clickable(
                                enabled = isSupported && !isCapturing && !isGenerating && internalFrameCount.get() == 0
                            ) {
                                resolutionIndex = idx.toFloat()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                val sensitivityLabel = when {
                    motionSensitivity < 0.85f -> "Relaxed"
                    motionSensitivity > 1.2f -> "Strict"
                    else -> "Normal"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Motion Warning Sensitivity", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "$sensitivityLabel (${(16f / motionSensitivity).roundToInt()}°/s)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = motionSensitivity,
                    onValueChange = { motionSensitivity = it },
                    valueRange = 0.6f..1.6f,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGenerating
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Relaxed",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (motionSensitivity < 0.85f) MaterialTheme.colorScheme.primary else Color.Gray,
                        fontWeight = if (motionSensitivity < 0.85f) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable(enabled = !isGenerating) { motionSensitivity = 0.65f }
                    )
                    Text(
                        text = "Normal (Default)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (motionSensitivity in 0.85f..1.2f) MaterialTheme.colorScheme.primary else Color.Gray,
                        fontWeight = if (motionSensitivity in 0.85f..1.2f) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable(enabled = !isGenerating) { motionSensitivity = 1.0f }
                    )
                    Text(
                        text = "Strict",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (motionSensitivity > 1.2f) MaterialTheme.colorScheme.primary else Color.Gray,
                        fontWeight = if (motionSensitivity > 1.2f) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable(enabled = !isGenerating) { motionSensitivity = 1.35f }
                    )
                }
                Text(
                    text = "Controls the threshold for the 'Moving Too Fast' warning and discarding blurred frames.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (gpuStatus == -1) {
                Text(
                    text = "Running on CPU (Slow). GPU Delegate initialization failed.",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (initError != null) {
                Text(
                    text = initError ?: "",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (isExportReady) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.capture_save_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = projectNameInput,
                                onValueChange = { projectNameInput = it },
                                label = { Text(stringResource(R.string.capture_name_label)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                enabled = !isSavingCapture
                            )
                            IconButton(
                                onClick = {
                                    val project = savedProject
                                    if (project != null && projectNameInput.isNotBlank()) {
                                        coroutineScope.launch {
                                            val updated = CaptureStorageManager.renameCapture(
                                                context,
                                                project,
                                                projectNameInput.trim()
                                            )
                                            if (updated != null) {
                                                savedProject = updated
                                                Toast.makeText(context, "Renamed to ${updated.name}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Rename capture",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Capture saved to Library (${CaptureStorageManager.getCaptureFolderDisplay(context)}). You can share and export it anytime from the Library tab.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if (isCapturing) {
                    Button(
                        onClick = {
                            isCapturing = false
                            controlsExpanded.value = true
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ar_splat_capture_stop))
                    }
                } else if (internalFrameCount.get() == 0) {
                    Button(
                        onClick = {
                            resetCaptureState()
                            isCapturing = true
                            controlsExpanded.value = false
                        },
                        enabled = pipelineHandle != 0L,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ar_splat_capture_start))
                    }
                } else {
                    // Left column: New Capture (ALWAYS present here)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Button(
                            onClick = {
                                if (isGenerating || isExportReady) {
                                    showDiscardDialog = true
                                } else {
                                    resetCaptureState()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("New Capture")
                        }
                    }

                    // Right button: Generate OR Train
                    if (isExportReady) {
                        // Train Button
                        Button(
                            onClick = {
                                if (pipelineHandle == 0L) return@Button
                                val project = savedProject
                                if (project != null) {
                                    onSendToTraining?.invoke(project)
                                } else {
                                    coroutineScope.launch {
                                        try {
                                            val p = CaptureStorageManager.saveCaptureZip(
                                                context = context,
                                                projectName = projectNameInput,
                                                sourceDir = tempDir,
                                                frameCount = displayFrameCount,
                                                pointCount = displayPointCount
                                            )
                                            savedProject = p
                                            onSendToTraining?.invoke(p)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Train")
                        }
                    } else {
                        // Generate Button
                        Column(modifier = Modifier.weight(1f)) {
                            if (isGenerating) {
                                val phaseText = when (processingPhase) {
                                    0 -> "Starting..."
                                    1 -> "Extracting Features..."
                                    2 -> "Culling Matches..."
                                    3 -> "Matching & Triangulating..."
                                    4 -> "Bundle Adjustment..."
                                    5 -> "Dense Optical Flow..."
                                    6 -> "Consistency & Thinning..."
                                    7 -> "Exporting..."
                                    8 -> "Complete"
                                    else -> "Processing..."
                                }
                                Text(
                                    text = phaseText,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp)
                                )
                                androidx.compose.material3.LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                                )
                            }
                            
                            Button(
                                onClick = {
                                    if (internalFrameCount.get() > 0) {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        
                                        isGenerating = true
                                        isCapturing = false
                                        processingPhase = 0

                                        coroutineScope.launch {
                                            // Await any pending background JPEG compressions & writes
                                            val pendingJobs = synchronized(captureContext.pendingWriteJobs) {
                                                captureContext.pendingWriteJobs.toList()
                                            }
                                            pendingJobs.joinAll()
                                            totalFramesToProcess = internalFrameCount.get()

                                            // Rewrite manifest.txt using loop-closed anchor poses from ARCore's spatial graph
                                            val manifestFile = File(tempDir, "manifest.txt")
                                            manifestFile.writeText("${tempDir.absolutePath}\n0\n")
                                            synchronized(captureContext.capturedFrames) {
                                                for (frameMeta in captureContext.capturedFrames) {
                                                    val poseMatrix = FloatArray(16)
                                                    val anchor = frameMeta.anchor
                                                    if (anchor != null && anchor.trackingState == TrackingState.TRACKING) {
                                                        anchor.pose.toMatrix(poseMatrix, 0)
                                                    } else {
                                                        System.arraycopy(frameMeta.initialPoseMatrix, 0, poseMatrix, 0, 16)
                                                    }
                                                    val poseStr = poseMatrix.joinToString(" ")
                                                    manifestFile.appendText(
                                                        "${frameMeta.imageFile.absolutePath} ${frameMeta.fx} ${frameMeta.fy} ${frameMeta.cx} ${frameMeta.cy} ${frameMeta.camW} ${frameMeta.camH} $poseStr ${frameMeta.isRollingShutterRisk}\n"
                                                    )
                                                }
                                                captureContext.clearAnchors()
                                            }
        
                                            val serviceIntent = android.content.Intent(context, io.github.sceneview.demo.service.SplatProcessService::class.java).apply {
                                                action = io.github.sceneview.demo.service.SplatProcessService.ACTION_START_PROCESSING
                                                putExtra("manifest_path", File(tempDir, "manifest.txt").absolutePath)
                                                putExtra("pipeline_handle", pipelineHandle)
                                            }
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                context.startForegroundService(serviceIntent)
                                            } else {
                                                context.startService(serviceIntent)
                                            }
                                        }
                                    }
                                },
                                enabled = !isGenerating && internalFrameCount.get() > 0,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                if (isGenerating) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generating...")
                                } else {
                                    Text("Generate Point Cloud")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        val previewAspectRatio = when (selectedResolution) {
            CaptureResolution.P480 -> 480f / 640f
            CaptureResolution.P720 -> 720f / 1280f
            CaptureResolution.P1080 -> 1080f / 1920f
        }

        val density = LocalDensity.current
        val initialScreenHeightPx = activity?.windowManager?.currentWindowMetrics?.bounds?.height()?.toFloat()
            ?: context.resources.displayMetrics.heightPixels.toFloat()
        val initialTopRibbonPx = WindowInsets.statusBars.getTop(density).toFloat() + with(density) { 64.dp.toPx() }

        var topRibbonPx by remember { mutableFloatStateOf(initialTopRibbonPx) }
        var rootHeightPx by remember { mutableFloatStateOf(initialScreenHeightPx) }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onGloballyPositioned { coordinates ->
                    if (coordinates.isAttached) {
                        val root = coordinates.findRootCoordinates()
                        if (root.isAttached) {
                            rootHeightPx = root.size.height.toFloat()
                        }
                        topRibbonPx = coordinates.positionInRoot().y
                    }
                }
        ) {
            val matchHeight = (maxWidth.value / maxHeight.value) > previewAspectRatio

            Layout(
                content = {
                    if (!isGenerating) {
                        key(selectedResolution) {
                            val cameraStream = rememberARCameraStream(materialLoader)
                            ARSceneView(
                                modifier = Modifier.aspectRatio(previewAspectRatio, matchHeightConstraintsFirst = matchHeight),
                                engine = engine,
                                modelLoader = modelLoader,
                                materialLoader = materialLoader,
                                cameraStream = cameraStream,
                                planeRenderer = false,
                                sessionCameraConfig = { session ->
                                    val detected = getSupportedResolutions(session)
                                    supportedResolutions = detected
                                    val targetConfig = selectCameraConfig(session, selectedResolution)
                                    activeCameraFps = targetConfig.fpsRange.upper
                                    targetConfig
                                },
                                onSessionCreated = { session ->
                                    if (isGenerating) {
                                        try { session.pause() } catch (_: Exception) {}
                                    } else {
                                        arSession = session
                                    }
                                },
                                onSessionResumed = { session ->
                                    if (isGenerating) {
                                        try { session.pause() } catch (_: Exception) {}
                                    }
                                },
                                onSessionFailure = { failure ->
                                    when (failure) {
                                        is ARSessionFailure.DeviceNotCompatible -> {
                                            arErrorMessage = Pair(
                                                "AR Not Supported",
                                                "This device does not meet the hardware requirements for ARCore motion tracking. Splat capture cannot run on this device."
                                            )
                                        }
                                        is ARSessionFailure.ArCoreNotInstalled -> {
                                            arErrorMessage = Pair(
                                                "ARCore Not Installed",
                                                "Google Play Services for AR is required for motion tracking. Please install it from Google Play Store."
                                            )
                                        }
                                        is ARSessionFailure.ApkTooOld -> {
                                            arErrorMessage = Pair(
                                                "ARCore Update Required",
                                                "Google Play Services for AR is outdated. Please update it from Google Play Store."
                                            )
                                        }
                                        is ARSessionFailure.CameraNotAvailable -> {
                                            arErrorMessage = Pair(
                                                "Camera Unavailable",
                                                "Camera access was denied or the camera is in use by another application. Please check app permissions."
                                            )
                                        }
                                        else -> {
                                            arErrorMessage = Pair(
                                                "AR Session Error",
                                                failure.cause.localizedMessage ?: "Failed to initialize ARCore session."
                                            )
                                        }
                                    }
                                },
                                sessionConfiguration = { session, config ->
                                    config.focusMode = if (isAutoFocus) Config.FocusMode.AUTO else Config.FocusMode.FIXED
                                    config.depthMode = Config.DepthMode.DISABLED
                                    config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                                },
                                onSessionUpdated = { session, frame ->
                                    if (isGenerating) {
                                        try { session.pause() } catch (_: Exception) {}
                                        return@ARSceneView
                                    }
                                    if (arSession != session) {
                                        arSession = session
                                        activeCameraFps = session.cameraConfig.fpsRange.upper
                                    }
                        
                        if (isCapturing && frame.camera.trackingState == TrackingState.TRACKING && pipelineHandle != 0L) {
                            if (warmupFrameCount < 60) {
                                warmupFrameCount++
                                return@ARSceneView
                            }
                            
                            val currentPose = frame.camera.pose
                            val currentTimestampNs = frame.timestamp

                            // Track instantaneous velocity between consecutive AR frames with EMA smoothing
                            val prevPose = captureContext.prevFramePose
                            val prevTimestampNs = captureContext.prevFrameTimestampNs
                            if (prevPose != null && prevTimestampNs > 0L) {
                                val dt = (currentTimestampNs - prevTimestampNs).toFloat() / 1e9f
                                if (dt in 0.005f..0.2f) {
                                    val instAngVel = angleBetween(prevPose, currentPose) / dt
                                    val instLinVel = distance(prevPose, currentPose) / dt
                                    val alpha = 0.35f
                                    captureContext.smoothedAngularVelocity = captureContext.smoothedAngularVelocity * (1f - alpha) + instAngVel * alpha
                                    captureContext.smoothedLinearVelocity = captureContext.smoothedLinearVelocity * (1f - alpha) + instLinVel * alpha
                                }
                            }
                            captureContext.prevFramePose = currentPose
                            captureContext.prevFrameTimestampNs = currentTimestampNs

                            // Fast motion detection based on sensitivity slider (default 1.0f gives 16°/s and 0.32 m/s, slightly relaxed from previous 12°/s)
                            val maxAngularVelocity = 16.0f / motionSensitivity
                            val maxLinearVelocity = 0.32f / motionSensitivity
                            val rollingShutterAngularThreshold = maxAngularVelocity * 0.85f

                            val isTooFast = captureContext.smoothedAngularVelocity > maxAngularVelocity || captureContext.smoothedLinearVelocity > maxLinearVelocity
                            val wasTooFast = isMovingTooFast
                            if (isMovingTooFast != isTooFast) {
                                isMovingTooFast = isTooFast
                            }
                            
                            val lastPose = captureContext.lastPose
                            val hasDisplacement = lastPose == null || run {
                                val dist = distance(lastPose, currentPose)
                                val angle = angleBetween(lastPose, currentPose)
                                dist > 0.04f || angle > 6.0f
                            }
                            
                            val hasTimeElapsed = captureContext.lastTimestampNs == 0L ||
                                (currentTimestampNs - captureContext.lastTimestampNs) >= 150_000_000L
                            
                            // Continuous capture with recovery trigger: capture continuously without freezing when fast, and immediately capture when slowing back down
                            val isRecovery = wasTooFast && !isTooFast && hasDisplacement
                            val shouldCapture = (hasDisplacement && hasTimeElapsed) || isRecovery

                            if (shouldCapture) {
                                val isRollingShutterRisk = if (isTooFast || captureContext.smoothedAngularVelocity > rollingShutterAngularThreshold) 1 else 0
                                captureContext.lastPose = currentPose
                                captureContext.lastTimestampNs = currentTimestampNs

                                val anchor = try {
                                    session.createAnchor(currentPose)
                                } catch (e: Exception) {
                                    null
                                }
                                
                                var cameraImage: Image? = null
                                
                                try {
                                    cameraImage = frame.cameraImage()
                                    if (cameraImage != null) {
                                        val width = cameraImage.width
                                        val height = cameraImage.height
                                        val yPlane = cameraImage.planes[0]
                                        val uPlane = cameraImage.planes[1]
                                        val vPlane = cameraImage.planes[2]
                                        
                                        val poseMatrix = FloatArray(16)
                                        currentPose.toMatrix(poseMatrix, 0)
                                        
                                        val intrinsics = frame.camera.imageIntrinsics
                                        val camW = intrinsics.imageDimensions[0]
                                        val camH = intrinsics.imageDimensions[1]
                                        var fx = intrinsics.focalLength[0]
                                        var fy = intrinsics.focalLength[1]
                                        var cx = intrinsics.principalPoint[0]
                                        var cy = intrinsics.principalPoint[1]
                                        
                                        var focusDiopters = 0.0f
                                        var physicalFocalLength = 0.0f
                                        try {
                                            val metadata = frame.imageMetadata
                                            focusDiopters = metadata.getFloat(com.google.ar.core.ImageMetadata.LENS_FOCUS_DISTANCE)
                                            physicalFocalLength = metadata.getFloat(com.google.ar.core.ImageMetadata.LENS_FOCAL_LENGTH)
                                        } catch (e: Exception) {}
                                        
                                        if (focusDiopters > 0f && physicalFocalLength > 0f) {
                                            val multiplier = 1.0f / (1.0f - (focusDiopters * (physicalFocalLength / 1000.0f)))
                                            if (!multiplier.isNaN() && !multiplier.isInfinite()) {
                                                fx *= multiplier
                                                fy *= multiplier
                                            }
                                        }
                                        
                                        val timestamp = System.currentTimeMillis()
                                        val imageRelPath = "images/frame_${timestamp}.jpg"
                                        val imageFile = File(tempDir, imageRelPath)
                                        
                                        // Synchronous fast raw YUV memory copy (~0.3 ms)
                                        val nv21 = ByteArray(width * height * 3 / 2)
                                        val yBuffer = yPlane.buffer
                                        val uBuffer = uPlane.buffer
                                        val vBuffer = vPlane.buffer
                                        
                                        yBuffer.position(0)
                                        uBuffer.position(0)
                                        vBuffer.position(0)
                                        
                                        val ySize = yBuffer.remaining()
                                        yBuffer.get(nv21, 0, ySize)
                                        
                                        if (vPlane.pixelStride == 2) {
                                            // NV21 interleaved
                                            val vSize = vBuffer.remaining()
                                            vBuffer.get(nv21, ySize, Math.min(vSize, nv21.size - ySize))
                                        } else {
                                            // Planar fallback
                                            val vSize = vBuffer.remaining()
                                            val uSize = uBuffer.remaining()
                                            vBuffer.get(nv21, ySize, Math.min(vSize, nv21.size - ySize))
                                            uBuffer.get(nv21, ySize + vSize, Math.min(uSize, nv21.size - ySize - vSize))
                                        }
                                        
                                        // Release cameraImage immediately to avoid blocking ARCore camera pipeline
                                        cameraImage.close()
                                        cameraImage = null

                                        // Store metadata in memory synchronously
                                        val frameMeta = CapturedFrameMetadata(
                                            imageFile = imageFile,
                                            fx = fx,
                                            fy = fy,
                                            cx = cx,
                                            cy = cy,
                                            camW = camW,
                                            camH = camH,
                                            anchor = anchor,
                                            initialPoseMatrix = poseMatrix,
                                            isRollingShutterRisk = isRollingShutterRisk
                                        )
                                        synchronized(captureContext.capturedFrames) {
                                            captureContext.capturedFrames.add(frameMeta)
                                        }
                                        internalFrameCount.incrementAndGet()

                                        // Offload JPEG encoding and flash I/O to background IO coroutine pool
                                        val writeJob = coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
                                                FileOutputStream(imageFile).use { out ->
                                                    yuvImage.compressToJpeg(Rect(0, 0, width, height), 95, out)
                                                }
                                            } catch (e: Exception) {
                                                Log.e("SplatCapture", "Failed to compress/write JPEG $imageFile", e)
                                            }
                                        }
                                        synchronized(captureContext.pendingWriteJobs) {
                                            captureContext.pendingWriteJobs.add(writeJob)
                                        }
                                        writeJob.invokeOnCompletion {
                                            synchronized(captureContext.pendingWriteJobs) {
                                                captureContext.pendingWriteJobs.remove(writeJob)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("SplatCapture", "Error capturing AR frame", e)
                                } finally {
                                    cameraImage?.close()
                                }
                            }
                        } else if (!isCapturing && isMovingTooFast) {
                            isMovingTooFast = false
                        }
                    }
                )
            }
        }
    },
            modifier = Modifier.fillMaxSize()
        ) { measurables, constraints ->
            val placeable = measurables.firstOrNull()?.measure(constraints)
            val containerW = constraints.maxWidth
            val containerH = constraints.maxHeight

            val targetX = if (placeable != null) (containerW - placeable.width) / 2 else 0

            val screenH = if (rootHeightPx > 0f) rootHeightPx else initialScreenHeightPx
            val topRibbon = if (topRibbonPx > 0f) topRibbonPx else initialTopRibbonPx

            // Center the frame on the physical screen rather than the sub-window below top bar
            val placeableH = placeable?.height ?: 0
            val targetScreenTop = (screenH - placeableH) / 2f
            val targetLocalTop = targetScreenTop - topRibbon
            // Clamp so the frame is never hidden behind the top bar (e.g. on small screens or 16:9 tall frames)
            val maxAvailableOffset = maxOf(0f, (containerH - placeableH).toFloat())
            val finalLocalTop = maxOf(0f, minOf(targetLocalTop, maxAvailableOffset))

            layout(containerW, containerH) {
                placeable?.placeRelative(targetX, finalLocalTop.roundToInt())
            }
        }

            if (isGenerating) {
                val phaseText = when (processingPhase) {
                    0 -> "Starting..."
                    1 -> "Extracting Features..."
                    2 -> "Culling Matches..."
                    3 -> "Matching & Triangulating..."
                    4 -> "Bundle Adjustment..."
                    5 -> "Dense Optical Flow..."
                    6 -> "Consistency & Thinning..."
                    7 -> "Exporting..."
                    8 -> "Complete"
                    else -> "Processing..."
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "Generating Point Cloud",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = phaseText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${selectedResolution.label} (${activeCameraFps}fps) • " + stringResource(R.string.ar_splat_capture_frames, displayFrameCount),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isExportReady) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stringResource(R.string.ar_splat_capture_points, displayPointCount), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (isCapturing) {
                        Icon(Icons.Default.FiberManualRecord, null, tint = Color.Red, modifier = Modifier.size(12.dp))
                    } else if (isGenerating) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExportReady,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 76.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Saved to Library • Tap Train to start training",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isCapturing && isMovingTooFast,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 76.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Moving Too Fast — Slow Down",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (isCapturing) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = {
                        isCapturing = false
                        controlsExpanded.value = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop Capture", modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Stop Capture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Capture?") },
            text = { Text("Are you sure you want to discard this generated capture? All depth maps and points will be lost.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        resetCaptureState()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                Button(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    arErrorMessage?.let { (title, message) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { /* Modal: require user to tap Go Back */ },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                Button(
                    onClick = {
                        arErrorMessage = null
                        onBack?.invoke()
                    }
                ) {
                    Text("Go Back")
                }
            }
        )
    }
}

private fun distance(p1: Pose, p2: Pose): Float {
    val dx = p1.tx() - p2.tx(); val dy = p1.ty() - p2.ty(); val dz = p1.tz() - p2.tz()
    return sqrt(dx * dx + dy * dy + dz * dz)
}

private fun angleBetween(p1: Pose, p2: Pose): Float {
    val dot = p1.qx() * p2.qx() + p1.qy() * p2.qy() + p1.qz() * p2.qz() + p1.qw() * p2.qw()
    return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(dot).toDouble()))).toFloat()
}

private fun zip(directory: File, zipFile: File) {
    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
        directory.walkTopDown().forEach { file ->
            val zipFileName = file.absolutePath.removePrefix(directory.absolutePath).removePrefix(File.separator)
            if (zipFileName.isNotEmpty()) {
                val entry = ZipEntry("$zipFileName${if (file.isDirectory) "/" else ""}")
                zos.putNextEntry(entry)
                if (file.isFile) {
                    file.inputStream().use { it.copyTo(zos) }
                }
            }
        }
    }
}
