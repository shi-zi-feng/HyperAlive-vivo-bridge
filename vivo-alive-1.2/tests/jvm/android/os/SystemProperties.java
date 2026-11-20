package android.os;
import java.util.HashMap;
public final class SystemProperties {
    private static final HashMap<String, String> VALUES = new HashMap<>();
    public static String get(String key, String fallback) { return VALUES.containsKey(key) ? VALUES.get(key) : fallback; }
    public static void set(String key, String value) { VALUES.put(key, value); }
}
