package com.qihang.vivobridge;

import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Draw gate for verified vivo clock branches. Never removes views or touch targets. */
final class NativeClockController implements ViewTreeObserver.OnPreDrawListener,
        ViewTreeObserver.OnGlobalLayoutListener, View.OnAttachStateChangeListener {
    private static final String TAG = "HyperAliveVivo";
    private static final Map<View, NativeClockController> ROOTS = new WeakHashMap<>();
    private static final Map<View, WeakReference<NativeClockController>> TARGETS = new WeakHashMap<>();
    private final WeakReference<View> root;
    private final List<WeakReference<View>> clocks = new ArrayList<>();
    private ViewTreeObserver observer;
    private long nextStateCheck;
    private boolean sleeping;
    private boolean replacing;

    private NativeClockController(View view) {
        root = new WeakReference<>(view);
        view.addOnAttachStateChangeListener(this);
        if (view.isAttachedToWindow()) onViewAttachedToWindow(view);
    }

    static void install() throws Exception {
        Method attach = View.class.getDeclaredMethod("onAttachedToWindow");
        attach.setAccessible(true);
        XposedBridge.hookMethod(attach, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                View view = (View) p.thisObject;
                if (!"com.qihang.aod.FlymeClockView".equals(view.getClass().getName())) return;
                View top = view.getRootView();
                NativeClockController controller = ROOTS.get(top);
                if (controller == null) {
                    controller = new NativeClockController(top);
                    ROOTS.put(top, controller);
                }
                controller.onGlobalLayout();
            }
        });
        Method draw = ViewGroup.class.getDeclaredMethod("drawChild",
                android.graphics.Canvas.class, View.class, long.class);
        draw.setAccessible(true);
        XposedBridge.hookMethod(draw, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                WeakReference<NativeClockController> ref = TARGETS.get((View) p.args[1]);
                NativeClockController controller = ref == null ? null : ref.get();
                if (controller != null && controller.replacing
                        && ((View) p.args[1]).getRootView() == controller.root.get()) p.setResult(Boolean.FALSE);
            }
        });
        Log.i(TAG, "OriginOS clock draw gate installed; waits for visible custom AOD clock");
    }

    @Override public void onGlobalLayout() {
        View top = root.get();
        if (top == null) return;
        clocks.clear();
        collect(top, false);
        nextStateCheck = 0;
    }

    private void collect(View view, boolean inVivoAod) {
        if ("com.qihang.aod.FlymeClockView".equals(view.getClass().getName())) {
            clocks.add(new WeakReference<>(view));
            return;
        }
        inVivoAod |= view.getClass().getName().startsWith("com.vivo.aod_keyguard.");
        try {
            if (view.getId() != View.NO_ID && BridgePolicy.isNativeClockBranch(
                    view.getResources().getResourcePackageName(view.getId()),
                    view.getResources().getResourceEntryName(view.getId()), inVivoAod)) {
                TARGETS.put(view, new WeakReference<>(this));
                return; // Whole time/date branch is gated; keep sibling notifications.
            }
        } catch (android.content.res.Resources.NotFoundException ignored) {}
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), inVivoAod);
        }
    }

    @Override public boolean onPreDraw() {
        try {
            View top = root.get();
            if (top == null) return true;
            long now = SystemClock.uptimeMillis();
            if (now >= nextStateCheck) {
                sleeping = top.getDisplay() != null && VivoBridge.sleeping(
                        top.getContext(), top.getDisplay().getDisplayId());
                nextStateCheck = now + 100;
            }
            boolean ready = false;
            if (sleeping) {
                for (WeakReference<View> ref : clocks) {
                    View clock = ref.get();
                    if (clock != null && Integer.valueOf(0).equals(VivoBridge.field(clock, "mClockMode"))
                            && visible(clock) && hasTimeText(clock)) {
                        ready = true;
                        break;
                    }
                }
            }
            if (ready != replacing) {
                replacing = ready;
                Log.i(TAG, ready ? "Custom AOD clock ready; gated vivo time/date branches"
                        : "Native clock drawing restored");
            }
        } catch (Throwable error) {
            replacing = false; // Failed readiness checks always leave the system clock available.
            Log.e(TAG, "Clock readiness unavailable", error);
        }
        return true;
    }

    private static boolean visible(View view) {
        if (!view.isAttachedToWindow() || !view.isShown() || view.getWidth() <= 0
                || view.getHeight() <= 0 || view.getWindowVisibility() != View.VISIBLE) return false;
        View node = view;
        while (node != null) {
            if (node.getAlpha() <= 0.01f || node.getTransitionAlpha() <= 0.01f) return false;
            ViewParent parent = node.getParent();
            node = parent instanceof View ? (View) parent : null;
        }
        return true;
    }

    private static boolean hasTimeText(View clock) throws Exception {
        // A date or battery label alone is insufficient to replace the system time.
        return timeTextReady(VivoBridge.field(clock, "mSingleTimeView"))
                || (timeTextReady(VivoBridge.field(clock, "mHourView"))
                    && timeTextReady(VivoBridge.field(clock, "mMinuteView")));
    }

    private static boolean timeTextReady(Object value) {
        if (!(value instanceof TextView)) return false;
        TextView text = (TextView) value;
        return visible(text) && text.getText().length() > 0;
    }

    @Override public void onViewAttachedToWindow(View view) {
        observer = view.getViewTreeObserver();
        observer.addOnPreDrawListener(this);
        observer.addOnGlobalLayoutListener(this);
        onGlobalLayout();
    }

    @Override public void onViewDetachedFromWindow(View view) {
        replacing = false;
        clocks.clear();
        if (observer != null && observer.isAlive()) {
            observer.removeOnPreDrawListener(this);
            observer.removeOnGlobalLayoutListener(this);
        }
        observer = null;
    }
}
