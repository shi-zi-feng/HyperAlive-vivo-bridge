package com.qihang.vivobridge;

import android.util.Log;
import java.lang.reflect.Method;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Lets vivo consume OFF in setAodDisplayState instead of stranding it in fake DOZE. */
final class NativeAodLifecycle {
    private static final String TAG = "HyperAliveVivo";
    private static final String EXTRA = "vivobridge.displayDepth";
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<Integer>();

    static boolean inDisplayCallback() {
        Integer depth = DEPTH.get();
        return depth != null && depth > 0;
    }

    static void install(ClassLoader loader) {
        int count = 0;
        // Highest priority enters before HyperAlive's before-hooks and exits after them.
        // No arguments, display states, brightness values or results are changed here.
        XC_MethodHook scope = new XC_MethodHook(10000) {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Integer previous = DEPTH.get();
                p.setObjectExtra(EXTRA, previous == null ? 0 : previous);
                DEPTH.set(previous == null ? 1 : previous + 1);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object previous = p.getObjectExtra(EXTRA);
                if (!(previous instanceof Integer) || ((Integer) previous) == 0) DEPTH.remove();
                else DEPTH.set((Integer) previous);
            }
        };
        for (String name : new String[]{"com.android.server.display.DisplayPowerController",
                "com.android.server.display.DisplayPowerControllerImpl",
                "com.android.server.display.DisplayPowerState"}) {
            try {
                Class<?> cls = Class.forName(name, false, loader);
                for (Method method : cls.getDeclaredMethods()) {
                    if (!("setScreenState".equals(method.getName())
                            || (name.endsWith(".DisplayPowerState")
                                && "prepareColorFade".equals(method.getName())))) continue;
                    method.setAccessible(true);
                    XposedBridge.hookMethod(method, scope);
                    count++;
                }
            } catch (ClassNotFoundException ignored) {
                // DPCImpl is vendor-dependent.
            } catch (Throwable error) { Log.e(TAG, "Native AOD scope failed: " + name, error); }
        }
        try {
            Class<?> cls = Class.forName("com.android.server.display.DisplayPowerState", false, loader);
            Method method = cls.getDeclaredMethod("setAodDisplayState", int.class);
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (!Integer.valueOf(0).equals(VivoBridge.field(p.thisObject, "mDisplayId"))) return;
                        Log.i(TAG, "Native AOD request=" + p.args[0]
                                + " frameworkState=" + VivoBridge.field(p.thisObject, "mScreenState")
                                + " acceptedAodState=" + VivoBridge.field(p.thisObject, "mAodstate"));
                    } catch (Throwable error) { Log.w(TAG, "Native AOD trace unavailable", error); }
                }
            });
        } catch (Throwable error) { Log.w(TAG, "Native AOD entry unavailable", error); }
        Log.i(TAG, "Native AOD lifecycle scope hooks=" + count);
    }
}
