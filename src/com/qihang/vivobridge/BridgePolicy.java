package com.qihang.vivobridge;

/** Deliberately excludes other vendors, wallpapers, displays and awake screens. */
public final class BridgePolicy {
    private BridgePolicy() {}

    public static boolean ownsAod(boolean vivo, boolean nativeAod, String lockWallpaper) {
        return vivo && nativeAod && lockWallpaper != null
                && lockWallpaper.contains("com.qihang.aod/");
    }

    public static boolean mayExposeWallpaper(boolean ownsAod, boolean interactive,
                                             int displayId, long sleepAt, long wakeAt) {
        return ownsAod && !interactive && displayId == 0
                && sleepAt > 0 && sleepAt >= wakeAt;
    }

    public static boolean isModuleSettingRead(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            if ("com.qihang.aod.hook.StateCoordinator".equals(frame.getClassName())) return true;
        }
        return false;
    }

    /** Direct calls from the two original display callbacks, excluding power-event dispatch. */
    public static boolean isModuleDisplayCallback(StackTraceElement[] stack) {
        for (int i = 0; i + 1 < stack.length; i++) {
            StackTraceElement frame = stack[i];
            StackTraceElement caller = stack[i + 1];
            if ("com.qihang.aod.hook.StateCoordinator".equals(frame.getClassName())
                    && "isAodEnabled".equals(frame.getMethodName())
                    && caller.getClassName().startsWith("com.qihang.aod.hook.SystemServerHook$")
                    && "beforeHookedMethod".equals(caller.getMethodName())) return true;
        }
        return false;
    }

    public static boolean isNativeClockBranch(String owner, String name, boolean inVivoAod) {
        if (("com.vivo.systemuiplugin".equals(owner) || "com.android.systemui".equals(owner))
                && ("time_container".equals(name) || "time_container_deep".equals(name))) return true;
        return inVivoAod && ("rl_root_time".equals(name) || "fl_keyguard_time".equals(name)
                || "date".equals(name));
    }

    public static int restoreWallpaperFlag(int flags, boolean originallySet, int wallpaperFlag) {
        return originallySet ? flags | wallpaperFlag : flags & ~wallpaperFlag;
    }

    public static boolean isSystemUiMask(String owner, String name) {
        if (!"com.vivo.systemuiplugin".equals(owner) && !"com.android.systemui".equals(owner)) return false;
        return "aod_root_view_bg_bottom".equals(name) || "aod_root_view_bg_top".equals(name)
                || "aod_off_mask".equals(name) || "full_aod_mask_view".equals(name)
                || "full_aod_mask".equals(name);
    }

    public static boolean isAodWindowTitle(String title) {
        return "AOD".equals(title) || "AOD_ON_KEYGUARD".equals(title);
    }
}
