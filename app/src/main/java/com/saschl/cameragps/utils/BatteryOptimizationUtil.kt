package com.saschl.cameragps.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Source: https://github.com/fei0316/snapstreak-alarm/blob/master/app/src/main/java/com/iatfei/streakalarm/BatteryOptimizationUtil.java
 * Source: https://gist.github.com/moopat/e9735fa8b5cff69d003353a4feadcdbc
 */
object BatteryOptimizationUtil {

    fun getResolveableComponentName(context: Context): Intent? {
        for (componentName in componentNames) {
            val intent = Intent()
            intent.setComponent(componentName)
            if (context.packageManager
                    .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
            ) return intent
        }
        return null
    }

    private val componentNames: MutableList<ComponentName?>
        /**
         * Get a list of all known ComponentNames that provide battery optimization on different
         * devices.
         * Based on Shivam Oberoi's answer on StackOverflow: https://stackoverflow.com/a/48166241/2143225
         *
         * @return list of ComponentName
         */
        get() {
            val names: MutableList<ComponentName?> = ArrayList<ComponentName?>()
            names.add(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.color.safecenter",
                    "com.color.safecenter.permission.startup.StartupAppListActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.samsung.android.sm_cn",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.vivo.abe",
                    "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.htc.pitroad",
                    "com.htc.pitroad.landingpage.activity.LandingPageActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.asus.mobilemanager",
                    "com.asus.mobilemanager.MainActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.meizu.safe",
                    "com.meizu.safe.powerui.PowerAppPermissionActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.zte.heartyservice",
                    "com.zte.heartyservice.setting.ClearAppSettingsActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.lenovo.security",
                    "com.lenovo.security.purebackground.PureBackgroundActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.yulong.android.security",
                    "com.yulong.android.seccenter.tabbarmain"
                )
            )
            names.add(
                ComponentName(
                    "com.letv.android.letvsafe",
                    "com.letv.android.letvsafe.BackgroundAppManageActivity"
                )
            )
            names.add(
                ComponentName(
                    "com.gionee.softmanager",
                    "com.gionee.softmanager.MainActivity"
                )
            )
            return names
        }
}