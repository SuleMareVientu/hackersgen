@file:Suppress("MaxLineLength") // test DemoEntry constructor args are long by nature

package io.github.sceneview.demo

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewInAr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pure-JVM tests for [DeepLinkRouter]. Robolectric is the cheapest path
 * to a real `android.net.Uri` parser without spinning up a device.
 *
 * Tests the **end-to-end intent → demo id** lookup, including the
 * registry guard so we can assert that fuzzed / spoofed deep links
 * fall through to `null` (= the activity falls back to the demo list).
 */
@RunWith(RobolectricTestRunner::class)
class DeepLinkRouterTest {

    // Title / subtitle don't matter for the router under test — it only
    // looks at the id. We pass arbitrary R.string.* values to satisfy the
    // post-#1099 resource-ID typed fields without resolving them.
    private val knownRegistry = listOf(
        DemoEntry("ar-splat-capture", R.string.demo_ar_splat_capture_title, R.string.demo_ar_splat_capture_subtitle, "Augmented Reality", Icons.Filled.ViewInAr),
        DemoEntry("splat-viewer", R.string.demo_splat_viewer, R.string.demo_splat_viewer_subtitle, "3D Basics", Icons.Filled.ViewInAr),
    )

    // ── Custom scheme: sceneview://demo/<id> ──────────────────────────────

    @Test
    fun `custom scheme resolves a known demo id`() {
        val uri = Uri.parse("sceneview://demo/ar-splat-capture")
        assertEquals("ar-splat-capture", DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `custom scheme returns null for an unknown demo id (guards against fuzzing)`() {
        val uri = Uri.parse("sceneview://demo/totally-not-a-demo")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `custom scheme tolerates uppercase scheme and host`() {
        val uri = Uri.parse("SCENEVIEW://Demo/splat-viewer")
        assertEquals("splat-viewer", DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `custom scheme returns null for missing path segment`() {
        val uri = Uri.parse("sceneview://demo")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `custom scheme returns null for wrong host`() {
        val uri = Uri.parse("sceneview://playground/ar-splat-capture")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    // ── HTTPS App-Links: sceneview.github.io/open?demo=<id> ───────────────

    @Test
    fun `https app link resolves a known demo id from the demo query parameter`() {
        val uri = Uri.parse("https://sceneview.github.io/open?demo=ar-splat-capture")
        assertEquals("ar-splat-capture", DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `https app link returns null when demo query param is missing`() {
        val uri = Uri.parse("https://sceneview.github.io/open")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `https app link returns null for the wrong path`() {
        val uri = Uri.parse("https://sceneview.github.io/playground?demo=ar-splat-capture")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `https app link returns null for the wrong host`() {
        val uri = Uri.parse("https://example.com/open?demo=ar-splat-capture")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    // ── Fall-through cases ────────────────────────────────────────────────

    @Test
    fun `null uri returns null`() {
        assertNull(DeepLinkRouter.parse(null, knownRegistry))
    }

    @Test
    fun `unsupported scheme returns null`() {
        val uri = Uri.parse("ftp://demo/ar-splat-capture")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `extractCandidate exposes the raw id without registry validation`() {
        // Round-trip through the URI parser to cover a path other tests
        // don't (the bare-id extraction is the security-sensitive bit; we
        // want it covered independently of the registry).
        val uri = Uri.parse("sceneview://demo/some-future-id-not-yet-shipped")
        assertEquals("some-future-id-not-yet-shipped", DeepLinkRouter.extractCandidate(uri))
        // …but the public API still gates it on the registry:
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    // ── validate(id) — guard for the `--es demo <id>` QA ingress (#958) ───
    // The QA channel used by `adb shell am start ... --es demo <id>` is
    // reachable by any app on the device.
    @Test
    fun `validate returns the id when it matches the registry`() {
        assertEquals("ar-splat-capture", DeepLinkRouter.validate("ar-splat-capture", knownRegistry))
    }

    @Test
    fun `validate returns null for an unknown id (guards QA channel)`() {
        assertNull(DeepLinkRouter.validate("totally-not-a-demo", knownRegistry))
    }

    @Test
    fun `validate returns null for null and blank ids`() {
        assertNull(DeepLinkRouter.validate(null, knownRegistry))
        assertNull(DeepLinkRouter.validate("", knownRegistry))
        assertNull(DeepLinkRouter.validate("   ", knownRegistry))
    }

    // ── cameraDistance — deep-link zoom param for the device-QA harness (#1571) ──
    //
    // Maestro has no pinch gesture, so the Android device-QA flows drive 3D camera
    // zoom via a deep-link param instead. parseCameraDistance reads the URL query
    // parameter; validateCameraDistance is the shared clamp both ingress channels use.

    @Test
    fun `parseCameraDistance reads a valid distance from the custom-scheme query param`() {
        val uri = Uri.parse("sceneview://demo/splat-viewer?cameraDistance=3.5")
        assertEquals(3.5f, DeepLinkRouter.parseCameraDistance(uri))
    }

    @Test
    fun `parseCameraDistance reads a valid distance from the https app-link query param`() {
        val uri = Uri.parse("https://sceneview.github.io/open?demo=splat-viewer&cameraDistance=8")
        assertEquals(8f, DeepLinkRouter.parseCameraDistance(uri))
    }

    @Test
    fun `parseCameraDistance returns null when the query param is absent`() {
        val uri = Uri.parse("sceneview://demo/splat-viewer")
        assertNull(DeepLinkRouter.parseCameraDistance(uri))
    }

    @Test
    fun `parseCameraDistance returns null for an unparseable value`() {
        val uri = Uri.parse("sceneview://demo/splat-viewer?cameraDistance=close")
        assertNull(DeepLinkRouter.parseCameraDistance(uri))
    }

    @Test
    fun `parseCameraDistance returns null for null uri`() {
        assertNull(DeepLinkRouter.parseCameraDistance(null))
    }

    @Test
    fun `validateCameraDistance accepts an in-range value`() {
        assertEquals(2.5f, DeepLinkRouter.validateCameraDistance(2.5f))
        assertEquals(
            DeepLinkRouter.CAMERA_DISTANCE_MIN,
            DeepLinkRouter.validateCameraDistance(DeepLinkRouter.CAMERA_DISTANCE_MIN),
        )
        assertEquals(
            DeepLinkRouter.CAMERA_DISTANCE_MAX,
            DeepLinkRouter.validateCameraDistance(DeepLinkRouter.CAMERA_DISTANCE_MAX),
        )
    }

    @Test
    fun `validateCameraDistance rejects null, non-finite and out-of-range values`() {
        assertNull(DeepLinkRouter.validateCameraDistance(null))
        assertNull(DeepLinkRouter.validateCameraDistance(Float.NaN))
        assertNull(DeepLinkRouter.validateCameraDistance(Float.POSITIVE_INFINITY))
        assertNull(DeepLinkRouter.validateCameraDistance(Float.NEGATIVE_INFINITY))
        assertNull(DeepLinkRouter.validateCameraDistance(0f))
        assertNull(DeepLinkRouter.validateCameraDistance(-5f))
        assertNull(
            DeepLinkRouter.validateCameraDistance(DeepLinkRouter.CAMERA_DISTANCE_MAX + 1f),
        )
    }

    @Test
    fun `validate rejects fuzzed ids that look like path traversal or HTML`() {
        // Spot-check the kinds of strings a hostile app on the device might
        // try via the unprotected --es channel. None of these are in the
        // registry, so all must drop to null.
        listOf(
            "../",
            "../../etc/passwd",
            "<script>alert(1)</script>",
            "ar-splat-capture ; rm -rf",
            "ar-splat-capture extra",
            "AR-SPLAT-CAPTURE", // case-sensitive: registry uses kebab-case lowercase
        ).forEach { hostile ->
            assertNull(
                "validate must reject '$hostile'",
                DeepLinkRouter.validate(hostile, knownRegistry),
            )
        }
    }


    // ── camera_distance intent-extra coercion (#2652) ─────────────────────

    @Test
    fun `camera distance extra accepts every sender encoding`() {
        // adb --ef delivers a Float; Maestro launchApp delivers env-interpolated
        // values as String extras and could deliver bare YAML numbers as
        // Integer/Double. All must resolve identically (#2652).
        assertEquals(0.6f, DeepLinkRouter.coerceCameraDistanceExtra(0.6f))
        assertEquals(0.6f, DeepLinkRouter.coerceCameraDistanceExtra(0.6))
        assertEquals(40f, DeepLinkRouter.coerceCameraDistanceExtra(40))
        assertEquals(40f, DeepLinkRouter.coerceCameraDistanceExtra(40L))
        assertEquals(0.6f, DeepLinkRouter.coerceCameraDistanceExtra("0.6"))
        assertEquals(40f, DeepLinkRouter.coerceCameraDistanceExtra("40"))
    }

    @Test
    fun `camera distance extra rejects garbage without throwing`() {
        assertNull(DeepLinkRouter.coerceCameraDistanceExtra(null))
        assertNull(DeepLinkRouter.coerceCameraDistanceExtra("not-a-number"))
        assertNull(DeepLinkRouter.coerceCameraDistanceExtra(""))
        assertNull(DeepLinkRouter.coerceCameraDistanceExtra(true))
        assertNull(DeepLinkRouter.coerceCameraDistanceExtra(Float.NaN))
        assertNull(DeepLinkRouter.coerceCameraDistanceExtra(Double.POSITIVE_INFINITY))
        assertNull(DeepLinkRouter.coerceCameraDistanceExtra("NaN"))
    }

    @Test
    fun `camera distance extra applies the shared clamp on every encoding`() {
        // Below CAMERA_DISTANCE_MIN (0.05) and above CAMERA_DISTANCE_MAX (100)
        // must drop to null — auto-fit framing — never crash or pass through.
        assertNull(DeepLinkRouter.coerceCameraDistanceExtra(0.01f))
        assertNull(DeepLinkRouter.coerceCameraDistanceExtra("0.01"))
        assertNull(DeepLinkRouter.coerceCameraDistanceExtra(500))
        assertNull(DeepLinkRouter.coerceCameraDistanceExtra("500"))
        // Boundary values are inclusive.
        assertEquals(
            DeepLinkRouter.CAMERA_DISTANCE_MIN,
            DeepLinkRouter.coerceCameraDistanceExtra(DeepLinkRouter.CAMERA_DISTANCE_MIN),
        )
        assertEquals(
            DeepLinkRouter.CAMERA_DISTANCE_MAX,
            DeepLinkRouter.coerceCameraDistanceExtra(DeepLinkRouter.CAMERA_DISTANCE_MAX),
        )
    }

    @Test
    fun `camera distance URL query applies the same clamp as the extra channel`() {
        assertEquals(
            0.6f,
            DeepLinkRouter.parseCameraDistance(
                Uri.parse("sceneview://demo/splat-viewer?cameraDistance=0.6"),
            ),
        )
        assertNull(
            DeepLinkRouter.parseCameraDistance(
                Uri.parse("sceneview://demo/splat-viewer?cameraDistance=junk"),
            ),
        )
        assertNull(DeepLinkRouter.parseCameraDistance(null))
    }

}
