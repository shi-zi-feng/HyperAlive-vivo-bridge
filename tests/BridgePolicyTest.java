import com.qihang.vivobridge.BridgePolicy;

public class BridgePolicyTest {
    static int checks;
    static void check(boolean result) {
        checks++;
        if (!result) throw new AssertionError("Failed check " + checks);
    }
    public static void main(String[] args) {
        String ours = "ComponentInfo{com.qihang.aod/com.qihang.aod.QihangWallpaper}";
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
        StackTraceElement frame = new StackTraceElement("com.qihang.aod.hook.StateCoordinator", "isAodEnabled", "StateCoordinator.java", 1);
        check(BridgePolicy.isModuleSettingRead(new StackTraceElement[]{frame}));
        check(!BridgePolicy.isModuleSettingRead(new StackTraceElement[]{new StackTraceElement("com.android.systemui.DozeParameters", "getAlwaysOn", "DozeParameters.java", 1)}));
        check(!BridgePolicy.isModuleSettingRead(new StackTraceElement[0]));
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
        StackTraceElement callback = new StackTraceElement(
                "com.qihang.aod.hook.SystemServerHook$9", "beforeHookedMethod", "SystemServerHook.java", 1);
        check(BridgePolicy.isModuleDisplayCallback(new StackTraceElement[]{frame, callback}));
        check(!BridgePolicy.isModuleDisplayCallback(new StackTraceElement[]{frame,
                new StackTraceElement("com.qihang.aod.hook.StateCoordinator", "onSleepIntent", "StateCoordinator.java", 1), callback}));
        check(!BridgePolicy.isModuleDisplayCallback(new StackTraceElement[]{frame}));
        check(!BridgePolicy.isModuleDisplayCallback(new StackTraceElement[]{callback, frame}));
        check(!BridgePolicy.isModuleDisplayCallback(new StackTraceElement[]{frame,
                new StackTraceElement("com.qihang.aod.hook.SystemUiHook$1", "beforeHookedMethod", "SystemUiHook.java", 1)}));
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
        System.out.println("Passed " + checks + " ownership, display, wake and setting isolation checks");
    }
}
