package com.example

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.getcapacitor.BridgeActivity
import com.getcapacitor.BridgeWebChromeClient
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : BridgeActivity() {

  private var customFullscreenView: View? = null
  private var customViewCallback: CustomViewCallbackAlias? = null
  private var isVideoFullscreen: Boolean = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.parseColor("#0A0A0A")
    window.navigationBarColor = Color.parseColor("#0A0A0A")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val lp = window.attributes
      lp.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      window.attributes = lp
    }

    WindowCompat.getInsetsController(window, window.decorView).apply {
      isAppearanceLightStatusBars = false
      isAppearanceLightNavigationBars = false
    }

    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    val decorView = window.decorView
    decorView.setBackgroundColor(Color.parseColor("#0A0A0A"))
    decorView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
      if (isVideoFullscreen) {
        if (v.paddingLeft != 0 || v.paddingTop != 0 || v.paddingRight != 0 || v.paddingBottom != 0) {
          v.setPadding(0, 0, 0, 0)
        }
        updateFullscreenCutoutCss()
      }
    }

    bridge?.webView?.let { webView ->
      webView.setBackgroundColor(Color.parseColor("#0A0A0A"))
      webView.overScrollMode = View.OVER_SCROLL_NEVER
      webView.isHorizontalScrollBarEnabled = false
      webView.addJavascriptInterface(MuviDateNativeBridge(), "MuviDateNative")

      CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(webView, true)
      }

      webView.settings.apply {
        mediaPlaybackRequiresUserGesture = false
        domStorageEnabled = true
        databaseEnabled = true
        allowFileAccess = true
        allowContentAccess = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
      }

      webView.webChromeClient = object : BridgeWebChromeClient(bridge) {
        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
          if (view == null) return
          if (customFullscreenView != null) {
            callback?.onCustomViewHidden()
            return
          }
          customFullscreenView = view
          customViewCallback = callback

          runOnUiThread {
            val rootLayout = findViewById<FrameLayout>(android.R.id.content)
            view.setBackgroundColor(Color.BLACK)
            rootLayout?.addView(
              view,
              FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )
            )
            applyFullscreenSystemUi(true)
          }
        }

        override fun onHideCustomView() {
          val viewToRemove = customFullscreenView ?: return
          val callbackToNotify = customViewCallback
          customFullscreenView = null
          customViewCallback = null

          runOnUiThread {
            val rootLayout = findViewById<FrameLayout>(android.R.id.content)
            rootLayout?.removeView(viewToRemove)
            callbackToNotify?.onCustomViewHidden()
            applyFullscreenSystemUi(false)
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (!isVideoFullscreen && customFullscreenView == null) {
      applyFullscreenSystemUi(false)
    } else {
      applyFullscreenSystemUi(true)
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if (isVideoFullscreen) {
      window.decorView.post { updateFullscreenCutoutCss() }
    }
  }

  override fun onPause() {
    super.onPause()
    try {
      CookieManager.getInstance().flush()
    } catch (_: Exception) {
    }
  }

  private fun applyFullscreenSystemUi(fullscreen: Boolean) {
    isVideoFullscreen = fullscreen
    val decorView = window.decorView

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val lp = window.attributes
      lp.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      window.attributes = lp
    }

    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowCompat.getInsetsController(window, decorView)

    if (fullscreen) {
      decorView.setPadding(0, 0, 0, 0)
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      controller.hide(WindowInsetsCompat.Type.systemBars())
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      decorView.post { updateFullscreenCutoutCss() }
    } else {
      controller.show(WindowInsetsCompat.Type.systemBars())
      controller.isAppearanceLightStatusBars = false
      controller.isAppearanceLightNavigationBars = false
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      clearFullscreenCutoutCss()
      ViewCompat.requestApplyInsets(decorView)
    }
  }

  private fun updateFullscreenCutoutCss() {
    val webView = bridge?.webView ?: return
    if (!isVideoFullscreen) return
    val rootInsets = ViewCompat.getRootWindowInsets(window.decorView) ?: return
    val cutout = rootInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout())
    val density = resources.displayMetrics.density.let { if (it > 0f) it else 1f }

    val topPx = (cutout.top / density).roundToInt()
    val rightPx = (cutout.right / density).roundToInt()
    val bottomPx = (cutout.bottom / density).roundToInt()
    val leftPx = (cutout.left / density).roundToInt()

    val script = String.format(
      Locale.US,
      "try{const s=document.documentElement.style;s.setProperty('--cutout-inset-top','%dpx');s.setProperty('--cutout-inset-right','%dpx');s.setProperty('--cutout-inset-bottom','%dpx');s.setProperty('--cutout-inset-left','%dpx');}catch(e){}",
      topPx, rightPx, bottomPx, leftPx
    )
    webView.evaluateJavascript(script, null)
  }

  private fun clearFullscreenCutoutCss() {
    val webView = bridge?.webView ?: return
    val script =
      "try{const s=document.documentElement.style;s.removeProperty('--cutout-inset-top');s.removeProperty('--cutout-inset-right');s.removeProperty('--cutout-inset-bottom');s.removeProperty('--cutout-inset-left');}catch(e){}"
    webView.evaluateJavascript(script, null)
  }

  private inner class MuviDateNativeBridge {
    @JavascriptInterface
    fun setKeepScreenOn(keepOn: Boolean) {
      runOnUiThread {
        if (keepOn) {
          window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
          window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
      }
    }

    @JavascriptInterface
    fun setFullscreenMode(fullscreen: Boolean) {
      runOnUiThread { applyFullscreenSystemUi(fullscreen) }
    }

    @JavascriptInterface
    fun openExternalUrl(url: String?) {
      if (url.isNullOrBlank()) return
      runOnUiThread {
        try {
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          startActivity(intent)
        } catch (_: Exception) {
        }
      }
    }
  }
}

private typealias CustomViewCallbackAlias = android.webkit.WebChromeClient.CustomViewCallback
