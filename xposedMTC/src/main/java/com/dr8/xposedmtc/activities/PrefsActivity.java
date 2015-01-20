package com.dr8.xposedmtc.activities;

import com.dr8.xposedmtc.R;
import com.dr8.xposedmtc.fragments.AppsPrefsFragment;
import com.dr8.xposedmtc.fragments.DimmerPrefsFragment;
import com.dr8.xposedmtc.fragments.MiscPrefsFragment;
import com.dr8.xposedmtc.fragments.OBDPrefsFragment;
import com.dr8.xposedmtc.fragments.PresetsPrefsFragment;
import com.dr8.xposedmtc.services.SunriseService;
import com.dr8.xposedmtc.utils.GetLocation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.View;

import java.util.List;

public class PrefsActivity extends PreferenceActivity {
	
	private static SharedPreferences prefs;
	private BroadcastReceiver prefwriter;
	private int station;
	private String presetnum;
	private Bundle bundle;
	private static String TAG = "XMTC-Prefs";
	private static String SAVE_PRESET = "com.dr8.xposedmtc.SAVE_PRESETS";

    private static Runnable myRunnable;
    private static Handler myHandler;
    private static Context ctx;

    @Override
    public void onBuildHeaders(List<PreferenceActivity.Header> target) {
        ctx = this.getApplicationContext();
        loadHeadersFromResource(R.xml.preference_headers, target);
    }

    protected boolean isValidFragment(String fragmentName) {
        return AppsPrefsFragment.class.getName().equals(fragmentName)
                || DimmerPrefsFragment.class.getName().equals(fragmentName)
                || MiscPrefsFragment.class.getName().equals(fragmentName)
                || OBDPrefsFragment.class.getName().equals(fragmentName)
                || PresetsPrefsFragment.class.getName().equals(fragmentName);
    }


	@SuppressWarnings("deprecation")
    @Override
	public void onCreate(Bundle savedInstanceState) {
		setTitle(R.string.app_name);
		super.onCreate(savedInstanceState);
		
		prefs = getSharedPreferences("com.dr8.xposedmtc_preferences", MODE_WORLD_READABLE);

        if (prefs.getBoolean("firstrun", true)) {
            GetLocation inst = new GetLocation();
            long[] times = inst.getLocation(this);
            prefs.edit().putLong("dimmerstart", times[0]).commit();
            prefs.edit().putLong("dimmerend", times[1]).commit();
        }

		IntentFilter preffilter = new IntentFilter();
		preffilter.addAction(SAVE_PRESET);
		
		prefwriter = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {

				bundle = intent.getExtras();
				if (bundle != null) {
					Log.d(TAG, "bundle not null");
					for (String key : bundle.keySet()) {
						Object value = bundle.get(key);
						if (key.contains("RadioFrequency")) {
							presetnum = key;
							station = Integer.valueOf(value.toString());
						}
					}
					Log.d(TAG, "writing " + presetnum + " : " + station + " to prefs");
					prefs.edit().putInt(presetnum, station).commit();
				} else {
					Log.d(TAG, "bundle is null");
				}
			}
		};
		this.registerReceiver(prefwriter, preffilter);
	}

	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	public void onStop() {
		super.onStop();
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(prefwriter);
		super.onDestroy();
	}
	
	@Override
	public void onBackPressed() {
		if (prefs.getBoolean("firstrun", true)) {
			prefs.edit().putBoolean("firstrun", false).commit();
			Intent returnIntent = new Intent();
			returnIntent.putExtra("result", prefs.getString("apps_key", "com.microntek.music"));
			returnIntent.putExtra("resultvideo", prefs.getString("video_key", "com.microntek.movie"));
			setResult(RESULT_OK, returnIntent);
		}
		super.onBackPressed();
	}

	public void startService(View v) {
		Intent i = new Intent(this, SunriseService.class);
		this.startService(i);
		Log.w(TAG, "SunriseService started manually");	
	}

	public void stopService(View v) {
		Intent i = new Intent(this, SunriseService.class);
		this.stopService(i);
		Log.w(TAG, "SunriseService stopped manually");	
	}

}
