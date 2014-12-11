package com.dr8.xposedmtc.receivers;

import com.maxmpz.poweramp.player.PowerampAPI;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StatusReceiver extends BroadcastReceiver {

	private static final String TAG = "XMTC-StatusReceiver";
	private Intent mStatusIntent;
	private Intent sendIntent;
	private boolean isPaused = false;
	private static int status;
	private static String SEND_STATUS = "com.dr8.xposedmtc.SEND_STATUS";

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "mStatusReceiver " + intent);
		mStatusIntent = intent;
		if (intent != null)
			isPaused = true;
		status = mStatusIntent.getIntExtra(PowerampAPI.STATUS, -1);
		Log.d(TAG, "status is " + status);
		switch (status) { 
		case PowerampAPI.Status.TRACK_PLAYING:
			isPaused = mStatusIntent.getBooleanExtra("paused", false);
			break;
		case PowerampAPI.Status.TRACK_ENDED:
		case PowerampAPI.Status.PLAYING_ENDED:
			break;
		}
		
		Log.d(TAG, "pause is " + isPaused);
		sendIntent = new Intent();
		sendIntent.setAction(SEND_STATUS);
		sendIntent.putExtra("paused", isPaused);
		context.sendBroadcast(sendIntent);

	}

}
