package de.dennisguse.opentracks.services.handlers;

import static android.content.Context.BATTERY_SERVICE;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.time.Duration;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.content.data.Distance;
import de.dennisguse.opentracks.content.data.TrackPoint;
import de.dennisguse.opentracks.util.LocationUtils;
import de.dennisguse.opentracks.util.PreferencesUtils;

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public class LocationHandler implements LocationListener, GpsStatus.GpsStatusListener {

    private final String TAG = LocationHandler.class.getSimpleName();

    private LocationManager locationManager;
    private final TrackPointCreator trackPointCreator;
    private GpsStatus gpsStatus;
    private Duration gpsInterval;
    private Distance thresholdHorizontalAccuracy;
    private TrackPoint lastTrackPoint;

    public LocationHandler(TrackPointCreator trackPointCreator) {
        this.trackPointCreator = trackPointCreator;
    }

    public void onStart(@NonNull Context context, SharedPreferences sharedPreferences) {

        onSharedPreferenceChanged(context, sharedPreferences, null);
        gpsStatus = new GpsStatus(context, this);
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        registerLocationListener(context);
        gpsStatus.start();
    }

    private boolean isStarted() {
        return locationManager != null;
    }

    @SuppressWarnings({"MissingPermission"})
    //TODO upgrade to AGP7.0.0/API31 started complaining about removeUpdates.
    public void onStop() {
        lastTrackPoint = null;
        if (locationManager != null) {
            locationManager.removeUpdates(this);
            locationManager = null;
        }

        if (gpsStatus != null) {
            gpsStatus.stop();
            gpsStatus = null;
        }
    }
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
    public void onSharedPreferenceChanged(@NonNull Context context, @NonNull SharedPreferences sharedPreferences, String key) {
        boolean registerListener = false;

        if (PreferencesUtils.isKey(context, R.string.min_recording_interval_key, key)) {
            registerListener = true;

            gpsInterval = PreferencesUtils.getMinRecordingInterval(sharedPreferences, context);
            Log.d(TAG, "========gpsInterval: "+gpsInterval);
            if (gpsStatus != null) {
                gpsStatus.onMinRecordingIntervalChanged(gpsInterval);
            }
        }
        if (PreferencesUtils.isKey(context, R.string.recording_gps_accuracy_key, key)) {
            thresholdHorizontalAccuracy = PreferencesUtils.getThresholdHorizontalAccuracy(sharedPreferences, context);
        }
        if (PreferencesUtils.isKey(context, R.string.recording_distance_interval_key, key)) {
            registerListener = true;

            if (gpsStatus != null) {
                Distance gpsMinDistance = PreferencesUtils.getRecordingDistanceInterval(sharedPreferences, context);
                gpsStatus.onRecordingDistanceChanged(gpsMinDistance);
            }
        }

        if (registerListener) {
            registerLocationListener(context);
        }
    }

    /**
     * Checks if location is valid and builds a track point that will be send through TrackPointCreator.
     *
     * @param location {@link Location} object.
     */
    @Override
    public void onLocationChanged(@NonNull Location location) {

        if (!isStarted()) {
            Log.w(TAG, "Location is ignored; not started.");
            return;
        }

        TrackPoint trackPoint = new TrackPoint(location, trackPointCreator.createNow());
        boolean isAccurate = trackPoint.fulfillsAccuracy(thresholdHorizontalAccuracy);
        boolean isValid = LocationUtils.isValidLocation(location);

        if (gpsStatus != null) {
            gpsStatus.onLocationChanged(trackPoint);
        }

        if (!isValid) {
            Log.w(TAG, "Ignore newTrackPoint. location is invalid.");
            return;
        }

        if (!isAccurate) {
            Log.d(TAG, "Ignore newTrackPoint. Poor accuracy.");
            return;
        }

        lastTrackPoint = trackPoint;
        trackPointCreator.onNewTrackPoint(trackPoint, thresholdHorizontalAccuracy);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        if (gpsStatus != null) {
            gpsStatus.onGpsEnabled();
        }
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        if (gpsStatus != null) {
            gpsStatus.onGpsDisabled();
        }
    }

    private void registerLocationListener(Context context) {
        if (locationManager == null) {
            Log.e(TAG, "locationManager is null.");
            return;
        }
        try {
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

            long gpsLoggingInterval = returnLevel(context);
//            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, gpsInterval.toMillis(), 0, this);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, gpsLoggingInterval, 0, this);
            Log.d(TAG, "========SI-gpsInterval: "+gpsLoggingInterval);

        } catch (SecurityException e) {
            Log.e(TAG, "Could not register location listener; permissions not granted.", e);
        }
    }

    TrackPoint getLastTrackPoint() {
        return lastTrackPoint;
    }

    /**
     * Called from {@link GpsStatus} to inform that GPS status has changed from prevStatus to currentStatus.
     *
     * @param prevStatus    previous {@link GpsStatusValue}.
     * @param currentStatus current {@link GpsStatusValue}.
     */
    @Override
    public void onGpsStatusChanged(GpsStatusValue prevStatus, GpsStatusValue currentStatus) {
        trackPointCreator.sendGpsStatus(currentStatus);
    }
}
