/*
 * Copyright (c) 2015, Sergii Pylypenko
 *           (c) 2018, Joe Maples
 *           (c) 2018, Adin Kwok
 *           (c) 2018, CarbonROM
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
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
import com.fk.smartpixelsposed.SettingsSystem;

import de.robv.android.xposed.XposedHelpers;

public class SmartPixelsService {
    public static final String LOG = "SmartPixelsService";
    public static final String INTENT_ACTION = "com.android.systemui.action.SMART_PIXELS_REFRESH";

    private WindowManager windowManager;
    private View view = null;
    private BitmapDrawable draw;

    private boolean destroyed = false;
    public static boolean running = false;

    private int startCounter = 0;
    private Context mContext;
    private int orientation;
    private ContentObserver mObserver;
    private Handler mHandler;
    private IntentFilter mIntentFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int pattern = intent.getIntExtra(SettingsSystem.SMART_PIXELS_PATTERN, mPattern);
            int timeout = intent.getIntExtra(SettingsSystem.SMART_PIXELS_SHIFT_TIMEOUT, mShiftTimeout);

            Settings.System.putInt(
                    context.getContentResolver(),
                    SettingsSystem.SMART_PIXELS_PATTERN,
                    pattern
            );
            Settings.System.putInt(
                    context.getContentResolver(),
                    SettingsSystem.SMART_PIXELS_SHIFT_TIMEOUT,
                    timeout
            );
        }
    };

    // Pixel Filter Settings
    private int mPattern = 3;
    private int mShiftTimeout = 4;

    public static final int PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY = 1 << 20;
    public static final int PRIVATE_FLAG_TRUSTED_OVERLAY = 1 << 29;

    public SmartPixelsService(Context context, Handler handler) {
        onCreate(context, handler);
    }

    private void onCreate(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        orientation = context.getResources().getConfiguration().orientation;

        updateSettings();
        Log.d(LOG, "Service started");

        startFilter();
    }

    public void startFilter(){
        if (view != null) {
            return;
        }

        running = true;
        windowManager = (WindowManager) mContext.getSystemService(WINDOW_SERVICE);

        view = new View(mContext);

        if (mIntentFilter == null) {
            mIntentFilter = new IntentFilter(INTENT_ACTION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mContext.registerReceiver(mReceiver, mIntentFilter, Context.RECEIVER_EXPORTED);
            } else {
                mContext.registerReceiver(mReceiver, mIntentFilter);
            }
        }

        if (mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }

        if (mObserver == null) {
            mObserver = new ContentObserver(mHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);

                    updateSettings();
                    reloadFilter();
                }
            };

            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(SettingsSystem.SMART_PIXELS_PATTERN),
                    false,
                    mObserver
            );
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(SettingsSystem.SMART_PIXELS_SHIFT_TIMEOUT),
                    false,
                    mObserver
            );
        }

        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);

        if (draw == null) {
            Bitmap bmp = Bitmap.createBitmap(Grids.GridSideSize, Grids.GridSideSize, Bitmap.Config.ARGB_4444);
            draw = new BitmapDrawable(mContext.getResources(), bmp);
            draw.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            draw.setFilterBitmap(false);
            draw.setAntiAlias(false);
            draw.setTargetDensity(metrics.densityDpi);
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

    private void reloadFilter() {
        mHandler.removeCallbacksAndMessages(null);
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

        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
        }

        if (view == null) {
            return;
        }

        startCounter++;

        windowManager.removeView(view);
        view = null;
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
        Log.d(LOG, "Screen orientation changed, updating window layout");

        WindowManager.LayoutParams params = getLayoutParams();
        windowManager.updateViewLayout(view, params);
    }

    private WindowManager.LayoutParams getLayoutParams() {
        Point displaySize = new Point();
        windowManager.getDefaultDisplay().getRealSize(displaySize);
        Point windowSize = new Point();
        windowManager.getDefaultDisplay().getRealSize(windowSize);
        Resources res = mContext.getResources();

        int resId = res.getIdentifier("status_bar_height", "dimen", "android");
        int mStatusBarHeight = resId != 0 ? res.getDimensionPixelOffset(resId) : (int)(res.getDisplayMetrics().density * 25);
        displaySize.x += displaySize.x - windowSize.x + (mStatusBarHeight * 2);
        displaySize.y += displaySize.y - windowSize.y + (mStatusBarHeight * 2);

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

        // Use the rounded corners overlay to hide it from screenshots. See 132c9f514.
        int val = XposedHelpers.getIntField(params, "privateFlags");
        val |= PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY | PRIVATE_FLAG_TRUSTED_OVERLAY;
        XposedHelpers.setIntField(params, "privateFlags", val);
        return params;
    }

    private int getShift() {
        long shift = (System.currentTimeMillis() / Grids.ShiftTimeouts[mShiftTimeout]) % Grids.GridSize;
        return Grids.GridShift[(int)shift];
    }

    private void updatePattern() {
        int shift = getShift();
        int shiftX = shift % Grids.GridSideSize;
        int shiftY = shift / Grids.GridSideSize;
        for (int i = 0; i < Grids.GridSize; i++) {
            int x = (i + shiftX) % Grids.GridSideSize;
            int y = ((i / Grids.GridSideSize) + shiftY) % Grids.GridSideSize;
            int color = (Grids.Patterns[mPattern][i] == 0) ? Color.TRANSPARENT : Color.BLACK;
            draw.getBitmap().setPixel(x, y, color);
        }

        if (view != null) {
            view.invalidate();
        }
    }

    private void updateSettings() {
        mPattern = SafeValueGetter.getPattern(mContext);
        mShiftTimeout = SafeValueGetter.getShiftTimeout(mContext);
    }
}
