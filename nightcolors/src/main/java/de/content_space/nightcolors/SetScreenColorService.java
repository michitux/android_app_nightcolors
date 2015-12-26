package de.content_space.nightcolors;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.PowerManager;
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

        NightTimeHelper helper = new NightTimeHelper(context);

        // if we are before the start of the day, set night colors
        if (helper.isDay()) {
            sendWakefulWork(context, ACTION_DAY);
        } else {
            sendWakefulWork(context, ACTION_NIGHT);
        }

        Calendar beginNight = helper.getBeginOfNextNight();
        // With setInexactRepeating(), you have to use one of the AlarmManager interval
        // constants--in this case, AlarmManager.INTERVAL_DAY.
        alarmMgr.setInexactRepeating(AlarmManager.RTC, beginNight.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, getPendingNightIntent(context));
        Log.i("NightColors", "Set night intent on " + beginNight.get(Calendar.DAY_OF_MONTH) + ". at " + beginNight.get(Calendar.HOUR_OF_DAY));

        Calendar beginDay = helper.getBeginOfNextDay();
        alarmMgr.setInexactRepeating(AlarmManager.RTC, helper.getBeginOfNextDay().getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, getPendingDayIntent(context));
        Log.i("NightColors", "Set day intent on " + beginDay.get(Calendar.DAY_OF_MONTH) + ". at " + beginDay.get(Calendar.HOUR_OF_DAY));
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
        PowerManager.WakeLock lock = getLock(this.getApplicationContext());

        // make sure that we hold the lock even if the service should have been restarted
        if (!lock.isHeld()) {
            lock.acquire();
        }

        int result = START_REDELIVER_INTENT;

        try {
            if (intent != null) {
                final String action = intent.getAction();
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

                // handle the action directly if the screen is on
                if (pm.isScreenOn()) {
                    // The original action that should be executed with screen on
                    String originalAction = action;

                    // If this is the screen on intent, recover the original action which might be saved (or not)
                    if (action.equals(Intent.ACTION_SCREEN_ON)) {
                        if (mLastAction != null) {
                            originalAction = mLastAction;
                        } else { // recover action by comparing the current time to the configured times
                            NightTimeHelper helper = new NightTimeHelper(getApplicationContext());
                            if (helper.isDay()) {
                                originalAction = ACTION_DAY;
                            } else {
                                originalAction = ACTION_NIGHT;
                            }
                        }
                    }

                    if (ACTION_NIGHT.equals(originalAction)) {
                        handleActionNight();
                    } else if (ACTION_DAY.equals(originalAction)) {
                        handleActionDay();
                    } else {
                        Log.e("NightColors", "Error, unknown action " + originalAction + " received");
                    }

                    // Clean up: unregister receiver, stop service
                    if (mReceiver != null) {
                        unregisterReceiver(mReceiver);
                        mReceiver = null;
                    }

                    stopSelf(startId);
                    result = START_NOT_STICKY;
                } else {
                    // if the screen is off register a receiver, but only if it does not exist yet
                    // this might be the case if the screen remained off between two alarms
                    if (mReceiver == null) {
                        mReceiver = new NightColorsReceiver();
                        registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
                        Log.i("NightColors", "Cannot set colors while screen is off, scheduled receiver for screen on event");
                    }

                    // Store the action so we know what to do when the screen is turned on.
                    // When the service is restarted we might receive again the ACTION_SCREEN_ON intent but the screen might still be off,
                    // in this case do not store any action (will be recovered above instead).
                    if (ACTION_DAY.equals(action) || ACTION_NIGHT.equals(action)) {
                        mLastAction = action;
                    }
                }
            }
        } finally {
            if (lock.isHeld()) {
                lock.release();
            }
        }

        return result;
    }


    @Override
    public void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
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
