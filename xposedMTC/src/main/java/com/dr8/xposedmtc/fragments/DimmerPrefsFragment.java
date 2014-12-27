package com.dr8.xposedmtc.fragments;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.dr8.xposedmtc.R;

public class DimmerPrefsFragment extends PreferenceFragment {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.prefs_dimmer);
	}
}
