package com.dr8.xposedmtc.receivers;

import com.dr8.xposedmtc.services.SunriseService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ServiceIntentReceiver extends BroadcastReceiver {

	private static String TAG = "XMTC-IntentRecv";

	@Override
	public void onReceive(Context arg0, Intent arg1) {
		Intent intent = new Intent(arg0, SunriseService.class);
		if (arg1.getAction().equals("com.dr8.xposedmtc.START_SERVICE")) {
			arg0.startService(intent);
	        Log.w(TAG, "SunriseService started via intent");	
		} else if (arg1.getAction().equals("com.dr8.xposedmtc.STOP_SERVICE")) {
			arg0.stopService(intent);
			Log.w(TAG, "SunriseService stopped via intent");
		}
	}

}
