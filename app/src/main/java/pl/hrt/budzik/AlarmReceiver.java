package pl.hrt.budzik;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

/**
 * Diagnostyczna wersja wybudzania dla Google TV / TCL.
 * Uwaga: producent telewizora moze zablokowac wybudzenie podswietlenia
 * nawet jesli odbiornik Androida otrzyma prawidlowo alarm.
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !AlarmScheduler.ACTION_ALARM.equals(intent.getAction())) return;

        long scheduledAt = Prefs.get(context).getLong("next_at", 0L);
        if (scheduledAt > 0L) {
            long lagMs = System.currentTimeMillis() - scheduledAt;
            LogBook.add(context, "ALARM OTRZYMANY; opoznienie: " + lagMs + " ms");
        } else {
            LogBook.add(context, "ALARM OTRZYMANY; brak czasu planowanego");
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        LogBook.add(context, "Przed proba wybudzenia: interactive=" + pm.isInteractive()
                + ", API=" + Build.VERSION.SDK_INT);
        PowerManager.WakeLock cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HRT:alarm-start");
        cpu.acquire(30000L);

        try {
            AlarmScheduler.onAlarmTriggered(context);

            // Android oferuje ACQUIRE_CAUSES_WAKEUP, ale flaga jest przestarzala
            // i na nowszym Androidzie moze wymagac specjalnego uprawnienia.
            // To celowo krotki, DIAGNOSTYCZNY test, nie docelowa metoda produkcyjna.
            if (!pm.isInteractive()) {
                try {
                    @SuppressWarnings("deprecation")
                    PowerManager.WakeLock displayWake = pm.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "HRT:alarm-display-test");
                    displayWake.acquire(15000L); // Automatyczne zwolnienie po 15 sekundach.
                    LogBook.add(context, "Proba ACQUIRE_CAUSES_WAKEUP; interactive=" + pm.isInteractive());
                } catch (RuntimeException ex) {
                    LogBook.add(context, "Nie udalo sie ACQUIRE_CAUSES_WAKEUP: " + ex);
                }
            }

            // Uruchom radio niezaleznie od powodzenia wybudzenia.
            Intent service = new Intent(context, RadioService.class)
                    .setAction(RadioService.ACTION_ALARM_PLAY);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
            LogBook.add(context, "Zadano uruchomienia RadioService");

            // Dodatkowa proba otwarcia okna budzika. Android 10+ moze
            // zablokowac start aktywnosci z tla mimo odebranego alarmu.
            try {
                Intent ui = new Intent(context, AlarmActivity.class);
                ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(ui);
                LogBook.add(context, "Zadano bezposredniego otwarcia AlarmActivity");
            } catch (RuntimeException ex) {
                LogBook.add(context, "Nie udalo sie otworzyc AlarmActivity: " + ex);
            }
            LogBook.add(context, "Po probach wybudzenia: interactive=" + pm.isInteractive());
        } catch (RuntimeException ex) {
            LogBook.add(context, "BLAD alarmu: " + ex);
        }
        // Pozostawamy poza watkiem UI i poza watkiem BroadcastReceiver:
        // lokalny ADB uzywa socketu i moze czekac na autoryzacje.
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        Thread wakeThread = new Thread(() -> {
            try {
                LogBook.add(app, "Lokalny ADB: proba wyslania KEYCODE_WAKEUP przez 127.0.0.1:5555");
                LocalAdbClient.Result result = LocalAdbClient.execute(app, "input keyevent 224", 5000);
                LogBook.add(app, "Lokalny ADB: " + result);
                try { Thread.sleep(1200L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                PowerManager power = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                LogBook.add(app, "1200 ms po lokalnym ADB: interactive=" + power.isInteractive());
            } catch (Throwable ex) {
                LogBook.add(app, "Lokalny ADB nieudany: " + ex);
            } finally {
                pending.finish();
            }
        }, "HRT-LocalADB-Wakeup");
        wakeThread.start();
        // CPU wake lock ma 30-sekundowy timeout, dlatego procesor moze dokonczyc ADB.

    }
}
