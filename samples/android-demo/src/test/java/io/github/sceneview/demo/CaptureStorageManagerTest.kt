package io.github.sceneview.demo

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.sceneview.demo.storage.CaptureProject
import io.github.sceneview.demo.storage.CaptureStorageManager
import io.github.sceneview.demo.storage.TrainedSplat
import io.github.sceneview.demo.storage.TrainedSplatStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CaptureStorageManagerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        CaptureStorageManager.resetToDefaultFolder(context)
        context.getSharedPreferences("splat_captures_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("splat_training_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `default folder display is Documents OpenSplat Captures`() {
        assertFalse(CaptureStorageManager.hasCustomFolder(context))
        assertEquals("Documents/OpenSplat/Captures", CaptureStorageManager.getCaptureFolderDisplay(context))
    }

    @Test
    fun `custom folder can be set and reset`() {
        val testUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMyScans")
        CaptureStorageManager.setCustomFolder(context, testUri)
        assertTrue(CaptureStorageManager.hasCustomFolder(context))

        CaptureStorageManager.resetToDefaultFolder(context)
        assertFalse(CaptureStorageManager.hasCustomFolder(context))
        assertEquals("Documents/OpenSplat/Captures", CaptureStorageManager.getCaptureFolderDisplay(context))
    }

    @Test
    fun `trained splat storage saves, loads, and deletes models in app data`() {
        val splat = TrainedSplat(
            id = "splat-1",
            datasetName = "Scan_Garden",
            iterations = 10000,
            elapsedMs = 45000L,
            psnr = 28.5f,
            ssim = 0.912f,
            status = "Finished!",
            exportName = "garden_10k.ply",
            timestamp = 1000L
        )

        TrainedSplatStorage.saveTrainedSplats(context, listOf(splat))
        val loaded = TrainedSplatStorage.loadTrainedSplats(context)
        assertEquals(1, loaded.size)
        assertEquals("Scan_Garden", loaded[0].datasetName)
        assertEquals(28.5f, loaded[0].psnr, 0.001f)

        TrainedSplatStorage.deleteTrainedSplat(context, splat)
        val afterDelete = TrainedSplatStorage.loadTrainedSplats(context)
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun `capture zip packaging and history tracking`() = runBlocking {
        // Create mock capture directory
        val mockCaptureDir = File(context.cacheDir, "test_capture_dir").apply {
            mkdirs()
            File(this, "manifest.txt").writeText("OpenSplat Manifest v1")
            val imgDir = File(this, "images").apply { mkdirs() }
            File(imgDir, "frame_000.jpg").writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val project = CaptureStorageManager.saveCaptureZip(
            context = context,
            projectName = "TestScan",
            sourceDir = mockCaptureDir,
            frameCount = 1,
            pointCount = 50
        )

        assertNotNull(project)
        assertEquals("TestScan", project.name)
        assertEquals(1, project.frameCount)
        assertEquals(50, project.pointCount)
        assertTrue(project.sizeBytes > 0)

        val history = CaptureStorageManager.getCaptureProjects(context)
        assertEquals(1, history.size)
        assertEquals(project.id, history[0].id)

        // Rename
        val renamed = CaptureStorageManager.renameCapture(context, project, "RenamedScan")
        assertNotNull(renamed)
        assertEquals("RenamedScan", renamed!!.name)

        val historyAfterRename = CaptureStorageManager.getCaptureProjects(context)
        assertEquals(1, historyAfterRename.size)
        assertEquals("RenamedScan", historyAfterRename[0].name)

        // Delete
        val deleted = CaptureStorageManager.deleteCapture(context, renamed)
        assertTrue(deleted)

        val historyAfterDelete = CaptureStorageManager.getCaptureProjects(context)
        assertTrue(historyAfterDelete.isEmpty())
    }
}
