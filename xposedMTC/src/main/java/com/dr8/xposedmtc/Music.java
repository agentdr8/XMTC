package com.dr8.xposedmtc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class Music extends Activity {

	private static SharedPreferences prefs;
	private Intent intent;
	private Intent muteintent;
	private static String musicapp;
	private static String MUTE_ACTION = "com.dr8.xposedmtc.MuteRadio";
	
	@SuppressLint("WorldReadableFiles")
	@SuppressWarnings("deprecation")
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		prefs = getSharedPreferences("com.dr8.xposedmtc_preferences", MODE_WORLD_READABLE);

		if (prefs.getBoolean("firstrun", true)) {
			intent = new Intent(this, PrefsActivity.class);
			this.startActivityForResult(intent, 1);
		} else {
			musicapp = prefs.getString("apps_key", "com.microntek.music");
			launchApp(musicapp);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
	    if (requestCode == 1) {
	        if(resultCode == RESULT_OK){
	            String result = data.getStringExtra("result");
	            launchApp(result);
			}
	        if (resultCode == RESULT_CANCELED) {
	            return;
	        }
	    }
	}
	
	private void launchApp(String pkg) {
		
		if (!pkg.equals("com.microntek.music")) {
			muteintent = new Intent();
			muteintent.setAction(MUTE_ACTION);
			muteintent.putExtra("mode", "music");
			muteintent.putExtra("pkg", pkg);
			this.sendBroadcast(muteintent);

		}
		
		this.finish();
	}
}
