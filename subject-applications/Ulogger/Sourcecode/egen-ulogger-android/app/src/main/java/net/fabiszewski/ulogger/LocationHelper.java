/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

import static android.content.Context.BATTERY_SERVICE;

class LocationHelper {

    private static final String TAG = LocationHelper.class.getSimpleName();
    private static LocationHelper instance;
    private final Context context;
    private final LocationManager locationManager;

    // millis 1999-08-21T23:59:42+00:00
    private static final long FIRST_ROLLOVER_TIMESTAMP = 935279982000L;
    // millis 2019-04-06T23:59:42+00:00
    private static final long SECOND_ROLLOVER_TIMESTAMP = 1554595182000L;
    // 1024 weeks in milliseconds
    private static final long ROLLOVER_MILLIS = 1024 * 7 * 24 * 60 * 60 * 1000L;

    private boolean liveSync = false;
    private int maxAccuracy;
    private float minDistance;
    private long minTimeMillis;
    // max time tolerance is half min time, but not more that 5 min
    final private long minTimeTolerance = Math.min(minTimeMillis / 2, 5 * 60 * 1000);
    final private long maxTimeMillis = minTimeMillis + minTimeTolerance;
    private final List<String> userProviders = new ArrayList<>();

    private class BatteryLevel{
        private int BATTERY_HIGH;
        private int BATTERY_LOW;
        private int BATTERY_MEDIUM;
    }
    private BatteryLevel defineLevel = new BatteryLevel();
    private void setDefineLevel(BatteryLevel defineLevel){
        this.defineLevel = defineLevel;
        this.defineLevel.BATTERY_HIGH = 60;
        this.defineLevel.BATTERY_MEDIUM = 20;
        this.defineLevel.BATTERY_LOW = 0;
    }
    protected void BatteryThresholdPoints(int THRESHOLD_HIGH, int THRESHOLD_MEDIUM)
    {
        defineLevel.BATTERY_HIGH = THRESHOLD_HIGH;
        defineLevel.BATTERY_MEDIUM = THRESHOLD_MEDIUM;
    }
    private class AdaptiveEngine
    {
        private long SENSING_INTERVAL;
        private long SLOPE;
        private String BatteryAwareFunction;
    }
    private AdaptiveEngine setUpPolicy_HCF = new AdaptiveEngine();
    private void setSetUpPolicy_HCF(AdaptiveEngine setUpPolicy_HCF) {
        this.setUpPolicy_HCF = setUpPolicy_HCF;
        this.setUpPolicy_HCF.SENSING_INTERVAL = 1000;
        this.setUpPolicy_HCF.SLOPE = 0;
        this.setUpPolicy_HCF.BatteryAwareFunction = "Linear";
    }
    protected void AdaptationPolicy_H_C_F(long INTERVAL, long STEP, String BatteryAwareFunction)
    {
        setUpPolicy_HCF.SENSING_INTERVAL = INTERVAL;
        setUpPolicy_HCF.SLOPE = STEP;
        setUpPolicy_HCF.BatteryAwareFunction = BatteryAwareFunction;
    }

    private AdaptiveEngine setUpPolicy_HCB = new AdaptiveEngine();
    private void setSetUpPolicy_HCB(AdaptiveEngine setUpPolicy_HCB) {
        this.setUpPolicy_HCB = setUpPolicy_HCB;
        this.setUpPolicy_HCB.SENSING_INTERVAL = 1000;
        this.setUpPolicy_HCB.SLOPE = 0;
        this.setUpPolicy_HCB.BatteryAwareFunction = "Exponential";
    }
    private void AdaptationPolicy_H_C_B(long INTERVAL, long STEP, String BatteryAwareFunction)
    {
        setUpPolicy_HCB.SENSING_INTERVAL = INTERVAL;
        setUpPolicy_HCB.SLOPE = STEP;
        setUpPolicy_HCB.BatteryAwareFunction = BatteryAwareFunction;
    }

    private AdaptiveEngine setUpPolicy_HDF = new AdaptiveEngine();
    private void setSetUpPolicy_HDF(AdaptiveEngine setUpPolicy_HDF) {
        this.setUpPolicy_HDF = setUpPolicy_HDF;
        this.setUpPolicy_HDF.SENSING_INTERVAL = 1000;
        this.setUpPolicy_HDF.SLOPE = 1;
        this.setUpPolicy_HDF.BatteryAwareFunction = "Linear";
    }
    protected void AdaptationPolicy_H_D_F(long INTERVAL, long STEP, String BatteryAwareFunction)
    {
        setUpPolicy_HDF.SENSING_INTERVAL = INTERVAL;
        setUpPolicy_HDF.SLOPE = STEP;
        setUpPolicy_HDF.BatteryAwareFunction = BatteryAwareFunction;
    }

    private AdaptiveEngine setUpPolicy_HDB = new AdaptiveEngine();
    private void setSetUpPolicy_HDB(AdaptiveEngine setUpPolicy_HDB) {
        this.setUpPolicy_HDB = setUpPolicy_HDB;
        this.setUpPolicy_HDB.SENSING_INTERVAL = 1000;
        this.setUpPolicy_HDB.SLOPE = 2;
        this.setUpPolicy_HDB.BatteryAwareFunction = "Exponential";
    }
    protected void AdaptationPolicy_H_D_B(long INTERVAL, long STEP, String BatteryAwareFunction)
    {
        setUpPolicy_HDB.SENSING_INTERVAL = INTERVAL;
        setUpPolicy_HDB.SLOPE = STEP;
        setUpPolicy_HDB.BatteryAwareFunction = BatteryAwareFunction;
    }

    private AdaptiveEngine setUpPolicy_MCF = new AdaptiveEngine();
    private void setSetUpPolicy_MCF(AdaptiveEngine setUpPolicy_MCF) {
        this.setUpPolicy_MCF = setUpPolicy_MCF;
        this.setUpPolicy_MCF.SENSING_INTERVAL = 1000;
        this.setUpPolicy_MCF.SLOPE = 0;
        this.setUpPolicy_MCF.BatteryAwareFunction = "Linear";
    }
    protected void AdaptationPolicy_M_C_F(long INTERVAL, long STEP, String BatteryAwareFunction)
    {
        setUpPolicy_MCF.SENSING_INTERVAL = INTERVAL;
        setUpPolicy_MCF.SLOPE = STEP;
        setUpPolicy_MCF.BatteryAwareFunction = BatteryAwareFunction;
    }

    private AdaptiveEngine setUpPolicy_MCB = new AdaptiveEngine();
    private void setSetUpPolicy_MCB(AdaptiveEngine setUpPolicy_MCB) {
        this.setUpPolicy_MCB = setUpPolicy_MCB;
        this.setUpPolicy_MCB.SENSING_INTERVAL = 1000;
        this.setUpPolicy_MCB.SLOPE = 0;
        this.setUpPolicy_MCB.BatteryAwareFunction = "Exponential";
    }
    protected void AdaptationPolicy_M_C_B(long INTERVAL, long STEP, String BatteryAwareFunction)
    {
        setUpPolicy_MCB.SENSING_INTERVAL = INTERVAL;
        setUpPolicy_MCB.SLOPE = STEP;
        setUpPolicy_MCB.BatteryAwareFunction = BatteryAwareFunction;
    }

    private AdaptiveEngine setUpPolicy_MDF = new AdaptiveEngine();
    private void setSetUpPolicy_MDF(AdaptiveEngine setUpPolicy_MDF) {
        this.setUpPolicy_MDF = setUpPolicy_MDF;
        this.setUpPolicy_MDF.SENSING_INTERVAL = setUpPolicy_HDF.SENSING_INTERVAL + setUpPolicy_HDF.SLOPE*(100-defineLevel.BATTERY_HIGH);
        this.setUpPolicy_MDF.SLOPE = 2;
        this.setUpPolicy_MDF.BatteryAwareFunction = "Linear";
    }
    protected void AdaptationPolicy_M_D_F(long INTERVAL, long STEP, String BatteryAwareFunction)
    {
        setUpPolicy_MDF.SENSING_INTERVAL = INTERVAL;
        setUpPolicy_MDF.SLOPE = STEP;
        setUpPolicy_MDF.BatteryAwareFunction = BatteryAwareFunction;
    }

    private AdaptiveEngine setUpPolicy_MDB = new AdaptiveEngine();
    private void setSetUpPolicy_MDB(AdaptiveEngine setUpPolicy_MDB) {
        this.setUpPolicy_MDB = setUpPolicy_MDB;
        this.setUpPolicy_MDB.SENSING_INTERVAL = setUpPolicy_MDF.SENSING_INTERVAL;
        this.setUpPolicy_MDB.SLOPE = 2;
        this.setUpPolicy_MDB.BatteryAwareFunction = "Exponential";
    }
    protected void AdaptationPolicy_M_D_B(long INTERVAL, long STEP, String BatteryAwareFunction)
    {
        setUpPolicy_MDB.SENSING_INTERVAL = INTERVAL;
        setUpPolicy_MDB.SLOPE = STEP;
        setUpPolicy_MDB.BatteryAwareFunction = BatteryAwareFunction;
    }

    private AdaptiveEngine setUpPolicy_LCF = new AdaptiveEngine();
    private void setSetUpPolicy_LCF(AdaptiveEngine setUpPolicy_LCF) {
        this.setUpPolicy_LCF = setUpPolicy_LCF;
        this.setUpPolicy_LCF.SENSING_INTERVAL = 1000;
        this.setUpPolicy_LCF.SLOPE = 0;
        this.setUpPolicy_LCF.BatteryAwareFunction = "Exponential";
    }
    protected void AdaptationPolicy_L_C_F(long INTERVAL, long STEP, String BatteryAwareFunction)
    {
        setUpPolicy_LCF.SENSING_INTERVAL = INTERVAL;
        setUpPolicy_LCF.SLOPE = STEP;
        setUpPolicy_LCF.BatteryAwareFunction = BatteryAwareFunction;
    }

    private AdaptiveEngine setUpPolicy_LCB = new AdaptiveEngine();
    private void setSetUpPolicy_LCB(AdaptiveEngine setUpPolicy_LCB) {
        this.setUpPolicy_LCB = setUpPolicy_LCB;
        this.setUpPolicy_LCB.SENSING_INTERVAL = 1000;
        this.setUpPolicy_LCB.SLOPE = 0;
        this.setUpPolicy_LCB.BatteryAwareFunction = "Exponential";
    }
    protected void AdaptationPolicy_L_C_B(long INTERVAL, long STEP, String BatteryAwareFunction)
    {
        setUpPolicy_LCB.SENSING_INTERVAL = INTERVAL;
        setUpPolicy_LCB.SLOPE = STEP;
        setUpPolicy_LCB.BatteryAwareFunction = BatteryAwareFunction;
    }

    private AdaptiveEngine setUpPolicy_LDF = new AdaptiveEngine();
    private void setSetUpPolicy_LDF(AdaptiveEngine setUpPolicy_LDF) {
        this.setUpPolicy_LDF = setUpPolicy_LDF;
        this.setUpPolicy_LDF.SENSING_INTERVAL = setUpPolicy_MDF.SENSING_INTERVAL + setUpPolicy_MDF.SLOPE*(100-defineLevel.BATTERY_LOW);
        this.setUpPolicy_LDF.SLOPE = 2;
        this.setUpPolicy_LDF.BatteryAwareFunction = "Exponential";
    }
    protected void AdaptationPolicy_L_D_F(long INTERVAL, long STEP, String BatteryAwareFunction)
    {
        setUpPolicy_LDF.SENSING_INTERVAL = INTERVAL;
        setUpPolicy_LDF.SLOPE = STEP;
        setUpPolicy_LDF.BatteryAwareFunction = BatteryAwareFunction;
    }

    private AdaptiveEngine setUpPolicy_LDB = new AdaptiveEngine();
    private void setSetUpPolicy_LDB(AdaptiveEngine setUpPolicy_LDB) {
        this.setUpPolicy_LDB = setUpPolicy_LDB;
        this.setUpPolicy_LDB.SENSING_INTERVAL = setUpPolicy_LDF.SENSING_INTERVAL;
        this.setUpPolicy_LDB.SLOPE = 2;
        this.setUpPolicy_LDB.BatteryAwareFunction = "Exponential";
    }
    protected void AdaptationPolicy_L_D_B(long INTERVAL, long STEP, String BatteryAwareFunction)
    {
        setUpPolicy_LDB.SENSING_INTERVAL = INTERVAL;
        setUpPolicy_LDB.SLOPE = STEP;
        setUpPolicy_LDB.BatteryAwareFunction = BatteryAwareFunction;
    }

    private int level;
    private boolean isCharging;
    private boolean appInForeground;
    protected int returnLevel() {
        int BATTERY_AWARE_SI = 1000;
        BatteryManager bm = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        if(bm != null) {
            level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            int status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
            switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING:
                    isCharging = true;
                    break;
                case BatteryManager.BATTERY_STATUS_DISCHARGING:
                    isCharging = false;
                    break;
            }
            if (appInForeground) {
                if (level > defineLevel.BATTERY_HIGH) {
                    if (isCharging) {
                        if (setUpPolicy_HCF.BatteryAwareFunction.equals("Linear")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCF.SENSING_INTERVAL + setUpPolicy_HCF.SLOPE * (100 - level));
                        } else if (setUpPolicy_HCF.BatteryAwareFunction.equals("Exponential")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCF.SENSING_INTERVAL + (Math.exp(setUpPolicy_HCF.SLOPE/(float)7)) * (100 - level));
                        }
                    } else {
                        if (setUpPolicy_HDF.BatteryAwareFunction.equals("Linear")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HDF.SENSING_INTERVAL + setUpPolicy_HDF.SLOPE * (100 - level));
                        } else if (setUpPolicy_HDF.BatteryAwareFunction.equals("Exponential"))  {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCF.SENSING_INTERVAL + (Math.exp(setUpPolicy_HDF.SLOPE/(float)7)) * (100 - level));
                        }
                    }
                } else if (level > defineLevel.BATTERY_MEDIUM) {
                    if (isCharging) {
                        if (setUpPolicy_MCF.BatteryAwareFunction.equals("Linear")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_MCF.SENSING_INTERVAL + setUpPolicy_MCF.SLOPE * (100 - level));
                        } else if (setUpPolicy_MCF.BatteryAwareFunction.equals("Exponential")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCF.SENSING_INTERVAL + (Math.exp(setUpPolicy_MCF.SLOPE/(float)7)) * (100 - level));
                        }
                    } else {
                        if (setUpPolicy_MDF.BatteryAwareFunction.equals("Linear")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_MDF.SENSING_INTERVAL + setUpPolicy_MDF.SLOPE * (100 - level));
                        } else if (setUpPolicy_MDF.BatteryAwareFunction.equals("Exponential")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCF.SENSING_INTERVAL + (Math.exp(setUpPolicy_MDF.SLOPE/(float)7)) * (100 - level));

                        }
                    }
                } else {
                    if (isCharging) {
                        if (setUpPolicy_LCF.BatteryAwareFunction.equals("Linear")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_LCF.SENSING_INTERVAL + setUpPolicy_LCF.SLOPE * (100 - level));
                        } else if (setUpPolicy_LCF.BatteryAwareFunction.equals("Exponential")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCF.SENSING_INTERVAL + (Math.exp(setUpPolicy_LCF.SLOPE/(float)7)) * (100 - level));

                        }
                    } else {
                        if (setUpPolicy_LDF.BatteryAwareFunction.equals("Linear")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_LDF.SENSING_INTERVAL + setUpPolicy_LDF.SLOPE * (100 - level));
                        } else if (setUpPolicy_LDF.BatteryAwareFunction.equals("Exponential")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCF.SENSING_INTERVAL + (Math.exp(setUpPolicy_LDF.SLOPE/(float)7)) * (100 - level));

                        }
                    }
                }
            } else {
                if (level > defineLevel.BATTERY_HIGH) {
                    if (isCharging) {
                        if (setUpPolicy_HCB.BatteryAwareFunction.equals("Linear")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCB.SENSING_INTERVAL + setUpPolicy_HCB.SLOPE * (100 - level));
                        } else if (setUpPolicy_HCB.BatteryAwareFunction.equals("Exponential")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCF.SENSING_INTERVAL + (Math.exp(setUpPolicy_HCB.SLOPE/(float)7)) * (100 - level));

                        }
                    } else {
                        if (setUpPolicy_HDB.BatteryAwareFunction.equals("Linear")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HDB.SENSING_INTERVAL + setUpPolicy_HDB.SLOPE * (100 - level));
                        } else if (setUpPolicy_HDB.BatteryAwareFunction.equals("Exponential")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCF.SENSING_INTERVAL + (Math.exp(setUpPolicy_HDB.SLOPE/(float)7)) * (100 - level));

                        }
                    }
                } else if (level > defineLevel.BATTERY_MEDIUM) {
                    if (isCharging) {
                        if (setUpPolicy_MCB.BatteryAwareFunction.equals("Linear")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_MCB.SENSING_INTERVAL + setUpPolicy_MCB.SLOPE * (100 - level));
                        } else if (setUpPolicy_MCB.BatteryAwareFunction.equals("Exponential")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCF.SENSING_INTERVAL + (Math.exp(setUpPolicy_MCB.SLOPE/(float)7)) * (100 - level));
                        }
                    } else {
                        if (setUpPolicy_MDB.BatteryAwareFunction.equals("Linear")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_MDB.SENSING_INTERVAL + setUpPolicy_MDB.SLOPE * (100 - level));
                        } else if (setUpPolicy_MDB.BatteryAwareFunction.equals("Exponential")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCF.SENSING_INTERVAL + (Math.exp(setUpPolicy_MDB.SLOPE/(float)7)) * (100 - level));

                        }
                    }
                } else {
                    if (isCharging) {
                        if (setUpPolicy_LCB.BatteryAwareFunction.equals("Linear")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_LCB.SENSING_INTERVAL + setUpPolicy_LCB.SLOPE * (100 - level));
                        } else if (setUpPolicy_LCB.BatteryAwareFunction.equals("Exponential")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCF.SENSING_INTERVAL + (Math.exp(setUpPolicy_LCB.SLOPE/(float)7)) * (100 - level));
                        }
                    } else {
                        if (setUpPolicy_LDB.BatteryAwareFunction.equals("Linear")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_LDB.SENSING_INTERVAL + setUpPolicy_LDB.SLOPE * (100 - level));
                        } else if (setUpPolicy_LDB.BatteryAwareFunction.equals("Exponential")) {
                            BATTERY_AWARE_SI = (int) (setUpPolicy_HCF.SENSING_INTERVAL + (Math.exp(setUpPolicy_LDB.SLOPE/(float)7)) * (100 - level));
                        }
                    }
                }
            }
        }
        return BATTERY_AWARE_SI;
    }
    private LocationHelper(@NonNull Context context) {
        this.context = context.getApplicationContext();
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        updatePreferences();
    }

    /**
     * Get instance
     * @param context Context
     * @return LocationHelper instance
     */
    public static LocationHelper getInstance(@NonNull Context context) {
        synchronized(LocationHelper.class) {
            if (instance == null) {
                instance = new LocationHelper(context);
            }
            return instance;
        }
    }

    /**
     * Get preferences
     */
    void updatePreferences() {
        if (Logger.DEBUG) { Log.d(TAG, "[updatePreferences]"); }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setSetUpPolicy_HCF(setUpPolicy_HCF);
        setSetUpPolicy_HCB(setUpPolicy_HCB);
        setSetUpPolicy_HDF(setUpPolicy_HDF);
        setSetUpPolicy_HDB(setUpPolicy_HDB);

        setSetUpPolicy_MCF(setUpPolicy_MCF);
        setSetUpPolicy_MCB(setUpPolicy_MCB);
        setSetUpPolicy_MDF(setUpPolicy_MDF);
        setSetUpPolicy_MDB(setUpPolicy_MDB);

        setSetUpPolicy_LCF(setUpPolicy_LCF);
        setSetUpPolicy_LCB(setUpPolicy_LCB);
        setSetUpPolicy_LDF(setUpPolicy_LDF);
        setSetUpPolicy_LDB(setUpPolicy_LDB);
        appInForeground = true;

        BatteryThresholdPoints(80,50);

        // While Discharging & Foreground - this state drains the battery most
        AdaptationPolicy_H_D_F(3000,10,"Linear");
        AdaptationPolicy_M_D_F(4000,20,"Linear");
        AdaptationPolicy_L_D_F(5000,30,"Linear");

        // While Discharging & Background - next state that drains battery more
        AdaptationPolicy_H_D_B(2000,10,"Linear");
        AdaptationPolicy_M_D_B(3000,20,"Linear");
        AdaptationPolicy_L_D_B(4000,30,"Linear");

        // While Charging & Foreground - this state that drains battery lesser
        AdaptationPolicy_H_C_F(1000,10,"Linear");
        AdaptationPolicy_M_C_F(2000,20,"Linear");
        AdaptationPolicy_L_C_F(3000,30,"Linear");

        // While Charging & Background - this state drains least amount of battery
        AdaptationPolicy_H_C_B(0,10,"Linear");
        AdaptationPolicy_M_C_B(1000,10,"Linear");
        AdaptationPolicy_L_C_B(2000,20,"Linear");


//        minTimeMillis = Long.parseLong(prefs.getString(SettingsActivity.KEY_MIN_TIME, context.getString(R.string.pref_mintime_default))) * 1000;
        minTimeMillis = returnLevel();
        minDistance = 0;
//        minDistance = Float.parseFloat(prefs.getString(SettingsActivity.KEY_MIN_DISTANCE, context.getString(R.string.pref_mindistance_default)));
        Log.d(TAG, "============minTimeMillis: " + minTimeMillis);
//        maxAccuracy = Integer.parseInt(prefs.getString(SettingsActivity.KEY_MIN_ACCURACY, context.getString(R.string.pref_minaccuracy_default)));
        maxAccuracy = 10;
        userProviders.clear();
        if (prefs.getBoolean(SettingsActivity.KEY_USE_GPS, providerExists(LocationManager.GPS_PROVIDER))) {
            userProviders.add(LocationManager.GPS_PROVIDER);
        }
        if (prefs.getBoolean(SettingsActivity.KEY_USE_NET, providerExists(LocationManager.NETWORK_PROVIDER))) {
            userProviders.add(LocationManager.NETWORK_PROVIDER);
        }
        liveSync = prefs.getBoolean(SettingsActivity.KEY_LIVE_SYNC, false);
    }

    /**
     * Check if user granted permission to access location.
     *
     * @return True if permission granted, false otherwise
     */
    boolean canAccessLocation() {
        boolean ret = (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        if (Logger.DEBUG) { Log.d(TAG, "[canAccessLocation: " + ret + "]"); }
        return ret;
    }

    /**
     * Check if given provider exists on device
     * @param provider Provider
     * @return True if exists, false otherwise
     */
    private boolean providerExists(String provider) {
        boolean ret = locationManager.getAllProviders().contains(provider);
        if (Logger.DEBUG) { Log.d(TAG, "[providerExists " + provider + ": " + ret + "]"); }
        return ret;
    }

    /**
     * Request single location update
     * @param listener Listener
     * @param cancellationSignal Cancellation signal
     * @throws LoggerException Exception on permission denied or all providers disabled
     */
    void requestSingleUpdate(@NonNull LocationListener listener, CancellationSignal cancellationSignal) throws LoggerException {
        requestAllProvidersUpdates(listener, Looper.getMainLooper(), true, cancellationSignal);
    }

    /**
     * Request location updates for user selected providers
     * @param listener Listener
     * @param looper Looper
     * @throws LoggerException Exception on all requested providers failure
     */
    void requestLocationUpdates(@NonNull LocationListener listener, @Nullable Looper looper) throws LoggerException {
        requestAllProvidersUpdates(listener, looper, false, null);
    }

    /**
     * Request location updates for user selected providers
     * @param listener Listener
     * @param looper Looper
     * @param singleShot Request single update if true
     * @param cancellationSignal Cancellation signal
     * @throws LoggerException Exception on all requested providers failure
     */
    private void requestAllProvidersUpdates(@NonNull LocationListener listener, @Nullable Looper looper, 
                                            boolean singleShot, CancellationSignal cancellationSignal) throws LoggerException {
        List<Integer> results = new ArrayList<>();
        for (String provider : userProviders) {
            try {
                requestProviderUpdates(provider, listener, looper, singleShot, cancellationSignal);
                results.add(LoggerException.E_OK);
            } catch (LoggerException e) {
                results.add(e.getCode());
            }
        }
        if (!results.contains(LoggerException.E_OK)) {
            int errorCode = results.isEmpty() ? LoggerException.E_DISABLED : results.get(0);
            throw new LoggerException(errorCode);
        }
    }

    /**
     * Request location updates for provider
     * @param provider Provider
     * @param listener Listener
     * @param looper Looper
     * @param singleShot Request single update if true
     * @param cancellationSignal Cancellation signal
     * @throws LoggerException Exception on permission denied or provider disabled
     */
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private void requestProviderUpdates(@NonNull String provider, @NonNull LocationListener listener, @Nullable Looper looper,
                                        boolean singleShot, @Nullable CancellationSignal cancellationSignal) throws LoggerException {
        if (Logger.DEBUG) { Log.d(TAG, "[requestProviderUpdates: " + provider + " (" + singleShot + ")]"); }
        try {
            if (!singleShot) {
                // request even if provider is disabled to allow users re-enable it later
                locationManager.requestLocationUpdates(provider, minTimeMillis, minDistance, listener, looper);
            } else if (locationManager.isProviderEnabled(provider)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    locationManager.getCurrentLocation(provider, cancellationSignal, context.getMainExecutor(), location -> {
                        if (Logger.DEBUG) { Log.d(TAG, "[getCurrentLocation location: " + location + ", provider: " + provider + "]"); }
                        if (location != null) {
                            listener.onLocationChanged(location);
                        }
                    });
                } else {
                    locationManager.requestSingleUpdate(provider, listener, looper);
                }
            }
            if (!locationManager.isProviderEnabled(provider)) {
                if (Logger.DEBUG) { Log.d(TAG, "[requestProviderUpdates disabled: " + provider + " (" + singleShot + ")]"); }
                throw new LoggerException("Provider disabled", LoggerException.E_DISABLED);
            }
        } catch (SecurityException e) {
            if (Logger.DEBUG) { Log.d(TAG, "[requestProviderUpdates permission denied: " + provider + " (" + singleShot + ")]"); }
            throw new LoggerException("Permission denied", LoggerException.E_PERMISSION);
        }
    }

    /**
     * Remove all location updates for listener
     * @param listener Listener
     */
    void removeUpdates(LocationListener listener) {
        if (Logger.DEBUG) { Log.d(TAG, "[removeUpdates]"); }
        locationManager.removeUpdates(listener);
    }

    /**
     * Is any of user location providers enabled
     * @return True if enabled
     */
    boolean hasEnabledProviders() {
        for (String provider : userProviders) {
            if (locationManager.isProviderEnabled(provider)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check location accuracy meets user criteria
     * @param location Location
     * @return True if location accuracy within limit
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean hasRequiredAccuracy(@NonNull Location location) {
        boolean ret = location.hasAccuracy() && location.getAccuracy() <= maxAccuracy;
        if (Logger.DEBUG) { Log.d(TAG, "[hasRequiredAccuracy: " + ret + "]"); }
        return ret;
    }

    /**
     * Check location distance meets user criteria
     * @param location Current location
     * @param lastLocation Previous location
     * @return True if location distance within limit
     */
    boolean hasRequiredDistance(@NonNull Location location, @Nullable Location lastLocation) {
        if (lastLocation == null) {
            return true;
        }
        float distance = location.distanceTo(lastLocation);
        boolean ret = distance >= minDistance;
        if (Logger.DEBUG) { Log.d(TAG, "[hasRequiredDistance: " + ret + "]"); }
        return ret;
    }

    /**
     * Check location time meets user criteria when compared to current time
     * @param location Location
     * @return True if location time within limit
     */
    boolean hasRequiredTime(@NonNull Location location) {
        long elapsedMillis = SystemClock.elapsedRealtime() - location.getElapsedRealtimeNanos() / 1000000;
        boolean ret = elapsedMillis <= maxTimeMillis;
        if (Logger.DEBUG) { Log.d(TAG, "[hasRequiredTime: " + ret + "]"); }
        return ret;
    }

    /**
     * Is location from GPS provider
     * @param location Location
     * @return True if is from GPS
     */
    static boolean isGps(@NonNull Location location) {
        boolean ret = location.getProvider().equals(LocationManager.GPS_PROVIDER);
        if (Logger.DEBUG) { Log.d(TAG, "[isGps: " + ret + "]"); }
        return ret;
    }

    /**
     * Is location from Network provider
     * @param location Location
     * @return True if is from Network
     */
    static boolean isNetwork(@NonNull Location location) {
        boolean ret = location.getProvider().equals(LocationManager.NETWORK_PROVIDER);
        if (Logger.DEBUG) { Log.d(TAG, "[isNetwork: " + ret + "]"); }
        return ret;
    }

    /**
     * Is live web synchronization on
     * @return True if on
     */
    boolean isLiveSync() {
        if (Logger.DEBUG) { Log.d(TAG, "[isLiveSync: " + liveSync + "]"); }
        return liveSync;
    }


    /**
     * Fix GPS week count rollover bug if needed
     * https://galileognss.eu/gps-week-number-rollover-april-6-2019/
     * @param location Location
     */
    static void handleRolloverBug(@NonNull Location location) {
        long gpsTime = location.getTime();
        if (gpsTime > FIRST_ROLLOVER_TIMESTAMP && gpsTime < SECOND_ROLLOVER_TIMESTAMP) {
            if (Logger.DEBUG) { Log.d(TAG, "[Fixing GPS rollover bug: " + gpsTime + "]"); }
            location.setTime(gpsTime + ROLLOVER_MILLIS);
        }
    }

    /**
     * Logger exceptions
     */
    static class LoggerException extends Exception {

        private final int code;

        static final int E_OK = 0;
        static final int E_PERMISSION = 1;
        static final int E_DISABLED = 2;

        LoggerException(int code) {
            super();
            this.code = code;
        }
        
        LoggerException(String message, int code) {
            super(message);
            this.code = code;
        }

        int getCode() {
            return code;
        }
    }


}
