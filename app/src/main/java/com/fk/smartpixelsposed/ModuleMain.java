// Copyright (C) 2024 Furkan Karcıoğlu <https://github.com/frknkrc44>
//
// This file is part of SmartPiXelsPosed project,
// and licensed under GNU Affero General Public License v3.
// See the GNU Affero General Public License for more details.
//
// All rights reserved. See COPYING, AUTHORS.
//

package com.fk.smartpixelsposed;

import android.content.res.Configuration;
import android.view.View;

import com.android.systemui.smartpixels.SmartPixelsService;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ModuleMain implements IXposedHookLoadPackage {
    private static final String SYSTEMUI_PKG = "com.android.systemui";
    private static final String SYSTEMUI_SB = SYSTEMUI_PKG + ".statusbar.phone.PhoneStatusBarView";
    private static final String SYSTEMUI_BC = SYSTEMUI_PKG + ".statusbar.policy.BatteryController";
    private static final String SYSTEMUI_BCIMPL = SYSTEMUI_PKG + ".statusbar.policy.BatteryControllerImpl";
    private static final String SYSTEMUI_BCCB = SYSTEMUI_BC + "$BatteryStateChangeCallback";
    private static final String SYSTEMUI_BST = SYSTEMUI_PKG + ".qs.tiles.BatterySaverTile";
    private static final String SYSTEMUI_BT = SYSTEMUI_PKG + ".qs.tiles.BatteryTile";
    private SmartPixelsService mSmartPixelsService;
    private boolean mUsingWorkaroundForBS = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(SYSTEMUI_PKG)) {
            return;
        }

        XC_MethodHook powerSaverHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (mSmartPixelsService != null) {
                    mSmartPixelsService.batterySaverEnabled = (boolean) param.args[0];
                    mSmartPixelsService.reloadFilter();
                }
            }
        };

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
                if (mSmartPixelsService == null) {
                    try {
                        View view = (View) param.thisObject;
                        mSmartPixelsService = new SmartPixelsService(view.getContext(), view.getHandler());
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
                try {
                    Configuration conf = (Configuration) param.args[0];
                    mSmartPixelsService.onConfigurationChanged(conf);
                } catch (Throwable e) {
                    XposedBridge.log(e);
                }
            }
        });

        XposedHelpers.findAndHookMethod(clazz2, "onDetachedFromWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
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
}
