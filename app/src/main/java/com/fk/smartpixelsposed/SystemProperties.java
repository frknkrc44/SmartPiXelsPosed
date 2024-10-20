package com.fk.smartpixelsposed;

import android.annotation.SuppressLint;
import android.text.TextUtils;

import de.robv.android.xposed.XposedHelpers;

@SuppressLint("PrivateApi")
public class SystemProperties {
    private SystemProperties() {}

    private static Class<?> PROPS = null;

    static {
        try {
            PROPS = Class.forName("android.os.SystemProperties");
        } catch (Throwable ignored) {}
    }

    public static String get(String key) {
        return (String) XposedHelpers.callStaticMethod(PROPS, "get", key);
    }

    public static boolean isOEM() {
        return isXiaomi() || isOOS();
    }

    public static boolean isXiaomi() {
        return !TextUtils.isEmpty(get("ro.miui.ui.version.code")) || !TextUtils.isEmpty(get("ro.mi.os.version.name"));
    }

    public static boolean isOOS() {
        return get("ro.build.ota.versionname").contains("Oxygen") ||
                !TextUtils.isEmpty(get("ro.build.version.oplusrom")) ||
                get("ro.product.cuptsm").contains("ONEPLUS") ||
                !TextUtils.isEmpty("ro.vendor.oplus.market.name");
    }
}
