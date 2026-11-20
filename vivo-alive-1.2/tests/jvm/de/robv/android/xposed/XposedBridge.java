package de.robv.android.xposed;
import java.lang.reflect.Member;
import java.util.ArrayList;
public final class XposedBridge {
    public static final ArrayList<Member> HOOKS = new ArrayList<>();
    public static XC_MethodHook.Unhook hookMethod(Member method, XC_MethodHook callback) {
        HOOKS.add(method); return null;
    }
    public static void log(String message) {}
    public static void log(Throwable error) {}
}
