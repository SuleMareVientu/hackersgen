package io.github.sceneview.demo.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Representation of an exported Splat Capture project (.zip package).
 * Captures are stored in external, user-accessible storage (never in internal app data).
 */
@Serializable
data class CaptureProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val uriString: String,
    val frameCount: Int = 0,
    val pointCount: Int = 0,
    val sizeBytes: Long = 0L
)

object CaptureStorageManager {
    private const val TAG = "CaptureStorageManager"
    private const val PREFS_NAME = "splat_captures_prefs"
    private const val KEY_CUSTOM_FOLDER_URI = "custom_folder_uri"
    private const val KEY_CAPTURE_PROJECTS = "capture_projects"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns true if the user has configured a custom SAF folder.
     */
    fun hasCustomFolder(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_CUSTOM_FOLDER_URI, null).isNullOrBlank()
    }

    /**
     * Returns a human-readable description of the current capture storage directory.
     */
    fun getCaptureFolderDisplay(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customUriStr = prefs.getString(KEY_CUSTOM_FOLDER_URI, null)
        if (!customUriStr.isNullOrBlank()) {
            try {
                val uri = Uri.parse(customUriStr)
                val doc = DocumentFile.fromTreeUri(context, uri)
                if (doc != null && doc.name != null) {
                    return doc.name!!
                }
                return uri.lastPathSegment ?: "Custom Folder"
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve custom folder display: ${e.message}")
            }
        }
        return "Documents/OpenSplat/Captures"
    }

    /**
     * Saves a user-selected SAF tree URI as the persistent capture directory.
     */
    fun setCustomFolder(context: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            Log.w(TAG, "takePersistableUriPermission failed: ${e.message}")
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_FOLDER_URI, uri.toString()).apply()
    }

    /**
     * Resets the capture directory to the default (Documents/OpenSplat/Captures).
     */
    fun resetToDefaultFolder(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CUSTOM_FOLDER_URI).apply()
    }

    /**
     * Retrieves all saved capture projects from persistent history.
     */
    fun getCaptureProjects(context: Context): List<CaptureProject> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CAPTURE_PROJECTS, null) ?: return emptyList()
        return try {
            val list = json.decodeFromString<List<CaptureProject>>(raw)
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode capture projects: ${e.message}")
            emptyList()
        }
    }

    /**
     * Packages a capture source directory into a .zip file and persists it in external storage.
     * Captures are NEVER stored inside the app's internal sandbox.
     */
    suspend fun saveCaptureZip(
        context: Context,
        projectName: String,
        sourceDir: File,
        frameCount: Int,
        pointCount: Int
    ): CaptureProject = withContext(Dispatchers.IO) {
        val cleanName = sanitizeFileName(projectName.ifBlank { "Scan_${System.currentTimeMillis()}" })
        val tempZip = File(context.cacheDir, "staging_${System.currentTimeMillis()}.zip")
        try {
            zipDirectory(sourceDir, tempZip)
            val sizeBytes = tempZip.length()

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val customUriStr = prefs.getString(KEY_CUSTOM_FOLDER_URI, null)

            val finalUri: Uri = if (!customUriStr.isNullOrBlank()) {
                saveToCustomTree(context, Uri.parse(customUriStr), cleanName, tempZip)
            } else {
                saveToDocuments(context, cleanName, tempZip)
            }

            val project = CaptureProject(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                timestamp = System.currentTimeMillis(),
                uriString = finalUri.toString(),
                frameCount = frameCount,
                pointCount = pointCount,
                sizeBytes = sizeBytes
            )

            saveProjectToHistory(context, project)
            project
        } finally {
            if (tempZip.exists()) {
                tempZip.delete()
            }
        }
    }

    /**
     * Renames a capture in external storage and updates history.
     */
    suspend fun renameCapture(
        context: Context,
        project: CaptureProject,
        newName: String
    ): CaptureProject? = withContext(Dispatchers.IO) {
        val cleanNewName = sanitizeFileName(newName)
        if (cleanNewName.isBlank() || cleanNewName == project.name) return@withContext project

        val uri = Uri.parse(project.uriString)
        var updatedUri = uri

        try {
            if (uri.scheme == "content") {
                val doc = DocumentFile.fromSingleUri(context, uri)
                if (doc != null && doc.exists()) {
                    val success = doc.renameTo("$cleanNewName.zip")
                    if (success) {
                        updatedUri = doc.uri
                    }
                } else {
                    // Try MediaStore update
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "$cleanNewName.zip")
                    }
                    context.contentResolver.update(uri, values, null, null)
                }
            } else if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    val target = File(file.parentFile, "$cleanNewName.zip")
                    if (file.renameTo(target)) {
                        updatedUri = Uri.fromFile(target)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename file on storage: ${e.message}")
        }

        val updated = project.copy(name = cleanNewName, uriString = updatedUri.toString())
        updateProjectInHistory(context, updated)
        updated
    }

    /**
     * Deletes a capture project from external storage and removes it from history.
     */
    suspend fun deleteCapture(context: Context, project: CaptureProject): Boolean = withContext(Dispatchers.IO) {
        var fileDeleted = false
        val uri = Uri.parse(project.uriString)
        try {
            if (uri.scheme == "content") {
                val doc = DocumentFile.fromSingleUri(context, uri)
                fileDeleted = doc?.delete() ?: (context.contentResolver.delete(uri, null, null) > 0)
            } else if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                fileDeleted = file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file on storage: ${e.message}")
        }

        removeProjectFromHistory(context, project.id)
        true
    }

    /**
     * Opens an input stream to read a capture ZIP file.
     */
    fun openInputStream(context: Context, project: CaptureProject): InputStream? {
        val uri = Uri.parse(project.uriString)
        return try {
            if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)
            } else {
                File(uri.path ?: "").inputStream()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open input stream for project: ${e.message}")
            null
        }
    }

    private fun saveToCustomTree(context: Context, treeUri: Uri, name: String, tempZip: File): Uri {
        val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("Unable to access custom folder")
        val fileDoc = treeDoc.createFile("application/zip", "$name.zip")
            ?: throw IOException("Failed to create file in custom folder")

        context.contentResolver.openOutputStream(fileDoc.uri)?.use { out ->
            tempZip.inputStream().use { inp -> inp.copyTo(out) }
        } ?: throw IOException("Failed to open output stream for custom folder file")

        return fileDoc.uri
    }

    private fun saveToDocuments(context: Context, name: String, tempZip: File): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.zip")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/OpenSplat/Captures")
            }
        }

        val collection = MediaStore.Files.getContentUri("external")
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IOException("Failed to insert MediaStore entry in Documents")

        context.contentResolver.openOutputStream(uri)?.use { out ->
            tempZip.inputStream().use { inp -> inp.copyTo(out) }
        } ?: throw IOException("Failed to open output stream for Documents entry")

        return uri
    }

    private fun saveProjectToHistory(context: Context, project: CaptureProject) {
        val existing = getCaptureProjects(context).toMutableList()
        existing.removeAll { it.id == project.id }
        existing.add(0, project)
        saveHistory(context, existing)
    }

    private fun updateProjectInHistory(context: Context, project: CaptureProject) {
        val existing = getCaptureProjects(context).map {
            if (it.id == project.id) project else it
        }
        saveHistory(context, existing)
    }

    private fun removeProjectFromHistory(context: Context, projectId: String) {
        val existing = getCaptureProjects(context).filterNot { it.id == projectId }
        saveHistory(context, existing)
    }

    private fun saveHistory(context: Context, projects: List<CaptureProject>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = json.encodeToString(projects)
        prefs.edit().putString(KEY_CAPTURE_PROJECTS, raw).apply()
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").trimEnd('.')
    }

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            zipFileTree(sourceDir, sourceDir, zos)
        }
    }

    private fun zipFileTree(rootDir: File, currentDir: File, zos: ZipOutputStream) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                zipFileTree(rootDir, file, zos)
            } else {
                val relativePath = file.relativeTo(rootDir).path.replace('\\', '/')
                val entry = ZipEntry(relativePath)
                entry.time = file.lastModified()
                zos.putNextEntry(entry)
                file.inputStream().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }
}
