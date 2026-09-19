package io.github.sceneview.demo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * On-device / emulator launch tests for the OpenSplat pipeline stages:
 * 1. [arSplatCapture_launchesAndRenders] — ARCore camera feed, XFeat feature tracking, point cloud triangulation.
 * 2. [splatTraining_launchesAndRenders] — On-device 3D Gaussian Splatting optimization screen.
 * 3. [splatViewer_launchesAndRenders] — Filament 3D Gaussian Splat PLY viewer.
 *
 * Verifies that each screen boots cleanly without JNI linkage crashes (Filament, LiteRT, Ceres, OpenCV)
 * and captures a visual QA screenshot to:
 * ```bash
 * adb pull /sdcard/Download/sceneview-qa/ tools/qa-screenshots/interactions/
 * ```
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SplatPipelineLaunchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val pkg = "io.github.sceneview.demo"
    private val timeout = 10_000L

    @Before
    fun goHome() {
        device.pressHome()
    }

    @After
    fun teardown() {
        device.pressHome()
    }

    /**
     * Launches a splat screen via [DemoHostActivity], waits for the scaffold title,
     * and pauses for initial PBR/cold-boot frame rendering before taking a screenshot.
     */
    private fun openScreen(demoId: String, expectedTitle: String): Boolean {
        val intent = Intent().apply {
            setClassName(pkg, "$pkg.DemoHostActivity")
            putExtra(DemoHostActivity.EXTRA_DEMO_ID, demoId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        val found = device.wait(Until.hasObject(By.text(expectedTitle)), timeout) != null
        Thread.sleep(10000)
        return found
    }

    private fun screenshot(name: String) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val tmpDir = File(targetContext.getExternalFilesDir(null), "sceneview-qa-tmp")
        if (!tmpDir.exists()) tmpDir.mkdirs()
        val tmpPng = File(tmpDir, ".tmp_$name.png")
        device.takeScreenshot(tmpPng)
        val bmp = BitmapFactory.decodeFile(tmpPng.absolutePath)
            ?: error("Failed to decode screenshot PNG for '$name'")

        val resolver = targetContext.contentResolver
        val filename = "$name.jpg"
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Downloads.RELATIVE_PATH}=? AND " +
            "${MediaStore.Downloads.DISPLAY_NAME}=?"
        val args = arrayOf("Download/sceneview-qa/", filename)
        resolver.query(collection, arrayOf(MediaStore.Downloads._ID), selection, args, null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val oldUri = android.content.ContentUris.withAppendedId(collection, id)
                    resolver.delete(oldUri, null, null)
                }
            }

        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/sceneview-qa/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, pending)
            ?: error("MediaStore insert returned null for '$name'")
        resolver.openOutputStream(uri)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 75, out)
        } ?: error("MediaStore openOutputStream returned null for '$name'")
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null,
        )
        bmp.recycle()
        tmpPng.delete()
    }

    @Test
    fun a01_arSplatCapture_launchesAndRenders() {
        openScreen("ar-splat-capture", "Splat Capture")
        screenshot("01_ar_splat_capture")
    }

    @Test
    fun a02_splatTraining_launchesAndRenders() {
        openScreen("splat-training", "Splat Training")
        screenshot("02_splat_training")
    }

    @Test
    fun a03_splatViewer_launchesAndRenders() {
        openScreen("splat-viewer", "Splat Viewer")
        screenshot("03_splat_viewer")
    }
}
