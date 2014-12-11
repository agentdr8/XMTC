package com.dr8.xposedmtc.receivers;

import com.dr8.xposedmtc.services.SunriseService;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class AutostartReceiver extends BroadcastReceiver {

	private static String TAG = "XMTC-Autostart";
	private static SharedPreferences prefs;
	
	@SuppressLint("WorldReadableFiles")
	@SuppressWarnings("deprecation")
	@Override
	public void onReceive(Context arg0, Intent arg1) {
		Intent intent = new Intent(arg0, SunriseService.class);
		prefs = arg0.getSharedPreferences("com.dr8.xposedmtc_preferences", Context.MODE_WORLD_READABLE);
		if (prefs.getBoolean("sunriseswitch", false) && prefs.getBoolean("dimmerswitch", false)) {
			arg0.startService(intent);
	        Log.w(TAG, "SunriseService started on boot");	
		} else {
			Log.w(TAG, "SunriseService not started per prefs");
		}
	}

}
