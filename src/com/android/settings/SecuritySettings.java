/*
 * Copyright (C) 2007 The Android Open Source Project
 * Modifications Copyright (C) 2012-2013 CyanogenMod
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

package com.android.settings;


import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.security.KeyStore;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.util.BlacklistUtils;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.cyanogenmod.ButtonSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Gesture lock pattern settings.
 */
public class SecuritySettings extends RestrictedSettingsFragment
        implements OnPreferenceChangeListener, DialogInterface.OnClickListener {
    static final String TAG = "SecuritySettings";

    // Lock Settings
    private static final String KEY_UNLOCK_SET_OR_CHANGE = "unlock_set_or_change";
    private static final String KEY_BIOMETRIC_WEAK_IMPROVE_MATCHING =
            "biometric_weak_improve_matching";
    private static final String KEY_BIOMETRIC_WEAK_LIVELINESS = "biometric_weak_liveliness";
    private static final String KEY_LOCK_ENABLED = "lockenabled";
    private static final String KEY_VISIBLE_PATTERN = "visiblepattern";
    private static final String KEY_VISIBLE_ERROR_PATTERN = "visible_error_pattern";
    private static final String KEY_VISIBLE_DOTS = "visibledots";
    private static final String KEY_SECURITY_CATEGORY = "security_category";
    private static final String KEY_DEVICE_ADMIN_CATEGORY = "device_admin_category";
    private static final String KEY_LOCK_AFTER_TIMEOUT = "lock_after_timeout";
    private static final String KEY_OWNER_INFO_SETTINGS = "owner_info_settings";

    private static final int SET_OR_CHANGE_LOCK_METHOD_REQUEST = 123;
    private static final int CONFIRM_EXISTING_FOR_BIOMETRIC_WEAK_IMPROVE_REQUEST = 124;
    private static final int CONFIRM_EXISTING_FOR_BIOMETRIC_WEAK_LIVELINESS_OFF = 125;

    // Misc Settings
    private static final String KEY_SIM_LOCK = "sim_lock";
    private static final String KEY_SHOW_PASSWORD = "show_password";
    private static final String KEY_CREDENTIAL_STORAGE_TYPE = "credential_storage_type";
    private static final String KEY_RESET_CREDENTIALS = "reset_credentials";
    private static final String KEY_CREDENTIALS_INSTALL = "credentials_install";
    private static final String KEY_TOGGLE_INSTALL_APPLICATIONS = "toggle_install_applications";
    private static final String KEY_TOGGLE_VERIFY_APPLICATIONS = "toggle_verify_applications";
    private static final String KEY_POWER_INSTANTLY_LOCKS = "power_button_instantly_locks";
    private static final String KEY_CREDENTIALS_MANAGER = "credentials_management";
    private static final String KEY_NOTIFICATION_ACCESS = "manage_notification_access";
    private static final String PACKAGE_MIME_TYPE = "application/vnd.android.package-archive";

    private static final String KEY_APP_SECURITY_CATEGORY = "app_security";
    private static final String KEY_BLACKLIST = "blacklist";
    private static final String KEY_SMS_SECURITY_CHECK_PREF = "sms_security_check_limit";
    private static final String SLIDE_LOCK_TIMEOUT_DELAY = "slide_lock_timeout_delay";
    private static final String SLIDE_LOCK_SCREENOFF_DELAY = "slide_lock_screenoff_delay";
    private static final String LOCKSCREEN_QUICK_UNLOCK_CONTROL = "lockscreen_quick_unlock_control";
    private static final String KEY_LOCKSCREEN_NOTIFICATIONS = "lockscreen_notifications_allowed";
    private static final String CATEGORY_ADDITIONAL = "additional_options";

    private PackageManager mPM;
    private DevicePolicyManager mDPM;

    private ChooseLockSettingsHelper mChooseLockSettingsHelper;
    private LockPatternUtils mLockPatternUtils;
    private ListPreference mLockAfter;

    private CheckBoxPreference mBiometricWeakLiveliness;
    private CheckBoxPreference mVisiblePattern;
    private CheckBoxPreference mVisibleErrorPattern;
    private CheckBoxPreference mVisibleDots;

    private CheckBoxPreference mShowPassword;

    private KeyStore mKeyStore;
    private Preference mResetCredentials;

    private CheckBoxPreference mToggleAppInstallation;
    private DialogInterface mWarnInstallApps;
    private CheckBoxPreference mToggleVerifyApps;
    private CheckBoxPreference mPowerButtonInstantlyLocks;


    private Preference mNotificationAccess;
    private PreferenceScreen mBlacklist;

    private boolean mIsPrimary;

    private CheckBoxPreference mLockNotifications;
    private CheckBoxPreference mQuickUnlockScreen;
    private ListPreference mSmsSecurityCheck;
    private ListPreference mSlideLockTimeoutDelay;
    private ListPreference mSlideLockScreenOffDelay;

    public SecuritySettings() {
        super(null /* Don't ask for restrictions pin on creation. */);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLockPatternUtils = new LockPatternUtils(getActivity());

        mPM = getActivity().getPackageManager();
        mDPM = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);

        mChooseLockSettingsHelper = new ChooseLockSettingsHelper(getActivity());
    }

    private PreferenceScreen createPreferenceHierarchy() {
        PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.security_settings);
        root = getPreferenceScreen();

        final ContentResolver resolver = getContentResolver();
        final Resources res = getResources();

        // Add package manager to check if features are available
        PackageManager pm = getPackageManager();

        // Add options for lock/unlock screen
        int resid = 0;
        if (!mLockPatternUtils.isSecure()) {
            // if there are multiple users, disable "None" setting
            UserManager mUm = (UserManager) getSystemService(Context.USER_SERVICE);
            List<UserInfo> users = mUm.getUsers(true);
            final boolean singleUser = users.size() == 1;

            if (singleUser && mLockPatternUtils.isLockScreenDisabled()) {
                resid = R.xml.security_settings_lockscreen;
            } else {
                resid = R.xml.security_settings_chooser;
            }
        } else if (mLockPatternUtils.usingBiometricWeak() &&
                mLockPatternUtils.isBiometricWeakInstalled()) {
            resid = R.xml.security_settings_biometric_weak;
        } else {
            switch (mLockPatternUtils.getKeyguardStoredPasswordQuality()) {
                case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                    resid = R.xml.security_settings_pattern;
                    break;
                case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
                    resid = R.xml.security_settings_pin;
                    break;
                case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
                case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
                case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
                    resid = R.xml.security_settings_password;
                    break;
            }
        }
        addPreferencesFromResource(resid);


        // Add options for device encryption
        mIsPrimary = UserHandle.myUserId() == UserHandle.USER_OWNER;

        if (!mIsPrimary) {
            // Rename owner info settings
            Preference ownerInfoPref = findPreference(KEY_OWNER_INFO_SETTINGS);
            if (ownerInfoPref != null) {
                if (UserManager.get(getActivity()).isLinkedUser()) {
                    ownerInfoPref.setTitle(R.string.profile_info_settings_title);
                } else {
                    ownerInfoPref.setTitle(R.string.user_info_settings_title);
                }
            }
        }

        if (mIsPrimary) {
            switch (mDPM.getStorageEncryptionStatus()) {
            case DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE:
                // The device is currently encrypted.
                addPreferencesFromResource(R.xml.security_settings_encrypted);
                break;
            case DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE:
                // This device supports encryption but isn't encrypted.
                addPreferencesFromResource(R.xml.security_settings_unencrypted);
                break;
            }
        }

        // lock after preference
        mLockAfter = (ListPreference) root.findPreference(KEY_LOCK_AFTER_TIMEOUT);
        if (mLockAfter != null) {
            setupLockAfterPreference();
            updateLockAfterPreferenceSummary();
        } else if (!mLockPatternUtils.isLockScreenDisabled()) {
            addPreferencesFromResource(R.xml.security_settings_slide_delay_cyanogenmod);

            mSlideLockTimeoutDelay = (ListPreference) root
                    .findPreference(SLIDE_LOCK_TIMEOUT_DELAY);
            int slideTimeoutDelay = Settings.System.getInt(resolver,
                    Settings.System.SCREEN_LOCK_SLIDE_TIMEOUT_DELAY, 5000);
            mSlideLockTimeoutDelay.setValue(String.valueOf(slideTimeoutDelay));
            updateSlideAfterTimeoutSummary();
            mSlideLockTimeoutDelay.setOnPreferenceChangeListener(this);

            mSlideLockScreenOffDelay = (ListPreference) root
                    .findPreference(SLIDE_LOCK_SCREENOFF_DELAY);
            int slideScreenOffDelay = Settings.System.getInt(resolver,
                    Settings.System.SCREEN_LOCK_SLIDE_SCREENOFF_DELAY, 0);
            mSlideLockScreenOffDelay.setValue(String.valueOf(slideScreenOffDelay));
            updateSlideAfterScreenOffSummary();
            mSlideLockScreenOffDelay.setOnPreferenceChangeListener(this);
        }

            // lock instantly on power key press
            mPowerButtonInstantlyLocks = (CheckBoxPreference) root.findPreference(
                    KEY_POWER_INSTANTLY_LOCKS);
            checkPowerInstantLockDependency();

            // Add the additional CyanogenMod settings
            addPreferencesFromResource(R.xml.security_settings_cyanogenmod);

            CheckBoxPreference menuUnlock = (CheckBoxPreference)
                    findPreference(Settings.System.MENU_UNLOCK_SCREEN);
            CheckBoxPreference homeUnlock = (CheckBoxPreference)
                    findPreference(Settings.System.HOME_UNLOCK_SCREEN);
            CheckBoxPreference cameraUnlock = (CheckBoxPreference)
                    findPreference(Settings.System.CAMERA_UNLOCK_SCREEN);

            final int deviceKeys = res.getInteger(
                    com.android.internal.R.integer.config_deviceHardwareKeys);
            final PreferenceGroup additionalPrefs =
                    (PreferenceGroup) findPreference(CATEGORY_ADDITIONAL);

            // hide all lock options if lock screen set to NONE
            if (mLockPatternUtils.isLockScreenDisabled()) {
                root.removePreference(additionalPrefs);
            // hide the quick unlock if using Pattern
            } else if (mLockPatternUtils.isLockPatternEnabled()) {
            // hide the quick unlock if its not using PIN/password
            // as a primary lock screen or as a backup to biometric
            }

            // Hide the MenuUnlock setting if no menu button is available
            if ((deviceKeys & ButtonSettings.KEY_MASK_MENU) == 0) {
                additionalPrefs.removePreference(menuUnlock);
            }
            // Hide the HomeUnlock setting if no home button is available
            if ((deviceKeys & ButtonSettings.KEY_MASK_HOME) == 0) {
                additionalPrefs.removePreference(homeUnlock);
            }
            // Hide the CameraUnlock setting if no camera button is available
            if ((deviceKeys & ButtonSettings.KEY_MASK_CAMERA) == 0) {
                additionalPrefs.removePreference(cameraUnlock);
            }

        // biometric weak liveliness
        mBiometricWeakLiveliness =
                (CheckBoxPreference) root.findPreference(KEY_BIOMETRIC_WEAK_LIVELINESS);

        // visible pattern
        mVisiblePattern = (CheckBoxPreference) root.findPreference(KEY_VISIBLE_PATTERN);

        // visible error pattern
        mVisibleErrorPattern = (CheckBoxPreference) root.findPreference(KEY_VISIBLE_ERROR_PATTERN);

        // visible dots
        mVisibleDots = (CheckBoxPreference) root.findPreference(KEY_VISIBLE_DOTS);

        // lock instantly on power key press
        mPowerButtonInstantlyLocks = (CheckBoxPreference) root.findPreference(
                KEY_POWER_INSTANTLY_LOCKS);

        // don't display visible pattern if biometric and backup is not pattern
        if (resid == R.xml.security_settings_biometric_weak &&
                mLockPatternUtils.getKeyguardStoredPasswordQuality() !=
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING) {
            PreferenceGroup securityCategory = (PreferenceGroup)
                    root.findPreference(KEY_SECURITY_CATEGORY);
            if (securityCategory != null && mVisiblePattern != null &&
                    mVisibleErrorPattern != null && mVisibleDots != null) {
                securityCategory.removePreference(mVisiblePattern);
                securityCategory.removePreference(mVisibleErrorPattern);
                securityCategory.removePreference(mVisibleDots);
            }
        }

            // Append the rest of the settings
            addPreferencesFromResource(R.xml.security_settings_misc);

            // Do not display SIM lock for devices without an Icc card
            TelephonyManager tm = TelephonyManager.getDefault();
            if (!mIsPrimary || !tm.hasIccCard()) {
                root.removePreference(root.findPreference(KEY_SIM_LOCK));
            } else {
                // Disable SIM lock if sim card is missing or unknown
                if ((TelephonyManager.getDefault().getSimState() ==
                                    TelephonyManager.SIM_STATE_ABSENT) ||
                    (TelephonyManager.getDefault().getSimState() ==
                                    TelephonyManager.SIM_STATE_UNKNOWN)) {
                    root.findPreference(KEY_SIM_LOCK).setEnabled(false);
                }
            }

            // Show password
            mShowPassword = (CheckBoxPreference) root.findPreference(KEY_SHOW_PASSWORD);
            mResetCredentials = root.findPreference(KEY_RESET_CREDENTIALS);

            // Credential storage
            final UserManager um = (UserManager) getActivity().getSystemService(Context.USER_SERVICE);
            mKeyStore = KeyStore.getInstance(); // needs to be initialized for onResume()
            if (!um.hasUserRestriction(UserManager.DISALLOW_CONFIG_CREDENTIALS)) {
                Preference credentialStorageType = root.findPreference(KEY_CREDENTIAL_STORAGE_TYPE);

                final int storageSummaryRes =
                    mKeyStore.isHardwareBacked() ? R.string.credential_storage_type_hardware
                            : R.string.credential_storage_type_software;
                credentialStorageType.setSummary(storageSummaryRes);

            } else {
                removePreference(KEY_CREDENTIALS_MANAGER);
            }

            // Application install
            PreferenceGroup deviceAdminCategory = (PreferenceGroup)
                    root.findPreference(KEY_DEVICE_ADMIN_CATEGORY);
            mToggleAppInstallation = (CheckBoxPreference) findPreference(
                    KEY_TOGGLE_INSTALL_APPLICATIONS);
            mToggleAppInstallation.setChecked(isNonMarketAppsAllowed());

            // Side loading of apps.
            mToggleAppInstallation.setEnabled(mIsPrimary);

            // Package verification, only visible to primary user and if enabled
            mToggleVerifyApps = (CheckBoxPreference) findPreference(KEY_TOGGLE_VERIFY_APPLICATIONS);
            if (mIsPrimary && showVerifierSetting()) {
                if (isVerifierInstalled()) {
                    mToggleVerifyApps.setChecked(isVerifyAppsEnabled());
                } else {
                    mToggleVerifyApps.setChecked(false);
                    mToggleVerifyApps.setEnabled(false);
                }
            } else {
                if (deviceAdminCategory != null) {
                    deviceAdminCategory.removePreference(mToggleVerifyApps);
                } else {
                    mToggleVerifyApps.setEnabled(false);
                }
            }

            // App security settings
            addPreferencesFromResource(R.xml.security_settings_app_cyanogenmod);
            mBlacklist = (PreferenceScreen) root.findPreference(KEY_BLACKLIST);
            mSmsSecurityCheck = (ListPreference) root.findPreference(KEY_SMS_SECURITY_CHECK_PREF);
            // Determine options based on device telephony support
            if (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                mSmsSecurityCheck = (ListPreference) root.findPreference(KEY_SMS_SECURITY_CHECK_PREF);
                mSmsSecurityCheck.setOnPreferenceChangeListener(this);
                int smsSecurityCheck = Integer.valueOf(mSmsSecurityCheck.getValue());
                updateSmsSecuritySummary(smsSecurityCheck);
            } else {
                // No telephony, remove dependent options
                PreferenceGroup appCategory = (PreferenceGroup)
                        root.findPreference(KEY_APP_SECURITY_CATEGORY);
                appCategory.removePreference(mBlacklist);
                appCategory.removePreference(mSmsSecurityCheck);
                root.removePreference(appCategory);
            }

            // WhisperPush
            // Only add if device has telephony support and has WhisperPush installed.
            if (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                    && isPackageInstalled("org.whispersystems.whisperpush")) {
                addPreferencesFromResource(R.xml.security_settings_whisperpush);
            }

        mLockNotifications = (CheckBoxPreference) root.findPreference(KEY_LOCKSCREEN_NOTIFICATIONS);
        if (mLockNotifications != null) {
            mLockNotifications.setChecked(Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_NOTIFICATIONS_ALLOWED, 0) == 1);
            mLockNotifications.setOnPreferenceChangeListener(this);
        }

        mQuickUnlockScreen = (CheckBoxPreference) root.findPreference(LOCKSCREEN_QUICK_UNLOCK_CONTROL);
        if (mQuickUnlockScreen  != null) {
            mQuickUnlockScreen.setChecked(Settings.System.getInt(getContentResolver(), 
                    Settings.System.LOCKSCREEN_QUICK_UNLOCK_CONTROL, 0) == 1);
            mQuickUnlockScreen.setOnPreferenceChangeListener(this);
        }

        mNotificationAccess = findPreference(KEY_NOTIFICATION_ACCESS);
        if (mNotificationAccess != null) {
            final int total = NotificationAccessSettings.getListenersCount(mPM);
            if (total == 0) {
                if (deviceAdminCategory != null) {
                    deviceAdminCategory.removePreference(mNotificationAccess);
                }
            } else {
                final int n = getNumEnabledNotificationListeners();
                if (n == 0) {
                    mNotificationAccess.setSummary(getResources().getString(
                            R.string.manage_notification_access_summary_zero));
                } else {
                    mNotificationAccess.setSummary(String.format(getResources().getQuantityString(
                            R.plurals.manage_notification_access_summary_nonzero,
                            n, n)));
                }
            }
        }

        if (shouldBePinProtected(RESTRICTIONS_PIN_SET)) {
            protectByRestrictions(mToggleAppInstallation);
            protectByRestrictions(mToggleVerifyApps);
            protectByRestrictions(mResetCredentials);
            protectByRestrictions(root.findPreference(KEY_CREDENTIALS_INSTALL));
        }
        return root;
    }

    private int getNumEnabledNotificationListeners() {
        final String flat = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_NOTIFICATION_LISTENERS);
        if (flat == null || "".equals(flat)) return 0;
        final String[] components = flat.split(":");
        return components.length;
    }

    private boolean isNonMarketAppsAllowed() {
        return Settings.Global.getInt(getContentResolver(),
                                      Settings.Global.INSTALL_NON_MARKET_APPS, 0) > 0;
    }

    private void setNonMarketAppsAllowed(boolean enabled) {
        final UserManager um = (UserManager) getActivity().getSystemService(Context.USER_SERVICE);
        if (um.hasUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)) {
            return;
        }
        // Change the system setting
        Settings.Global.putInt(getContentResolver(), Settings.Global.INSTALL_NON_MARKET_APPS,
                                enabled ? 1 : 0);
    }

    private boolean isVerifyAppsEnabled() {
        return Settings.Global.getInt(getContentResolver(),
                                      Settings.Global.PACKAGE_VERIFIER_ENABLE, 1) > 0;
    }

    private boolean isVerifierInstalled() {
        final PackageManager pm = getPackageManager();
        final Intent verification = new Intent(Intent.ACTION_PACKAGE_NEEDS_VERIFICATION);
        verification.setType(PACKAGE_MIME_TYPE);
        verification.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        final List<ResolveInfo> receivers = pm.queryBroadcastReceivers(verification, 0);
        return (receivers.size() > 0) ? true : false;
    }

    private boolean showVerifierSetting() {
        return Settings.Global.getInt(getContentResolver(),
                                      Settings.Global.PACKAGE_VERIFIER_SETTING_VISIBLE, 1) > 0;
    }

    private void warnAppInstallation() {
        // TODO: DialogFragment?
        mWarnInstallApps = new AlertDialog.Builder(getActivity()).setTitle(
                getResources().getString(R.string.error_title))
                .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
                .setMessage(getResources().getString(R.string.install_all_warning))
                .setPositiveButton(android.R.string.yes, this)
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (dialog == mWarnInstallApps && which == DialogInterface.BUTTON_POSITIVE) {
            setNonMarketAppsAllowed(true);
            if (mToggleAppInstallation != null) {
                mToggleAppInstallation.setChecked(true);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mWarnInstallApps != null) {
            mWarnInstallApps.dismiss();
        }
    }

    private void updateSlideAfterTimeoutSummary() {
        // Update summary message with current value
        long currentTimeout = Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_LOCK_SLIDE_TIMEOUT_DELAY, 5000);
        final CharSequence[] entries = mSlideLockTimeoutDelay.getEntries();
        final CharSequence[] values = mSlideLockTimeoutDelay.getEntryValues();
        int best = 0;
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.valueOf(values[i].toString());
            if (currentTimeout >= timeout) {
                best = i;
            }
        }
        mSlideLockTimeoutDelay.setSummary(entries[best]);
    }

    private void updateSlideAfterScreenOffSummary() {
        // Update summary message with current value
        long currentTimeout = Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_LOCK_SLIDE_SCREENOFF_DELAY, 0);
        final CharSequence[] entries = mSlideLockScreenOffDelay.getEntries();
        final CharSequence[] values = mSlideLockScreenOffDelay.getEntryValues();
        int best = 0;
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.valueOf(values[i].toString());
            if (currentTimeout >= timeout) {
                best = i;
            }
        }
        mSlideLockScreenOffDelay.setSummary(entries[best]);
    }

    private void updateSmsSecuritySummary(int selection) {
        String message = selection > 0
                ? getString(R.string.sms_security_check_limit_summary, selection)
                : getString(R.string.sms_security_check_limit_summary_none);
        mSmsSecurityCheck.setSummary(message);
    }

    private void setupLockAfterPreference() {
        // Compatible with pre-Froyo
        long currentTimeout = Settings.Secure.getLong(getContentResolver(),
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT, 5000);
        mLockAfter.setValue(String.valueOf(currentTimeout));
        mLockAfter.setOnPreferenceChangeListener(this);
        final long adminTimeout = (mDPM != null ? mDPM.getMaximumTimeToLock(null) : 0);
        final long displayTimeout = Math.max(0,
                Settings.System.getInt(getContentResolver(), SCREEN_OFF_TIMEOUT, 0));
        if (adminTimeout > 0) {
            // This setting is a slave to display timeout when a device policy is enforced.
            // As such, maxLockTimeout = adminTimeout - displayTimeout.
            // If there isn't enough time, shows "immediately" setting.
            disableUnusableTimeouts(Math.max(0, adminTimeout - displayTimeout));
        }
    }

    private void updateLockAfterPreferenceSummary() {
        // Update summary message with current value
        long currentTimeout = Settings.Secure.getLong(getContentResolver(),
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT, 5000);
        final CharSequence[] entries = mLockAfter.getEntries();
        final CharSequence[] values = mLockAfter.getEntryValues();
        int best = 0;
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.valueOf(values[i].toString());
            if (currentTimeout >= timeout) {
                best = i;
            }
        }
        mLockAfter.setSummary(getString(R.string.lock_after_timeout_summary, entries[best]));
    }

    private void checkPowerInstantLockDependency() {
        if (mPowerButtonInstantlyLocks != null) {
            long timeout = Settings.Secure.getLong(getContentResolver(),
                    Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT, 5000);
            if (timeout == 0) {
                mPowerButtonInstantlyLocks.setEnabled(false);
            } else {
                mPowerButtonInstantlyLocks.setEnabled(true);
            }
        }
    }

    private void disableUnusableTimeouts(long maxTimeout) {
        final CharSequence[] entries = mLockAfter.getEntries();
        final CharSequence[] values = mLockAfter.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.valueOf(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            mLockAfter.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            mLockAfter.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            final int userPreference = Integer.valueOf(mLockAfter.getValue());
            if (userPreference <= maxTimeout) {
                mLockAfter.setValue(String.valueOf(userPreference));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        mLockAfter.setEnabled(revisedEntries.size() > 0);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Make sure we reload the preference hierarchy since some of these settings
        // depend on others...
        createPreferenceHierarchy();

        final LockPatternUtils lockPatternUtils = mChooseLockSettingsHelper.utils();
        if (mBiometricWeakLiveliness != null) {
            mBiometricWeakLiveliness.setChecked(
                    lockPatternUtils.isBiometricWeakLivelinessEnabled());
        }
        if (mVisiblePattern != null) {
            mVisiblePattern.setChecked(lockPatternUtils.isVisiblePatternEnabled());
        }
        if (mVisibleErrorPattern != null) {
            mVisibleErrorPattern.setChecked(lockPatternUtils.isShowErrorPath());
        }
        if (mVisibleDots != null) {
            mVisibleDots.setChecked(lockPatternUtils.isVisibleDotsEnabled());
        }
        if (mPowerButtonInstantlyLocks != null) {
            mPowerButtonInstantlyLocks.setChecked(lockPatternUtils.getPowerButtonInstantlyLocks());
        }

        if (mShowPassword != null) {
            mShowPassword.setChecked(Settings.System.getInt(getContentResolver(),
                    Settings.System.TEXT_SHOW_PASSWORD, 1) != 0);
        }

        if (mResetCredentials != null) {
            mResetCredentials.setEnabled(!mKeyStore.isEmpty());
        }
        updateBlacklistSummary();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (ensurePinRestrictedPreference(preference)) {
            return true;
        }
        final String key = preference.getKey();

        final LockPatternUtils lockPatternUtils = mChooseLockSettingsHelper.utils();
        if (KEY_UNLOCK_SET_OR_CHANGE.equals(key)) {
            startFragment(this, "com.android.settings.ChooseLockGeneric$ChooseLockGenericFragment",
                    SET_OR_CHANGE_LOCK_METHOD_REQUEST, null);
            // set lockscreen notification to allowed
        } else if (KEY_BIOMETRIC_WEAK_IMPROVE_MATCHING.equals(key)) {
            ChooseLockSettingsHelper helper =
                    new ChooseLockSettingsHelper(this.getActivity(), this);
            if (!helper.launchConfirmationActivity(
                    CONFIRM_EXISTING_FOR_BIOMETRIC_WEAK_IMPROVE_REQUEST, null, null)) {
                // If this returns false, it means no password confirmation is required, so
                // go ahead and start improve.
                // Note: currently a backup is required for biometric_weak so this code path
                // can't be reached, but is here in case things change in the future
                startBiometricWeakImprove();
            }
        } else if (KEY_BIOMETRIC_WEAK_LIVELINESS.equals(key)) {
            if (isToggled(preference)) {
                lockPatternUtils.setBiometricWeakLivelinessEnabled(true);
            } else {
                // In this case the user has just unchecked the checkbox, but this action requires
                // them to confirm their password.  We need to re-check the checkbox until
                // they've confirmed their password
                mBiometricWeakLiveliness.setChecked(true);
                ChooseLockSettingsHelper helper =
                        new ChooseLockSettingsHelper(this.getActivity(), this);
                if (!helper.launchConfirmationActivity(
                                CONFIRM_EXISTING_FOR_BIOMETRIC_WEAK_LIVELINESS_OFF, null, null)) {
                    // If this returns false, it means no password confirmation is required, so
                    // go ahead and uncheck it here.
                    // Note: currently a backup is required for biometric_weak so this code path
                    // can't be reached, but is here in case things change in the future
                    lockPatternUtils.setBiometricWeakLivelinessEnabled(false);
                    mBiometricWeakLiveliness.setChecked(false);
                }
            }
        } else if (KEY_LOCK_ENABLED.equals(key)) {
            lockPatternUtils.setLockPatternEnabled(isToggled(preference));
        } else if (KEY_VISIBLE_PATTERN.equals(key)) {
            lockPatternUtils.setVisiblePatternEnabled(isToggled(preference));
        } else if (KEY_VISIBLE_ERROR_PATTERN.equals(key)) {
            lockPatternUtils.setShowErrorPath(isToggled(preference));
        } else if (KEY_VISIBLE_DOTS.equals(key)) {
            lockPatternUtils.setVisibleDotsEnabled(isToggled(preference));
        } else if (KEY_POWER_INSTANTLY_LOCKS.equals(key)) {
            lockPatternUtils.setPowerButtonInstantlyLocks(isToggled(preference));        
        } else if (preference == mQuickUnlockScreen) {
            Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(),
                    Settings.System.LOCKSCREEN_QUICK_UNLOCK_CONTROL, isToggled(preference) ? 1 : 0);
        } else if (preference == mLockNotifications) {
            Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(),
                    Settings.System.LOCKSCREEN_NOTIFICATIONS_ALLOWED, isToggled(preference) ? 1 : 0);
        } else if (preference == mShowPassword) {
            Settings.System.putInt(getContentResolver(), Settings.System.TEXT_SHOW_PASSWORD,
                    mShowPassword.isChecked() ? 1 : 0);
        } else if (preference == mToggleAppInstallation) {
            if (mToggleAppInstallation.isChecked()) {
                mToggleAppInstallation.setChecked(false);
                warnAppInstallation();
            } else {
                setNonMarketAppsAllowed(false);
            }
        } else if (KEY_TOGGLE_VERIFY_APPLICATIONS.equals(key)) {
            Settings.Global.putInt(getContentResolver(), Settings.Global.PACKAGE_VERIFIER_ENABLE,
                    mToggleVerifyApps.isChecked() ? 1 : 0);
        } else {
            // If we didn't handle it, let preferences handle it.
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        return true;
    }

    private boolean isToggled(Preference pref) {
        return ((CheckBoxPreference) pref).isChecked();
    }

    /**
     * see confirmPatternThenDisableAndClear
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CONFIRM_EXISTING_FOR_BIOMETRIC_WEAK_IMPROVE_REQUEST &&
                resultCode == Activity.RESULT_OK) {
            startBiometricWeakImprove();
            return;
        } else if (requestCode == CONFIRM_EXISTING_FOR_BIOMETRIC_WEAK_LIVELINESS_OFF &&
                resultCode == Activity.RESULT_OK) {
            final LockPatternUtils lockPatternUtils = mChooseLockSettingsHelper.utils();
            lockPatternUtils.setBiometricWeakLivelinessEnabled(false);
            // Setting the mBiometricWeakLiveliness checked value to false is handled when onResume
            // is called by grabbing the value from lockPatternUtils.  We can't set it here
            // because mBiometricWeakLiveliness could be null
            return;
        }
        createPreferenceHierarchy();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        if (preference == mLockAfter) {
            int timeout = Integer.parseInt((String) value);
            try {
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT, timeout);
            } catch (NumberFormatException e) {
                Log.e("SecuritySettings", "could not persist lockAfter timeout setting", e);
            }
            updateLockAfterPreferenceSummary();
            checkPowerInstantLockDependency();
        } else if (preference == mSlideLockTimeoutDelay) {
            int slideTimeoutDelay = Integer.valueOf((String) value);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_LOCK_SLIDE_TIMEOUT_DELAY, slideTimeoutDelay);
            updateSlideAfterTimeoutSummary();
        } else if (preference == mSlideLockScreenOffDelay) {
            int slideScreenOffDelay = Integer.valueOf((String) value);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_LOCK_SLIDE_SCREENOFF_DELAY, slideScreenOffDelay);
            updateSlideAfterScreenOffSummary();
        } else if (preference == mSmsSecurityCheck) {
            int smsSecurityCheck = Integer.valueOf((String) value);
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.SMS_OUTGOING_CHECK_MAX_COUNT, smsSecurityCheck);
            updateSmsSecuritySummary(smsSecurityCheck);
        }
        return true;
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_security;
    }

    public void startBiometricWeakImprove(){
        Intent intent = new Intent();
        intent.setClassName("com.android.facelock", "com.android.facelock.AddToSetup");
        startActivity(intent);
    }

    private void updateBlacklistSummary() {
        if (mBlacklist != null) {
            if (BlacklistUtils.isBlacklistEnabled(getActivity())) {
                mBlacklist.setSummary(R.string.blacklist_summary);
            } else {
                mBlacklist.setSummary(R.string.blacklist_summary_disabled);
            }
        }
    }
}
