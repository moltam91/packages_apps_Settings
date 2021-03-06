/*
 * Copyright (C) 2014 VanirAOSP && the Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.vanir;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;

import com.android.settings.DisplaySettings;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.hardware.DisplayColor;
import com.android.settings.hardware.DisplayGamma;
import com.android.settings.hardware.VibratorIntensity;
import com.android.settings.location.LocationSettings;
import com.android.settings.DevelopmentSettings;

import java.io.File;

public class BootReceiver extends BroadcastReceiver {

    private static final String LOG_PATH = "/sys/module/logger/parameters/log_enabled";

    @Override
    public void onReceive(Context ctx, Intent intent) {

        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            if (exists(LOG_PATH)) {
                DevelopmentSettings.updateLogging(ctx);
            }
        }

        if (BatterySaverHelper.deviceSupportsMobileData(ctx)) {
            BatterySaverHelper.scheduleService(ctx);
        }

        /* Restore hardware tunable values */
        DisplayColor.restore(ctx);
        DisplayGamma.restore(ctx);
        VibratorIntensity.restore(ctx);
        DisplaySettings.restore(ctx);
        LocationSettings.restore(ctx);
    }

    private static boolean exists(String string) {
        File f = new File(string);
        if (f.exists()) {
            return true;
        } else {
            return false;
        }
    }
}
