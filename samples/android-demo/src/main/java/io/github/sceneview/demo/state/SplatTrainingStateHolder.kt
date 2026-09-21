package io.github.sceneview.demo.state

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import io.github.sceneview.demo.service.SplatTrainingService
import io.github.sceneview.demo.storage.TrainedSplat
import io.github.sceneview.demo.storage.TrainedSplatStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Retained state holder for Splat Training.
 * Lives across tab switches so training progress, status, and dataset selections
 * are never reset when navigating between tabs.
 *
 * Training execution is delegated to [SplatTrainingService] running in an isolated
 * process (:training), ensuring zero UI stutter and enabling instant cancellation.
 */
object SplatTrainingStateHolder {
    private const val TAG = "SplatTrainingState"
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var datasetUri by mutableStateOf<Uri?>(null)
    var datasetName by mutableStateOf<String?>(null)
    var iterationsInput by mutableStateOf("1000")

    var isTraining by mutableStateOf(false)
    var currentIteration by mutableIntStateOf(0)
    var totalIterations by mutableIntStateOf(1000)
    var trainingElapsedMs by mutableLongStateOf(0L)
    var estimatedRemainingMs by mutableLongStateOf(0L)
    var evalPsnr by mutableFloatStateOf(0f)
    var evalSsim by mutableFloatStateOf(0f)
    var statusText by mutableStateOf("Ready to train")
    var trainingCompleted by mutableStateOf(false)

    var activeSplatId by mutableStateOf<String?>(null)
    var currentExportName by mutableStateOf("")

    private data class ProgressSample(val iteration: Int, val elapsedMs: Long)
    private val progressSamples = ArrayDeque<ProgressSample>()

    private var receiverRegistered = false

    private val trainingBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                SplatTrainingService.BROADCAST_TRAINING_PROGRESS -> {
                    if (!isTraining) return
                    val iter = intent.getIntExtra("iteration", 0)
                    val total = intent.getIntExtra("total", totalIterations)
                    val elapsedMs = intent.getLongExtra("elapsed_ms", 0L)

                    currentIteration = iter
                    totalIterations = total
                    trainingElapsedMs = elapsedMs
                    statusText = "Training…"

                    if (iter >= total) {
                        estimatedRemainingMs = 0L
                        progressSamples.clear()
                    } else {
                        if (progressSamples.isNotEmpty() && progressSamples.last().iteration == iter) {
                            progressSamples.removeLast()
                        }
                        progressSamples.addLast(ProgressSample(iter, elapsedMs))

                        // Maintain sliding window across the last ~30 iterations to account for non-linear training speed
                        while (progressSamples.size > 2 && (iter - progressSamples[1].iteration >= 30)) {
                            progressSamples.removeFirst()
                        }

                        val oldest = progressSamples.first()
                        val deltaIter = iter - oldest.iteration
                        val deltaMs = elapsedMs - oldest.elapsedMs
                        if (deltaIter > 0 && deltaMs > 0) {
                            val msPerIter = deltaMs.toDouble() / deltaIter
                            val remainingIters = (total - iter).coerceAtLeast(0)
                            estimatedRemainingMs = (remainingIters * msPerIter).toLong()
                        }
                    }
                }
                SplatTrainingService.BROADCAST_TRAINING_EVAL -> {
                    evalPsnr = intent.getFloatExtra("psnr", 0f)
                    evalSsim = intent.getFloatExtra("ssim", 0f)
                }
                SplatTrainingService.BROADCAST_TRAINING_COMPLETE -> {
                    statusText = "Finished!"
                    isTraining = false
                    trainingCompleted = true
                    estimatedRemainingMs = 0L
                    progressSamples.clear()

                    val export = intent.getStringExtra("export_name") ?: currentExportName
                    val splatFile = File(context.applicationContext.filesDir, export)
                    if (splatFile.exists() && splatFile.length() > 0L) {
                        val newSplat = TrainedSplat(
                            id = activeSplatId ?: System.currentTimeMillis().toString(),
                            datasetName = datasetName ?: "Unknown Dataset",
                            iterations = totalIterations,
                            elapsedMs = trainingElapsedMs,
                            psnr = evalPsnr,
                            ssim = evalSsim,
                            status = "Finished!",
                            exportName = export,
                            timestamp = System.currentTimeMillis(),
                            currentIteration = totalIterations
                        )
                        val existing = TrainedSplatStorage.loadTrainedSplats(context.applicationContext)
                        TrainedSplatStorage.saveTrainedSplats(context.applicationContext, listOf(newSplat) + existing)
                    }
                    activeSplatId = null
                }
                SplatTrainingService.BROADCAST_TRAINING_ERROR -> {
                    val errMsg = intent.getStringExtra("error_message") ?: "Training failed"
                    Log.e(TAG, "Training error broadcast: $errMsg")
                    statusText = "Error: $errMsg"
                    isTraining = false
                    activeSplatId = null
                }
            }
        }
    }

    fun initListener(context: Context) {
        if (receiverRegistered) return
        receiverRegistered = true

        val filter = IntentFilter().apply {
            addAction(SplatTrainingService.BROADCAST_TRAINING_PROGRESS)
            addAction(SplatTrainingService.BROADCAST_TRAINING_EVAL)
            addAction(SplatTrainingService.BROADCAST_TRAINING_COMPLETE)
            addAction(SplatTrainingService.BROADCAST_TRAINING_ERROR)
        }

        ContextCompat.registerReceiver(
            context.applicationContext,
            trainingBroadcastReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun startTraining(context: Context) {
        val uri = datasetUri ?: return
        if (isTraining) return

        initListener(context)

        val iters = iterationsInput.toIntOrNull()?.coerceIn(100, 10000) ?: 1000
        currentIteration = 0
        totalIterations = iters
        trainingElapsedMs = 0L
        estimatedRemainingMs = 0L
        progressSamples.clear()
        evalPsnr = 0f
        evalSsim = 0f
        isTraining = true
        trainingCompleted = false
        statusText = "Initializing training…"

        val timestamp = System.currentTimeMillis()
        val sanitizedDataset = datasetName?.substringBeforeLast('.')?.replace(Regex("[^a-zA-Z0-9_]"), "_") ?: "model"
        val uniqueExportName = "splat_${sanitizedDataset}_${timestamp}.ply"
        currentExportName = uniqueExportName
        activeSplatId = timestamp.toString()

        appScope.launch(Dispatchers.Default) {
            try {
                val tempFile = withContext(Dispatchers.IO) {
                    val cacheFile = File(context.applicationContext.cacheDir, "training_dataset.zip")
                    if (cacheFile.exists()) {
                        cacheFile.delete()
                    }
                    context.applicationContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                        cacheFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    cacheFile
                }
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        statusText = "Error: Failed to prepare dataset file"
                        isTraining = false
                        activeSplatId = null
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    SplatTrainingService.start(
                        context = context.applicationContext,
                        totalIterations = iters,
                        datasetName = sanitizedDataset,
                        datasetPath = tempFile.absolutePath,
                        exportName = uniqueExportName,
                        splatId = activeSplatId ?: timestamp.toString()
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error starting splat training", t)
                withContext(Dispatchers.Main) {
                    statusText = "Error: ${t.localizedMessage ?: t.message ?: t.javaClass.simpleName}"
                    isTraining = false
                    activeSplatId = null
                }
            }
        }
    }

    fun cancelTraining(context: Context) {
        isTraining = false
        statusText = "Cancelled"
        trainingCompleted = false
        estimatedRemainingMs = 0L
        progressSamples.clear()

        // Stops foreground service and forcefully kills isolated :training process
        SplatTrainingService.stop(context.applicationContext)

        // Clean up partial/incomplete PLY file if generated prematurely
        val export = currentExportName
        if (export.isNotEmpty()) {
            val fileInFiles = File(context.applicationContext.filesDir, export)
            if (fileInFiles.exists()) {
                fileInFiles.delete()
            }
            val fileInCache = File(context.applicationContext.cacheDir, export)
            if (fileInCache.exists()) {
                fileInCache.delete()
            }
        }

        // Clean up temporary dataset zip
        val cacheDataset = File(context.applicationContext.cacheDir, "training_dataset.zip")
        if (cacheDataset.exists()) {
            cacheDataset.delete()
        }

        // Remove any incomplete record matching active training session
        val currentId = activeSplatId
        if (currentId != null || export.isNotEmpty()) {
            val splats = TrainedSplatStorage.loadTrainedSplats(context.applicationContext)
            val filtered = splats.filterNot { it.id == currentId || it.exportName == export }
            if (filtered.size != splats.size) {
                TrainedSplatStorage.saveTrainedSplats(context.applicationContext, filtered)
            }
        }

        activeSplatId = null
        currentExportName = ""
    }
}
