package com.muvidate.app;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;

import java.util.Locale;

public class MainActivity extends BridgeActivity {

    private View customFullscreenView = null;
    private WebChromeClient.CustomViewCallback customViewCallback = null;
    private boolean isVideoFullscreen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.setStatusBarColor(Color.parseColor("#0A0A0A"));
            window.setNavigationBarColor(Color.parseColor("#0A0A0A"));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        final View decorView = window.getDecorView();
        decorView.setBackgroundColor(Color.parseColor("#0A0A0A"));

        // Manage all portrait system-bar + IME insets natively on decorView so WebView
        // always starts directly below the status bar with zero duplicate CSS top gap,
        // and smoothly resizes above the soft keyboard while fullscreen video fills edge-to-edge.
        ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

            if (isVideoFullscreen) {
                if (v.getPaddingLeft() != 0 || v.getPaddingTop() != 0
                        || v.getPaddingRight() != 0 || v.getPaddingBottom() != 0) {
                    v.setPadding(0, 0, 0, 0);
                }
                updateSafeAreaCssZero();
                updateFullscreenCutoutCssFromInsets(
                        insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout())
                );
            } else {
                int topPadding = Math.max(systemBars.top, cutout.top);
                int leftPadding = Math.max(systemBars.left, cutout.left);
                int rightPadding = Math.max(systemBars.right, cutout.right);
                int bottomPadding = keyboardVisible
                        ? ime.bottom
                        : Math.max(systemBars.bottom, cutout.bottom);

                if (v.getPaddingLeft() != leftPadding || v.getPaddingTop() != topPadding
                        || v.getPaddingRight() != rightPadding || v.getPaddingBottom() != bottomPadding) {
                    v.setPadding(leftPadding, topPadding, rightPadding, bottomPadding);
                }
                updateSafeAreaCssZero();
            }

            // Clear systemBars, displayCutout, and ime insets passed to the WebView so Chromium
            // neither adds duplicate CSS safe-area offsets nor double-resizes on keyboard open.
            return new WindowInsetsCompat.Builder(insets)
                    .setInsets(
                            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout(),
                            Insets.NONE
                    )
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                    .setVisible(WindowInsetsCompat.Type.ime(), false)
                    .build();
        });

        if (getBridge() != null && getBridge().getWebView() != null) {
            WebView webView = getBridge().getWebView();
            webView.setBackgroundColor(Color.parseColor("#0A0A0A"));
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            webView.setHorizontalScrollBarEnabled(false);
            webView.addJavascriptInterface(new MuviDateNativeBridge(), "MuviDateNative");

            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptThirdPartyCookies(webView, true);

            WebSettings settings = webView.getSettings();
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setSupportZoom(false);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);

            webView.setWebChromeClient(new BridgeWebChromeClient(getBridge()) {
                @Override
                public void onShowCustomView(View view, CustomViewCallback callback) {
                    if (customFullscreenView != null) {
                        callback.onCustomViewHidden();
                        return;
                    }
                    customFullscreenView = view;
                    customViewCallback = callback;

                    runOnUiThread(() -> {
                        FrameLayout rootLayout = findViewById(android.R.id.content);
                        if (rootLayout != null) {
                            view.setBackgroundColor(Color.BLACK);
                            rootLayout.addView(
                                    view,
                                    new FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                            );
                        }
                        applyFullscreenSystemUi(true);
                    });
                }

                @Override
                public void onHideCustomView() {
                    if (customFullscreenView == null) {
                        return;
                    }
                    final View viewToRemove = customFullscreenView;
                    final CustomViewCallback callbackToNotify = customViewCallback;
                    customFullscreenView = null;
                    customViewCallback = null;

                    runOnUiThread(() -> {
                        FrameLayout rootLayout = findViewById(android.R.id.content);
                        if (rootLayout != null) {
                            rootLayout.removeView(viewToRemove);
                        }
                        if (callbackToNotify != null) {
                            callbackToNotify.onCustomViewHidden();
                        }
                        applyFullscreenSystemUi(false);
                    });
                }
            });
        }

        ViewCompat.requestApplyInsets(decorView);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isVideoFullscreen && customFullscreenView == null) {
            applyFullscreenSystemUi(false);
        } else {
            applyFullscreenSystemUi(true);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isVideoFullscreen) {
            getWindow().getDecorView().post(this::updateFullscreenCutoutCss);
        } else {
            ViewCompat.requestApplyInsets(getWindow().getDecorView());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            CookieManager.getInstance().flush();
        } catch (Exception ignored) {
        }
    }

    private void applyFullscreenSystemUi(boolean fullscreen) {
        isVideoFullscreen = fullscreen;
        Window window = getWindow();
        View decorView = window.getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }

        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, decorView);

        if (fullscreen) {
            decorView.setPadding(0, 0, 0, 0);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
                controller.hide(WindowInsetsCompat.Type.systemBars());
            }
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            decorView.post(this::updateFullscreenCutoutCss);
        } else {
            if (controller != null) {
                controller.show(WindowInsetsCompat.Type.systemBars());
                controller.setAppearanceLightStatusBars(false);
                controller.setAppearanceLightNavigationBars(false);
            }
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            clearFullscreenCutoutCss();
            ViewCompat.requestApplyInsets(decorView);
        }
    }

    private void updateSafeAreaCssZero() {
        if (getBridge() == null || getBridge().getWebView() == null) {
            return;
        }
        String script = "try{const s=document.documentElement.style;s.setProperty('--safe-area-inset-top','0px');s.setProperty('--safe-area-inset-right','0px');s.setProperty('--safe-area-inset-bottom','0px');s.setProperty('--safe-area-inset-left','0px');}catch(e){}";
        getBridge().getWebView().post(() -> {
            if (getBridge() != null && getBridge().getWebView() != null) {
                getBridge().getWebView().evaluateJavascript(script, null);
            }
        });
    }

    private void updateFullscreenCutoutCss() {
        if (!isVideoFullscreen || getBridge() == null || getBridge().getWebView() == null) {
            return;
        }
        WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(getWindow().getDecorView());
        if (rootInsets == null) {
            return;
        }
        updateFullscreenCutoutCssFromInsets(
                rootInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout())
        );
    }

    private void updateFullscreenCutoutCssFromInsets(Insets cutout) {
        if (!isVideoFullscreen || getBridge() == null || getBridge().getWebView() == null || cutout == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        if (density <= 0f) density = 1f;

        int topPx = Math.round(cutout.top / density);
        int rightPx = Math.round(cutout.right / density);
        int bottomPx = Math.round(cutout.bottom / density);
        int leftPx = Math.round(cutout.left / density);

        String script = String.format(
                Locale.US,
                "try{const s=document.documentElement.style;s.setProperty('--cutout-inset-top','%dpx');s.setProperty('--cutout-inset-right','%dpx');s.setProperty('--cutout-inset-bottom','%dpx');s.setProperty('--cutout-inset-left','%dpx');}catch(e){}",
                topPx, rightPx, bottomPx, leftPx
        );
        getBridge().getWebView().post(() -> {
            if (getBridge() != null && getBridge().getWebView() != null) {
                getBridge().getWebView().evaluateJavascript(script, null);
            }
        });
    }

    private void clearFullscreenCutoutCss() {
        if (getBridge() == null || getBridge().getWebView() == null) {
            return;
        }
        String script = "try{const s=document.documentElement.style;s.removeProperty('--cutout-inset-top');s.removeProperty('--cutout-inset-right');s.removeProperty('--cutout-inset-bottom');s.removeProperty('--cutout-inset-left');}catch(e){}";
        getBridge().getWebView().post(() -> {
            if (getBridge() != null && getBridge().getWebView() != null) {
                getBridge().getWebView().evaluateJavascript(script, null);
            }
        });
    }

    private class MuviDateNativeBridge {
        @JavascriptInterface
        public void setKeepScreenOn(final boolean keepOn) {
            runOnUiThread(() -> {
                if (keepOn) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        @JavascriptInterface
        public void setFullscreenMode(final boolean fullscreen) {
            runOnUiThread(() -> applyFullscreenSystemUi(fullscreen));
        }

        @JavascriptInterface
        public void openExternalUrl(final String url) {
            if (url == null || url.trim().isEmpty()) return;
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
