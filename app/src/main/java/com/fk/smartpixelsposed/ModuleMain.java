// Copyright (C) 2024 Furkan Karcıoğlu <https://github.com/frknkrc44>
//
// This file is part of SmartPiXelsPosed project,
// and licensed under GNU Affero General Public License v3.
// See the GNU Affero General Public License for more details.
//
// All rights reserved. See COPYING, AUTHORS.
//

package com.fk.smartpixelsposed;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.os.Handler;
import android.provider.Settings;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import com.android.systemui.smartpixels.SmartPixelsService;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

@SuppressLint("DiscouragedApi")
public class ModuleMain implements IXposedHookLoadPackage {
    private static final String SYSTEMUI_PKG = "com.android.systemui";
    private static final String SYSTEMUI_APP = SYSTEMUI_PKG + ".SystemUIApplication";
    private static final String SYSTEMUI_PANELBAR = SYSTEMUI_PKG + ".statusbar.phone.PanelBar";
    private static final String SYSTEMUI_SB = SYSTEMUI_PKG + ".statusbar.phone.PhoneStatusBarView";
    private static final String SYSTEMUI_BC = SYSTEMUI_PKG + ".statusbar.policy.BatteryController";
    private static final String SYSTEMUI_BCIMPL = SYSTEMUI_PKG + ".statusbar.policy.BatteryControllerImpl";
    private static final String SYSTEMUI_MIUIBCIMPL = SYSTEMUI_PKG + ".statusbar.policy.MiuiBatteryControllerImpl";
    private static final String SYSTEMUI_BCCB = SYSTEMUI_BC + "$BatteryStateChangeCallback";
    private static final String SYSTEMUI_BST = SYSTEMUI_PKG + ".qs.tiles.BatterySaverTile";
    private static final String SYSTEMUI_BT = SYSTEMUI_PKG + ".qs.tiles.BatteryTile";
    private static final String SETTINGSLIB_BSUTILS = "com.android.settingslib.fuelgauge.BatterySaverUtils";

/*
    private static final String SYSTEMUI_NBVIEW1 = "com.android.systemui.statusbar.phone.NavigationBarView";
    private static final String SYSTEMUI_NBVIEW2 = "com.android.systemui.navigationbar.NavigationBarView";
*/

    private static final String ID_STATUS_BAR_CONTENTS = "status_bar_contents";
    private static final String ID_NOTIFICATION_LIGHTS_OUT = "notification_lights_out";
    private static final String ID_SYSTEM_ICONS = "system_icons";
    private static final String ID_CENTERED_AREA = "centered_area";
    private static final String ID_CLOCK_RIGHT_LAYOUT = "right_clock_layout";

    private SmartPixelsService mSmartPixelsService;
    private View mStatusBarView;
    // private View mNavBarView;
    private boolean mUsingWorkaroundForBS = false;
    private boolean mIsOEM = false;
    private DisplayManagerGlobal mDisplayManagerGlobal;
    private DisplayManager mDisplayManager;

    private long lastFunctionCalledTime = 0;

    // classes for alternative inject points
    private Class<?> clazzPanelBar = null;
    private Class<?> clazzApp = null;

    // --- GravityBox inspirations - start --- //
    private int mLinger;
    private boolean mJustPeeked;
    private int mInitialTouchX;
    private int mInitialTouchY;
    // --- GravityBox inspirations - end --- //

    public boolean isAlternativeInjectEnabled() {
        return clazzPanelBar != null || clazzApp != null;
    }

    private final BroadcastReceiver mScreenListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mSmartPixelsService.isEnabled()) {
                return;
            }

            switch (intent.getAction()) {
                case Intent.ACTION_USER_UNLOCKED:
                case Intent.ACTION_SCREEN_ON:
                    mSmartPixelsService.reloadFilter();
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    mSmartPixelsService.stopFilter();
                    break;
            }
        }
    };

    private final DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            XposedBridge.log("[SpSd - DC] Display change event triggered!");

            DisplayInfo displayInfo = mDisplayManagerGlobal.getDisplayInfo(displayId);

            XposedBridge.log("[SpSd - DC] State: " + displayInfo.state + " Committed: " + displayInfo.committedState);

            mScreenListener.onReceive(
                    mStatusBarView.getContext(),
                    new Intent(
                            displayInfo.state == Display.STATE_OFF
                                    ? Intent.ACTION_SCREEN_OFF
                                    : Intent.ACTION_SCREEN_ON
                    )
            );
        }
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) {
            XposedHelpers.findAndHookMethod(
                    MainActivity.class.getName(),
                    lpparam.classLoader,
                    "isEnabled",
                    XC_MethodReplacement.returnConstant(true)
            );
            return;
        }

        if (!lpparam.packageName.equals(SYSTEMUI_PKG)) {
            return;
        }

        Class<?> bsUtilsClazz = XposedHelpers.findClassIfExists(SETTINGSLIB_BSUTILS, lpparam.classLoader);
        if (bsUtilsClazz != null) {
            mUsingWorkaroundForBS = true;

            XposedBridge.hookAllMethods(bsUtilsClazz, "setPowerSaveMode", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[SpSd - PS] " + SETTINGSLIB_BSUTILS + " " + param.method.getName());

                    for (Object arg : param.args) {
                        if (arg instanceof Boolean) {
                            mSmartPixelsService.batterySaverEnabled = (boolean) arg;
                            mSmartPixelsService.reloadFilter();
                            return;
                        }
                    }
                }
            });
        }

        XC_MethodHook powerSaverHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("[SpSd - PS] " + param.thisObject + " " + param.method.getName());

                if (mSmartPixelsService != null) {
                    mSmartPixelsService.batterySaverEnabled = (boolean) param.args[0];
                    mSmartPixelsService.reloadFilter();
                }
            }
        };

        Class<?> miuibcImplClazz = XposedHelpers.findClassIfExists(SYSTEMUI_MIUIBCIMPL, lpparam.classLoader);
        if (miuibcImplClazz != null) {
            mUsingWorkaroundForBS = true;
            mIsOEM = true;

            XposedBridge.hookAllMethods(miuibcImplClazz, "setPowerSave", powerSaverHook);
        }

        Class<?> bcImplClazz = XposedHelpers.findClassIfExists(SYSTEMUI_BCIMPL, lpparam.classLoader);
        if (bcImplClazz != null) {
            mUsingWorkaroundForBS = true;

            XposedBridge.hookAllMethods(bcImplClazz, "setPowerSave", powerSaverHook);
            XposedBridge.hookAllMethods(bcImplClazz, "setPowerSaveMode", powerSaverHook);
        }

        Class<?> bsTileClazz = XposedHelpers.findClassIfExists(SYSTEMUI_BST, lpparam.classLoader);
        if (bsTileClazz != null) {
            mUsingWorkaroundForBS = true;

            XposedBridge.hookAllMethods(bsTileClazz, "onPowerSaveChanged", powerSaverHook);
        }

        Class<?> bTileClazz = XposedHelpers.findClassIfExists(SYSTEMUI_BT, lpparam.classLoader);
        if (bTileClazz != null) {
            mUsingWorkaroundForBS = true;

            XposedBridge.hookAllMethods(bTileClazz, "onPowerSaveChanged", powerSaverHook);
        }

        Class<?> bcCallbackClazz = XposedHelpers.findClassIfExists(SYSTEMUI_BCCB, lpparam.classLoader);
        if (bcCallbackClazz != null) {
            mUsingWorkaroundForBS = true;

            XposedBridge.hookAllMethods(bcCallbackClazz, "onPowerSaveChanged", powerSaverHook);
        }

        /*
        Class<?> nbViewClazz = XposedHelpers.findClassIfExists(SYSTEMUI_NBVIEW1, lpparam.classLoader);
        if (nbViewClazz == null) nbViewClazz = XposedHelpers.findClassIfExists(SYSTEMUI_NBVIEW2, lpparam.classLoader);
        if (nbViewClazz != null) {
            XposedBridge.hookAllConstructors(nbViewClazz, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[SpSd - NB] " + param.thisObject);
                    mNavBarView = (View) param.thisObject;
                }
            });
        }
        */

        Class<?> clazz2 = XposedHelpers.findClass(SYSTEMUI_SB, lpparam.classLoader);

        Method onAttachedToWindowMethod = XposedHelpers.findMethodExactIfExists(clazz2, "onAttachedToWindow");
        if (onAttachedToWindowMethod == null) {
            clazzPanelBar = XposedHelpers.findClassIfExists(SYSTEMUI_PANELBAR, lpparam.classLoader);
            clazzApp = XposedHelpers.findClassIfExists(SYSTEMUI_APP, lpparam.classLoader);
        }

        XposedHelpers.findAndHookMethod(clazzPanelBar != null ? clazzPanelBar : clazz2, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("[SpSd - AW] " + param.thisObject + " " + param.method.getName());

                mStatusBarView = (View) param.thisObject;

                if (mDisplayManagerGlobal == null) {
                    mDisplayManagerGlobal = DisplayManagerGlobal.getInstance();
                    mDisplayManager = (DisplayManager) mStatusBarView.getContext().getSystemService(Context.DISPLAY_SERVICE);
                }

                if (mSmartPixelsService == null) {
                    try {
                        mSmartPixelsService = new SmartPixelsServiceImpl(mStatusBarView.getContext(), mStatusBarView.getHandler());
                        mSmartPixelsService.useAlternativeMethodForBS = mUsingWorkaroundForBS;
                        mSmartPixelsService.usingAltLogic = isAlternativeInjectEnabled();

                        /*
                        IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
                        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
                        screenFilter.addAction(Intent.ACTION_USER_UNLOCKED);
                        mStatusBarView.getContext().registerReceiver(mScreenListener, screenFilter);
                        */
                        mDisplayManager.registerDisplayListener(mDisplayListener, mStatusBarView.getHandler());
                    } catch (Throwable e) {
                        XposedBridge.log(e);
                        return;
                    }
                }

                mSmartPixelsService.startFilter();
                updateSystemBarShifting();
                updateSystemBarsAlpha();
            }
        });

        XposedHelpers.findAndHookMethod(clazzApp != null ? clazzApp : clazz2, "onConfigurationChanged", Configuration.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var currentTime = System.currentTimeMillis();
                if ((currentTime - lastFunctionCalledTime) < 3000) return;
                lastFunctionCalledTime = currentTime;

                XposedBridge.log("[SpSd - CC] " + param.thisObject + " " + param.method.getName());

                mStatusBarView = (View) param.thisObject;

                if (mSmartPixelsService == null) {
                    return;
                }

                try {
                    Configuration conf = (Configuration) param.args[0];
                    mSmartPixelsService.onConfigurationChanged(conf);
                } catch (Throwable e) {
                    XposedBridge.log(e);
                }

                updateSystemBarShifting();
                updateSystemBarsAlpha();
            }
        });

        XposedHelpers.findAndHookMethod(clazzPanelBar != null ? clazzPanelBar : clazz2, "onDetachedFromWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("[SpSd - DW] " + param.thisObject + " " + param.method.getName());

                mStatusBarView = (View) param.thisObject;

                if (mSmartPixelsService != null) {
                    try {
                        mSmartPixelsService.onDestroy();
                        // mStatusBarView.getContext().unregisterReceiver(mScreenListener);
                        mDisplayManager.unregisterDisplayListener(mDisplayListener);
                        mSmartPixelsService = null;
                        System.gc();
                    } catch (Throwable e) {
                        XposedBridge.log(e);
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod(clazz2, "onTouchEvent", MotionEvent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (mSmartPixelsService.dimDragEnabled) {
                    dimControl((MotionEvent) param.args[0]);
                    param.setResult(true);
                }
            }
        });

        mIsOEM = SystemProperties.isOEM();
    }

    private void updateSystemBarsAlpha() {
        if (isAlternativeInjectEnabled()) {
            Settings.System.putInt(
                    mStatusBarView.getContext().getContentResolver(),
                    SettingsSystem.SMART_PIXELS_ALT_LOGIC,
                    1
            );

            return;
        }

        if (!(mStatusBarView != null && mSmartPixelsService != null)) {
            return;
        }

        mStatusBarView.setAlpha(
                mSmartPixelsService.isEnabled()
                        ? 1.0f - (mSmartPixelsService.mBarsAlphaPercent / 100.0f)
                        : 1
        );

        /*
        if (mNavBarView != null) {
            mNavBarView.setAlpha(mStatusBarView.getAlpha());
        }
        */
    }

    private void updateSystemBarShifting() {
        if (!(mStatusBarView != null && mSmartPixelsService != null && mSmartPixelsService.isEnabled() && mSmartPixelsService.mEnabledSystemBarsShift)) {
            return;
        }

        final int pixelShiftAmount = 6;
        final int startPaddingAdd = (int) (System.currentTimeMillis() % pixelShiftAmount) + 2;
        final int topPaddingAdd = (int) (System.currentTimeMillis() % pixelShiftAmount) + 2;
        final boolean addToTop = (startPaddingAdd % 2) == 1;

        applyShiftingToView(ID_STATUS_BAR_CONTENTS, startPaddingAdd, topPaddingAdd, addToTop);
        applyShiftingToView(ID_NOTIFICATION_LIGHTS_OUT, startPaddingAdd, topPaddingAdd, addToTop);
        applyShiftingToView(ID_SYSTEM_ICONS, startPaddingAdd, topPaddingAdd, addToTop);
        applyShiftingToView(ID_CENTERED_AREA, startPaddingAdd, topPaddingAdd, addToTop);

        // fix right clock and system icons' look
        setHeight(ID_SYSTEM_ICONS, ViewGroup.LayoutParams.MATCH_PARENT);
        setHeight(ID_CLOCK_RIGHT_LAYOUT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void setHeight(String viewId, int height) {
        XposedBridge.log("[SpSd - SH] ID: " + viewId + " H: " + height);

        final Resources res = mStatusBarView.getResources();
        final int stContentsId = res.getIdentifier(
                viewId, "id", mStatusBarView.getContext().getPackageName());
        View foundView = stContentsId == 0 ? null : mStatusBarView.findViewById(stContentsId);

        if (foundView != null) {
            foundView.getLayoutParams().height = height;
        }
    }

    private void applyShiftingToView(String viewId, int startPaddingAdd, int topPaddingAdd, boolean addToTop) {
        XposedBridge.log("[SpSd - AS] ID: " + viewId + " ATT: " + addToTop);

        final Resources res = mStatusBarView.getResources();
        final int stContentsId = res.getIdentifier(
                viewId, "id", mStatusBarView.getContext().getPackageName());
        View foundView = stContentsId == 0 ? null : mStatusBarView.findViewById(stContentsId);

        int startPaddingBase, topPaddingBase, endPaddingBase, bottomPaddingBase;
        if (foundView != null) {
            if (mIsOEM) {
                startPaddingBase = ID_NOTIFICATION_LIGHTS_OUT.equals(viewId)
                        ? 0
                        : getDimension("status_bar_padding_start", foundView.getPaddingStart());
                topPaddingBase = ID_NOTIFICATION_LIGHTS_OUT.equals(viewId)
                        ? startPaddingBase
                        : getDimension("status_bar_padding_top", foundView.getPaddingTop());
                endPaddingBase = ID_NOTIFICATION_LIGHTS_OUT.equals(viewId)
                        ? 0
                        : getDimension("status_bar_padding_end", foundView.getPaddingEnd());
                bottomPaddingBase = ID_NOTIFICATION_LIGHTS_OUT.equals(viewId)
                        ? 0
                        : getDimension("status_bar_padding_bottom", 0);
            } else {
                startPaddingBase = foundView.getPaddingStart();
                topPaddingBase = foundView.getPaddingTop();
                endPaddingBase = foundView.getPaddingEnd();
                bottomPaddingBase = foundView.getPaddingBottom();
            }

            foundView.setPaddingRelative(
                    addToTop ? startPaddingBase + startPaddingAdd : startPaddingBase,
                    addToTop ? topPaddingBase + topPaddingAdd : topPaddingBase,
                    addToTop ? endPaddingBase : endPaddingBase + startPaddingAdd,
                    addToTop ? bottomPaddingBase : bottomPaddingBase + startPaddingAdd
            );
        }
    }

    private int getDimension(String name, int def) {
        Resources res = mStatusBarView.getResources();
        int resId = res.getIdentifier(name, "dimen", mStatusBarView.getContext().getPackageName());
        if (resId == 0) return def;
        return res.getDimensionPixelSize(resId);
    }

    // --- GravityBox inspirations - start --- //
    private void dimControl(MotionEvent event) {
        final int action = event.getAction();
        final int x = (int) event.getRawX();
        final int y = (int) event.getRawY();
        Handler handler = mStatusBarView.getHandler();
        final int statusBarHeight = mStatusBarView.getMeasuredHeight();
        int mPeekHeight = (int) (mStatusBarView.getResources().getDisplayMetrics().density * 84);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (y < statusBarHeight) {
                    mLinger = 0;
                    mInitialTouchX = x;
                    mInitialTouchY = y;
                    mJustPeeked = true;
                    handler.removeCallbacks(mLongPressBrightnessChange);
                    handler.postDelayed(mLongPressBrightnessChange, 500);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (y < statusBarHeight && mJustPeeked) {
                    if (mLinger > 20) {
                        adjustDim(x);
                    } else {
                        final int xDiff = Math.abs(x - mInitialTouchX);
                        final int yDiff = Math.abs(y - mInitialTouchY);
                        final int touchSlop = ViewConfiguration.get(mStatusBarView.getContext()).getScaledTouchSlop();
                        if (xDiff > yDiff) {
                            mLinger++;
                        }
                        if (xDiff > touchSlop || yDiff > touchSlop) {
                            handler.removeCallbacks(mLongPressBrightnessChange);
                        }
                    }
                } else {
                    if (y > mPeekHeight) {
                        mJustPeeked = false;
                    }
                    handler.removeCallbacks(mLongPressBrightnessChange);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(mLongPressBrightnessChange);
                break;
        }
    }

    private final Runnable mLongPressBrightnessChange = () -> adjustDim(mInitialTouchX);

    private void adjustDim(int x) {
        Context statusBarContext = mStatusBarView.getContext();
        int mScreenWidth = statusBarContext.getResources().getDisplayMetrics().widthPixels;
        float raw = ((float) x) / mScreenWidth;
        int brightnessValue = Math.max(0, 90 - (int)(raw * 100));

        Intent refreshIntent = new Intent(SmartPixelsService.INTENT_ACTION);
        refreshIntent.putExtra(SettingsSystem.SMART_PIXELS_DIM, brightnessValue);
        mSmartPixelsService.mSettingsReceiver.onReceive(statusBarContext, refreshIntent);
    }
    // --- GravityBox inspirations - end --- //

    private class SmartPixelsServiceImpl extends SmartPixelsService {
        public SmartPixelsServiceImpl(Context context, Handler handler) {
            super(context, handler);
        }

        @Override
        protected void onSettingsUpdated() {
            XposedHelpers.callMethod(
                    mStatusBarView,
                    "onConfigurationChanged",
                    mStatusBarView.getResources().getConfiguration()
            );
        }
    }
}
