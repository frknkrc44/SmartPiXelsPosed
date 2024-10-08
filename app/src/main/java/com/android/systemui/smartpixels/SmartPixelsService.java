/*
 * Copyright (c) 2015, Sergii Pylypenko
 *           (c) 2018, Joe Maples
 *           (c) 2018, Adin Kwok
 *           (c) 2018, CarbonROM
 *           (c) 2024, Furkan Karcıoğlu
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of screen-dimmer-pixel-filter nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.android.systemui.smartpixels;

import static android.content.Context.WINDOW_SERVICE;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.fk.smartpixelsposed.SafeValueGetter;
import com.fk.smartpixelsposed.SettingsGlobal;
import com.fk.smartpixelsposed.SettingsSystem;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public abstract class SmartPixelsService {
    public static final String LOG = "SmartPixelsService";
    public static final String INTENT_ACTION = "com.android.systemui.action.SMART_PIXELS_REFRESH";

    private WindowManager windowManager;
    private View view = null;
    private BitmapDrawable draw;

    private boolean destroyed = false;
    public boolean running = false;
    public boolean useAlternativeMethodForBS = false;
    public boolean batterySaverEnabled = false;
    public boolean dimDragEnabled = false;

    private int startCounter = 0;
    private Context mContext;
    private int orientation;
    private ContentObserver mObserver;
    private Handler mHandler;
    private IntentFilter mSettingsIntentFilter;

    public final BroadcastReceiver mSettingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ContentResolver resolver = context.getContentResolver();

            int enabled = intent.getIntExtra(SettingsSystem.SMART_PIXELS_ENABLED, mEnabled ? 1 : 0);
            int enabledOnBatterySaver = intent.getIntExtra(SettingsSystem.SMART_PIXELS_ON_POWER_SAVE, mEnabledOnPowerSaver ? 1 : 0);
            int enableSystemBarsShift = intent.getIntExtra(SettingsSystem.SMART_PIXELS_SYSTEM_BARS_SHIFT, mEnabledSystemBarsShift ? 1 : 0);
            int dimPercent = intent.getIntExtra(SettingsSystem.SMART_PIXELS_DIM, mDimPercent);
            int enabledDimDrag = intent.getIntExtra(SettingsSystem.SMART_PIXELS_DIM_DRAG, dimDragEnabled ? 1 : 0);
            int pattern = intent.getIntExtra(SettingsSystem.SMART_PIXELS_PATTERN, mPattern);
            int timeout = intent.getIntExtra(SettingsSystem.SMART_PIXELS_SHIFT_TIMEOUT, mShiftTimeout);

            Settings.System.putInt(
                    resolver,
                    SettingsSystem.SMART_PIXELS_ENABLED,
                    enabled
            );
            Settings.System.putInt(
                    resolver,
                    SettingsSystem.SMART_PIXELS_ON_POWER_SAVE,
                    enabledOnBatterySaver
            );
            Settings.System.putInt(
                    resolver,
                    SettingsSystem.SMART_PIXELS_SYSTEM_BARS_SHIFT,
                    enableSystemBarsShift
            );
            Settings.System.putInt(
                    resolver,
                    SettingsSystem.SMART_PIXELS_DIM,
                    dimPercent
            );
            Settings.System.putInt(
                    resolver,
                    SettingsSystem.SMART_PIXELS_DIM_DRAG,
                    enabledDimDrag
            );
            Settings.System.putInt(
                    resolver,
                    SettingsSystem.SMART_PIXELS_PATTERN,
                    pattern
            );
            Settings.System.putInt(
                    resolver,
                    SettingsSystem.SMART_PIXELS_SHIFT_TIMEOUT,
                    timeout
            );

            mObserver.onChange(false);
        }
    };

    // Pixel Filter Settings
    private boolean mEnabled = false;
    private boolean mEnabledOnPowerSaver = false;
    public boolean mEnabledSystemBarsShift = false;
    private int mDimPercent = 0;
    private int mPattern = 3;
    private int mShiftTimeout = 4;

    public static final int PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY = 1 << 20;
    public static final int PRIVATE_FLAG_TRUSTED_OVERLAY = 1 << 29;

    public SmartPixelsService(Context context, Handler handler) {
        onCreate(context, handler);
    }

    private boolean isBatterySaverEnabled() {
        return (useAlternativeMethodForBS && batterySaverEnabled) || SafeValueGetter.isLowPowerMode(mContext);
    }

    public boolean isEnabled() {
        return mEnabled || (mEnabledOnPowerSaver && isBatterySaverEnabled());
    }

    private void onCreate(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;

        Configuration conf =  context.getResources().getConfiguration();
        orientation = conf.orientation;

        updateSettings();
        Log.d(LOG, "Service started");
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public void startFilter(){
        if (view != null) {
            return;
        }

        destroyed = false;
        running = true;
        windowManager = (WindowManager) mContext.getSystemService(WINDOW_SERVICE);

        view = new View(mContext);

        if (mSettingsIntentFilter == null) {
            mSettingsIntentFilter = new IntentFilter(INTENT_ACTION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mContext.registerReceiver(mSettingsReceiver, mSettingsIntentFilter, Context.RECEIVER_EXPORTED);
            } else {
                mContext.registerReceiver(mSettingsReceiver, mSettingsIntentFilter);
            }
        }

        if (mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }

        if (mObserver == null) {
            mObserver = new SmartPixelsObserver();
        }

        if (draw == null) {
            Bitmap bmp = Bitmap.createBitmap(Grids.GridSideSize, Grids.GridSideSize, Bitmap.Config.ARGB_4444);
            draw = new BitmapDrawable(mContext.getResources(), bmp);
            draw.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            draw.setFilterBitmap(false);
            draw.setAntiAlias(false);
            draw.setTargetDensity(mContext.getResources().getDisplayMetrics());
        }

        view.setBackground(draw);

        try {
            WindowManager.LayoutParams params = getLayoutParams();
            windowManager.addView(view, params);
        } catch (Exception e) {
            running = false;
            view = null;
            return;
        }

        reloadFilter();
    }

    public void reloadFilter() {
        if (!isEnabled()) {
            stopFilter();
            return;
        }

        mHandler.removeCallbacksAndMessages(null);

        if (view.getParent() == null) {
            windowManager.addView(view, getLayoutParams());
        }

        startCounter++;
        final int handlerStartCounter = startCounter;
        final PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (view == null || destroyed || handlerStartCounter != startCounter) {
                    return;
                } else if (pm.isInteractive()) {
                    updatePattern();
                }
                if (!destroyed) {
                    mHandler.postDelayed(this, Grids.ShiftTimeouts[mShiftTimeout]);
                }
            }
        });
    }

    public void stopFilter() {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }

        if (mObserver != null && destroyed) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
        }

        if (view == null) {
            return;
        }

        startCounter++;

        if (view.getParent() != null) {
            windowManager.removeView(view);
        }

        if (destroyed) {
            view = null;
        }
    }

    public void onDestroy() {
        destroyed = true;
        stopFilter();
        Log.d(LOG, "Service stopped");
        running = false;
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (orientation == newConfig.orientation) {
            return;
        }

        orientation = newConfig.orientation;
        updatePattern();

        if (view.getParent() != null) {
            Log.d(LOG, "Screen orientation or smallest width changed, updating window layout");

            WindowManager.LayoutParams params = getLayoutParams();
            windowManager.updateViewLayout(view, params);
        }
    }

    private WindowManager.LayoutParams getLayoutParams() {
        Point displaySize = new Point();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            windowManager.getDefaultDisplay().getRealSize(displaySize);
        } else {
            Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
            displaySize.x = bounds.width();
            displaySize.y = bounds.height();
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                displaySize.x,
                displaySize.y,
                0,
                0,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSPARENT
        );

        try {
            // Use the rounded corners overlay to hide it from screenshots. See 132c9f514.
            // Use the trusted overlay to use it on some security required screens like VPN dialogs.
            int val = XposedHelpers.getIntField(params, "privateFlags");
            val |= PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY | PRIVATE_FLAG_TRUSTED_OVERLAY;
            XposedHelpers.setIntField(params, "privateFlags", val);
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }

        return params;
    }

    private int getShift() {
        long shift = (System.currentTimeMillis() / Grids.ShiftTimeouts[mShiftTimeout]) % Grids.GridSize;
        return Grids.GridShift[(int)shift];
    }

    public static void drawPattern(Bitmap bmp, int pattern, int shift, int dimColor) {
        int shiftX = shift % Grids.GridSideSize;
        int shiftY = shift / Grids.GridSideSize;
        for (int i = 0; i < Grids.GridSize; i++) {
            int x = (i + shiftX) % Grids.GridSideSize;
            int y = ((i / Grids.GridSideSize) + shiftY) % Grids.GridSideSize;
            int color = (Grids.Patterns[pattern][i] == 0) ? dimColor : Color.BLACK;
            bmp.setPixel(x, y, color);
        }
    }

    private void updatePattern() {
        drawPattern(draw.getBitmap(), mPattern, getShift(), getDimColor());

        if (view != null) {
            view.invalidate();
        }

        onPatternUpdated();
    }

    protected abstract void onPatternUpdated();
    protected abstract void onSettingsUpdated();

    private int getDimColor() {
        return Color.argb((int) ((mDimPercent / 100.0f) * 255), 0, 0, 0);
    }

    private void updateSettings() {
        mEnabled = SafeValueGetter.getEnabled(mContext);
        mEnabledOnPowerSaver = SafeValueGetter.getEnabledOnPowerSaver(mContext);
        mEnabledSystemBarsShift = SafeValueGetter.getSystemBarsShiftEnabled(mContext);
        mDimPercent = SafeValueGetter.getDimPercent(mContext);
        dimDragEnabled = SafeValueGetter.isSetDimOnSBDragEnabled(mContext);
        mPattern = SafeValueGetter.getPattern(mContext);
        mShiftTimeout = SafeValueGetter.getShiftTimeout(mContext);

        onSettingsUpdated();
    }

    private class SmartPixelsObserver extends ContentObserver {
        public SmartPixelsObserver() {
            super(mHandler);
            ContentResolver resolver = mContext.getContentResolver();

            if (!useAlternativeMethodForBS) {
                resolver.registerContentObserver(
                        Settings.Global.getUriFor(SettingsGlobal.LOW_POWER),
                        false,
                        this
                );
            }

            resolver.registerContentObserver(
                    Settings.System.getUriFor(SettingsSystem.SMART_PIXELS_ENABLED),
                    false,
                    this
            );

            resolver.registerContentObserver(
                    Settings.Global.getUriFor(SettingsSystem.SMART_PIXELS_ON_POWER_SAVE),
                    false,
                    this
            );

            resolver.registerContentObserver(
                    Settings.Global.getUriFor(SettingsSystem.SMART_PIXELS_SYSTEM_BARS_SHIFT),
                    false,
                    this
            );

            resolver.registerContentObserver(
                    Settings.System.getUriFor(SettingsSystem.SMART_PIXELS_DIM),
                    false,
                    this
            );

            resolver.registerContentObserver(
                    Settings.System.getUriFor(SettingsSystem.SMART_PIXELS_DIM_DRAG),
                    false,
                    this
            );

            resolver.registerContentObserver(
                    Settings.System.getUriFor(SettingsSystem.SMART_PIXELS_PATTERN),
                    false,
                    this
            );

            resolver.registerContentObserver(
                    Settings.System.getUriFor(SettingsSystem.SMART_PIXELS_SHIFT_TIMEOUT),
                    false,
                    this
            );
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            updateSettings();
            reloadFilter();
        }
    }
}
