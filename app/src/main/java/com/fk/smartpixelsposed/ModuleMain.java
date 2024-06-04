// Copyright (C) 2024 Furkan Karcıoğlu <https://github.com/frknkrc44>
//
// This file is part of SmartPiXelsPosed project,
// and licensed under GNU Affero General Public License v3.
// See the GNU Affero General Public License for more details.
//
// All rights reserved. See COPYING, AUTHORS.
//

package com.fk.smartpixelsposed;

import android.content.Context;
import android.content.res.Configuration;

import com.android.systemui.smartpixels.SmartPixelsService;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ModuleMain implements IXposedHookLoadPackage {
    private static final String SYSTEMUI_PKG = "com.android.systemui";
    private static final String SYSTEMUI_APP = SYSTEMUI_PKG + ".SystemUIApplication";
    private static final String SYSTEMUI_SB = SYSTEMUI_PKG + ".statusbar.phone.PhoneStatusBarView";
    private SmartPixelsService mSmartPixelsService;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(SYSTEMUI_PKG)) {
            return;
        }

        Class<?> clazz = XposedHelpers.findClass(SYSTEMUI_APP, lpparam.classLoader);
        XposedHelpers.findAndHookMethod(clazz, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);

                try {
                    Context context = (Context) param.thisObject;
                    mSmartPixelsService = new SmartPixelsService(context);
                } catch (Throwable e) {
                    XposedBridge.log(e);
                }
            }
        });

        Class<?> clazz2 = XposedHelpers.findClass(SYSTEMUI_SB, lpparam.classLoader);
        XposedHelpers.findAndHookMethod(clazz2, "onConfigurationChanged", Configuration.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                XposedBridge.log(param.thisObject.getClass().getSimpleName() + " - onConfigurationChanged");

                try {
                    Configuration conf = (Configuration) param.args[0];
                    mSmartPixelsService.onConfigurationChanged(conf);
                } catch (Throwable e) {
                    XposedBridge.log(e);
                }
            }
        });
    }
}
