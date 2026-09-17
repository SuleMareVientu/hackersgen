package io.github.sceneview.demo.service

import android.app.Notification
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
import io.github.sceneview.demo.demos.SplatCapturePipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplatProcessService : Service() {
    companion object {
        const val ACTION_START_PROCESSING = "io.github.sceneview.demo.service.START_PROCESSING"
        private const val TAG = "SplatProcessService"
    }

    private val CHANNEL_ID = "SplatProcessChannel"
    private val NOTIFICATION_ID = 1
    private var job: kotlinx.coroutines.Job? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
                    Log.w(TAG, "Thermal status escalated: $status. Throttling/shedding load (§4).")
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
                } catch (e: Exception) {}
            }
        }
        job?.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START_PROCESSING) return START_NOT_STICKY
        
        val manifestPath = intent.getStringExtra("manifest_path") ?: return START_NOT_STICKY
        val pipelineHandle = intent.getLongExtra("pipeline_handle", 0L)
        if (pipelineHandle == 0L) return START_NOT_STICKY

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Processing 3D Capture")
            .setContentText("Generating 3D model...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                SplatCapturePipeline.processDataset(pipelineHandle, manifestPath)
                
                if (isActive && SplatCapturePipeline.getProcessingPhase(pipelineHandle) == 8) {
                    val intent = Intent("io.github.sceneview.demo.SPLAT_PROCESSING_COMPLETE")
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
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
                "3D Processing",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
