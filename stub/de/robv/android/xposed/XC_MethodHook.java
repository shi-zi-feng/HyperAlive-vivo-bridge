package de.robv.android.xposed;
/** Signature-only compile stub. Excluded from the runtime DEX. */
public abstract class XC_MethodHook {
    public XC_MethodHook() {}
    public XC_MethodHook(int priority) {}
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
    public final class Unhook {}
    public static final class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public void setResult(Object value) {}
        public void setObjectExtra(String key, Object value) {}
        public Object getObjectExtra(String key) { return null; }
    }
}
