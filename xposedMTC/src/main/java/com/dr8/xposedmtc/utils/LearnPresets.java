package com.dr8.xposedmtc.utils;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

public class LearnPresets extends Preference implements Preference.OnPreferenceClickListener {
    public LearnPresets(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        getContext().sendBroadcast(getIntent());
        return true;
    }
}