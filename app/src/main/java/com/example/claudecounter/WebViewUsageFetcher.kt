package com.example.claudecounter

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.UUID

/**
 * Fetches /usage the same way the browser extension does: from inside a real
 * claude.ai page, using that page's own `fetch()` and cookies (see
 * extension_src/src/injected/bridge.js for the browser-side equivalent).
 *
 * Android WebView's fetch() runs through Chromium's network stack, producing a
 * TLS/HTTP fingerprint indistinguishable from a real browser. A plain
 * HttpURLConnection request does not, which is why Anthropic's bot detection
 * 403s it (see ClaudeApiService). This class trades that block for the cost of
 * keeping one hidden WebView loaded on claude.ai.
 *
 * All WebView calls happen on the main thread; callbacks are always delivered
 * on the main thread too.
 */
class WebViewUsageFetcher private constructor() {

    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var pageReady = false
    private val pendingOnReady = mutableListOf<() -> Unit>()
    private val resultCallbacks = mutableMapOf<String, (String?, String?) -> Unit>()

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(context: Context) {
        if (webView != null) return
        val wv = WebView(context.applicationContext)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.addJavascriptInterface(JsBridge(), JS_INTERFACE_NAME)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (pageReady) return
                pageReady = true
                val callbacks = pendingOnReady.toList()
                pendingOnReady.clear()
                callbacks.forEach { it() }
            }
        }
        wv.loadUrl("https://claude.ai/")
        webView = wv
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun onResult(requestId: String, json: String?, error: String?) {
            // WebView invokes @JavascriptInterface methods off the main thread.
            handler.post {
                resultCallbacks.remove(requestId)?.invoke(json, error)
            }
        }
    }

    /**
     * Fetches usage for [orgId]. [callback] receives either a raw JSON body or an
     * error message (never both), and always runs on the main thread.
     */
    fun fetchUsage(context: Context, orgId: String, callback: (json: String?, error: String?) -> Unit) {
        handler.post {
            ensureWebView(context)

            var resolved = false
            val requestId = UUID.randomUUID().toString()
            val safeCallback: (String?, String?) -> Unit = { json, error ->
                if (!resolved) {
                    resolved = true
                    callback(json, error)
                }
            }

            fun runFetch() {
                resultCallbacks[requestId] = safeCallback
                val url = "https://claude.ai/api/organizations/${orgId}/usage"
                val js = """
                    (function() {
                        fetch(${JSONObject.quote(url)}, { credentials: 'include' })
                            .then(function(r) {
                                if (!r.ok) {
                                    return r.text().then(function(t) {
                                        throw new Error('HTTP ' + r.status + ': ' + t);
                                    });
                                }
                                return r.text();
                            })
                            .then(function(text) {
                                $JS_INTERFACE_NAME.onResult(${JSONObject.quote(requestId)}, text, null);
                            })
                            .catch(function(e) {
                                $JS_INTERFACE_NAME.onResult(${JSONObject.quote(requestId)}, null, String(e && e.message || e));
                            });
                    })();
                """.trimIndent()
                webView?.evaluateJavascript(js, null)
            }

            if (pageReady) runFetch() else pendingOnReady.add(::runFetch)

            handler.postDelayed({
                if (!resolved) {
                    resultCallbacks.remove(requestId)
                    Log.w(TAG, "Timed out waiting for claude.ai page/fetch")
                    safeCallback(null, "Timed out waiting for claude.ai")
                }
            }, TIMEOUT_MS)
        }
    }

    companion object {
        private const val TAG = "WebViewUsageFetcher"
        private const val JS_INTERFACE_NAME = "AndroidUsageBridge"
        private const val TIMEOUT_MS = 25_000L

        @Volatile
        private var INSTANCE: WebViewUsageFetcher? = null

        fun getInstance(): WebViewUsageFetcher =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebViewUsageFetcher().also { INSTANCE = it }
            }
    }
}
