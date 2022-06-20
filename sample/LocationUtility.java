public static String getREQUESTING_LOCATION_UPDATES_KEY(){
        return REQUESTING_LOCATION_UPDATES_KEY;
    }
    public static String getLOCATION_KEY(){
        return LOCATION_KEY;
    }
    public String getCurrentBatteryLevel()
    {
        return percentageLabel;
    }
    public String getCurrentUpdateInterval()
    {
        return timeRunning;
    }

    public void updateValuesFromBundle(Bundle savedInstanceState) {
        Log.i(TAG, "Updating values from bundle");
        if (savedInstanceState != null) {
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
                global_button = savedInstanceState.getBoolean(
                        REQUESTING_LOCATION_UPDATES_KEY);
            }

            if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
                mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }
            if (savedInstanceState.keySet().contains(LAST_UPDATED_TIME_STRING_KEY)) {
                mLastUpdateTime = savedInstanceState.getString(LAST_UPDATED_TIME_STRING_KEY);
            }
        }
    }
    public Location getLocation()
    {
        return mCurrentLocation;
    }
    public String getLastLocationUpdateTime()
    {
        return mLastUpdateTime;
    }
    protected synchronized void buildGoogleApiClient() {
        Log.i(TAG, "Building GoogleApiClient");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
    }
    public void createLocationRequest()
    {
        mLocationRequest = new LocationRequest();
        if(timeRunning == null)
        	{
            	mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
                Log.i("String:",String.valueOf(UPDATE_INTERVAL_IN_MILLISECONDS));
            }
        else
            {
                mLocationRequest.setInterval(Long.parseLong(timeRunning));
                Log.i("String:",timeRunning);
            }
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS/2);
        Log.i("String:",String.valueOf(UPDATE_INTERVAL_IN_MILLISECONDS));
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }
    public boolean isLocationServiceOn(){
        return global_button;
    }

    public void startLocationService() {
        if (!isPlayServicesAvailable(this)) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
        else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                showRationaleDialog();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            }
        }
    }

    public void startLocationUpdates() {
        Log.i(TAG, "startLocationUpdates");

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                final Status status = locationSettingsResult.getStatus();

                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        if (ContextCompat.checkSelfPermission(BatteryAware.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, BatteryAware.this);
                        }
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            status.startResolutionForResult(BatteryAware.this, REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        break;
                }
            }
        });
        global_button = true;
        onLocationServiceStart();
    }
    protected void stopLocationUpdates() {
        Log.i(TAG, "stopLocationUpdates");
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        global_button = false;
        onLocationServiceStop();
    }
    public void onLocationServiceStop()
    {

    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates();
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                        global_button = false;
                        Toast.makeText(BatteryAware.this, "To enable the function of this application, please enable the location information permission of the application from the setting screen of the terminal.", Toast.LENGTH_SHORT).show();
                    } else {
                        showRationaleDialog();
                    }
                }
                break;
            }
        }
    }
    public void onLocationServiceStart()
    {

    }
    public void showRationaleDialog() {
        new AlertDialog.Builder(this)
                .setPositiveButton("To give permission", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(BatteryAware.this,
                                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);

                    }
                })
                .setNegativeButton("don't do", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(BatteryAware.this,"Location permission not allowed", Toast.LENGTH_SHORT).show();
                        global_button = false;
                    }
                })
                .setCancelable(false)
                .setMessage("This app needs to allow the use of location information.")
                .show();
    }
    public static boolean isPlayServicesAvailable(Context context) {
        int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
        if (resultCode != ConnectionResult.SUCCESS) {
            GoogleApiAvailability.getInstance().getErrorDialog((Activity) context, resultCode, 2).show();
            return false;
        }
        return true;
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        startLocationUpdates();
                        break;
                    case Activity.RESULT_CANCELED:
                        break;
                }
                break;
        }
    }
   
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "onConnected");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (mCurrentLocation == null) {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        }
        if (global_button) {
            startLocationUpdates();
        }
    }
    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, "onLocationChanged");
        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        onLocationUpdate();
    }
    public void onLocationUpdate()
    {

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
    }
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY, global_button);
        savedInstanceState.putParcelable(LOCATION_KEY, mCurrentLocation);
        savedInstanceState.putString(LAST_UPDATED_TIME_STRING_KEY, mLastUpdateTime);
        super.onSaveInstanceState(savedInstanceState);
    }
    @Override
    public boolean moveTaskToBack(boolean nonRoot)
    {
        return super.moveTaskToBack(setUpIntervalObject.runInBackground);
    }
