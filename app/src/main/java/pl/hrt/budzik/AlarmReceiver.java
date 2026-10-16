package pl.hrt.budzik;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !AlarmScheduler.ACTION_ALARM.equals(intent.getAction())) return;
        LogBook.add(context, "ALARM OTRZYMANY — receiver Android");
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HRT:alarm-start");
        wake.acquire(20000L); // Timed CPU wake lock; playback service holds its own while playing.
        try {
            AlarmScheduler.onAlarmTriggered(context);
            Intent service = new Intent(context, RadioService.class).setAction(RadioService.ACTION_ALARM_PLAY);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
            LogBook.add(context, "Zażądano uruchomienia RadioService");
        } catch (Exception e) {
            LogBook.add(context, "BŁĄD startu usługi: " + e.toString());
        } finally {
            // The lock has a safety timeout. Don't release before service startup.
        }
    }
}
