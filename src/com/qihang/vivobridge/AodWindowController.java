package com.qihang.vivobridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import java.lang.ref.WeakReference;

/** Watches only a known primary-display AOD window, on its original UI thread. */
final class AodWindowController implements View.OnAttachStateChangeListener {
    private static final String TAG = "HyperAliveVivo";
    private final WeakReference<View> reference;
    private final WindowManager manager;
    private final Context context;
    private final Handler handler;
    private final boolean overlay;
    private final boolean maskOnly;
    private WindowManager.LayoutParams params;
    private boolean registered, closed, applying, patched, observing, listening;
    private int retries, originalFormat;
    private boolean originalShowWallpaper;
    private float originalAlpha;
    private Drawable originalBackground;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (closed) return;
            try {
                if (apply() && manager != null && reference.get() != null) {
                    View view = reference.get();
                    if (view != null && view.isAttachedToWindow()) {
                        applying = true;
                        try { manager.updateViewLayout(view, params); }
                        finally { applying = false; }
                    }
                }
            } catch (Throwable error) {
                Log.e(TAG, "AOD window update failed", error);
            }
            // Bounded retries cover the power-event/property ordering race.
            // No frame listener, permanent polling or wake lock is used.
            if (--retries > 0) handler.postDelayed(this, 100L);
        }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) { refresh(); }
    };
    private final ContentObserver observer;

    AodWindowController(View view, WindowManager manager, boolean overlay) {
        this(view, manager, overlay, false);
    }

    AodWindowController(View maskView) {
        this(maskView, null, false, true);
    }

    private AodWindowController(View view, WindowManager manager, boolean overlay, boolean maskOnly) {
        reference = new WeakReference<>(view);
        this.manager = manager;
        Context app = view.getContext().getApplicationContext();
        context = app != null ? app : view.getContext();
        Looper looper = Looper.myLooper();
        if (looper == null) throw new IllegalStateException("AOD window has no UI looper");
        handler = new Handler(looper);
        this.overlay = overlay;
        this.maskOnly = maskOnly;
        observer = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) { refresh(); }
        };
        view.addOnAttachStateChangeListener(this);
        if (view.isAttachedToWindow()) onViewAttachedToWindow(view);
    }

    void watch(WindowManager.LayoutParams current) {
        if (closed || applying) return;
        params = current;
        // NightPearl is a before-hook: the original call sends changed params.
        // The black overlay is an after-hook and must relayout on the next UI turn.
        if (!overlay) {
            try { apply(); }
            catch (Throwable error) { Log.e(TAG, "AOD window patch failed", error); }
        }
        // Defer relayout until the original addView/updateViewLayout has returned.
        refresh();
    }

    private boolean apply() {
        View view = reference.get();
        if (view == null || (!maskOnly && params == null)) return false;
        int displayId = view.getDisplay() == null ? -1 : view.getDisplay().getDisplayId();
        boolean active = VivoBridge.sleeping(view.getContext(), displayId);
        if (active) {
            if (!patched) {
                if (!maskOnly) {
                    originalAlpha = params.alpha;
                    originalFormat = params.format;
                    originalShowWallpaper = (params.flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) != 0;
                }
                originalBackground = view.getBackground();
                patched = true;
                Log.i(TAG, maskOnly ? "Cleared OriginOS AOD mask background" : overlay
                        ? "Primary black overlay exposed" : "AOD wallpaper exposed; native clock retained");
            }
            if (maskOnly) {
                if (view.getBackground() != null) view.setBackground(null);
                return false;
            }
            boolean changed;
            if (overlay) {
                changed = params.alpha != 0f;
                params.alpha = 0f;
            } else {
                changed = (params.flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) == 0
                        || params.format != PixelFormat.TRANSLUCENT;
                params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
                params.format = PixelFormat.TRANSLUCENT;
                // Keep the native clock, notifications, touch targets and its alpha animations.
                if (view.getBackground() != null) view.setBackground(null);
            }
            return changed;
        }
        return restore(view);
    }

    private boolean restore(View view) {
        if (!patched || (!maskOnly && params == null)) return false;
        if (maskOnly) {
            if (view.getBackground() == null) view.setBackground(originalBackground);
        } else if (overlay) {
            if (params.alpha == 0f) params.alpha = originalAlpha;
        } else {
            params.flags = BridgePolicy.restoreWallpaperFlag(params.flags, originalShowWallpaper,
                    WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            if (params.format == PixelFormat.TRANSLUCENT) params.format = originalFormat;
            if (view.getBackground() == null) view.setBackground(originalBackground);
        }
        patched = false;
        originalBackground = null;
        Log.i(TAG, "Restored " + (maskOnly ? "OriginOS AOD mask" : overlay ? "black overlay" : "AOD window"));
        return true;
    }

    private void refresh() {
        if (closed) return;
        retries = 12;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override public void onViewAttachedToWindow(View view) {
        if (registered || closed) return;
        registered = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_WALLPAPER_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter, null, handler);
            }
            listening = true;
        } catch (Throwable error) { Log.e(TAG, "AOD screen receiver unavailable", error); }
        try {
            context.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor("screen_off_remind"), false, observer);
            observing = true;
            context.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor("lockscreen_wallpaper_res"), false, observer);
        } catch (Throwable error) { Log.e(TAG, "AOD setting observer unavailable", error); }
        refresh();
    }

    @Override public void onViewDetachedFromWindow(View view) { close(); }

    void close() {
        if (closed) return;
        closed = true;
        handler.removeCallbacks(tick);
        View view = reference.get();
        if (view != null) {
            restore(view);
            view.removeOnAttachStateChangeListener(this);
        }
        if (listening) {
            try { context.unregisterReceiver(receiver); }
            catch (Throwable error) { Log.w(TAG, "AOD receiver cleanup failed", error); }
        }
        if (observing) {
            try { context.getContentResolver().unregisterContentObserver(observer); }
            catch (Throwable error) { Log.w(TAG, "AOD observer cleanup failed", error); }
        }
        registered = false;
    }

    boolean isClosed() { return closed; }
}
