package com.fk.smartpixelsposed;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.systemui.smartpixels.Grids;

public class SafeValueGetter {
    public static final int DIM_PERCENT_MAX = 90;

    private SafeValueGetter() {}

    public static boolean getEnabled(Context context) {
        String value = Settings.System.getString(
                context.getContentResolver(),
                SettingsSystem.SMART_PIXELS_ENABLED
        );

        if (!TextUtils.isDigitsOnly(value)) {
            value = "1";
        }

        return safeGet(Integer.parseInt(value), 2) == 1;
    }

    public static int getDimPercent(Context context) {
        int value = Settings.System.getInt(
                context.getContentResolver(),
                SettingsSystem.SMART_PIXELS_DIM,
                0
        );
        return safeGet(value, DIM_PERCENT_MAX + 1);
    }

    public static int getPattern(Context context) {
        int value = Settings.System.getInt(
                context.getContentResolver(),
                SettingsSystem.SMART_PIXELS_PATTERN,
                5
        );
        return safeGet(value, Grids.Patterns.length);
    }

    public static int getShiftTimeout(Context context) {
        int value = Settings.System.getInt(
                context.getContentResolver(),
                SettingsSystem.SMART_PIXELS_SHIFT_TIMEOUT,
                4
        );
        return safeGet(value, Grids.ShiftTimeouts.length);
    }

    private static int safeGet(int value, int max) {
        return Math.max(0, Math.min(value, max - 1));
    }
}
