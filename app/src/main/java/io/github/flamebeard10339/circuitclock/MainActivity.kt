package io.github.flamebeard10339.circuitclock

import android.annotation.SuppressLint
import android.app.Activity
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

private const val PAGE_URL = "https://appassets.androidplatform.net/assets/workout-timer.html"

/**
 * Circuit Clock is a single self-contained HTML document in `assets/`. This activity is
 * only a shell around it.
 *
 * The app holds no INTERNET permission, so the WebView cannot reach the network even if
 * the page asked it to. The page is served through [WebViewAssetLoader] rather than from a
 * `file://` URL: that gives it a real origin, which is what makes localStorage durable and
 * the page a secure context. The loader answers out of the APK's own assets, before
 * anything would reach a socket — which is why it works with no permission at all.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var tts: TextToSpeech? = null

    /** Written on the TTS init callback, read from the JavaScript bridge thread. */
    @Volatile
    private var ttsReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.getDefault())
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
            settings.domStorageEnabled = true                  // the workout library lives here
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

        // restoreState returns null when there is nothing to restore, in which case the
        // WebView would sit blank — so fall through to loading the page.
        val restored = savedInstanceState?.let { web.restoreState(it) }
        if (restored == null) web.loadUrl(PAGE_URL)
    }

    /**
     * The two things the page cannot do for itself inside a WebView.
     *
     * Exposing an interface to WebView content is only dangerous when that content is
     * untrusted. Here it is a file shipped inside the APK, served from an origin the app
     * itself answers, with all navigation blocked. minSdk 26 also puts this well past the
     * API-16 reflection flaw that gave `addJavascriptInterface` its reputation.
     */
    private inner class Host {

        /** Called by the page when a workout starts and when it ends. */
        @JavascriptInterface
        fun keepAwake(on: Boolean) = runOnUiThread {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        /** Android WebView ships no Web Speech API, so names go to the system engine. */
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
        web.removeJavascriptInterface("AndroidHost")
        web.destroy()
        super.onDestroy()
    }
}
