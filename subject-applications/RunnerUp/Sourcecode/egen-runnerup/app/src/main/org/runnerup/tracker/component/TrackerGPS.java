/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.runnerup.tracker.component;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.runnerup.R;
import org.runnerup.tracker.GpsStatus;
import org.runnerup.tracker.Tracker;
import org.runnerup.util.TickListener;

import static android.content.ContentValues.TAG;
import static android.content.Context.BATTERY_SERVICE;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static android.location.LocationManager.PASSIVE_PROVIDER;
import static com.mapbox.mapboxsdk.Mapbox.getApplicationContext;


public class TrackerGPS extends DefaultTrackerComponent implements TickListener {

    private boolean mWithoutGps = false;
    private int frequency_ms;
    private Location mLastLocation;
    private final Tracker tracker;

    private static final String NAME = "GPS";
    private GpsStatus mGpsStatus;
    private Callback mConnectCallback;


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
    protected int returnLevel(Context context) {
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
    @Override
    public String getName() {
        return NAME;
    }

    public TrackerGPS(Tracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public ResultCode onInit(final Callback callback, Context context) {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                return ResultCode.RESULT_NOT_SUPPORTED;
            }
            if (lm.getProvider(LocationManager.GPS_PROVIDER) == null) {
                return ResultCode.RESULT_NOT_SUPPORTED;
            }
        } catch (Exception ex) {
            return ResultCode.RESULT_ERROR;
        }
        return ResultCode.RESULT_OK;
    }

    @Override
    public ResultCode onConnecting(final Callback callback, Context context) {

        if (ContextCompat.checkSelfPermission(this.tracker,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            mWithoutGps = true;
        }
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            setDefineLevel(defineLevel);
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

//            frequency_ms = Integer.parseInt(preferences.getString(context.getString(
//                    R.string.pref_pollInterval), "1000"));
            frequency_ms = returnLevel(context);
            Log.d(TAG, "=======onLocationChanged: "+frequency_ms);
            if (!mWithoutGps) {
                String frequency_meters = preferences.getString(context.getString(
                        R.string.pref_pollDistance), "0");
                lm.requestLocationUpdates(GPS_PROVIDER,
                        frequency_ms,
                        Integer.parseInt(frequency_meters),
                        tracker);
                mGpsStatus = new GpsStatus(context);
                mGpsStatus.start(this);
                mConnectCallback = callback;
                return ResultCode.RESULT_PENDING;
            } else {
                String[] list = {
                        GPS_PROVIDER,
                        NETWORK_PROVIDER,
                        PASSIVE_PROVIDER };
                mLastLocation = null;
                for (String s : list) {
                    Location tmp = lm.getLastKnownLocation(s);
                    if (mLastLocation == null || tmp.getTime() > mLastLocation.getTime()) {
                        mLastLocation = tmp;
                    }
                }
                if (mLastLocation != null) {
                    mLastLocation.removeSpeed();
                    mLastLocation.removeAltitude();
                    mLastLocation.removeAccuracy();
                    mLastLocation.removeBearing();
                }
                gpsLessLocationProvider.run();
                return ResultCode.RESULT_OK;
            }


        } catch (Exception ex) {
            return ResultCode.RESULT_ERROR;
        }
    }

    @Override
    public boolean isConnected() {
        return (mWithoutGps) ||
                (mGpsStatus != null) && mGpsStatus.isFixed();
    }

    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        if (!mWithoutGps) {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            try {
                lm.removeUpdates(tracker);
            } catch (SecurityException ex) {
                ex.printStackTrace();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (mGpsStatus != null) {
                mGpsStatus.stop(this);
            }
            mGpsStatus = null;
            mConnectCallback = null;
        }
        return ResultCode.RESULT_OK;
    }

    private final Runnable gpsLessLocationProvider = new Runnable() {

        Location location = null;
        final Handler handler = new Handler();

        @Override
        public void run() {
            if (location == null) {
                location = new Location(mLastLocation);
                mLastLocation = null;
            }
            switch (tracker.getState()) {
                case INIT:
                case CLEANUP:
                case ERROR:
                    /* end loop be returning directly here */
                    return;
                case INITIALIZING:
                case INITIALIZED:
                case STARTED:
                case PAUSED:
                    /* continue looping */
                    break;
            }
            tracker.onLocationChanged(location);
            setDefineLevel(defineLevel);
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

//            frequency_ms = Integer.parseInt(preferences.getString(context.getString(
//                    R.string.pref_pollInterval), "1000"));
            frequency_ms = returnLevel(getApplicationContext());
            Log.d(TAG, "=======run: "+frequency_ms);
            handler.postDelayed(this, frequency_ms);
        }
    };

    @Override
    public void onTick() {
        if (mGpsStatus == null)
            return;

        if (!mGpsStatus.isFixed())
            return;

        if (mConnectCallback == null)
            return;

        Callback tmp = mConnectCallback;

        mConnectCallback = null;
        mGpsStatus.stop(this);
        //note: Don't reset mGpsStatus, it's used for isConnected()

        tmp.run(this, ResultCode.RESULT_OK);
    }
}
