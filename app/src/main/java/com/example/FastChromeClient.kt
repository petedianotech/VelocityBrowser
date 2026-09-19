package com.example

import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Custom WebChromeClient handling page progress, titles, favicons,
 * HTML5 full-screen video playback, file uploads, and JS dialogs without crashes.
 */
class FastChromeClient(
    private val listener: ChromeListener
) : WebChromeClient() {

    interface ChromeListener {
        fun onProgress(progress: Int)
        fun onTitle(title: String?)
        fun onFavicon(icon: Bitmap?)
        fun onShowFullScreen(customView: View, callback: CustomViewCallback)
        fun onHideFullScreen()
        fun onShowFileChooser(filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean
        fun onJsAlert(message: String, result: JsResult): Boolean
        fun onJsConfirm(message: String, result: JsResult): Boolean
    }

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        listener.onProgress(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        listener.onTitle(title)
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        listener.onFavicon(icon)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        if (view != null && callback != null) {
            listener.onShowFullScreen(view, callback)
        }
    }

    override fun onHideCustomView() {
        super.onHideCustomView()
        if (customView == null) return
        listener.onHideFullScreen()
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        if (filePathCallback != null && fileChooserParams != null) {
            return listener.onShowFileChooser(filePathCallback, fileChooserParams)
        }
        return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        if (message != null && result != null) {
            return listener.onJsAlert(message, result)
        }
        return super.onJsAlert(view, url, message, result)
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        if (message != null && result != null) {
            return listener.onJsConfirm(message, result)
        }
        return super.onJsConfirm(view, url, message, result)
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        callback?.invoke(origin, false, false)
    }
}

