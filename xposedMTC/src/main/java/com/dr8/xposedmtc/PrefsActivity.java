package com.dr8.xposedmtc;

import com.dr8.xposedmtc.services.SunriseService;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.View;

public class PrefsActivity extends Activity {
	
	private static SharedPreferences prefs;
	private BroadcastReceiver prefwriter;
	private int station;
	private String presetnum;
	private Bundle bundle;
	private static String TAG = "XMTC-Prefs";
	private static String SAVE_PRESET = "com.dr8.xposedmtc.SAVE_PRESETS";

	@SuppressLint("WorldReadableFiles")
	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTitle(R.string.app_name);
		super.onCreate(savedInstanceState);
		
		prefs = getSharedPreferences("com.dr8.xposedmtc_preferences", MODE_WORLD_READABLE);

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
		
		// Display the fragment as the main content.
		if (savedInstanceState == null)
			getFragmentManager().beginTransaction().replace(android.R.id.content,
					new PrefsFragment()).commit();

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
	
	public static class PrefsFragment extends PreferenceFragment {
		private ProgressDialog pd;
		private Context ctx;
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			ctx = this.getActivity();
			
			AsyncTask<Void, Void, Void> doAppsList = new AsyncTask<Void, Void, Void>() {
	            @SuppressWarnings("deprecation")
				@Override
	            protected Void doInBackground(Void... params) {
	            	getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
	    			addPreferencesFromResource(R.xml.preferences);
	                return null;
	            }
	            
	            @Override
	            protected void onPostExecute(Void result) {
	                pd.dismiss();
	            }
	        };
	        
	        pd = ProgressDialog.show(ctx, getResources().getString(R.string.loading), getResources().getString(R.string.pleasewait), true, false);
	        doAppsList.execute((Void[])null);
			
		}
	}
	
}
