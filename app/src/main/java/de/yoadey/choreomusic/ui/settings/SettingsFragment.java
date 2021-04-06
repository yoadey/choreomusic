package de.yoadey.choreomusic.ui.settings;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import de.yoadey.choreomusic.R;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }
}

