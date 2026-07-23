package com.example.claudecounter

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

/**
 * Full-screen WebView that opens claude.ai so the user can log in normally.
 * Once the `lastActiveOrg` cookie appears we extract both the org ID and
 * the full cookie string, save them, and finish.
 */
class LoginWebViewActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        val webView = WebView(this).also { wv ->
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
            }
            cookieManager.setAcceptThirdPartyCookies(wv, true)

            wv.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean = false   // let WebView handle all navigation

                override fun onPageFinished(view: WebView, url: String?) {
                    checkForOrgCookie(cookieManager)
                }
            }
        }

        setContentView(webView)
        webView.loadUrl("https://claude.ai/login")
    }

    private fun checkForOrgCookie(cookieManager: CookieManager) {
        val rawCookie = cookieManager.getCookie("https://claude.ai") ?: return
        val orgId = rawCookie
            .split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("lastActiveOrg=") }
            ?.removePrefix("lastActiveOrg=")
            ?.trim()

        if (!orgId.isNullOrBlank()) {
            // Persist auth
            SessionManager.getInstance(applicationContext).saveAuth(orgId, rawCookie)

            // Start the polling service
            val svcIntent = Intent(this, UsagePollingService::class.java)
            startForegroundService(svcIntent)

            setResult(RESULT_OK)
            finish()
        }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, LoginWebViewActivity::class.java)
    }
}
