package de.content_space.nightcolors;

import android.content.SharedPreferences;
import android.preference.PreferenceFragment;
import android.os.Bundle;

/**
 * A placeholder fragment containing a simple view.
 */
public class NightColorsSettingsFragment extends PreferenceFragment
implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String KEY_PREF_START = "pref_start";
    public static final String KEY_PREF_END = "pref_end";

    public NightColorsSettingsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        if (key.equals(KEY_PREF_START) || key.equals(KEY_PREF_END)) {
            SetScreenColorService.installAlarms(getActivity().getApplicationContext());
            /*
            Preference connectionPref = findPreference(key);
            // Set summary to be the user-description for the selected value
            connectionPref.setSummary(sharedPreferences.getString(key, ""));
            */
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
}
