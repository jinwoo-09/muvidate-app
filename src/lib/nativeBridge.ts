import { Capacitor } from "@capacitor/core";
import { App as CapApp } from "@capacitor/app";
import { Browser } from "@capacitor/browser";
import { Network } from "@capacitor/network";
import { ScreenOrientation } from "@capacitor/screen-orientation";
import { StatusBar, Style } from "@capacitor/status-bar";
import { SplashScreen } from "@capacitor/splash-screen";

interface AndroidBridgeInterface {
  setKeepScreenOn?: (keepOn: boolean) => void;
  setFullscreenMode?: (fullscreen: boolean) => void;
  openExternalUrl?: (url: string) => void;
}

declare global {
  interface Window {
    MuviDateNative?: AndroidBridgeInterface;
  }
}

export function isNativeAndroid(): boolean {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === "android";
}

export function isNativeApp(): boolean {
  return Capacitor.isNativePlatform();
}

/**
 * Returns the base URL for backend API routes such as /api/diagnose-media.
 * On web deployments (Vercel / Express), returns "" (same-origin).
 * Inside the offline-bundled Android APK, uses VITE_API_BASE_URL or the hosted MuviDate API endpoint.
 */
export function getApiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;
  if (typeof configured === "string" && configured.trim() !== "") {
    return configured.trim().replace(/\/+$/, "");
  }
  if (isNativeApp()) {
    return "https://muvidate.vercel.app";
  }
  return "";
}

/**
 * Returns the public shareable URL for a watch room.
 * Ensures Android APK shares a valid web room link instead of https://localhost.
 */
export function getShareableRoomUrl(roomCode: string): string {
  if (isNativeApp()) {
    const publicBase =
      (typeof import.meta.env.VITE_PUBLIC_WEB_URL === "string" &&
        import.meta.env.VITE_PUBLIC_WEB_URL.trim().replace(/\/+$/, "")) ||
      "https://muvidate.vercel.app";
    return `${publicBase}/?room=${roomCode}`;
  }
  return window.location.href;
}

/**
 * Opens an external URL (e.g. developer Instagram link) in the device's native browser or app.
 */
export async function openExternalUrl(url: string): Promise<void> {
  if (!isNativeApp()) {
    window.open(url, "_blank", "noopener,noreferrer");
    return;
  }
  try {
    if (window.MuviDateNative?.openExternalUrl) {
      window.MuviDateNative.openExternalUrl(url);
      return;
    }
    await Browser.open({ url });
  } catch (err) {
    console.warn("Failed to open external URL via native browser:", err);
    window.open(url, "_blank", "noopener,noreferrer");
  }
}

/**
 * Hides the native Android splash screen once the local app UI is mounted and ready.
 */
export async function hideNativeSplash(): Promise<void> {
  if (!isNativeApp()) return;
  try {
    await SplashScreen.hide({ fadeOutDuration: 250 });
  } catch {
    // Safe fallback
  }
}

/**
 * Initializes native Android status bar styling and locks portrait orientation on app startup.
 */
export async function configureNativeSystemBars(): Promise<void> {
  if (!isNativeApp()) return;
  try {
    await ScreenOrientation.lock({ orientation: "portrait" }).catch(() => {});
    await StatusBar.setStyle({ style: Style.Dark });
    await StatusBar.setOverlaysWebView({ overlay: true });
    await StatusBar.setBackgroundColor({ color: "#0a0a0a" });
  } catch {
    // Safe fallback
  }
}

/**
 * Prevents the Android screen from sleeping while playing video.
 */
export function setKeepScreenOn(keepOn: boolean): void {
  if (!isNativeApp()) return;
  try {
    window.MuviDateNative?.setKeepScreenOn?.(keepOn);
  } catch {
    // Safe fallback
  }
}

/**
 * Enters immersive landscape fullscreen on Android (hides system bars + sensor-based landscape + wake lock).
 */
export async function enterNativeFullscreen(): Promise<void> {
  if (typeof document !== "undefined") {
    document.documentElement.dataset.videoFullscreen = "true";
  }
  if (!isNativeApp()) return;
  try {
    const hasNativeBridge = !!window.MuviDateNative?.setFullscreenMode;
    window.MuviDateNative?.setFullscreenMode?.(true);
    window.MuviDateNative?.setKeepScreenOn?.(true);
    await StatusBar.hide().catch(() => {});
    if (!hasNativeBridge) {
      await ScreenOrientation.lock({ orientation: "landscape" }).catch(() => {});
    }
  } catch {
    // Safe fallback
  }
}

/**
 * Restores portrait orientation and system bars when exiting fullscreen video.
 */
export async function exitNativeFullscreen(): Promise<void> {
  if (typeof document !== "undefined") {
    delete document.documentElement.dataset.videoFullscreen;
  }
  if (!isNativeApp()) return;
  try {
    window.MuviDateNative?.setFullscreenMode?.(false);
    await ScreenOrientation.lock({ orientation: "portrait" }).catch(() => {});
    await StatusBar.show().catch(() => {});
    await StatusBar.setStyle({ style: Style.Dark }).catch(() => {});
    await StatusBar.setOverlaysWebView({ overlay: true }).catch(() => {});
    await StatusBar.setBackgroundColor({ color: "#0a0a0a" }).catch(() => {});
  } catch {
    // Safe fallback
  }
}

/**
 * Subscribes to native network connectivity changes (with browser online/offline fallback).
 */
export function subscribeToNetworkStatus(
  onStatusChange: (connected: boolean) => void
): () => void {
  let active = true;

  const handleBrowserOnline = () => active && onStatusChange(true);
  const handleBrowserOffline = () => active && onStatusChange(false);

  window.addEventListener("online", handleBrowserOnline);
  window.addEventListener("offline", handleBrowserOffline);

  if (typeof navigator !== "undefined") {
    onStatusChange(navigator.onLine);
  }

  let capHandle: { remove: () => Promise<void> } | null = null;

  if (isNativeApp()) {
    Network.getStatus()
      .then((status) => {
        if (active) onStatusChange(status.connected);
      })
      .catch(() => {});

    Network.addListener("networkStatusChange", (status) => {
      if (active) onStatusChange(status.connected);
    })
      .then((handle) => {
        if (active) {
          capHandle = handle;
        } else {
          handle.remove().catch(() => {});
        }
      })
      .catch(() => {});
  }

  return () => {
    active = false;
    window.removeEventListener("online", handleBrowserOnline);
    window.removeEventListener("offline", handleBrowserOffline);
    capHandle?.remove().catch(() => {});
  };
}

/**
 * Registers the Android hardware back button listener.
 */
export function registerAndroidBackHandler(
  handler: () => void
): () => void {
  if (!isNativeAndroid()) return () => {};
  let active = true;
  let listenerHandle: { remove: () => Promise<void> } | null = null;

  CapApp.addListener("backButton", () => {
    if (active) handler();
  })
    .then((handle) => {
      if (active) {
        listenerHandle = handle;
      } else {
        handle.remove().catch(() => {});
      }
    })
    .catch(() => {});

  return () => {
    active = false;
    listenerHandle?.remove().catch(() => {});
  };
}

export async function exitNativeApp(): Promise<void> {
  if (!isNativeAndroid()) return;
  try {
    await CapApp.exitApp();
  } catch {
    // Safe fallback
  }
}
