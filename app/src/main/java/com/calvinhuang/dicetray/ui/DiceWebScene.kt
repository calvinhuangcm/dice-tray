package com.calvinhuang.dicetray.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.calvinhuang.dicetray.DiceColor
import com.calvinhuang.dicetray.DiceRules
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

private const val ASSET_HOST = "appassets.androidplatform.net"
private const val SCENE_URL = "https://appassets.androidplatform.net/assets/dice/index.html"

interface DiceRendererListener {
    fun onReady()
    fun onRollFinished(requestId: Long)
    fun onSceneTapped()
    fun onError()
}

private class DiceWebViewHolder {
    var view: DiceWebView? = null
}

@Composable
fun DiceWebScene(
    modifier: Modifier = Modifier,
    requestId: Long,
    clearRequestId: Long,
    results: List<Int>,
    sides: Int,
    color: DiceColor,
    trailsEnabled: Boolean,
    tiltEnabled: Boolean,
    motionSensitivity: Float,
    listener: DiceRendererListener,
) {
    val currentListener = rememberUpdatedState(listener)
    val lifecycleOwner = LocalLifecycleOwner.current
    val webViewHolder = remember { DiceWebViewHolder() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            DiceWebView(context).also { webView ->
                webViewHolder.view = webView
                webView.rendererListener = object : DiceRendererListener {
                    override fun onReady() = currentListener.value.onReady()
                    override fun onRollFinished(requestId: Long) =
                        currentListener.value.onRollFinished(requestId)

                    override fun onSceneTapped() = currentListener.value.onSceneTapped()

                    override fun onError() = currentListener.value.onError()
                }
                webView.setLifecycleActive(
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                )
            }
        },
        update = { webView ->
            webView.rendererListener = object : DiceRendererListener {
                override fun onReady() = currentListener.value.onReady()
                override fun onRollFinished(requestId: Long) =
                    currentListener.value.onRollFinished(requestId)

                override fun onSceneTapped() = currentListener.value.onSceneTapped()

                override fun onError() = currentListener.value.onError()
            }
            webView.setMotionSensitivity(motionSensitivity)
            webView.setTiltEnabled(tiltEnabled)
            webView.clearScene(clearRequestId)
            if (results.isNotEmpty()) {
                webView.requestRoll(
                    requestId = requestId,
                    results = results,
                    sides = sides,
                    color = color,
                    trailsEnabled = trailsEnabled,
                )
            }
        },
    )

    DisposableEffect(lifecycleOwner, webViewHolder) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> webViewHolder.view?.setLifecycleActive(true)
                Lifecycle.Event.ON_STOP -> webViewHolder.view?.setLifecycleActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        webViewHolder.view?.setLifecycleActive(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewHolder.view?.release()
            webViewHolder.view = null
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private class DiceWebView(context: Context) : WebView(context) {
    var rendererListener: DiceRendererListener? = null

    private var lifecycleActive = true
    private var sensorsRegistered = false
    private var registeredSensorDelay: Int? = null
    private var released = false
    private var sceneReady = false
    private var lastSentRequestId = -1L
    private var lastSentClearRequestId = 0L
    private var pendingRoll: RollRequest? = null
    private var pendingClearRequestId: Long? = null
    private var tiltEnabled = false
    private var smoothedTiltX = 0f
    private var smoothedTiltY = 0f
    private var smoothedTiltZ = 1f
    private var smoothedShakeX = 0f
    private var smoothedShakeY = 0f
    private var smoothedShakeZ = 0f
    private var lastSentTiltX = Float.NaN
    private var lastSentTiltY = Float.NaN
    private var lastSentTiltZ = Float.NaN
    private var lastSentShakeX = Float.NaN
    private var lastSentShakeY = Float.NaN
    private var lastSentShakeZ = Float.NaN
    private var lastMotionDispatchAt = 0L
    private var gravityEstimateInitialized = false
    private val gravityEstimate = FloatArray(3)
    private var motionSensitivity = DiceRules.DEFAULT_MOTION_SENSITIVITY
    private val sensorsActive: Boolean
        get() = lifecycleActive && sensorsRegistered
    private val motionActive: Boolean
        get() = tiltEnabled && sensorsActive

    private companion object {
        // Baseline response. The in-app sensitivity slider adjusts this
        // within a bounded range to avoid turning ordinary hand tremor into
        // visible jitter.
        const val BASE_TILT_FILTER = 0.28f
        const val TILT_EPSILON = 0.012f
        const val SHAKE_FILTER = 0.38f
        const val SHAKE_EPSILON = 0.025f
        const val MOTION_DISPATCH_INTERVAL_MS = 16L
    }

    private val sensorManager = context
        .getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val linearAccelerationSensor =
        sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val orientationSensor = gravitySensor ?: accelerometerSensor
    private val shakeSensor = linearAccelerationSensor ?: accelerometerSensor

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!lifecycleActive || event.values.size < 3) return

            when (event.sensor.type) {
                Sensor.TYPE_GRAVITY -> {
                    updateTilt(event.values)
                }

                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    if (tiltEnabled) updateShake(event.values)
                }

                Sensor.TYPE_ACCELEROMETER -> {
                    // Always keep an app-side low-pass/high-pass pair. Some
                    // devices expose a virtual linear-acceleration sensor but
                    // deliver it too slowly for short hand movements; the raw
                    // accelerometer keeps shaking responsive in that case.
                    updateGravityEstimate(event.values)
                    updateTilt(gravityEstimate)
                    if (tiltEnabled) updateShakeFromAccelerometer(event.values)
                }
            }
            dispatchMotionIfNeeded()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun updateGravityEstimate(values: FloatArray) {
        if (values.size < 3) return
        if (!gravityEstimateInitialized) {
            values.copyInto(gravityEstimate, endIndex = 3)
            gravityEstimateInitialized = true
        } else {
            for (index in 0..2) {
                gravityEstimate[index] = gravityEstimate[index] * 0.90f + values[index] * 0.10f
            }
        }
    }

    private fun updateTilt(values: FloatArray) {
        if (values.size < 3) return
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val gravityX = when (rotation) {
            Surface.ROTATION_90 -> values[1]
            Surface.ROTATION_180 -> -values[0]
            Surface.ROTATION_270 -> -values[1]
            else -> values[0]
        }
        val gravityY = when (rotation) {
            Surface.ROTATION_90 -> -values[0]
            Surface.ROTATION_180 -> -values[1]
            Surface.ROTATION_270 -> values[0]
            else -> values[1]
        }
        val gravityZ = values[2]
        val magnitude = sqrt(
            gravityX * gravityX +
                gravityY * gravityY +
                gravityZ * gravityZ,
        )
        if (magnitude < 0.001f) return

        // Android sensor axes point opposite to the visual downhill direction.
        // First rotate the raw axes into the current display orientation, then
        // invert both horizontal components so tilting toward an edge moves
        // dice toward that same visual edge in portrait or landscape.
        val rawX = (-gravityX / magnitude).coerceIn(-1f, 1f)
        val rawY = (-gravityY / magnitude).coerceIn(-1f, 1f)
        // The absolute normal component tells the renderer how far the device
        // is from edge-on. Keep it positive so the virtual tray never flips
        // underneath the user when the screen briefly faces down.
        val rawZ = (abs(gravityZ) / magnitude).coerceIn(0f, 1f)
        val filter = (BASE_TILT_FILTER * motionSensitivity)
            .coerceIn(0.14f, 0.56f)
        smoothedTiltX = (smoothedTiltX * (1f - filter) + rawX * filter)
            .coerceIn(-1f, 1f)
        smoothedTiltY = (smoothedTiltY * (1f - filter) + rawY * filter)
            .coerceIn(-1f, 1f)
        smoothedTiltZ = (smoothedTiltZ * (1f - filter) + rawZ * filter)
            .coerceIn(0f, 1f)
    }

    private fun updateShakeFromAccelerometer(values: FloatArray) {
        if (values.size < 3 || !gravityEstimateInitialized) return
        val linearX = values[0] - gravityEstimate[0]
        val linearY = values[1] - gravityEstimate[1]
        val linearZ = values[2] - gravityEstimate[2]
        updateShakeValues(linearX, linearY, linearZ)
    }

    private fun updateShake(values: FloatArray) {
        if (values.size < 3) return
        updateShakeValues(values[0], values[1], values[2])
    }

    private fun updateShakeValues(accelerationX: Float, accelerationY: Float, accelerationZ: Float) {
        // Linear acceleration is converted to g units and inverted for the
        // inertial direction felt by dice inside the moving phone.
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val screenAccelerationX = when (rotation) {
            Surface.ROTATION_90 -> accelerationY
            Surface.ROTATION_180 -> -accelerationX
            Surface.ROTATION_270 -> -accelerationY
            else -> accelerationX
        }
        val screenAccelerationY = when (rotation) {
            Surface.ROTATION_90 -> -accelerationX
            Surface.ROTATION_180 -> -accelerationY
            Surface.ROTATION_270 -> accelerationX
            else -> accelerationY
        }
        val rawX = (-screenAccelerationX / SensorManager.GRAVITY_EARTH)
            .coerceIn(-3f, 3f)
        val rawY = (-screenAccelerationY / SensorManager.GRAVITY_EARTH)
            .coerceIn(-3f, 3f)
        val rawZ = (-accelerationZ / SensorManager.GRAVITY_EARTH).coerceIn(-2f, 2f)
        smoothedShakeX = smoothedShakeX * (1f - SHAKE_FILTER) + rawX * SHAKE_FILTER
        smoothedShakeY = smoothedShakeY * (1f - SHAKE_FILTER) + rawY * SHAKE_FILTER
        smoothedShakeZ = smoothedShakeZ * (1f - SHAKE_FILTER) + rawZ * SHAKE_FILTER
    }

    private fun dispatchMotionIfNeeded() {
        if (!sensorsActive) return
        val now = SystemClock.uptimeMillis()
        if (now - lastMotionDispatchAt < MOTION_DISPATCH_INTERVAL_MS) return

        val tiltChanged = lastSentTiltX.isNaN() ||
            abs(smoothedTiltX - lastSentTiltX) >= TILT_EPSILON ||
            abs(smoothedTiltY - lastSentTiltY) >= TILT_EPSILON ||
            abs(smoothedTiltZ - lastSentTiltZ) >= TILT_EPSILON
        val shakeChanged = lastSentShakeX.isNaN() ||
            abs(smoothedShakeX - lastSentShakeX) >= SHAKE_EPSILON ||
            abs(smoothedShakeY - lastSentShakeY) >= SHAKE_EPSILON ||
            abs(smoothedShakeZ - lastSentShakeZ) >= SHAKE_EPSILON
        if (!tiltChanged && !shakeChanged) return

        lastMotionDispatchAt = now
        lastSentTiltX = smoothedTiltX
        lastSentTiltY = smoothedTiltY
        lastSentTiltZ = smoothedTiltZ
        lastSentShakeX = smoothedShakeX
        lastSentShakeY = smoothedShakeY
        lastSentShakeZ = smoothedShakeZ
        sendMotionToScene(
            tiltX = smoothedTiltX,
            tiltY = smoothedTiltY,
            tiltZ = smoothedTiltZ,
            shakeX = smoothedShakeX,
            shakeY = smoothedShakeY,
            shakeZ = smoothedShakeZ,
            enabled = motionActive,
        )
    }

    private val bridge = object {
        @JavascriptInterface
        fun onReady() {
            post {
                if (!released) {
                    sceneReady = true
                    dispatchPendingClear()
                    sendMotionToScene(
                        tiltX = smoothedTiltX,
                        tiltY = smoothedTiltY,
                        tiltZ = smoothedTiltZ,
                        shakeX = smoothedShakeX,
                        shakeY = smoothedShakeY,
                        shakeZ = smoothedShakeZ,
                        enabled = motionActive,
                    )
                    rendererListener?.onReady()
                    dispatchPendingRoll()
                }
            }
        }

        @JavascriptInterface
        fun onRollFinished(requestId: Long) {
            post {
                if (!released) {
                    rendererListener?.onRollFinished(requestId)
                }
            }
        }

        @JavascriptInterface
        fun onSceneTap() {
            post {
                if (!released) {
                    rendererListener?.onSceneTapped()
                }
            }
        }

        @JavascriptInterface
        fun onError() {
            post {
                if (!released) {
                    rendererListener?.onError()
                }
            }
        }
    }

    init {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(Color.rgb(17, 24, 32))
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.safeBrowsingEnabled = true
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(context),
            )
            .build()
        webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                return request.url.host != ASSET_HOST
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                if (!released) {
                    this@DiceWebView.post {
                        if (!released) {
                            rendererListener?.onError()
                        }
                    }
                }
                // The renderer is no longer usable. Compose will recreate the
                // WebView after the user retries, so do not let WebView
                // continue with its default process-crash handling.
                return true
            }
        }
        webChromeClient = WebChromeClient()
        addJavascriptInterface(bridge, "AndroidDiceBridge")
        loadUrl(SCENE_URL)
    }

    fun setTiltEnabled(enabled: Boolean) {
        if (released) return
        if (tiltEnabled == enabled) {
            updateSensorRegistration()
            return
        }
        tiltEnabled = enabled
        updateSensorRegistration()
        if (!enabled) resetShakeState()
        sendCurrentMotion(enabled = motionActive)
    }

    fun setLifecycleActive(active: Boolean) {
        if (released || lifecycleActive == active) return
        lifecycleActive = active
        if (active) {
            onResume()
            updateSensorRegistration()
            sendCurrentMotion(enabled = motionActive)
        } else {
            unregisterSensors()
            onPause()
            sendCurrentMotion(enabled = false)
        }
    }

    private fun updateSensorRegistration() {
        if (released || !lifecycleActive || sensorManager == null || orientationSensor == null) {
            unregisterSensors()
            return
        }
        val requestedDelay = if (tiltEnabled) {
            SensorManager.SENSOR_DELAY_GAME
        } else {
            // Camera horizon tracking only needs UI-rate gravity updates. The
            // higher-rate stream is reserved for active dice physics.
            SensorManager.SENSOR_DELAY_UI
        }
        if (sensorsRegistered && registeredSensorDelay == requestedDelay) return
        if (sensorsRegistered) unregisterSensors()

        val sensors = if (tiltEnabled) {
            listOfNotNull(
                orientationSensor,
                shakeSensor,
                accelerometerSensor,
            ).distinctBy { it.type }
        } else {
            listOf(orientationSensor)
        }
        var registered = false
        for (sensor in sensors) {
            if (sensorManager.registerListener(
                    sensorListener,
                    sensor,
                    requestedDelay,
                )
            ) {
                registered = true
            }
        }
        sensorsRegistered = registered
        registeredSensorDelay = if (registered) requestedDelay else null
    }

    private fun unregisterSensors() {
        if (sensorsRegistered) {
            sensorManager?.unregisterListener(sensorListener)
            sensorsRegistered = false
        }
        registeredSensorDelay = null
    }

    private fun resetShakeState() {
        smoothedShakeX = 0f
        smoothedShakeY = 0f
        smoothedShakeZ = 0f
        lastSentShakeX = Float.NaN
        lastSentShakeY = Float.NaN
        lastSentShakeZ = Float.NaN
    }

    private fun sendCurrentMotion(enabled: Boolean) {
        sendMotionToScene(
            tiltX = smoothedTiltX,
            tiltY = smoothedTiltY,
            tiltZ = smoothedTiltZ,
            shakeX = smoothedShakeX,
            shakeY = smoothedShakeY,
            shakeZ = smoothedShakeZ,
            enabled = enabled,
        )
    }

    fun setMotionSensitivity(value: Float) {
        val next = DiceRules.sanitizeMotionSensitivity(value)
        if (abs(motionSensitivity - next) < 0.001f) return
        motionSensitivity = next
        sendMotionToScene(
            tiltX = smoothedTiltX,
            tiltY = smoothedTiltY,
            tiltZ = smoothedTiltZ,
            shakeX = smoothedShakeX,
            shakeY = smoothedShakeY,
            shakeZ = smoothedShakeZ,
            enabled = motionActive,
        )
    }

    private fun sendMotionToScene(
        tiltX: Float,
        tiltY: Float,
        tiltZ: Float,
        shakeX: Float,
        shakeY: Float,
        shakeZ: Float,
        enabled: Boolean,
    ) {
        if (released) return
        val tiltXText = String.format(Locale.US, "%.4f", tiltX)
        val tiltYText = String.format(Locale.US, "%.4f", tiltY)
        val tiltZText = String.format(Locale.US, "%.4f", tiltZ)
        val shakeXText = String.format(Locale.US, "%.4f", shakeX)
        val shakeYText = String.format(Locale.US, "%.4f", shakeY)
        val shakeZText = String.format(Locale.US, "%.4f", shakeZ)
        val sensitivityText = String.format(Locale.US, "%.3f", motionSensitivity)
        val enabledText = enabled.toString()
        post {
            if (!released) {
                evaluateJavascript(
                    "window.DiceScene && window.DiceScene.setMotion && " +
                        "window.DiceScene.setMotion(" +
                        "{tiltX:$tiltXText,tiltY:$tiltYText,tiltZ:$tiltZText," +
                        "shakeX:$shakeXText,shakeY:$shakeYText,shakeZ:$shakeZText," +
                        "sensitivity:$sensitivityText," +
                        "enabled:$enabledText});",
                    null,
                )
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (released) return
        updateSensorRegistration()
        sendCurrentMotion(enabled = motionActive)
    }

    override fun onDetachedFromWindow() {
        unregisterSensors()
        if (!released) {
            sendCurrentMotion(enabled = false)
        }
        super.onDetachedFromWindow()
    }

    fun release() {
        if (released) return
        released = true
        lifecycleActive = false
        unregisterSensors()
        rendererListener = null
        pendingRoll = null
        pendingClearRequestId = null
        sceneReady = false
        stopLoading()
        onPause()
        removeJavascriptInterface("AndroidDiceBridge")
        webChromeClient = null
        destroy()
    }

    fun requestRoll(
        requestId: Long,
        results: List<Int>,
        sides: Int,
        color: DiceColor,
        trailsEnabled: Boolean,
    ) {
        if (released) return
        if (requestId <= 0L || requestId == lastSentRequestId) return
        pendingRoll = RollRequest(requestId, results, sides, color.cssColor, trailsEnabled)
        dispatchPendingRoll()
    }

    fun clearScene(requestId: Long) {
        if (released) return
        if (requestId <= 0L || requestId == lastSentClearRequestId) return
        pendingClearRequestId = requestId
        pendingRoll = null
        dispatchPendingClear()
    }

    private fun dispatchPendingClear() {
        if (released) return
        val requestId = pendingClearRequestId ?: return
        if (!sceneReady || requestId == lastSentClearRequestId) return

        evaluateJavascript(
            "window.DiceScene && window.DiceScene.clear && window.DiceScene.clear();",
            null,
        )
        lastSentClearRequestId = requestId
        pendingClearRequestId = null
    }

    private fun dispatchPendingRoll() {
        if (released) return
        val roll = pendingRoll ?: return
        if (!sceneReady || roll.requestId == lastSentRequestId) return

        val resultJson = roll.results.joinToString(separator = ",")
        val script = "window.DiceScene.roll({" +
            "id:" + roll.requestId + "," +
            "sides:" + roll.sides + "," +
            "results:[" + resultJson + "]," +
            "color:'" + roll.cssColor + "'," +
            "trails:" + roll.trailsEnabled +
            "});"
        evaluateJavascript(script, null)
        lastSentRequestId = roll.requestId
        pendingRoll = null
    }

    private data class RollRequest(
        val requestId: Long,
        val results: List<Int>,
        val sides: Int,
        val cssColor: String,
        val trailsEnabled: Boolean,
    )
}
