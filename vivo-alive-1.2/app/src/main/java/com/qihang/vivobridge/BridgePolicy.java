package com.qihang.vivobridge;

/** Deliberately excludes other vendors, wallpapers, displays and awake screens. */
public final class BridgePolicy {
    private BridgePolicy() {}

    public static boolean ownsAod(boolean vivo, boolean nativeAod, String lockWallpaper) {
        if (!vivo || !nativeAod || lockWallpaper == null) return false;
        String component = lockWallpaper;
        if (component.startsWith("ComponentInfo{") && component.endsWith("}"))
            component = component.substring(14, component.length() - 1);
        return "com.hyperalive.aod/com.hyperalive.aod.QihangWallpaper".equals(component)
                || "com.hyperalive.aod/.QihangWallpaper".equals(component);
    }

    public static boolean mayExposeWallpaper(boolean ownsAod, boolean interactive,
                                             int displayId, long sleepAt, long wakeAt) {
        return ownsAod && !interactive && displayId == 0
                && sleepAt > 0 && sleepAt >= wakeAt;
    }

    /** Announce a replacement only after actual content is ready in this sleep scene. */
    public static boolean mayPublishNativeClock(boolean owns, boolean sleeping,
                                                 boolean enabled, boolean clockReady) {
        return owns && sleeping && enabled && clockReady;
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

    public static boolean isNightPearlClock(String className) {
        return "com.vivo.nightpearl.view.ScreenOffClockView".equals(className)
                || "com.vivo.nightpearl.view.LargeClockView.ScreenOffLargeClockView".equals(className);
    }

    /** Only visual lock-screen branches observed on this device; never the keyguard host. */
    public static boolean isAwakeDecoration(String owner, String name) {
        if ("com.android.systemui".equals(owner)) {
            return "status_bar_container".equals(name) || "keyguard_header".equals(name);
        }
        if (!"com.vivo.systemuiplugin".equals(owner)) return false;
        return "time_container".equals(name) || "time_container_deep".equals(name)
                || "tools_view".equals(name) || "vivo_password_unlock_icon_container".equals(name)
                || "vivo_smart_unlock_container".equals(name)
                || "vivo_keyguard_view_wallpaper".equals(name);
    }

    public static boolean isIntegratedAodContent(String owner, String name) {
        return "com.vivo.systemuiplugin".equals(owner)
                && ("aod_content_top".equals(name) || "aod_content_bottom".equals(name));
    }

    public static boolean currentNativeHost(boolean present, long hostEpoch, long sleepAt, long wakeAt) {
        return present && sleepAt > 0 && sleepAt >= wakeAt && hostEpoch == sleepAt;
    }

    public static boolean suppressSceneBranch(int role, boolean owns, boolean sleeping,
            boolean customAodReady, boolean customLockReady, boolean nativeHost,
            boolean timeContainer, int moduleMode) {
        if (!owns) return false;
        if (role == 1) return sleeping || (customLockReady && timeContainer);
        if (role == 2) return !sleeping || customAodReady || nativeHost;
        if (role == 3) return !sleeping || nativeHost;
        if (role == 4) return moduleMode == 0 ? (!sleeping || nativeHost) : sleeping;
        return false;
    }
}
