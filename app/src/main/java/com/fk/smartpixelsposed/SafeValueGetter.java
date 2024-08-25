package com.fk.smartpixelsposed;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.systemui.smartpixels.Grids;

public class SafeValueGetter {
    public static final int DIM_PERCENT_MAX = 90;

    private SafeValueGetter() {}

    public static boolean getEnabled(Context context) {
        int enabled = getSystemStrAsInt(
                context,
                SettingsSystem.SMART_PIXELS_ENABLED,
                1
        );
        return safeGet(enabled, 2) == 1;
    }

    public static boolean getEnabledOnPowerSaver(Context context) {
        int enabled = getSystemStrAsInt(
                context,
                SettingsSystem.SMART_PIXELS_ON_POWER_SAVE,
                0
        );
        return safeGet(enabled, 2) == 1;
    }

    public static boolean isLowPowerMode(Context context) {
        int enabled = getGlobalStrAsInt(
                context,
                SettingsGlobal.LOW_POWER,
                0
        );
        return safeGet(enabled, 2) == 1;
    }

    public static int getDimPercent(Context context) {
        int value = getSystemStrAsInt(
                context,
                SettingsSystem.SMART_PIXELS_DIM,
                0
        );
        return safeGet(value, DIM_PERCENT_MAX + 1);
    }

    public static int getPattern(Context context) {
        int value = getSystemStrAsInt(
                context,
                SettingsSystem.SMART_PIXELS_PATTERN,
                5
        );
        return safeGet(value, Grids.Patterns.length);
    }

    public static int getShiftTimeout(Context context) {
        int value = getSystemStrAsInt(
                context,
                SettingsSystem.SMART_PIXELS_SHIFT_TIMEOUT,
                4
        );
        return safeGet(value, Grids.ShiftTimeouts.length);
    }

    private static int getGlobalStrAsInt(Context context, String key, int def) {
        String value = Settings.Global.getString(
                context.getContentResolver(),
                key
        );

        if (TextUtils.isEmpty(value) || !TextUtils.isDigitsOnly(value)) {
            value = String.valueOf(def);
        }

        return Integer.parseInt(value);
    }

    private static int getSystemStrAsInt(Context context, String key, int def) {
        String value = Settings.System.getString(
                context.getContentResolver(),
                key
        );

        if (TextUtils.isEmpty(value) || !TextUtils.isDigitsOnly(value)) {
            value = String.valueOf(def);
        }

        return Integer.parseInt(value);
    }

    private static int safeGet(int value, int max) {
        return Math.max(0, Math.min(value, max - 1));
    }
}
