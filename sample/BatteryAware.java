package com.batteryaware.egen;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
public class BatteryAware extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {
        
        // Add the code from the File AdaptationUtility.java here
        	
        @Override
            protected void onCreate(@Nullable Bundle savedInstanceState)
            {
        
                super.onCreate(savedInstanceState);
                mLastUpdateTime = "";
                updateValuesFromBundle(savedInstanceState);
                buildGoogleApiClient();
                this.registerReceiver(this.BatteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            }
            
        // Add the code from the File LocationUtility.java here
        
        @Override
            protected void onStart() {
                super.onStart();
                mGoogleApiClient.connect();
            }
            @Override
            public void onResume() {
                super.onResume();
                setUpIntervalObject.inForeGround = true;
                isPlayServicesAvailable(this);
                this.registerReceiver(this.BatteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            }
            @Override
            protected void onPause() {
                super.onPause();
                setUpIntervalObject.inForeGround = false;
                Log.i("Running in Background: ", getCurrentUpdateInterval());
            }
            @Override
            protected void onStop() {
                super.onStop();
                if (!setUpIntervalObject.runInBackground)
                {
                    stopLocationUpdates();
                    mGoogleApiClient.disconnect();
                }
        
            }
            @Override
            protected void onDestroy() {
                super.onDestroy();
            }
        }    

