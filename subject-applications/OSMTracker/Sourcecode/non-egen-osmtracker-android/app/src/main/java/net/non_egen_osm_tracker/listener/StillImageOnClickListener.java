package net.non_egen_osm_tracker.listener;

import net.non_egen_osm_tracker.activity.TrackLogger;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.view.View;
import android.view.View.OnClickListener;

/**
 * Manages still image recording with camera app.
 * 
 * @author Nicolas Guillaumin
 *
 */
public class StillImageOnClickListener implements OnClickListener {

	/**
	 * Parent activity
	 */
	TrackLogger activity;

	final private int RC_STORAGE_CAMERA_PERMISSIONS = 2;
	
	public StillImageOnClickListener(TrackLogger parent) {
		activity = parent;
	}
	
	@Override
	public void onClick(View v) {
		if (ContextCompat.checkSelfPermission(activity,
				Manifest.permission.WRITE_EXTERNAL_STORAGE) + ContextCompat.checkSelfPermission(activity,
				Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

			// Should we show an explanation?
			if ( (ActivityCompat.shouldShowRequestPermissionRationale(activity,
					Manifest.permission.WRITE_EXTERNAL_STORAGE))
					|| (ActivityCompat.shouldShowRequestPermissionRationale(activity,
					Manifest.permission.CAMERA)) ) {

				// Show an expanation to the user *asynchronously* -- don't block
				// this thread waiting for the user's response! After the user
				// sees the explanation, try again to request the permission.
				// TODO: explain why we need permission.
				//"we should explain why we need write and record audio permission"

			} else {

				// No explanation needed, we can request the permission.
				ActivityCompat.requestPermissions(activity,
						new String[]{
								Manifest.permission.WRITE_EXTERNAL_STORAGE,
								Manifest.permission.CAMERA},
						RC_STORAGE_CAMERA_PERMISSIONS);
			}

		} else {
			activity.requestStillImage();
		}

	}

	public void onRequestPermissionsResult(int requestCode,
										   String permissions[], int[] grantResults) {
		switch (requestCode) {
			case RC_STORAGE_CAMERA_PERMISSIONS: {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length == 2
						&& grantResults[0] == PackageManager.PERMISSION_GRANTED
						&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {

					// permission was granted, yay!
					activity.requestStillImage();

				} else {

					// permission denied, boo! Disable the
					// functionality that depends on this permission.
					//TODO: add an informative message.
				}
				return;
			}
		}
	}

}
