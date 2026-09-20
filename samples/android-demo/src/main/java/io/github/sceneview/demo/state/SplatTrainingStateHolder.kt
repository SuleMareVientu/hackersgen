package io.github.sceneview.demo.state

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.splats.brush.BrushConfig
import com.splats.brush.BrushEngine
import com.splats.brush.BrushProgressListener
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
 */
object SplatTrainingStateHolder {
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

    private var listenerRegistered = false

    fun initListener(context: Context) {
        if (listenerRegistered) return
        listenerRegistered = true
        var lastNotificationUpdateMs = 0L

        BrushEngine.setProgressListener(object : BrushProgressListener {
            override fun onProgress(iter: Int, total: Int, elapsedMs: Long) {
                appScope.launch {
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
                val now = System.currentTimeMillis()
                if (now - lastNotificationUpdateMs >= 500L || iter >= total) {
                    lastNotificationUpdateMs = now
                    SplatTrainingService.updateProgress(
                        context = context.applicationContext,
                        iteration = iter,
                        total = total,
                        elapsedMs = elapsedMs,
                        datasetName = datasetName
                    )
                }
            }

            override fun onEvalResult(iter: Int, psnr: Float, ssim: Float) {
                appScope.launch {
                    evalPsnr = psnr
                    evalSsim = ssim
                }
            }

            override fun onTrainingComplete() {
                SplatTrainingService.stop(context.applicationContext)
                appScope.launch {
                    statusText = "Finished!"
                    isTraining = false
                    trainingCompleted = true
                    estimatedRemainingMs = 0L
                    progressSamples.clear()
                    val newSplat = TrainedSplat(
                        id = activeSplatId ?: System.currentTimeMillis().toString(),
                        datasetName = datasetName ?: "Unknown Dataset",
                        iterations = totalIterations,
                        elapsedMs = trainingElapsedMs,
                        psnr = evalPsnr,
                        ssim = evalSsim,
                        status = "Finished!",
                        exportName = currentExportName,
                        timestamp = System.currentTimeMillis(),
                        currentIteration = totalIterations
                    )
                    val existing = TrainedSplatStorage.loadTrainedSplats(context.applicationContext)
                    TrainedSplatStorage.saveTrainedSplats(context.applicationContext, listOf(newSplat) + existing)
                    activeSplatId = null
                }
            }
        })
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

        SplatTrainingService.start(context.applicationContext, iters, sanitizedDataset)

        val config = BrushConfig().apply {
            totalTrainIters = iters
            exportName = uniqueExportName
        }

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
                    SplatTrainingService.stop(context.applicationContext)
                    withContext(Dispatchers.Main) {
                        statusText = "Error: Failed to prepare dataset file"
                        isTraining = false
                        activeSplatId = null
                    }
                    return@launch
                }
                val tempUri = Uri.fromFile(tempFile)
                BrushEngine.start(context.applicationContext, tempUri, config)
            } catch (e: Exception) {
                SplatTrainingService.stop(context.applicationContext)
                withContext(Dispatchers.Main) {
                    statusText = "Error: ${e.localizedMessage ?: e.message}"
                    isTraining = false
                    activeSplatId = null
                }
            }
        }
    }

    fun cancelTraining(context: Context) {
        isTraining = false
        statusText = "Cancelled"
        estimatedRemainingMs = 0L
        progressSamples.clear()
        SplatTrainingService.stop(context.applicationContext)
        BrushEngine.setProgressListener(null)
        listenerRegistered = false
    }
}
