package com.batteryaware.egen;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import com.batteryaware.egen.databinding.ActivityMainBinding;
import java.text.SimpleDateFormat;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.Date;
public class MainActivity extends BatteryAware {
    String loc = "";
    private String mLatitudeLabel;
    private String mLongitudeLabel;
    private String mLastUpdateTimeLabel;
    private ActivityMainBinding mBinding;
    private Location mCurrentLocation;
    private String mLastUpdateTime;
    private String start_string = "Start updates button";
    private boolean start_button = true;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mLatitudeLabel = getResources().getString(R.string.latitude_label);
        mLongitudeLabel = getResources().getString(R.string.longitude_label);
        mLastUpdateTimeLabel = getResources().getString(R.string.last_update_time_label);
        mBinding.percentageLabel.setText(getCurrentBatteryLevel());
        mCurrentLocation = getLocation();
        mLastUpdateTime = getLastLocationUpdateTime();
    }
    @Override
    public void onBatteryLevelChanged() {
        super.onBatteryLevelChanged();
        mBinding.percentageLabel.setText(getCurrentBatteryLevel());
        mBinding.timeRunning.setText(getCurrentUpdateInterval());
    }
    @Override
    public void updateValuesFromBundle(Bundle savedInstanceState) {
        super.updateValuesFromBundle(savedInstanceState);
        Log.i(TAG, "Updating values from bundle");
        if (savedInstanceState != null) {
            start_button = savedInstanceState.getBoolean(start_string);
            if (start_button)
            {
                startButton();
            }
            else
            {
                stopButton();
            }
            mCurrentLocation = getLocation();
            mLastUpdateTime = getLastLocationUpdateTime();
            updateUI();
        }
    }
    public void startUpdatesButtonHandler(View view) {
        startLocationService();
    }
    public void stopUpdatesButtonHandler(View view) {
        stopLocationUpdates();
    }
    void stopButton()
    {
        mBinding.startUpdatesButton.setEnabled(false);
        mBinding.stopUpdatesButton.setEnabled(true);
    }
    void startButton()
    {
        mBinding.startUpdatesButton.setEnabled(true);
        mBinding.stopUpdatesButton.setEnabled(false);
    }
    Double latitude1;
    Double longitude1;
    String lat1;
    String long1;
    Double latitude2;
    Double longitude2;
    private void updateUI() {
        if (mCurrentLocation == null) return;
        if (mBinding.longitudeText.getText().toString().isEmpty() || mBinding.latitudeText.getText().toString().isEmpty()) {
            latitude1 = mCurrentLocation.getLatitude();
            longitude1 = mCurrentLocation.getLongitude();
        } else {
            lat1 = mBinding.latitudeText.getText().toString().substring(10);
            long1 = mBinding.longitudeText.getText().toString().substring(11);
            latitude1 = Double.parseDouble(lat1);
            longitude1 = Double.parseDouble(long1);
        }
        mBinding.x1.setText(String.format("%s: %f", mLatitudeLabel,
                latitude1));
        mBinding.y1.setText(String.format("%s: %f", mLongitudeLabel,
                longitude1));
        latitude2 = mCurrentLocation.getLatitude();
        longitude2 = mCurrentLocation.getLongitude();
        double lati1 = Math.toRadians(latitude1);
        double longi1 = Math.toRadians(longitude1);
        double lati2 = Math.toRadians(latitude2);
        double longi2 = Math.toRadians(longitude2);
        double earthRadius = 6371.01;
        total_distance += 1000 * earthRadius * Math.acos(Math.sin(lati1) * Math.sin(lati2) + Math.cos(lati1) * Math.cos(lati2) * Math.cos(longi1 - longi2));
        mBinding.dist.setText(Double.toString(total_distance));
        mBinding.latitudeText.setText(String.format("%s: %f", mLatitudeLabel,
                latitude2));
        mBinding.longitudeText.setText(String.format("%s: %f", mLongitudeLabel,
                longitude2));
        mBinding.x2.setText(String.format("%s: %f", mLatitudeLabel,
                latitude2));
        mBinding.y2.setText(String.format("%s: %f", mLongitudeLabel,
                longitude2));
        mBinding.lastUpdateTimeText.setText(String.format("%s: %s", mLastUpdateTimeLabel,
                mLastUpdateTime));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/M/dd HH:mm:ss");
        String date = sdf.format(new Date());
        System.out.println(date);

        loc += String.format("%s: %f", mLatitudeLabel,
                latitude2) + String.format("%s: %f", mLongitudeLabel,
                longitude2) + " dist: " + total_distance + "battery: " + mBinding.percentageLabel.getText().toString();
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        DatabaseReference reference = db.getReference(date);
        reference.setValue(loc);
    }

    @Override
    public void onLocationServiceStart()
    {
        stopButton();
    }
    @Override
    public void onLocationServiceStop()
    {
        startButton();
    }
    @Override
    public void onLocationUpdate() {
        mCurrentLocation = getLocation();
        mLastUpdateTime = getLastLocationUpdateTime();
        updateUI();
        String location_updated_message = "Location Updated:" + getCurrentUpdateInterval() ;
        Log.i("Update Value: " , getCurrentUpdateInterval());
        Toast.makeText(this, location_updated_message, Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState)
    {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(start_string,start_button);
    }
}
