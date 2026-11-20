package com.qihang.vivobridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import com.hyperalive.aod.ClockSettings;
import com.hyperalive.aod.FlymeClockView;
import com.hyperalive.aod.hook.StateCoordinator;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Installs the module clock inside the existing primary NightPearl clock window. */
final class NativeAodContent implements View.OnAttachStateChangeListener,
        ViewTreeObserver.OnPreDrawListener {
    private static final WeakHashMap<View, NativeAodContent> ROOTS = new WeakHashMap<>();
    private static final WeakHashMap<View, WeakReference<NativeAodContent>> CLOCK_BRANCHES = new WeakHashMap<>();
    private final ViewGroup root;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FlymeClockView clock;
    private BroadcastReceiver receiver, queryReceiver;
    private boolean attached, active, replacing, hadReplacement, errorLogged;
    private int attempts;
    private long publishedEpoch;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!attached) return;
            update();
            if (--attempts > 0) handler.postDelayed(this, 100L);
        }
    };

    static void install() throws Exception {
        Method draw = ViewGroup.class.getDeclaredMethod("drawChild", android.graphics.Canvas.class,
                View.class, long.class);
        draw.setAccessible(true);
        XposedBridge.hookMethod(draw, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                View child = (View) p.args[1];
                WeakReference<NativeAodContent> ref = CLOCK_BRANCHES.get(child);
                NativeAodContent owner = ref == null ? null : ref.get();
                if (owner != null && owner.replacing && child.getRootView() == owner.root)
                    p.setResult(Boolean.FALSE);
            }
        });
    }

    static void watch(View root) {
        if (!(root instanceof ViewGroup) || ROOTS.containsKey(root)) return;
        // The device factory wraps the primary ScreenOffClockView in a FrameLayout.
        // Charging, fingerprint, secondary-display and notification windows are not targets.
        ViewGroup group = (ViewGroup) root;
        boolean found = false;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (BridgePolicy.isNightPearlClock(group.getChildAt(i).getClass().getName())) found = true;
        }
        if (!found) return;
        NativeAodContent controller = new NativeAodContent(group);
        ROOTS.put(root, controller);
        root.addOnAttachStateChangeListener(controller);
        if (root.isAttachedToWindow()) controller.onViewAttachedToWindow(root);
    }

    private NativeAodContent(ViewGroup root) { this.root = root; }

    private void update() {
        try {
            boolean next = root.getDisplay() != null && VivoBridge.sleeping(root.getContext(),
                    root.getDisplay().getDisplayId());
            ClockSettings.Config config = ClockSettings.get(root.getContext(), 0);
            boolean custom = next && config.enabled && !StateCoordinator.isFullScreenAod(root.getContext());
            if (custom && clock == null) {
                clock = new FlymeClockView(root.getContext());
                clock.setId(View.generateViewId());
                clock.setTag("vivo_alive_native_aod_clock");
                root.addView(clock, new ViewGroup.LayoutParams(-1, -1));
                clock.setClockMode(0, false);
                Log.i("HyperAliveVivo", "Module clock attached inside native NightPearl AOD window");
            }
            if (clock != null) {
                if (custom) {
                    clock.setClockMode(0, false);
                    if (clock.getVisibility() != View.VISIBLE) clock.setVisibility(View.VISIBLE);
                } else if (clock.getVisibility() != View.GONE) clock.setVisibility(View.GONE);
            }
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (BridgePolicy.isNightPearlClock(child.getClass().getName()))
                    CLOCK_BRANCHES.put(child, new WeakReference<>(this));
            }
            boolean ready = custom && NativeClockController.clockReady(clock, 0);
            boolean hostReady = BridgePolicy.mayPublishNativeClock(
                    VivoBridge.ownsAod(root.getContext().getContentResolver()), next, config.enabled, ready);
            hadReplacement |= ready;
            // Keep the old themed style out of the wake animation while the system dismisses its window.
            ready |= !next && hadReplacement && config.enabled
                    && VivoBridge.ownsAod(root.getContext().getContentResolver());
            if (ready != replacing) {
                replacing = ready;
                for (View branch : CLOCK_BRANCHES.keySet()) {
                    if (branch.getRootView() == root) branch.invalidate();
                }
                root.invalidate();
                Log.i("HyperAliveVivo", "NightPearl module replacement=" + ready);
            }
            long sleepEpoch = VivoBridge.propLong("sys.qihang.sleep_at");
            if (hostReady != active || (hostReady && sleepEpoch != publishedEpoch)) {
                active = hostReady;
                publishedEpoch = sleepEpoch;
                NativeAodSession.update(root.getContext(), root, active);
            }
            errorLogged = false;
        } catch (Throwable error) {
            boolean redrawNative = replacing;
            replacing = false;
            if (clock != null) clock.setVisibility(View.GONE);
            if (active) {
                active = false;
                try { NativeAodSession.update(root.getContext(), root, false); }
                catch (Throwable ignored) {}
            }
            if (redrawNative) root.invalidate();
            if (!errorLogged) Log.e("HyperAliveVivo", "Native AOD content unavailable; keeping native clock", error);
            errorLogged = true;
        }
    }

    private void refresh() {
        attempts = 15;
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override public boolean onPreDraw() { update(); return true; }

    @Override public void onViewAttachedToWindow(View view) {
        if (attached) return;
        attached = true;
        try {
        Context context = root.getContext();
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { refresh(); }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_WALLPAPER_CHANGED);
        filter.addAction(ClockSettings.ACTION_STYLE_CHANGED);
        filter.addAction(com.hyperalive.aod.AodStyleSettings.ACTION_STYLE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33)
            context.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_EXPORTED);
        else context.registerReceiver(receiver, filter, null, handler);
        queryReceiver = NativeAodSession.listenQueries(context);
        root.getViewTreeObserver().addOnPreDrawListener(this);
        refresh();
        } catch (Throwable error) {
            Log.e("HyperAliveVivo", "Native AOD listener unavailable", error);
            onViewDetachedFromWindow(view);
        }
    }

    @Override public void onViewDetachedFromWindow(View view) {
        if (!attached) return;
        attached = false;
        active = replacing = false;
        handler.removeCallbacks(refresh);
        if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
        try { if (receiver != null) root.getContext().unregisterReceiver(receiver); }
        catch (Throwable ignored) {}
        try { if (queryReceiver != null) root.getContext().unregisterReceiver(queryReceiver); }
        catch (Throwable ignored) {}
        try { NativeAodSession.update(root.getContext(), root, false); }
        catch (Throwable error) { Log.w("HyperAliveVivo", "Native AOD handoff cleanup failed", error); }
        // Release the root: detached windows can be reattached and get a new controller.
        root.removeOnAttachStateChangeListener(this);
        if (clock != null) root.removeView(clock);
        ROOTS.remove(root);
    }
}
