package io.github.sceneview.demo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.splats.brush.BrushConfig
import com.splats.brush.BrushEngine
import com.splats.brush.BrushProgressListener
import java.io.File
import kotlin.concurrent.thread

/**
 * Isolated background foreground service running in a dedicated process (:training).
 * Runs the heavy native BrushEngine 3D Gaussian Splatting training loop.
 *
 * Running in a dedicated process guarantees that:
 * 1. The main UI thread never suffers frame drops or GC pauses from the training compute.
 * 2. When training is cancelled, terminating this process immediately kills all native
 *    Rust/Tokio threads and frees all GPU resources, completely eliminating background lag.
 */
class SplatTrainingService : Service() {
    companion object {
        const val ACTION_START_TRAINING = "io.github.sceneview.demo.service.START_TRAINING"
        const val ACTION_STOP_TRAINING = "io.github.sceneview.demo.service.STOP_TRAINING"

        const val BROADCAST_TRAINING_PROGRESS = "io.github.sceneview.demo.TRAINING_PROGRESS"
        const val BROADCAST_TRAINING_EVAL = "io.github.sceneview.demo.TRAINING_EVAL"
        const val BROADCAST_TRAINING_COMPLETE = "io.github.sceneview.demo.TRAINING_COMPLETE"
        const val BROADCAST_TRAINING_ERROR = "io.github.sceneview.demo.TRAINING_ERROR"

        private const val CHANNEL_ID = "SplatTrainingChannel"
        private const val NOTIFICATION_ID = 2
        private const val TAG = "SplatTrainingService"

        fun start(
            context: Context,
            totalIterations: Int,
            datasetName: String,
            datasetPath: String,
            exportName: String,
            splatId: String
        ) {
            val intent = Intent(context, SplatTrainingService::class.java).apply {
                action = ACTION_START_TRAINING
                putExtra("total", totalIterations)
                putExtra("dataset_name", datasetName)
                putExtra("dataset_path", datasetPath)
                putExtra("export_name", exportName)
                putExtra("splat_id", splatId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            try {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.cancel(NOTIFICATION_ID)

                val intent = Intent(context, SplatTrainingService::class.java).apply {
                    action = ACTION_STOP_TRAINING
                }
                context.startService(intent)
                context.stopService(Intent(context, SplatTrainingService::class.java))
            } catch (_: Exception) {}
        }
    }

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var totalIterations: Int = 1000
    private var datasetName: String = "Dataset"
    private var exportName: String = "model.ply"
    private var splatId: String = ""
    private var lastNotificationUpdateMs = 0L

    @Volatile
    private var isStopped = false

    private var trainingThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        isStopped = false
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
                    Log.w(TAG, "Thermal status escalated: $status during splat training.")
                }
            }
            try {
                thermalListener?.let { powerManager?.addThermalStatusListener(it) }
            } catch (e: Exception) {
                Log.w(TAG, "Could not register thermal status listener: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        isStopped = true
        dismissNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener?.let {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                try {
                    powerManager?.removeThermalStatusListener(it)
                } catch (_: Exception) {}
            }
        }
        // Force-kill the isolated :training process to immediately free all native threads & GPU VRAM
        android.os.Process.killProcess(android.os.Process.myPid())
        super.onDestroy()
    }

    private fun dismissNotification() {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.cancel(NOTIFICATION_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRAINING -> {
                isStopped = false
                totalIterations = intent.getIntExtra("total", 1000)
                datasetName = intent.getStringExtra("dataset_name") ?: "Dataset"
                exportName = intent.getStringExtra("export_name") ?: "model.ply"
                splatId = intent.getStringExtra("splat_id") ?: ""
                val datasetPath = intent.getStringExtra("dataset_path") ?: ""

                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Training Gaussian Splat")
                    .setContentText("Starting training ($datasetName)...")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setProgress(totalIterations, 0, false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()

                startForeground(NOTIFICATION_ID, notification)

                startNativeTraining(datasetPath)
            }
            ACTION_STOP_TRAINING -> {
                isStopped = true
                try {
                    BrushEngine.setProgressListener(null)
                } catch (_: Exception) {}
                dismissNotification()
                stopSelf()
                // Instantly kill isolated :training process
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        return START_NOT_STICKY
    }

    private fun startNativeTraining(datasetPath: String) {
        BrushEngine.setProgressListener(object : BrushProgressListener {
            override fun onProgress(iter: Int, total: Int, elapsedMs: Long) {
                if (isStopped) return

                val now = System.currentTimeMillis()
                if (now - lastNotificationUpdateMs >= 500L || iter >= total) {
                    lastNotificationUpdateMs = now
                    updateNotification(iter, total, elapsedMs)
                }

                // Broadcast progress to main UI process
                val progressIntent = Intent(BROADCAST_TRAINING_PROGRESS).apply {
                    setPackage(packageName)
                    putExtra("iteration", iter)
                    putExtra("total", total)
                    putExtra("elapsed_ms", elapsedMs)
                }
                sendBroadcast(progressIntent)
            }

            override fun onEvalResult(iter: Int, psnr: Float, ssim: Float) {
                if (isStopped) return
                val evalIntent = Intent(BROADCAST_TRAINING_EVAL).apply {
                    setPackage(packageName)
                    putExtra("psnr", psnr)
                    putExtra("ssim", ssim)
                }
                sendBroadcast(evalIntent)
            }

            override fun onTrainingComplete() {
                if (isStopped) return
                Log.i(TAG, "Native Brush training completed.")

                val completeIntent = Intent(BROADCAST_TRAINING_COMPLETE).apply {
                    setPackage(packageName)
                    putExtra("export_name", exportName)
                    putExtra("dataset_name", datasetName)
                    putExtra("total", totalIterations)
                    putExtra("splat_id", splatId)
                }
                sendBroadcast(completeIntent)

                dismissNotification()
                stopSelf()
            }
        })

        trainingThread = thread(name = "BrushNativeTrainer") {
            try {
                val datasetFile = File(datasetPath)
                if (!datasetFile.exists() || datasetFile.length() == 0L) {
                    throw IllegalStateException("Dataset archive not found or empty at: $datasetPath")
                }
                val datasetUri = Uri.fromFile(datasetFile)
                val config = BrushConfig().apply {
                    totalTrainIters = totalIterations
                    exportName = this@SplatTrainingService.exportName
                }
                Log.i(TAG, "Starting BrushEngine native execution: iters=$totalIterations, export=$exportName")
                BrushEngine.start(applicationContext, datasetUri, config)
            } catch (t: Throwable) {
                Log.e(TAG, "Error in native BrushEngine execution", t)
                if (!isStopped) {
                    val errorIntent = Intent(BROADCAST_TRAINING_ERROR).apply {
                        setPackage(packageName)
                        putExtra("error_message", t.localizedMessage ?: t.message ?: "Native training failed")
                    }
                    sendBroadcast(errorIntent)
                    dismissNotification()
                    stopSelf()
                }
            }
        }
    }

    private fun updateNotification(iter: Int, total: Int, elapsedMs: Long) {
        try {
            val elapsedSec = elapsedMs / 1000
            val progressText = "Iteration $iter / $total • Elapsed: ${elapsedSec}s"

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Training Gaussian Splat")
                .setContentText(progressText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setProgress(total, iter, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Splat Training",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live progress during 3D Gaussian Splat model training"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
