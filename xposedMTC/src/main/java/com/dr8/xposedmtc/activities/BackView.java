package com.dr8.xposedmtc.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class BackView extends Activity {

	private Intent bvintent;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		bvintent = new Intent();
		bvintent.setAction("com.dr8.xposedmtc.BACKVIEW");
		this.sendBroadcast(bvintent);
		finish();
	}


}
