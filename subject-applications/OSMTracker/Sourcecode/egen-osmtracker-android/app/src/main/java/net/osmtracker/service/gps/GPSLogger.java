package net.osmtracker.service.gps;

import net.osmtracker.OSMTracker;
import net.osmtracker.R;
import net.osmtracker.activity.TrackLogger;
import net.osmtracker.db.DataHelper;
import net.osmtracker.db.TrackContentProvider;
import net.osmtracker.listener.PressureListener;
import net.osmtracker.listener.SensorListener;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;

import java.util.concurrent.TimeUnit;

/**
 * GPS logging service.
 *
 * @author Nicolas Guillaumin
 *
 */
public class GPSLogger extends Service implements LocationListener {

	private static final String TAG = GPSLogger.class.getSimpleName();

	/**
	 * Data helper.
	 */
	private DataHelper dataHelper;

	/**
	 * Are we currently tracking ?
	 */
	private boolean isTracking = false;

	/**
	 * Is GPS enabled ?
	 */
	private boolean isGpsEnabled = false;

	/**
	 * Use barometer yes/no ?
	 */
	private boolean use_barometer = false;

	/**
	 * System notification id.
	 */
	private static final int NOTIFICATION_ID = 1;
	private static String CHANNEL_ID = "GPSLogger_Channel";

	/**
	 * Last known location
	 */
	private Location lastLocation;

	/**
	 * LocationManager
	 */
	private LocationManager lmgr;

	/**
	 * Current Track ID
	 */
	private long currentTrackId = -1;

	/**
	 * the timestamp of the last GPS fix we used
	 */
	private long lastGPSTimestamp = 0;

	/**
	 * the interval (in ms) to log GPS fixes defined in the preferences
	 */
	private long gpsLoggingInterval;
	private long gpsLoggingMinDistance;

	/**
	 * sensors for magnetic orientation
	 */
	private SensorListener sensorListener = new SensorListener();

	/**
	 * sensor for atmospheric pressure
	 */
	private PressureListener pressureListener = new PressureListener();

	/**
	 * Receives Intent for way point tracking, and stop/start logging.
	 */
	private BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.v(TAG, "Received intent " + intent.getAction());

			if (OSMTracker.INTENT_TRACK_WP.equals(intent.getAction())) {
				// Track a way point
				Bundle extras = intent.getExtras();
				if (extras != null) {
					// because of the gps logging interval our last fix could be very old
					// so we'll request the last known location from the gps provider
					if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
						lastLocation = lmgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
						if (lastLocation != null) {
							Long trackId = extras.getLong(TrackContentProvider.Schema.COL_TRACK_ID);
							String uuid = extras.getString(OSMTracker.INTENT_KEY_UUID);
							String name = extras.getString(OSMTracker.INTENT_KEY_NAME);
							String link = extras.getString(OSMTracker.INTENT_KEY_LINK);

							dataHelper.wayPoint(trackId, lastLocation, name, link, uuid, sensorListener.getAzimuth(), sensorListener.getAccuracy(), pressureListener.getPressure());

							// If there is a waypoint in the track, there should also be a trackpoint
							dataHelper.track(currentTrackId, lastLocation, sensorListener.getAzimuth(), sensorListener.getAccuracy(), pressureListener.getPressure());
						}
					}
				}
			} else if (OSMTracker.INTENT_UPDATE_WP.equals(intent.getAction())) {
				// Update an existing waypoint
				Bundle extras = intent.getExtras();
				if (extras != null) {
					Long trackId = extras.getLong(TrackContentProvider.Schema.COL_TRACK_ID);
					String uuid = extras.getString(OSMTracker.INTENT_KEY_UUID);
					String name = extras.getString(OSMTracker.INTENT_KEY_NAME);
					String link = extras.getString(OSMTracker.INTENT_KEY_LINK);
					dataHelper.updateWayPoint(trackId, uuid, name, link);
				}
			} else if (OSMTracker.INTENT_DELETE_WP.equals(intent.getAction())) {
				// Delete an existing waypoint
				Bundle extras = intent.getExtras();
				if (extras != null) {
					String uuid = extras.getString(OSMTracker.INTENT_KEY_UUID);
					dataHelper.deleteWayPoint(uuid);
				}
			} else if (OSMTracker.INTENT_START_TRACKING.equals(intent.getAction()) ) {
				Bundle extras = intent.getExtras();
				if (extras != null) {
					Long trackId = extras.getLong(TrackContentProvider.Schema.COL_TRACK_ID);
					startTracking(trackId);
				}
			} else if (OSMTracker.INTENT_STOP_TRACKING.equals(intent.getAction()) ) {
				stopTrackingAndSave();
			}
		}
	};

	/**
	 * Binder for service interaction
	 */
	private final IBinder binder = new GPSLoggerBinder();

	@Override
	public IBinder onBind(Intent intent) {
		Log.v(TAG, "Service onBind()");
		return binder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.v(TAG, "Service onUnbind()");
		// If we aren't currently tracking we can
		// stop ourselves
		if (! isTracking ) {
			Log.v(TAG, "Service self-stopping");
			stopSelf();
		}

		// We don't want onRebind() to be called, so return false.
		return false;
	}

	/**
	 * Bind interface for service interaction
	 */
	public class GPSLoggerBinder extends Binder {

		/**
		 * Called by the activity when binding.
		 * Returns itself.
		 * @return the GPS Logger service
		 */
		public GPSLogger getService() {
			return GPSLogger.this;
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
	protected int returnLevel() {
		int BATTERY_AWARE_SI = 1000;
		BatteryManager bm = (BatteryManager) getApplicationContext().getSystemService(BATTERY_SERVICE);
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
	public void onCreate() {
		Log.v(TAG, "Service onCreate()");
		dataHelper = new DataHelper(this);
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

			gpsLoggingInterval = returnLevel();

			gpsLoggingMinDistance = Long.parseLong(PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext()).getString(
					OSMTracker.Preferences.KEY_GPS_LOGGING_MIN_DISTANCE, OSMTracker.Preferences.VAL_GPS_LOGGING_MIN_DISTANCE));
			use_barometer =  PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext()).getBoolean(
					OSMTracker.Preferences.KEY_USE_BAROMETER, OSMTracker.Preferences.VAL_USE_BAROMETER);

			// Register our broadcast receiver
			IntentFilter filter = new IntentFilter();
			filter.addAction(OSMTracker.INTENT_TRACK_WP);
			filter.addAction(OSMTracker.INTENT_UPDATE_WP);
			filter.addAction(OSMTracker.INTENT_DELETE_WP);
			filter.addAction(OSMTracker.INTENT_START_TRACKING);
			filter.addAction(OSMTracker.INTENT_STOP_TRACKING);
			registerReceiver(receiver, filter);

			// Register ourselves for location updates
			lmgr = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

			if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
				lmgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, gpsLoggingInterval, gpsLoggingMinDistance, this);
			}

			//register for Orientation updates
			sensorListener.register(this);

			// register for atmospheric pressure updates
			pressureListener.register(this, use_barometer);

			super.onCreate();

		//read the logging interval from preferences
//    gpsLoggingInterval = Long.parseLong(PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext()).getString(
//          OSMTracker.Preferences.KEY_GPS_LOGGING_INTERVAL, OSMTracker.Preferences.VAL_GPS_LOGGING_INTERVAL)) * 1000;



	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.v(TAG, "Service onStartCommand(-,"+flags+","+startId+")");
		createNotificationChannel();
		startForeground(NOTIFICATION_ID, getNotification());
		return Service.START_STICKY;
	}

	@Override
	public void onDestroy() {
		Log.v(TAG, "Service onDestroy()");
		if (isTracking) {
			// If we're currently tracking, save user data.
			stopTrackingAndSave();
		}

		// Unregister listener
		lmgr.removeUpdates(this);

		// Unregister broadcast receiver
		unregisterReceiver(receiver);

		// Cancel any existing notification
		stopNotifyBackgroundService();

		// stop sensors
		sensorListener.unregister();
		pressureListener.unregister();

		super.onDestroy();
	}

	/**
	 * Start GPS tracking.
	 */
	private void startTracking(long trackId) {
		currentTrackId = trackId;
		Log.v(TAG, "Starting track logging for track #" + trackId);
		// Refresh notification with correct Track ID
		NotificationManager nmgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nmgr.notify(NOTIFICATION_ID, getNotification());
		isTracking = true;
	}

	/**
	 * Stops GPS Logging
	 */
	private void stopTrackingAndSave() {
		isTracking = false;
		dataHelper.stopTracking(currentTrackId);
		currentTrackId = -1;
		this.stopSelf();
	}

	@Override
	public void onLocationChanged(Location location) {
		// We're receiving location, so GPS is enabled
		isGpsEnabled = true;

		// first of all we check if the time from the last used fix to the current fix is greater than the logging interval
		gpsLoggingInterval = returnLevel();
		if((lastGPSTimestamp + gpsLoggingInterval) < System.currentTimeMillis()){
			lastGPSTimestamp = System.currentTimeMillis(); // save the time of this fix

			lastLocation = location;

			if (isTracking) {
				dataHelper.track(currentTrackId, location, sensorListener.getAzimuth(), sensorListener.getAccuracy(), pressureListener.getPressure());
			}
		}
	}

	/**
	 * Builds the notification to display when tracking in background.
	 */
	private Notification getNotification() {

		Intent startTrackLogger = new Intent(this, TrackLogger.class);
		startTrackLogger.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, startTrackLogger, PendingIntent.FLAG_UPDATE_CURRENT);


		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_stat_track)
				.setContentTitle(getResources().getString(R.string.notification_title).replace("{0}", (currentTrackId > -1) ? Long.toString(currentTrackId) : "?"))
				.setContentText(getResources().getString(R.string.notification_text))
				.setPriority(NotificationCompat.PRIORITY_DEFAULT)
				.setContentIntent(contentIntent)
				.setAutoCancel(true);
		return mBuilder.build();
	}

	private void createNotificationChannel() {
		// Create the NotificationChannel, but only on API 26+ because
		// the NotificationChannel class is new and not in the support library
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// FIXME: following two strings must be obtained from 'R.string' to support translations
			CharSequence name = "GPS Logger";
			String description = "Display when tracking in Background";
			int importance = NotificationManager.IMPORTANCE_LOW;
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
			channel.setDescription(description);
			// Register the channel with the system; you can't change the importance
			// or other notification behaviors after this
			NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.createNotificationChannel(channel);
		}
	}


	/**
	 * Stops notifying the user that we're tracking in the background
	 */
	private void stopNotifyBackgroundService() {
		NotificationManager nmgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nmgr.cancel(NOTIFICATION_ID);
	}

	@Override
	public void onProviderDisabled(String provider) {
		isGpsEnabled = false;
	}

	@Override
	public void onProviderEnabled(String provider) {
		isGpsEnabled = true;
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// Not interested in provider status
	}

	/**
	 * Getter for gpsEnabled
	 * @return true if GPS is enabled, otherwise false.
	 */
	public boolean isGpsEnabled() {
		return isGpsEnabled;
	}

	/**
	 * Setter for isTracking
	 * @return true if we're currently tracking, otherwise false.
	 */
	public boolean isTracking() {
		return isTracking;
	}

}
