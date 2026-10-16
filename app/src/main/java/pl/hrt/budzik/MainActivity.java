package pl.hrt.budzik;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private RadioGroup radioGroup;
    private EditText customUrl, timeText;
    private TextView statusText, logText;
    private CheckBox[] days;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        radioGroup = findViewById(R.id.radioStations);
        customUrl = findViewById(R.id.customUrl);
        timeText = findViewById(R.id.timeText);
        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);
        days = new CheckBox[] {
                findViewById(R.id.day1), findViewById(R.id.day2), findViewById(R.id.day3),
                findViewById(R.id.day4), findViewById(R.id.day5), findViewById(R.id.day6), findViewById(R.id.day7)
        };
        restoreSettings();
        findViewById(R.id.btnPlay).setOnClickListener(v -> {
            if (!storeStation()) return;
            launchRadio(RadioService.ACTION_PLAY);
            refresh();
        });
        findViewById(R.id.btnStop).setOnClickListener(v -> {
            startService(new Intent(this, RadioService.class).setAction(RadioService.ACTION_STOP));
            refresh();
        });
        findViewById(R.id.btnTest2).setOnClickListener(v -> testAfter(2));
        findViewById(R.id.btnTest15).setOnClickListener(v -> testAfter(15));
        findViewById(R.id.btnSchedule).setOnClickListener(v -> scheduleWeekly());
        findViewById(R.id.btnCancel).setOnClickListener(v -> {
            AlarmScheduler.cancel(this);
            message("Alarm anulowany");
            refresh();
        });
        findViewById(R.id.btnPermissions).setOnClickListener(v -> requestPermissionsForAlarm());
        statusText.setOnClickListener(v -> refresh());
        logText.setOnClickListener(v -> refresh());
        refresh();
    }

    private void restoreSettings() {
        SharedPreferences p = Prefs.get(this);
        int station = p.getInt("station", 1);
        radioGroup.check(station == 2 ? R.id.radio2 : station == 3 ? R.id.radio3 : station == 4 ? R.id.radioCustom : R.id.radio1);
        customUrl.setText(p.getString("custom_url", ""));
        timeText.setText(String.format(Locale.ROOT, "%02d:%02d", p.getInt("hour", 6), p.getInt("minute", 30)));
        int daysMask = p.getInt("days", 31); // Monday through Friday.
        for (int i = 0; i < days.length; i++) days[i].setChecked((daysMask & (1 << i)) != 0);
    }

    private boolean storeStation() {
        int id = radioGroup.getCheckedRadioButtonId();
        int station = id == R.id.radio2 ? 2 : id == R.id.radio3 ? 3 : id == R.id.radioCustom ? 4 : 1;
        String url = customUrl.getText().toString().trim();
        if (station == 4 && !(url.startsWith("http://") || url.startsWith("https://"))) {
            message("Adres radia musi zaczynać się od http:// lub https://");
            return false;
        }
        Prefs.get(this).edit().putInt("station", station).putString("custom_url", url).apply();
        return true;
    }
    private void launchRadio(String action) {
        Intent i = new Intent(this, RadioService.class).setAction(action);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ex) {
            LogBook.add(this, "BŁĄD startu odtwarzacza: " + ex);
            message("Nie można uruchomić odtwarzacza — sprawdź dziennik");
        }
    }
    private void testAfter(int minutes) {
        if (!storeStation()) return;
        if (!AlarmScheduler.hasExactPermission(this)) {
            message("Najpierw nadaj uprawnienie: Alarmy i przypomnienia");
            requestPermissionsForAlarm();
            return;
        }
        try {
            AlarmScheduler.setTest(this, minutes);
            message("Test za " + minutes + " min. Wyłącz TV krótkim naciśnięciem Power.");
        } catch (Exception ex) {
            LogBook.add(this, "BŁĄD alarmu: " + ex);
            message("Nie udało się ustawić alarmu");
        }
        refresh();
    }
    private void scheduleWeekly() {
        if (!storeStation()) return;
        String value = timeText.getText().toString().trim();
        if (!value.matches("\\d{1,2}:\\d{2}")) {
            message("Wpisz godzinę HH:mm, np. 06:30"); return;
        }
        String[] parts = value.split(":");
        int h = Integer.parseInt(parts[0]), m = Integer.parseInt(parts[1]);
        if (h > 23 || m > 59) { message("Nieprawidłowa godzina"); return; }
        int mask = 0;
        for (int i = 0; i < days.length; i++) if (days[i].isChecked()) mask |= 1 << i;
        if (mask == 0) { message("Zaznacz przynajmniej jeden dzień tygodnia"); return; }
        if (!AlarmScheduler.hasExactPermission(this)) {
            message("Nadaj uprawnienie: Alarmy i przypomnienia");
            requestPermissionsForAlarm(); return;
        }
        try {
            if (AlarmScheduler.setWeekly(this, h, m, mask)) message("Ustawiono budzik tygodniowy");
            else message("Nie udało się ustawić budzika");
        } catch (Exception ex) {
            LogBook.add(this, "BŁĄD tygodniowego: " + ex);
            message("Błąd harmonogramu — sprawdź dziennik");
        }
        refresh();
    }
    private void requestPermissionsForAlarm() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ Manifest.permission.POST_NOTIFICATIONS }, 100);
            message("Zezwól na powiadomienia; potem znów wybierz Uprawnienia");
            return;
        }
        if (Build.VERSION.SDK_INT >= 31 && !AlarmScheduler.hasExactPermission(this)) {
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
                startActivity(i);
                return;
            } catch (Exception e) { LogBook.add(this, "Brak ekranu alarmów w firmware: " + e); }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (!nm.canUseFullScreenIntent()) {
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                    return;
                } catch (Exception e) { LogBook.add(this, "Brak ekranu pełnoekranowych alarmów: " + e); }
            }
        }
        message("Uprawnienia alarmowe: OK. Można wykonać test.");
    }
    private void message(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override protected void onResume() { super.onResume(); if (statusText != null) refresh(); }
    private void refresh() {
        SharedPreferences p = Prefs.get(this);
        long next = p.getLong("next_at", 0);
        boolean weekly = p.getBoolean("weekly_enabled", false);
        long fired = p.getLong("last_fired", 0);
        boolean exact = AlarmScheduler.hasExactPermission(this);
        String perm = exact ? "tak" : "NIE — nadaj w Uprawnienia";
        String status = "Dokładne alarmy: " + perm
                + "\nTryb: " + (next == 0 ? "brak zaplanowanego alarmu" : weekly ? "budzik tygodniowy" : "test jednorazowy")
                + "\nNastępny alarm: " + AlarmScheduler.format(next)
                + "\nOstatni alarm odebrany przez Android: " + AlarmScheduler.format(fired)
                + "\nWybrana stacja: " + Prefs.stationName(this)
                + "\nDOTKNIJ STATUSU, BY ODŚWIEŻYĆ.";
        statusText.setText(status);
        logText.setText(p.getString("log", "Brak zdarzeń"));
    }
}
