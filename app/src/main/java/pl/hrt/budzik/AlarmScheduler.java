package pl.hrt.budzik;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class AlarmScheduler {
    private AlarmScheduler() {}
    public static final String ACTION_ALARM = "pl.hrt.budzik.ACTION_ALARM";
    private static final int REQUEST_CODE = 4122;

    public static boolean hasExactPermission(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    private static PendingIntent receiverPI(Context ctx) {
        Intent intent = new Intent(ctx, AlarmReceiver.class).setAction(ACTION_ALARM);
        return PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent showPI(Context ctx) {
        Intent intent = new Intent(ctx, MainActivity.class);
        return PendingIntent.getActivity(ctx, 120, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static void schedule(Context ctx, long whenMillis) {
        if (!hasExactPermission(ctx)) throw new SecurityException("Brak uprawnienia: Alarmy i przypomnienia");
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(whenMillis, showPI(ctx)), receiverPI(ctx));
        Prefs.get(ctx).edit().putLong("next_at", whenMillis).apply();
        LogBook.add(ctx, "Zaplanowano " + format(whenMillis));
    }
    public static void setTest(Context ctx, int minutes) {
        cancel(ctx);
        Prefs.get(ctx).edit().putBoolean("weekly_enabled", false).apply();
        schedule(ctx, System.currentTimeMillis() + minutes * 60000L);
    }
    public static boolean setWeekly(Context ctx, int hour, int minute, int daysMask) {
        if (daysMask == 0) return false;
        // Save only if permission is present; do not pretend an alarm is scheduled.
        if (!hasExactPermission(ctx)) return false;
        cancel(ctx);
        Prefs.get(ctx).edit()
                .putBoolean("weekly_enabled", true)
                .putInt("hour", hour).putInt("minute", minute)
                .putInt("days", daysMask).apply();
        scheduleNextWeekly(ctx);
        return true;
    }
    public static void scheduleNextWeekly(Context ctx) {
        SharedPreferences p = Prefs.get(ctx);
        if (!p.getBoolean("weekly_enabled", false)) return;
        int mask = p.getInt("days", 0);
        int h = p.getInt("hour", 6), m = p.getInt("minute", 30);
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        for (int offset = 0; offset <= 7; offset++) {
            c.setTimeInMillis(now);
            c.add(Calendar.DAY_OF_YEAR, offset);
            int javaDay = c.get(Calendar.DAY_OF_WEEK);
            int mondayZero = (javaDay + 5) % 7;
            if ((mask & (1 << mondayZero)) == 0) continue;
            c.set(Calendar.HOUR_OF_DAY, h);
            c.set(Calendar.MINUTE, m);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis() > now + 1000L) {
                schedule(ctx, c.getTimeInMillis());
                return;
            }
        }
        LogBook.add(ctx, "Nie znaleziono kolejnego dnia alarmu");
    }
    public static void onAlarmTriggered(Context ctx) {
        // Mark one-off tests finished, advance weekly alarm before starting player.
        Prefs.get(ctx).edit().putLong("last_fired", System.currentTimeMillis()).putLong("next_at", 0).apply();
        if (Prefs.get(ctx).getBoolean("weekly_enabled", false)) {
            try { scheduleNextWeekly(ctx); } catch (Exception e) { LogBook.add(ctx, "BŁĄD następnego alarmu: " + e); }
        }
    }
    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(receiverPI(ctx));
        Prefs.get(ctx).edit().putBoolean("weekly_enabled", false).putLong("next_at", 0).apply();
        LogBook.add(ctx, "Alarm anulowany");
    }
    public static String format(long epoch) {
        if (epoch <= 0) return "brak";
        return new SimpleDateFormat("EEE, dd.MM HH:mm:ss", new Locale("pl", "PL")).format(new Date(epoch));
    }
}
