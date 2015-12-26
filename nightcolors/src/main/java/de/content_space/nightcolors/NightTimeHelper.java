package de.content_space.nightcolors;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Calendar;

/**
 * Helper class for reading the time preferences and interpreting them.
 */
public class NightTimeHelper {
    private Calendar mBeginNextDay = null;
    private Calendar mBeginNextNight = null;

    /**
     * Construct the NightTimeHelper
     *
     * @param context The application context for which the preferences shall be loaded
     */
    public NightTimeHelper(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        mBeginNextDay = prefToNextCalendar(sharedPref.getInt(NightColorsSettingsFragment.KEY_PREF_START, 480));
        mBeginNextNight = prefToNextCalendar(sharedPref.getInt(NightColorsSettingsFragment.KEY_PREF_END, 1200));
    }

    /**
     * Converts the preference value (stored in minutes after midnight) to a Calendar instance on the current day.
     * @param pref The input time in minutes after midnight
     * @return The input time as Calendar object on the current day
     */
    private Calendar prefToNextCalendar(int pref) {
        int hour = pref/60;
        int minute = pref%60;

        Calendar result = Calendar.getInstance();
        result.set(Calendar.SECOND, 0); // normalize second and millisecond
        result.set(Calendar.MILLISECOND, 0);
        result.set(Calendar.HOUR_OF_DAY, hour);
        result.set(Calendar.MINUTE, minute);

        // if we are after that time, the next time is tomorrow
        if (Calendar.getInstance().after(result)) {
            result.add(Calendar.DATE, 1);
        }

        return result;
    }

    /**
     * Get the beginning of the next day period
     *
     * @return The beginning of the next day period
     */
    public Calendar getBeginOfNextDay() {
        return mBeginNextDay;
    }

    /**
     * Get the beginning of the next night period
     *
     * @return The beginning of the next night period
     */
    public Calendar getBeginOfNextNight() {
        return mBeginNextNight;
    }

    /**
     * If we are currently in the day period, i.e. if the next event is the beginning of the night
     *
     * @return If we are currently in the day period
     */
    public boolean isDay() {
        return (mBeginNextDay.after(mBeginNextNight));
    }
}
