package io.github.sceneview.demo.storage

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Metadata for a trained 3D Gaussian Splat model.
 * Per project requirements, trained splat weights remain in app internal storage (filesDir).
 */
@Serializable
data class TrainedSplat(
    val id: String,
    val datasetName: String,
    val iterations: Int,
    val elapsedMs: Long,
    val psnr: Float,
    val ssim: Float,
    val status: String,
    val exportName: String,
    val timestamp: Long,
    val currentIteration: Int = 0
)

object TrainedSplatStorage {
    private const val PREFS_NAME = "splat_training_prefs"
    private const val KEY_TRAINED_SPLATS = "trained_splats"
    private val json = Json { ignoreUnknownKeys = true }

    fun loadTrainedSplats(context: Context): List<TrainedSplat> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TRAINED_SPLATS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<TrainedSplat>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTrainedSplats(context: Context, splats: List<TrainedSplat>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = json.encodeToString(splats)
        prefs.edit().putString(KEY_TRAINED_SPLATS, raw).apply()
    }

    fun deleteTrainedSplat(context: Context, splat: TrainedSplat): Boolean {
        val file = getSplatFile(context, splat.exportName)
        if (file.exists()) {
            file.delete()
        }
        val existing = loadTrainedSplats(context).filterNot { it.id == splat.id }
        saveTrainedSplats(context, existing)
        return true
    }

    fun getSplatFile(context: Context, exportName: String): File {
        val filesDirFile = File(context.filesDir, exportName)
        if (filesDirFile.exists()) return filesDirFile
        val cacheDirFile = File(context.cacheDir, exportName)
        if (cacheDirFile.exists()) return cacheDirFile
        val externalFile = File(context.getExternalFilesDir(null), exportName)
        if (externalFile.exists()) return externalFile
        return filesDirFile
    }

    /**
     * Purges any incomplete splat records or dangling zero-byte files from premature cancellations or crashes.
     */
    fun cleanIncompleteSplats(context: Context) {
        val existing = loadTrainedSplats(context)
        val valid = existing.filter { splat ->
            val isFinished = splat.status == "Finished!" || splat.status.equals("completed", ignoreCase = true)
            val file = getSplatFile(context, splat.exportName)
            isFinished && file.exists() && file.length() > 0L
        }
        existing.filterNot { valid.contains(it) }.forEach { incomplete ->
            val file = getSplatFile(context, incomplete.exportName)
            if (file.exists()) {
                file.delete()
            }
        }
        if (valid.size != existing.size) {
            saveTrainedSplats(context, valid)
        }
    }
}
