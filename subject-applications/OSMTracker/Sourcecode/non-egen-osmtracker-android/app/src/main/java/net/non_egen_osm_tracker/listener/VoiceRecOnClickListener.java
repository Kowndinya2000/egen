package net.non_egen_osm_tracker.listener;

import net.non_egen_osm_tracker.activity.TrackLogger;

import android.view.View;
import android.view.View.OnClickListener;

/**
 * Manages voice recording.
 * 
 * @author Nicolas Guillaumin
 * 
 */
public class VoiceRecOnClickListener implements OnClickListener{

	/**
	 * Parent activity
	 */
	private TrackLogger tl;
	
	public VoiceRecOnClickListener(TrackLogger trackLogger) {
		tl = trackLogger;
	}

	@Override
	public void onClick(View v) {
		tl.showDialog(TrackLogger.DIALOG_VOICE_RECORDING);
	}

}
