package com.example

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Custom ultra-fast WebViewClient with data-saving, aggressive tracking/ad blocking,
 * and robust external intent handling compatible with Android 5.0+ (API 21+).
 */
class FastWebClient(
    private val context: Context,
    private val listener: BrowserClientListener
) : WebViewClient() {

    interface BrowserClientListener {
        fun onPageStarted(url: String, favicon: Bitmap?)
        fun onPageFinished(url: String, title: String?)
        fun onUrlChanged(url: String, isSecure: Boolean)
        fun onProgressUpdate(progress: Int)
        fun onErrorReceived(errorCode: Int, description: String, failingUrl: String)
    }

    var isDataSaverEnabled: Boolean = true

    // Common tracking and ad networks to block when Data Saver is enabled
    private val blockedHostKeywords = arrayOf(
        "doubleclick.net",
        "googlesyndication.com",
        "google-analytics.com",
        "adservice.google.com",
        "adnxs.com",
        "criteo.com",
        "scorecardresearch.com",
        "facebook.net/tr",
        "analytics.twitter.com",
        "quantserve.com",
        "outbrain.com",
        "taboola.com",
        "pubmatic.com",
        "rubiconproject.com",
        "hotjar.com",
        "clarity.ms",
        "moatads.com"
    )

    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return handleUrlOverride(view, Uri.parse(url))
    }

    @TargetApi(Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return handleUrlOverride(view, request.url)
    }

    private fun handleUrlOverride(view: WebView, uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false

        // Standard web navigation
        if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data" || scheme == "javascript") {
            return false
        }

        // External app schemes (tel, mailto, sms, market, intent, etc.)
        return try {
            val intent = if (scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Intent couldn't be handled by any installed app
            true
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        val isSecure = url.startsWith("https://", ignoreCase = true)
        listener.onUrlChanged(url, isSecure)
        listener.onPageStarted(url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        val isSecure = url.startsWith("https://", ignoreCase = true)
        listener.onUrlChanged(url, isSecure)
        listener.onPageFinished(url, view.title)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (isDataSaverEnabled) {
            val host = request.url.host?.lowercase() ?: ""
            for (blocked in blockedHostKeywords) {
                if (host.contains(blocked)) {
                    // Return empty response to block tracker/heavy ad script
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
        if (isDataSaverEnabled) {
            val lowerUrl = url.lowercase()
            for (blocked in blockedHostKeywords) {
                if (lowerUrl.contains(blocked)) {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
            }
        }
        return super.shouldInterceptRequest(view, url)
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            listener.onErrorReceived(
                error.errorCode,
                error.description.toString(),
                request.url.toString()
            )
        }
    }

    @Suppress("DEPRECATION")
    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String,
        failingUrl: String
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        listener.onErrorReceived(errorCode, description, failingUrl)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // By default, do not automatically ignore SSL errors to maintain security
        handler.cancel()
        listener.onErrorReceived(
            -1,
            "SSL Certificate Error: Connection is not secure",
            view.url ?: ""
        )
    }
}
