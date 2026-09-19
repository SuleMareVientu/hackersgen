package io.github.sceneview.demo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.sceneview.math.Position
import java.io.File
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Overlay that covers the 3D viewport while [loading] is true. Shows a centred spinner and
 * the [label] underneath so users know *why* the viewport is black. Fades out automatically
 * when [loading] flips to false (Compose removes the Box from the tree).
 *
 * Drop this inside a SceneView's content block OR over the whole Box that contains the
 * SceneView — the scrim is semi-transparent so the first rendered frame shows through.
 */
@Composable
fun LoadingScrim(loading: Boolean, label: String = "Loading…") {
    if (!loading) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}


/**
 * First-frame signal for the [DemoScaffold] loading scrim.
 *
 * Cold-starting a SceneView demo leaves the viewport jet-black for 5–12 s while
 * Filament compiles shaders and uploads buffers — it reads as a crash to a
 * first-time user (#1022). [rememberFirstFrameState] returns this pair so a demo
 * can flip the scrim off exactly when the first Filament frame is presented:
 *
 * ```kotlin
 * val firstFrame = rememberFirstFrameState()
 * DemoScaffold(title = …, onBack = onBack, firstFrameRendered = firstFrame.rendered) {
 *     SceneView(onFrame = firstFrame.onFrame, …) { … }
 * }
 * ```
 *
 * @property rendered Read in the scaffold — `false` until the first frame, then `true`.
 * @property onFrame Pass straight to `SceneView(onFrame = …)`. Cheap after the first call.
 */
class FirstFrameState internal constructor(
    private val renderedState: androidx.compose.runtime.MutableState<Boolean>,
) {
    val rendered: androidx.compose.runtime.State<Boolean> get() = renderedState

    val onFrame: (frameTimeNanos: Long) -> Unit = {
        if (!renderedState.value) renderedState.value = true
    }
}

/**
 * Remembers a [FirstFrameState] for wiring the [DemoScaffold] loading scrim to a
 * SceneView's first presented frame. See [FirstFrameState] for the usage pattern.
 */
@Composable
fun rememberFirstFrameState(): FirstFrameState {
    val rendered = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    return androidx.compose.runtime.remember { FirstFrameState(rendered) }
}



/**
 * A [CameraGestureDetector.CameraManipulator] that orbits the camera around [target]
 * while idle, then hands control off to a stock [DefaultCameraManipulator] the moment
 * the user touches the viewport — so the model stays fixed in world space (lights and
 * reflections hit the same surface every frame) instead of spinning under the camera.
 * use it when a demo is *about* the object itself (hero showcase, PBR lighting,
 * environment comparison) so the viewer sees the model from different angles without
 * the model rotating through its own light setup.
 *
 * On first gesture the manipulator captures the current orbit pose as the new
 * [DefaultCameraManipulator.orbitHomePosition], so there's no snap — the user's first
 * drag continues from exactly where the idle orbit left off.
 *
 * ### Auto-orbit resume after idle (#2225)
 *
 * Once the user releases a grab/scroll the manipulator stays in user-control mode for
 * [resumeAfterMillis] of inactivity, then clears the fallback so the idle auto-orbit
 * resumes from the user's last pose (no snap — the next orbit yaw is read from
 * [yawProvider] when [getTransform] falls through to [orbitTransform]). Set
 * [resumeAfterMillis] to `0L` or negative to disable the resume — the manipulator then
 * stays in user control forever after the first touch (legacy behaviour).
 */
class HeroOrbitCameraManipulator(
    private val yawProvider: () -> Float,
    private val radius: Float,
    private val yHeight: Float,
    private val target: Position,
    private val resumeAfterMillis: Long = 3_000L,
    private val pinchZoomSpeed: Float = io.github.sceneview.gesture.CameraGestureDetector.DefaultCameraManipulator.DEFAULT_PINCH_ZOOM_SPEED,
    private val pinchZoomDamping: Float = io.github.sceneview.gesture.CameraGestureDetector.DefaultCameraManipulator.DEFAULT_PINCH_ZOOM_DAMPING,
) : io.github.sceneview.gesture.CameraGestureDetector.CameraManipulator {
    private var fallback: io.github.sceneview.gesture.CameraGestureDetector.DefaultCameraManipulator? =
        null
    private var viewportW = 1
    private var viewportH = 1

    /**
     * Timestamp (from [System.nanoTime]) when the last user gesture released, or `0L`
     * if the user is still actively dragging / scrolling (or has never touched yet).
     * Used by [update] to know when to clear the [fallback] and resume the auto-orbit.
     */
    private var grabEndTimeNanos: Long = 0L

    fun isPaused(): Boolean = fallback != null

    private fun currentEye(): Position {
        val rad = Math.toRadians(yawProvider().toDouble()).toFloat()
        return Position(
            x = sin(rad) * radius + target.x,
            y = target.y + yHeight,
            z = cos(rad) * radius + target.z,
        )
    }

    private fun orbitTransform(): io.github.sceneview.math.Transform {
        val eye = currentEye()
        val mat = dev.romainguy.kotlin.math.lookAt(
            eye = eye,
            target = target,
            up = dev.romainguy.kotlin.math.Float3(0f, 1f, 0f),
        )
        return io.github.sceneview.math.Transform(mat)
    }

    private fun ensureFallback() {
        if (fallback == null) {
            // Capture the current orbit eye as the manipulator's home so the hand-off is
            // seamless — the first drag begins exactly where we stopped orbiting.
            fallback = io.github.sceneview.gesture.CameraGestureDetector.DefaultCameraManipulator(
                orbitHomePosition = currentEye(),
                targetPosition = target,
                pinchZoomSpeed = pinchZoomSpeed,
                pinchZoomDamping = pinchZoomDamping,
            ).also { it.setViewport(viewportW, viewportH) }
        }
        // A new gesture is starting — clear the "idle since" stamp so the resume timer
        // doesn't fire mid-drag.
        grabEndTimeNanos = 0L
    }

    override fun setViewport(width: Int, height: Int) {
        viewportW = width.coerceAtLeast(1)
        viewportH = height.coerceAtLeast(1)
        fallback?.setViewport(viewportW, viewportH)
    }

    override fun getTransform(): io.github.sceneview.math.Transform {
        val fb = fallback ?: return orbitTransform()
        // While the user is dragging (or in the post-drag resume window) the camera
        // pose comes from the stock Filament orbit manipulator, which does NOT clamp
        // its polar angle — a drag straight over the top/bottom of the model carries
        // the eye onto the orbit pole, the lookAt's fixed world-up collapses, and the
        // model snaps fully upside-down (#2487, Pixel 9 review). Re-derive the eye from
        // the manipulator's transform, clamp its pitch just shy of the poles, and
        // re-aim it at the (unchanged) orbit target so the flip can never happen. The
        // idle auto-orbit path (`orbitTransform`) sits at a gentle down-tilt well clear
        // of the poles and needs no clamp.
        val transform = fb.getTransform()
        val eye = transform.position
        val clampedEye = clampOrbitEyePitch(eye, target)
        if (clampedEye == eye) return transform
        val mat = dev.romainguy.kotlin.math.lookAt(
            eye = clampedEye,
            target = target,
            up = dev.romainguy.kotlin.math.Float3(0f, 1f, 0f),
        )
        return io.github.sceneview.math.Transform(mat)
    }

    override fun grabBegin(x: Int, y: Int, strafe: Boolean) {
        ensureFallback()
        fallback?.grabBegin(x, y, strafe)
    }

    override fun grabUpdate(x: Int, y: Int) {
        fallback?.grabUpdate(x, y)
    }

    override fun grabEnd() {
        fallback?.grabEnd()
        // Mark the moment the user released — `update` watches this timestamp and
        // clears the fallback after `resumeAfterMillis`, restoring the auto-orbit.
        grabEndTimeNanos = System.nanoTime()
    }

    override fun scrollBegin(x: Int, y: Int, separation: Float) {
        ensureFallback()
        fallback?.scrollBegin(x, y, separation)
    }

    override fun scrollUpdate(x: Int, y: Int, prevSeparation: Float, currSeparation: Float) {
        fallback?.scrollUpdate(x, y, prevSeparation, currSeparation)
    }

    override fun scrollEnd() {
        fallback?.scrollEnd()
        grabEndTimeNanos = System.nanoTime()
    }

    override fun update(deltaTime: Float) {
        fallback?.update(deltaTime)
        // Clear the fallback after `resumeAfterMillis` of post-gesture inactivity so the
        // auto-orbit resumes (#2225). `resumeAfterMillis <= 0L` disables the resume —
        // legacy behaviour where the fallback is never cleared.
        if (fallback != null && grabEndTimeNanos != 0L && resumeAfterMillis > 0L) {
            val idleNs = System.nanoTime() - grabEndTimeNanos
            if (idleNs > resumeAfterMillis * 1_000_000L) {
                fallback = null
                grabEndTimeNanos = 0L
            }
        }
    }
}

/**
 * Factory for [HeroOrbitCameraManipulator] that wires the idle-orbit yaw to a pausable
 * animator. Returns the manipulator ready to drop into a `SceneView(cameraManipulator = ...)`.
 *
 * In [DemoSettings.qaMode] the yaw is frozen at [staticYaw] so screenshot tests stay stable.
 *
 * ### Deep-link zoom override (#1571)
 *
 * When [DemoSettings.cameraDistance] is non-null — set from the `camera_distance` intent
 * extra (`adb --ef` or a Maestro launch argument, any Bundle type — #2652) or the
 * `sceneview://demo/<id>?cameraDistance=<f>` deep link — it replaces
 * [radius] as the orbit distance, so the device-QA harness can launch a demo at a near or
 * far framing without a pinch gesture (Maestro has none). When `null` the caller's [radius]
 * (typically per-demo auto-fit) is used unchanged, so showcase behaviour is unaffected.
 */
@Composable
fun rememberHeroOrbitCameraManipulator(
    trigger: Boolean,
    radius: Float = 2.5f,
    yHeight: Float = 0.5f,
    durationMillis: Int = 20_000,
    staticYaw: Float = 45f,
    target: Position = Position(0f, 0f, 0f),
    resumeAfterMillis: Long = 3_000L,
    pinchZoomSpeed: Float = io.github.sceneview.gesture.CameraGestureDetector.DefaultCameraManipulator.DEFAULT_PINCH_ZOOM_SPEED,
    pinchZoomDamping: Float = io.github.sceneview.gesture.CameraGestureDetector.DefaultCameraManipulator.DEFAULT_PINCH_ZOOM_DAMPING,
): HeroOrbitCameraManipulator {
    val anim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(trigger, DemoSettings.qaMode) {
        if (trigger && !DemoSettings.qaMode) {
            while (true) {
                anim.snapTo(0f)
                anim.animateTo(
                    targetValue = 360f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = durationMillis,
                        easing = androidx.compose.animation.core.LinearEasing,
                    ),
                )
            }
        }
    }
    // Deep-link zoom override (#1571): a non-null DemoSettings.cameraDistance wins over the
    // caller's auto-fit `radius`. Reading the Compose state here (not inside remember{})
    // keeps it a recomposition input; it is also a remember{} key so the manipulator is
    // rebuilt with the new orbit distance if the zoom changes (e.g. a warm-start onNewIntent).
    val effectiveRadius = DemoSettings.cameraDistance ?: radius
    return androidx.compose.runtime.remember(effectiveRadius, yHeight, target, resumeAfterMillis, pinchZoomSpeed, pinchZoomDamping) {
        HeroOrbitCameraManipulator(
            yawProvider = { if (DemoSettings.qaMode) staticYaw else anim.value },
            radius = effectiveRadius,
            yHeight = yHeight,
            target = target,
            resumeAfterMillis = resumeAfterMillis,
            pinchZoomSpeed = pinchZoomSpeed,
            pinchZoomDamping = pinchZoomDamping,
        )
    }
}

/** Default polar-angle floor (degrees from world +Y) used by [clampOrbitEyePitch]. */
internal const val DEFAULT_MIN_ORBIT_POLAR_DEGREES: Float = 1f

/** Default polar-angle ceiling (degrees from world +Y) used by [clampOrbitEyePitch]. */
internal const val DEFAULT_MAX_ORBIT_POLAR_DEGREES: Float = 179f

/**
 * Clamps an orbit camera [eye] so its **polar angle** — the angle between the
 * `target → eye` direction and world-up `+Y` — stays inside
 * `[minPolarDegrees, maxPolarDegrees]`, keeping the orbit **radius** and
 * **azimuth** unchanged. Returns the original [eye] unchanged when it is already
 * in range (the common case), when it coincides with [target] (degenerate
 * radius), or when any component is non-finite.
 *
 * ### Why (#2487)
 *
 * The hero viewer's user-drag path delegates to Filament's `ORBIT`-mode
 * `Manipulator`, which does not clamp its polar angle. A drag straight over the
 * top (or bottom) of the model carries the eye onto the orbit pole; the
 * `lookAt(eye, target, up = +Y)` that builds the view matrix then has its
 * forward axis parallel to `up`, the `right = cross(up, forward)` collapses to
 * zero, and the model snaps fully **upside-down** — the orbit / gimbal flip the
 * Pixel 9 review reported on the Explore 3D viewer. Clamping just **shy** of the
 * poles (default `[1°, 179°]`) lets the eye reach a near-top-down / near-bottom-up
 * view but never the exact singularity, so `lookAt` always stays well-defined.
 *
 * @param eye    orbit eye world position to clamp.
 * @param target orbit target the eye looks at / pivots around.
 */
internal fun clampOrbitEyePitch(
    eye: Position,
    target: Position,
    minPolarDegrees: Float = DEFAULT_MIN_ORBIT_POLAR_DEGREES,
    maxPolarDegrees: Float = DEFAULT_MAX_ORBIT_POLAR_DEGREES,
): Position {
    val dx = eye.x - target.x
    val dy = eye.y - target.y
    val dz = eye.z - target.z
    if (!dx.isFinite() || !dy.isFinite() || !dz.isFinite()) return eye

    val radius = sqrt(dx * dx + dy * dy + dz * dz)
    // Degenerate orbit (eye == target): no direction to clamp.
    if (radius <= 1e-6f) return eye

    // Polar angle from world +Y, in radians, in [0, π].
    val polar = acos((dy / radius).coerceIn(-1f, 1f))
    val minRad = Math.toRadians(minPolarDegrees.toDouble()).toFloat()
    val maxRad = Math.toRadians(maxPolarDegrees.toDouble()).toFloat()
    val clampedPolar = polar.coerceIn(minRad, maxRad)
    // Already within range — return the eye untouched (fast path).
    if (clampedPolar == polar) return eye

    val horizontal = sqrt(dx * dx + dz * dz)
    val newDy = radius * cos(clampedPolar)
    val newHorizontal = radius * sin(clampedPolar)
    // Preserve azimuth from the horizontal projection of the original direction.
    // If the eye sat exactly on the polar axis there is no azimuth to preserve;
    // fall back to +Z so the eye still lifts off the singularity.
    val hx: Float
    val hz: Float
    if (horizontal <= 1e-6f) {
        hx = 0f
        hz = 1f
    } else {
        hx = dx / horizontal
        hz = dz / horizontal
    }
    return Position(
        x = target.x + hx * newHorizontal,
        y = target.y + newDy,
        z = target.z + hz * newHorizontal,
    )
}
