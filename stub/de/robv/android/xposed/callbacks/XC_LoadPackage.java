package de.robv.android.xposed.callbacks;
/** Fields consumed by this companion; not a runtime implementation. */
public final class XC_LoadPackage {
    public static final class LoadPackageParam {
        public String packageName;
        public ClassLoader classLoader;
    }
}
