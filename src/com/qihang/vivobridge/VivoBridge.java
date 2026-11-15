package com.qihang.vivobridge;

import android.app.WallpaperInfo;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Companion for HyperAlive 1.0.3; does not replace or re-sign the original APK. */
public final class VivoBridge implements IXposedHookLoadPackage {
    private static final String TAG = "HyperAliveVivo";
    private static final Map<View, AodWindowController> WINDOWS =
            Collections.synchronizedMap(new WeakHashMap<View, AodWindowController>());
    private static boolean installed;
    private static boolean reportedSetting;
    private static boolean reportedNativeLifecycle;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (installed || !isVivo()) return;
        String pkg = lp.packageName;
        if (!("android".equals(pkg) || "com.android.systemui".equals(pkg)
                || "com.vivo.nightpearl".equals(pkg))) return;
        installed = true;
        try {
            if ("android".equals(pkg) || "com.android.systemui".equals(pkg)) {
                installSettingBridge();
                installAmbientSupport();
            }
            if ("android".equals(pkg)) {
                NativeAodLifecycle.install(lp.classLoader);
                installDisplayOverlay(lp.classLoader);
            }
            if ("com.android.systemui".equals(pkg)) {
                installSystemUiMasks();
                installAodWindow("SystemUI");
                NativeClockController.install();
            }
            if ("com.vivo.nightpearl".equals(pkg)) installAodWindow("NightPearl");
            Log.i(TAG, "Installed in " + pkg + " SDK=" + Build.VERSION.SDK_INT);
        } catch (Throwable t) {
            Log.e(TAG, "Installation failed in " + pkg, t);
        }
    }

    private static boolean isVivo() {
        String vendor = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(Locale.ROOT);
        return vendor.contains("vivo") || vendor.contains("iqoo");
    }

    static boolean ownsAod(ContentResolver cr) {
        try {
            return BridgePolicy.ownsAod(isVivo(),
                    Settings.System.getInt(cr, "screen_off_remind", 0) == 1,
                    Settings.System.getString(cr, "lockscreen_wallpaper_res"));
        } catch (Throwable t) { return false; }
    }

    static boolean sleeping(Context context, int displayId) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && BridgePolicy.mayExposeWallpaper(
                    ownsAod(context.getContentResolver()), pm.isInteractive(), displayId,
                    propLong("sys.qihang.sleep_at"), propLong("sys.qihang.wake_at"));
        } catch (Throwable t) { return false; }
    }

    private static long propLong(String name) {
        try {
            Class<?> props = Class.forName("android.os.SystemProperties");
            return (Long) props.getMethod("getLong", String.class, long.class)
                    .invoke(null, name, 0L);
        } catch (Throwable t) { return 0; }
    }

    private static void installSettingBridge() {
        int count = hook(Settings.Secure.class, "getInt", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length < 2 || !(p.args[0] instanceof ContentResolver)
                        || !"doze_always_on".equals(p.args[1])
                        || !BridgePolicy.isModuleSettingRead(Thread.currentThread().getStackTrace())) return;
                ContentResolver cr = (ContentResolver) p.args[0];
                try {
                    // Only HyperAlive's call sees this mapping. No global setting is written.
                    String lock = Settings.System.getString(cr, "lockscreen_wallpaper_res");
                    if (lock == null || !lock.contains("com.qihang.aod/")) return;
                    int nativeSetting = Settings.System.getInt(cr, "screen_off_remind", 0);
                    if (nativeSetting == 1 && NativeAodLifecycle.inDisplayCallback()
                            && BridgePolicy.isModuleDisplayCallback(Thread.currentThread().getStackTrace())) {
                        // Disable only the original OFF->DOZE / color-fade shortcut.
                        // StateCoordinator's sleep/wake commands still see AOD enabled.
                        p.setResult(0);
                        if (!reportedNativeLifecycle) {
                            reportedNativeLifecycle = true;
                            Log.i(TAG, "Bypassed HyperAlive display shortcut; vivo owns AOD power lifecycle");
                        }
                        return;
                    }
                    p.setResult(nativeSetting == 1 ? 1 : 0);
                    if (!reportedSetting) {
                        reportedSetting = true;
                        Log.i(TAG, "Mapped HyperAlive AOD switch to screen_off_remind=" + nativeSetting);
                    }
                } catch (Throwable t) { Log.e(TAG, "AOD switch mapping failed", t); }
            }
        });
        Log.i(TAG, "Settings hooks=" + count);
    }

    private static void installAmbientSupport() {
        // The original wallpaper XML does not declare supportsAmbientMode.
        // Android's wallpaper controller also needs this capability, regardless
        // of the vendor AOD window's SHOW_WALLPAPER flag.
        int count = hook(WallpaperInfo.class, "supportsAmbientMode", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    WallpaperInfo info = (WallpaperInfo) p.thisObject;
                    if ("com.qihang.aod".equals(info.getPackageName())) p.setResult(Boolean.TRUE);
                } catch (Throwable t) { Log.e(TAG, "Ambient support compatibility failed", t); }
            }
        });
        Log.i(TAG, "Wallpaper ambient capability hooks=" + count);
    }

    private static void installSystemUiMasks() {
        int count = hook(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    View view = (View) p.thisObject;
                    if (view.getId() == View.NO_ID) return;
                    String owner = view.getResources().getResourcePackageName(view.getId());
                    String name = view.getResources().getResourceEntryName(view.getId());
                    if (!BridgePolicy.isSystemUiMask(owner, name)) return;
                    AodWindowController controller = WINDOWS.get(view);
                    if (controller == null || controller.isClosed()) {
                        controller = new AodWindowController(view);
                        WINDOWS.put(view, controller);
                        Log.i(TAG, "Watching OriginOS mask " + owner + ":" + name);
                    }
                } catch (android.content.res.Resources.NotFoundException ignored) {
                    // Generated and third-party resource IDs are not compatibility targets.
                } catch (Throwable error) { Log.e(TAG, "OriginOS mask attach failed", error); }
            }
        });
        Log.i(TAG, "OriginOS SystemUI mask attachment hooks=" + count);
    }

    private static void installDisplayOverlay(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.android.server.display.VivoDisplayOverlayController", false, loader);
            int count = hook(clazz, "enableDisableBlackOverlayInternal", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        // Preserve the original method's bookkeeping, draw locks and removal.
                        if (p.args.length != 1) return;
                        int displayId = (Integer) field(p.thisObject, "mDisplayId");
                        if (displayId != 0) return;
                        View view = (View) field(p.thisObject, "mVivoDisplayOverlay");
                        WindowManager wm = (WindowManager) field(p.thisObject, "mWindowManager");
                        WindowManager.LayoutParams params = (WindowManager.LayoutParams)
                                field(p.thisObject, "mWindowParams");
                        if (view == null) return;
                        AodWindowController controller = WINDOWS.get(view);
                        if (!Boolean.TRUE.equals(p.args[0])) {
                            if (controller != null) controller.close();
                            WINDOWS.remove(view);
                            return;
                        }
                        if (view == null || wm == null || params == null || !view.isAttachedToWindow()) return;
                        if (controller == null || controller.isClosed()) {
                            controller = new AodWindowController(view, wm, true);
                            WINDOWS.put(view, controller);
                        }
                        controller.watch(params);
                    } catch (Throwable t) { Log.e(TAG, "Overlay compatibility failed", t); }
                }
            });
            Log.i(TAG, "Vivo overlay hooks=" + count);
        } catch (Throwable t) { Log.e(TAG, "Vivo overlay class unavailable", t); }
    }

    private static void installAodWindow(String host) throws Exception {
        Class<?> wm = Class.forName("android.view.WindowManagerGlobal");
        XC_MethodHook callback = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length < 2 || !(p.args[0] instanceof View)
                        || !(p.args[1] instanceof WindowManager.LayoutParams)) return;
                final View view = (View) p.args[0];
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) p.args[1];
                String title = String.valueOf(params.getTitle());
                if (!BridgePolicy.isAodWindowTitle(title)) return;
                try {
                    if (view.getDisplay() != null && view.getDisplay().getDisplayId() != 0) return;
                    AodWindowController controller = WINDOWS.get(view);
                    if (controller == null || controller.isClosed()) {
                        WindowManager manager = (WindowManager)
                                view.getContext().getSystemService(Context.WINDOW_SERVICE);
                        if (manager == null) return;
                        controller = new AodWindowController(view, manager, false);
                        WINDOWS.put(view, controller);
                        Log.i(TAG, host + " matched AOD window title=" + title);
                    }
                    controller.watch(params);
                } catch (Throwable t) { Log.e(TAG, "AOD window compatibility failed", t); }
            }
        };
        int count = hook(wm, "addView", callback) + hook(wm, "updateViewLayout", callback);
        Log.i(TAG, host + " AOD window hooks=" + count);
    }

    static Object field(Object instance, String name) throws Exception {
        Class<?> clazz = instance.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static int hook(Class<?> clazz, String name, XC_MethodHook callback) {
        int count = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            if (!name.equals(method.getName())) continue;
            try {
                method.setAccessible(true);
                XposedBridge.hookMethod(method, callback);
                count++;
            } catch (Throwable t) { Log.e(TAG, "Hook failed: " + method, t); }
        }
        return count;
    }
}
