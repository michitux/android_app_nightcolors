package de.content_space.nightcolors;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Calendar;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class SetScreenColorService extends IntentService {
    private static final String ACTION_NIGHT = "de.content_space.nightcolors.action.NIGHT";
    private static final String ACTION_DAY = "de.content_space.nightcolors.action.DAY";
    private static final String BASE_PATH = "/sys/class/misc/samoled_color/";
    private static final String GREEN_PATH = BASE_PATH + "green_multiplier";
    private static final String BLUE_PATH = BASE_PATH + "blue_multiplier";


    /**
     * Starts this service to perform action Night. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static PendingIntent getPendingNightIntent(Context context) {
        Intent intent = new Intent(context, SetScreenColorService.class);
        intent.setAction(ACTION_NIGHT);
        return PendingIntent.getService(context, 0, intent, 0);
    }

    /**
     * Starts this service to perform action Day with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static PendingIntent getPendingDayIntent(Context context) {
        Intent intent = new Intent(context, SetScreenColorService.class);
        intent.setAction(ACTION_DAY);
        return PendingIntent.getService(context, 0, intent, 0);
    }

    public static void installAlarms(Context context) {
        AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        int startTime = sharedPref.getInt(NightColorsSettingsFragment.KEY_PREF_START, 480);
        int startHour = startTime/60;
        int startMinute = startTime%60;

        int endTime = sharedPref.getInt(NightColorsSettingsFragment.KEY_PREF_END, 1200);
        int endHour = endTime/60;
        int endMinute = endTime%60;

        // Set the alarm to start at approximately 2:00 p.m.
        Calendar startCalendar = Calendar.getInstance();
        startCalendar.set(Calendar.HOUR_OF_DAY, startHour);
        startCalendar.set(Calendar.MINUTE, startMinute);

        Calendar endCalendar = Calendar.getInstance();
        endCalendar.set(Calendar.HOUR_OF_DAY, endHour);
        endCalendar.set(Calendar.MINUTE, endMinute);

        Calendar currentCalendar = Calendar.getInstance();

        // if we are during the day, schedule night event for the same day and day event for the next day
        if (currentCalendar.after(startCalendar) && currentCalendar.after(endCalendar)) {
            startCalendar.roll(Calendar.DATE, 1);
        }

        // With setInexactRepeating(), you have to use one of the AlarmManager interval
        // constants--in this case, AlarmManager.INTERVAL_DAY.
        alarmMgr.setInexactRepeating(AlarmManager.RTC, endCalendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, getPendingNightIntent(context));
        Log.i("NightColors", "Set night intent on " + endCalendar.get(Calendar.DAY_OF_MONTH) + ". at " + endCalendar.get(Calendar.HOUR_OF_DAY));

        alarmMgr.setInexactRepeating(AlarmManager.RTC, startCalendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, getPendingDayIntent(context));
        Log.i("NightColors", "Set day intent on " + startCalendar.get(Calendar.DAY_OF_MONTH) + ". at " + startCalendar.get(Calendar.HOUR_OF_DAY));
    }

    public SetScreenColorService() {
        super("SetScreenColorService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_NIGHT.equals(action)) {
                handleActionNight();
            } else if (ACTION_DAY.equals(action)) {
                handleActionDay();
            }
        }
    }

    /**
     * Handle action Day in the provided background thread with the provided
     * parameters.
     */
    private void handleActionNight() {
        try {
            PrintWriter greenWriter = new PrintWriter(GREEN_PATH);
            greenWriter.print(200000000);
            greenWriter.close();

            PrintWriter blueWriter = new PrintWriter(BLUE_PATH);
            blueWriter.print(200000000);
            blueWriter.close();
        } catch (FileNotFoundException e) {
            Log.e("NightColors", "Error setting night colors", e);
        }
    }

    /**
     * Handle action Night in the provided background thread with the provided
     * parameters.
     */
    private void handleActionDay() {
        try {
            PrintWriter greenWriter = new PrintWriter(GREEN_PATH);
            greenWriter.print(2000000000);
            greenWriter.close();

            PrintWriter blueWriter = new PrintWriter(BLUE_PATH);
            blueWriter.print(2000000000);
            blueWriter.close();
        } catch (FileNotFoundException e) {
            Log.e("NightColors", "Error setting day colors", e);
        }
    }
}
