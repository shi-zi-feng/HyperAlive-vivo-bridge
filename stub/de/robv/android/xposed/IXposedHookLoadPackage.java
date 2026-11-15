package de.robv.android.xposed;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
/** Minimal compile-time declaration; the framework supplies the implementation. */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) throws Throwable;
}
