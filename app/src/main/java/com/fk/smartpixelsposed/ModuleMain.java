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
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.view.View;

import com.android.systemui.smartpixels.SmartPixelsService;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

@SuppressLint("DiscouragedApi")
public class ModuleMain implements IXposedHookLoadPackage {
    private static final String SYSTEMUI_PKG = "com.android.systemui";
    private static final String SYSTEMUI_SB = SYSTEMUI_PKG + ".statusbar.phone.PhoneStatusBarView";
    private static final String SYSTEMUI_BC = SYSTEMUI_PKG + ".statusbar.policy.BatteryController";
    private static final String SYSTEMUI_BCIMPL = SYSTEMUI_PKG + ".statusbar.policy.BatteryControllerImpl";
    private static final String SYSTEMUI_MIUIBCIMPL = SYSTEMUI_PKG + ".statusbar.policy.MiuiBatteryControllerImpl";
    private static final String SYSTEMUI_BCCB = SYSTEMUI_BC + "$BatteryStateChangeCallback";
    private static final String SYSTEMUI_BST = SYSTEMUI_PKG + ".qs.tiles.BatterySaverTile";
    private static final String SYSTEMUI_BT = SYSTEMUI_PKG + ".qs.tiles.BatteryTile";
    private static final String SETTINGSLIB_BSUTILS = "com.android.settingslib.fuelgauge.BatterySaverUtils";
    private SmartPixelsService mSmartPixelsService;
    private View mStatusBarView;
    private boolean mUsingWorkaroundForBS = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
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

            XposedBridge.hookAllMethods(miuibcImplClazz, "setPowerSave", powerSaverHook);
        }

        Class<?> bcImplClazz = XposedHelpers.findClassIfExists(SYSTEMUI_BCIMPL, lpparam.classLoader);
        if (bcImplClazz != null) {
            mUsingWorkaroundForBS = true;

            XposedBridge.hookAllMethods(bcImplClazz, "setPowerSave", powerSaverHook);
            XposedBridge.hookAllMethods(bcImplClazz, "setPowerSaveMode", powerSaverHook);
        }

        Class<?> bCtrlClazz = XposedHelpers.findClassIfExists(SYSTEMUI_BC, lpparam.classLoader);
        if (bCtrlClazz != null) {
            mUsingWorkaroundForBS = true;

            XposedBridge.hookAllMethods(bCtrlClazz, "setPowerSaveMode", powerSaverHook);
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

        Class<?> clazz2 = XposedHelpers.findClass(SYSTEMUI_SB, lpparam.classLoader);
        XposedHelpers.findAndHookMethod(clazz2, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("[SpSd - AW] " + param.thisObject + " " + param.method.getName());

                mStatusBarView = (View) param.thisObject;

                if (mSmartPixelsService == null) {
                    try {
                        mSmartPixelsService = new SmartPixelsServiceImpl(mStatusBarView.getContext(), mStatusBarView.getHandler());
                        mSmartPixelsService.useAlternativeMethodForBS = mUsingWorkaroundForBS;
                    } catch (Throwable e) {
                        XposedBridge.log(e);
                    }
                }

                mSmartPixelsService.startFilter();
            }
        });

        XposedHelpers.findAndHookMethod(clazz2, "onConfigurationChanged", Configuration.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("[SpSd - CC] " + param.thisObject + " " + param.method.getName());

                mStatusBarView = (View) param.thisObject;

                try {
                    Configuration conf = (Configuration) param.args[0];
                    mSmartPixelsService.onConfigurationChanged(conf);
                } catch (Throwable e) {
                    XposedBridge.log(e);
                }

                updateSystemBarShifting();
            }
        });

        XposedHelpers.findAndHookMethod(clazz2, "onDetachedFromWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("[SpSd - DW] " + param.thisObject + " " + param.method.getName());

                mStatusBarView = (View) param.thisObject;

                if (mSmartPixelsService != null) {
                    try {
                        mSmartPixelsService.onDestroy();
                        System.gc();
                    } catch (Throwable e) {
                        XposedBridge.log(e);
                    }
                }
            }
        });
    }

    private void updateSystemBarShifting() {
        if (!(mStatusBarView != null && mSmartPixelsService != null && mSmartPixelsService.isEnabled() && mSmartPixelsService.mEnabledSystemBarsShift)) {
            return;
        }

        final int pixelShiftAmount = 6;
        final int startPaddingAdd = (int) (System.currentTimeMillis() % pixelShiftAmount) + 2;
        final int topPaddingAdd = (int) (System.currentTimeMillis() % pixelShiftAmount) + 2;
        final boolean addToTop = (startPaddingAdd % 2) == 1;

        applyShiftingToView("status_bar_contents", startPaddingAdd, topPaddingAdd, addToTop);
        applyShiftingToView("notification_lights_out", startPaddingAdd, topPaddingAdd, addToTop);
        applyShiftingToView("system_icons", startPaddingAdd, topPaddingAdd, addToTop);
    }

    private void applyShiftingToView(String viewId, int startPaddingAdd, int topPaddingAdd, boolean addToTop) {
        final Resources res = mStatusBarView.getResources();
        final int stContentsId = res.getIdentifier(
                viewId, "id", mStatusBarView.getContext().getPackageName());
        View foundView = stContentsId == 0 ? null : mStatusBarView.findViewById(stContentsId);

        if (foundView != null) {
            final int startPaddingBase = foundView.getPaddingStart();
            final int topPaddingBase = foundView.getPaddingTop();
            final int endPaddingBase = foundView.getPaddingEnd();
            final int bottomPaddingBase = foundView.getPaddingBottom();

            foundView.setPaddingRelative(
                    addToTop ? startPaddingBase + startPaddingAdd : startPaddingBase,
                    addToTop ? topPaddingBase + topPaddingAdd : topPaddingBase,
                    addToTop ? endPaddingBase : endPaddingBase + startPaddingAdd,
                    addToTop ? bottomPaddingBase : bottomPaddingBase + startPaddingAdd
            );
        }
    }

    private class SmartPixelsServiceImpl extends SmartPixelsService {
        private boolean fromSettingsUpdate = false;

        public SmartPixelsServiceImpl(Context context, Handler handler) {
            super(context, handler);
        }

        @Override
        protected void onPatternUpdated() {
            if (fromSettingsUpdate) {
                fromSettingsUpdate = false;
                return;
            }

            XposedHelpers.callMethod(
                    mStatusBarView,
                    "onConfigurationChanged",
                    mStatusBarView.getResources().getConfiguration()
            );
        }

        @Override
        protected void onSettingsUpdated() {
            fromSettingsUpdate = true;

            XposedHelpers.callMethod(
                    mStatusBarView,
                    "onConfigurationChanged",
                    mStatusBarView.getResources().getConfiguration()
            );
        }
    }
}
