package org.circuitclock.timer

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.util.Locale

/**
 * Circuit Clock is a single self-contained HTML document in `assets/`. This activity is
 * only a shell around it.
 *
 * The app holds no INTERNET permission, so the WebView cannot reach the network even if
 * the page asked it to. The page is served through [WebViewAssetLoader] rather than a
 * `file://` URL: that gives it a real https origin, which makes localStorage durable and
 * the page a secure context. The loader answers from the APK's assets; nothing leaves
 * the device.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsReady = true
            }
        }

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF10151B.toInt())
            overScrollMode = WebView.OVER_SCROLL_NEVER

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true          // the workout library lives in localStorage
            settings.mediaPlaybackRequiresUserGesture = false  // cues follow the user's own Start tap
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

                // Nothing in this app navigates anywhere. Refuse everything.
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean = true
            }

            addJavascriptInterface(Host(), "AndroidHost")
        }

        setContentView(web)

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            web.loadUrl("https://appassets.androidplatform.net/assets/workout-timer.html")
        }
    }

    /**
     * The two things the page cannot do for itself inside a WebView.
     *
     * Exposing an interface to WebView content is only dangerous when that content is
     * untrusted; here it is a file shipped inside the APK, loaded from an origin the app
     * itself serves, with all navigation blocked. minSdk 26 also puts this well past the
     * API-16 reflection flaw that gave `addJavascriptInterface` its reputation.
     */
    private inner class Host {
        /** The page calls this when a workout starts and stops. */
        @JavascriptInterface
        fun keepAwake(on: Boolean) = runOnUiThread {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        /** Android WebView has no Web Speech API, so section names go to the system engine. */
        @JavascriptInterface
        fun speak(text: String, volume: Float) {
            val engine = tts ?: return
            if (!ttsReady) return
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "circuitclock")
        }

        @JavascriptInterface
        fun stopSpeaking() {
            tts?.stop()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Suppress("DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        // The page pushes a history entry per screen, so back walks runner -> editor -> library.
        if (web.canGoBack()) web.goBack() else finish()
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) tts?.stop()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            web.removeJavascriptInterface("AndroidHost")
        }
        web.destroy()
        super.onDestroy()
    }
}
