package io.github.sceneview.demo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class SplatTrainingService : Service() {
    companion object {
        const val ACTION_START_TRAINING = "io.github.sceneview.demo.service.START_TRAINING"
        const val ACTION_UPDATE_PROGRESS = "io.github.sceneview.demo.service.UPDATE_TRAINING_PROGRESS"
        const val ACTION_STOP_TRAINING = "io.github.sceneview.demo.service.STOP_TRAINING"

        private const val CHANNEL_ID = "SplatTrainingChannel"
        private const val NOTIFICATION_ID = 2
        private const val TAG = "SplatTrainingService"

        fun start(context: Context, totalIterations: Int, datasetName: String) {
            val intent = Intent(context, SplatTrainingService::class.java).apply {
                action = ACTION_START_TRAINING
                putExtra("total", totalIterations)
                putExtra("dataset_name", datasetName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, iteration: Int, total: Int, elapsedMs: Long, datasetName: String? = null) {
            val intent = Intent(context, SplatTrainingService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra("iteration", iteration)
                putExtra("total", total)
                putExtra("elapsed_ms", elapsedMs)
                if (datasetName != null) {
                    putExtra("dataset_name", datasetName)
                }
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SplatTrainingService::class.java).apply {
                    action = ACTION_STOP_TRAINING
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var totalIterations: Int = 1000
    private var datasetName: String = "Dataset"

    override fun onCreate() {
        super.onCreate()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener?.let {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                try {
                    powerManager?.removeThermalStatusListener(it)
                } catch (_: Exception) {}
            }
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRAINING -> {
                totalIterations = intent.getIntExtra("total", 1000)
                datasetName = intent.getStringExtra("dataset_name") ?: "Dataset"

                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Training Gaussian Splat")
                    .setContentText("Starting training ($datasetName)...")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setProgress(totalIterations, 0, false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()

                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_UPDATE_PROGRESS -> {
                val iter = intent.getIntExtra("iteration", 0)
                val total = intent.getIntExtra("total", totalIterations)
                val elapsedMs = intent.getLongExtra("elapsed_ms", 0L)
                intent.getStringExtra("dataset_name")?.let { datasetName = it }

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
            }
            ACTION_STOP_TRAINING -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
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
