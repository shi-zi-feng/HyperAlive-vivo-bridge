import android.os.Build;
import com.hyperalive.aod.hook.SystemServerHook;
import com.hyperalive.aod.hook.NativeOnAodRampCoordinator;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Member;

/** Calls the real compiled SystemServerHook.install against a recording JVM framework. */
public final class SystemServerRegistrationTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static int count(String owner, String method) {
        int count = 0;
        for (Member member : XposedBridge.HOOKS)
            if (member.getDeclaringClass().getName().equals(owner) && member.getName().equals(method)) count++;
        return count;
    }
    private static void install(String manufacturer, String brand, boolean expectedNative) {
        Build.MANUFACTURER = manufacturer; Build.BRAND = brand;
        XposedBridge.HOOKS.clear(); NativeOnAodRampCoordinator.installs = 0;
        SystemServerHook.install(SystemServerRegistrationTest.class.getClassLoader());
        for (String owner : new String[]{"DisplayPowerController", "DisplayPowerControllerImpl", "DisplayPowerState"})
            check(count("com.android.server.display." + owner, "setScreenState") == (expectedNative ? 0 : 1), owner);
        check(count("com.android.server.display.DisplayPowerState", "prepareColorFade") == (expectedNative ? 0 : 1), "fade");
        check(NativeOnAodRampCoordinator.installs == (expectedNative ? 0 : 1), "ramp installation");
        check(count("com.android.server.power.PowerManagerService", "goToSleep") == 1, "sleep feed retained");
        check(count("com.android.server.power.PowerManagerService", "wakeUp") == 1, "wake feed retained");
        check(count("com.android.server.power.PowerGroup", "setWakefulnessLocked") == 1, "timeout feed retained");
    }
    public static void main(String[] args) {
        install("vivo", "vivo", true);
        install("vivo", "iQOO", true);
        install("VIVO", "IQOO", true);
        install("Xiaomi", "Redmi", false);
        System.out.println("Passed " + checks + " real system-server hook registration checks (JVM fakes, no device)");
    }
}
