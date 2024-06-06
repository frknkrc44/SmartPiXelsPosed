package com.fk.smartpixelsposed;

import android.content.Context;
import android.provider.Settings;

import com.android.systemui.smartpixels.Grids;

public class SafeValueGetter {
    private SafeValueGetter() {}

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
