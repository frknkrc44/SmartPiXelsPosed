// Copyright (C) 2024 Furkan Karcıoğlu <https://github.com/frknkrc44>
//
// This file is part of SmartPiXelsPosed project,
// and licensed under GNU Affero General Public License v3.
// See the GNU Affero General Public License for more details.
//
// All rights reserved. See COPYING, AUTHORS.
//

package com.fk.smartpixelsposed;

// A replacement of Settings.System
public class SettingsSystem {
    private static final String SMART_PIXELS_PREFIX = "spsd_";
    public static final String SMART_PIXELS_ENABLED = SMART_PIXELS_PREFIX + "smart_pixels_enable";
    public static final String SMART_PIXELS_ON_POWER_SAVE = SMART_PIXELS_PREFIX + "smart_pixels_on_power_save";
    public static final String SMART_PIXELS_SYSTEM_BARS_SHIFT = SMART_PIXELS_PREFIX + "smart_pixels_system_bars_shift";
    public static final String SMART_PIXELS_PATTERN = SMART_PIXELS_PREFIX + "smart_pixels_pattern";
    public static final String SMART_PIXELS_SHIFT_TIMEOUT = SMART_PIXELS_PREFIX + "smart_pixels_shift_timeout";
    public static final String SMART_PIXELS_DIM = SMART_PIXELS_PREFIX + "smart_pixels_dim";
    public static final String SMART_PIXELS_BARS_ALPHA = SMART_PIXELS_PREFIX + "smart_pixels_bars_alpha";
    public static final String SMART_PIXELS_DIM_DRAG = SMART_PIXELS_PREFIX + "smart_pixels_dim_drag";
    public static final String SMART_PIXELS_ALT_LOGIC = SMART_PIXELS_PREFIX + "smart_pixels_alt_logic";
}
