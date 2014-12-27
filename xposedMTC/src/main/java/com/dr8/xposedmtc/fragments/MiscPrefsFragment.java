package com.dr8.xposedmtc.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import com.dr8.xposedmtc.R;

public class MiscPrefsFragment extends PreferenceFragment {

    private Context ctx;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        ctx = getActivity();
    }

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs_misc);
        Preference ver = (Preference) findPreference("versionpref");
        ver.setSummary("Version " + getApplicationVersionName(ctx));
	}

    public static String getApplicationVersionName(Context context) {
        PackageManager packageManager = context.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException ex) {} catch(Exception e){}
        return "";
    }
}
