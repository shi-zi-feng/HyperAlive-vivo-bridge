import com.qihang.vivobridge.BridgePolicy;

public class BridgePolicyTest {
    static int checks;
    static void check(boolean result) {
        checks++;
        if (!result) throw new AssertionError("Failed check " + checks);
    }
    public static void main(String[] args) {
        String ours = "ComponentInfo{com.hyperalive.aod/com.hyperalive.aod.QihangWallpaper}";
        check(BridgePolicy.ownsAod(true, true, ours));
        check(!BridgePolicy.ownsAod(false, true, ours));
        check(!BridgePolicy.ownsAod(true, false, ours));
        check(!BridgePolicy.ownsAod(true, true, null));
        check(!BridgePolicy.ownsAod(true, true, "ComponentInfo{com.vivo.wallpaper/Foo}"));
        check(BridgePolicy.mayExposeWallpaper(true, false, 0, 100, 0));
        check(!BridgePolicy.mayExposeWallpaper(false, false, 0, 100, 0));
        check(!BridgePolicy.mayExposeWallpaper(true, true, 0, 100, 0));
        check(!BridgePolicy.mayExposeWallpaper(true, false, 1, 100, 0));
        check(!BridgePolicy.mayExposeWallpaper(true, false, 0, 0, 0));
        check(!BridgePolicy.mayExposeWallpaper(true, false, 0, 100, 101));
        int wallpaperFlag = 0x100000;
        int unrelated = 0x800 | 0x8;
        check(BridgePolicy.restoreWallpaperFlag(unrelated | wallpaperFlag, false, wallpaperFlag) == unrelated);
        check(BridgePolicy.restoreWallpaperFlag(unrelated, true, wallpaperFlag) == (unrelated | wallpaperFlag));
        check(BridgePolicy.restoreWallpaperFlag(unrelated, false, wallpaperFlag) == unrelated);
        check(BridgePolicy.mayExposeWallpaper(true, false, 0, 100, 100));
        check(BridgePolicy.isSystemUiMask("com.vivo.systemuiplugin", "aod_root_view_bg_bottom"));
        check(BridgePolicy.isSystemUiMask("com.android.systemui", "full_aod_mask"));
        check(!BridgePolicy.isSystemUiMask("com.other.app", "aod_off_mask"));
        check(!BridgePolicy.isSystemUiMask("com.vivo.systemuiplugin", "aod_content_bottom"));
        check(!BridgePolicy.isSystemUiMask("com.vivo.systemuiplugin", "notification"));
        check(!BridgePolicy.isSystemUiMask(null, null));
        check(BridgePolicy.isAodWindowTitle("AOD"));
        check(BridgePolicy.isAodWindowTitle("AOD_ON_KEYGUARD"));
        check(!BridgePolicy.isAodWindowTitle("NotificationShade"));
        check(!BridgePolicy.isAodWindowTitle(null));
        check(BridgePolicy.isNativeClockBranch("com.vivo.systemuiplugin", "time_container", false));
        check(BridgePolicy.isNativeClockBranch("com.vivo.nightpearl", "rl_root_time", true));
        check(BridgePolicy.isNativeClockBranch("com.vivo.nightpearl", "fl_keyguard_time", true));
        check(BridgePolicy.isNativeClockBranch("com.vivo.nightpearl", "date", true));
        check(!BridgePolicy.isNativeClockBranch("com.vivo.nightpearl", "date", false));
        check(!BridgePolicy.isNativeClockBranch("com.other.app", "time_container", false));
        check(!BridgePolicy.isNativeClockBranch("com.vivo.systemuiplugin", "aod_content_bottom", true));
        check(!BridgePolicy.isNativeClockBranch("com.vivo.nightpearl", "notification", true));
        check(!BridgePolicy.isNativeClockBranch("com.vivo.nightpearl", "ll_aod_music_container", true));
        check(!BridgePolicy.isNativeClockBranch(null, null, false));
        check(BridgePolicy.isNightPearlClock("com.vivo.nightpearl.view.ScreenOffClockView"));
        check(!BridgePolicy.isNightPearlClock("com.vivo.nightpearl.view.ScreenOffChargingRemindView"));
        check(!BridgePolicy.isNightPearlClock("com.vivo.nightpearl.view.ScreenOffSecondaryClockView"));
        check(BridgePolicy.isAwakeDecoration("com.vivo.systemuiplugin", "time_container"));
        check(!BridgePolicy.isAwakeDecoration("com.vivo.systemuiplugin", "vivo_keyguard_host_container"));
        check(!BridgePolicy.isAwakeDecoration("com.vivo.systemuiplugin", "fingerprint_icon"));
        check(!BridgePolicy.isAwakeDecoration("com.other", "tools_view"));
        check(BridgePolicy.currentNativeHost(true, 10, 10, 0));
        check(!BridgePolicy.currentNativeHost(true, 9, 10, 0));
        check(!BridgePolicy.currentNativeHost(true, 10, 10, 11));
        check(!BridgePolicy.currentNativeHost(false, 10, 10, 0));
        check(!BridgePolicy.currentNativeHost(true, 0, 0, 0));
        // The same style can run in either native AOD host, but only one module clock draws.
        for (boolean nativeHost : new boolean[]{false, true}) {
            check(BridgePolicy.suppressSceneBranch(1, true, true, true, false, nativeHost, true, -1));
            check(BridgePolicy.suppressSceneBranch(2, true, true, true, false, nativeHost, false, -1));
            check(BridgePolicy.suppressSceneBranch(4, true, true, true, false, nativeHost, false, 0) == nativeHost);
            check(BridgePolicy.suppressSceneBranch(4, true, true, true, false, nativeHost, false, 1));
        }
        // Native fallback remains available when the integrated custom clock is not ready.
        check(!BridgePolicy.suppressSceneBranch(2, true, true, false, false, false, false, -1));
        // Wake restores native lock content and removes lingering AOD digits.
        check(!BridgePolicy.suppressSceneBranch(1, true, false, false, false, false, true, -1));
        check(BridgePolicy.suppressSceneBranch(2, true, false, false, false, false, false, -1));
        check(BridgePolicy.suppressSceneBranch(4, true, false, false, false, false, false, 0));
        check(!BridgePolicy.suppressSceneBranch(4, true, false, false, true, false, false, 1));
        // Custom lock clock suppresses only the native time branch, not lock gestures/decorations.
        check(BridgePolicy.suppressSceneBranch(1, true, false, false, true, false, true, -1));
        check(!BridgePolicy.suppressSceneBranch(1, true, false, false, true, false, false, -1));
        for (int role = 1; role <= 4; role++) {
            check(!BridgePolicy.suppressSceneBranch(role, false, true, true, true, true, true, 0));
        }
        // New package is the only eligible lock wallpaper; stale/similar values do not take over AOD.
        check(BridgePolicy.ownsAod(true, true, "com.hyperalive.aod/.QihangWallpaper"));
        check(BridgePolicy.ownsAod(true, true, "com.hyperalive.aod/com.hyperalive.aod.QihangWallpaper"));
        check(!BridgePolicy.ownsAod(true, true, "ComponentInfo{com.qihang.aod/com.qihang.aod.QihangWallpaper}"));
        check(!BridgePolicy.ownsAod(true, true, "ComponentInfo{other.com.hyperalive.aod/Other}"));
        check(!BridgePolicy.ownsAod(true, true, "ComponentInfo{com.hyperalive.aod/OtherService}"));
        check(!BridgePolicy.ownsAod(true, true, "ComponentInfo{com.hyperalive.aod/.QihangHomeWallpaper}"));
        check(!BridgePolicy.ownsAod(true, true, "prefix com.hyperalive.aod/.QihangWallpaper"));
        check(!BridgePolicy.ownsAod(true, true, ""));
        // A NightPearl window may exist before the module clock can draw. Keep
        // integrated/native fallback until the replacement is actually ready.
        check(!BridgePolicy.mayPublishNativeClock(true, true, true, false));
        check(!BridgePolicy.mayPublishNativeClock(true, true, false, true));
        check(!BridgePolicy.mayPublishNativeClock(true, false, true, true));
        check(!BridgePolicy.mayPublishNativeClock(false, true, true, true));
        check(BridgePolicy.mayPublishNativeClock(true, true, true, true));
        // Exercise both hosts across attach -> ready -> failure -> wake -> new sleep.
        boolean[] nightReady = {false, true, false, true, true};
        boolean[] asleep = {true, true, true, false, true};
        long[] hostEpoch = {100, 100, 100, 100, 100};
        long[] sleepEpoch = {100, 100, 100, 100, 200};
        boolean[] expectedNativeHost = {false, true, false, false, false};
        for (int i = 0; i < nightReady.length; i++) {
            boolean published = BridgePolicy.mayPublishNativeClock(true, asleep[i], true, nightReady[i]);
            boolean host = BridgePolicy.currentNativeHost(published, hostEpoch[i], sleepEpoch[i], asleep[i] ? 0 : 150);
            check(host == expectedNativeHost[i]);
            boolean integratedClockHidden = BridgePolicy.suppressSceneBranch(
                    4, true, asleep[i], true, false, host, false, 0);
            check(integratedClockHidden == (!asleep[i] || expectedNativeHost[i]));
        }
        System.out.println("Passed " + checks + " ownership, clock handoff, fallback and wake checks");
    }
}
