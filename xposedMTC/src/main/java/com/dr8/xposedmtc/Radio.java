package com.dr8.xposedmtc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class Radio extends Activity {

	private Intent muteintent;
	private static String MUTE_ACTION = "com.dr8.xposedmtc.MuteRadio";

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		launchApp();
	}


	private void launchApp() {

		muteintent = new Intent();
		muteintent.setAction(MUTE_ACTION);
		muteintent.putExtra("pkg", "com.microntek.radio");
		muteintent.putExtra("mode", "radio");
		this.sendBroadcast(muteintent);
		this.finish();
	}
}
