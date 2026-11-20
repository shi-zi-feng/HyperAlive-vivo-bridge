package com.hyperalive.aod.hook;
import de.robv.android.xposed.XC_MethodHook;
/** Test-only sentinel: an accidental vivo call is observable without running a brightness controller. */
public final class NativeOnAodRampCoordinator {
    public static int installs;
    public static void install(ClassLoader loader) { installs++; }
    public static void onPowerWakeSignal(String source) { throw new AssertionError("Unexpected ramp wake"); }
    public static void onPowerSleepSignal(String source) { throw new AssertionError("Unexpected ramp sleep"); }
    public static boolean handleSetScreenState(XC_MethodHook.MethodHookParam p, int state) { return false; }
    public static boolean handlePrepareColorFade(XC_MethodHook.MethodHookParam p) { return false; }
}
