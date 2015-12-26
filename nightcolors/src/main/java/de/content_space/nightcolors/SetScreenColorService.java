package de.content_space.nightcolors;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
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
 *
 * This uses some code from CommonsWare Android Components: WakefulIntentService
 * @see <a href="https://github.com/commonsguy/cwac-wakeful">github.com/commonsguy/cwac-wakeful</a>
 */
public class SetScreenColorService extends Service {
    public static final String ACTION_NIGHT = "de.content_space.nightcolors.action.NIGHT";
    public static final String ACTION_DAY = "de.content_space.nightcolors.action.DAY";
    private static final String BASE_PATH = "/sys/class/misc/samoled_color/";
    private static final String GREEN_PATH = BASE_PATH + "green_multiplier";
    private static final String BLUE_PATH = BASE_PATH + "blue_multiplier";

    static final String NAME= "de.content_space.nightcolors.SetScreenColorService";
    private static volatile PowerManager.WakeLock lockStatic=null;

    private NightColorsReceiver mReceiver = null;
    private String mLastAction = null;


    /**
     * Starts this service to perform action Night. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static PendingIntent getPendingNightIntent(Context context) {
        Intent intent = new Intent(context, NightColorsReceiver.class);
        intent.setAction(ACTION_NIGHT);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    /**
     * Starts this service to perform action Day with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static PendingIntent getPendingDayIntent(Context context) {
        Intent intent = new Intent(context, NightColorsReceiver.class);
        intent.setAction(ACTION_DAY);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
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

        // if we are before the start of the day, set night colors
        if (currentCalendar.before(startCalendar)) {
            sendWakefulWork(context, ACTION_NIGHT);
        }

        // if we are after the start day, move next event to tomorrow
        if (currentCalendar.after(startCalendar)) {
            startCalendar.roll(Calendar.DATE, 1);
            // if it is still before night, set colors to day
            if (currentCalendar.before(endCalendar)) {
                sendWakefulWork(context, ACTION_DAY);
            }
        }

        // if we are after the end of the day, set night colors and postpone night colors to tomorrow.
        if (currentCalendar.after(endCalendar)) {
            endCalendar.roll(Calendar.DATE, 1);
            sendWakefulWork(context, ACTION_NIGHT);
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


    synchronized private static PowerManager.WakeLock getLock(Context context) {
        if (lockStatic == null) {
            PowerManager mgr=
                    (PowerManager)context.getSystemService(Context.POWER_SERVICE);

            lockStatic=mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, NAME);
            lockStatic.setReferenceCounted(true);
        }

        return(lockStatic);
    }

    public static void sendWakefulWork(Context context, String action) {
        Intent intent = new Intent(context, SetScreenColorService.class);
        intent.setAction(action);
        sendWakefulWork(context, intent);
    }

    public static void sendWakefulWork(Context context, Intent intent) {
        getLock(context.getApplicationContext()).acquire();
        context.startService(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null) {
                final String action = intent.getAction();
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

                if (pm.isScreenOn()) {
                    String originalAction = action;

                    if (action.equals(Intent.ACTION_SCREEN_ON) && mLastAction != null) {
                        originalAction = mLastAction;
                    }

                    if (ACTION_NIGHT.equals(originalAction)) {
                        handleActionNight();
                    } else if (ACTION_DAY.equals(originalAction)) {
                        handleActionDay();
                    } else {
                        Log.e("NightColors", "Error, unknown action " + originalAction + " received");
                    }

                    if (mReceiver != null) {
                        unregisterReceiver(mReceiver);
                        mReceiver = null;
                    }

                    stopSelf();
                } else {
                    if (mReceiver == null) {
                        mReceiver = new NightColorsReceiver();
                    }

                    registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
                    mLastAction = action;

                    Log.i("NightColors", "Cannot set colors while screen is off, scheduled receiver for screen on event");
                }
            }
        } finally {
            PowerManager.WakeLock lock=getLock(this.getApplicationContext());

            if (lock.isHeld()) {
                lock.release();
            }
        }

        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
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

            Log.i("NightColors", "Set night colors");
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

            Log.i("NightColors", "Set day colors");
        } catch (FileNotFoundException e) {
            Log.e("NightColors", "Error setting day colors", e);
        }
    }
}
