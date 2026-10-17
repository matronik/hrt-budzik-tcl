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
    private EditText volumeStart, volumeTarget, rampMinutes;
    private CheckBox rampEnabled, audioOnly;
    private TextView statusText, logText;
    private CheckBox[] days;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        radioGroup = findViewById(R.id.radioStations);
        customUrl = findViewById(R.id.customUrl);
        timeText = findViewById(R.id.timeText);
        volumeStart = findViewById(R.id.volumeStart);
        volumeTarget = findViewById(R.id.volumeTarget);
        rampMinutes = findViewById(R.id.rampMinutes);
        rampEnabled = findViewById(R.id.rampEnabled);
        audioOnly = findViewById(R.id.audioOnly);
        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);
        days = new CheckBox[] {
                findViewById(R.id.day1), findViewById(R.id.day2), findViewById(R.id.day3),
                findViewById(R.id.day4), findViewById(R.id.day5), findViewById(R.id.day6), findViewById(R.id.day7)
        };
        restoreSettings();
        findViewById(R.id.btnPlay).setOnClickListener(v -> {
            if (!storeStation() || !saveAlarmOptions()) return;
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
        findViewById(R.id.btnSaveOptions).setOnClickListener(v -> {
            if (storeStation() && saveAlarmOptions()) message("Ustawienia budzika zapisane");
            refresh();
        });
        findViewById(R.id.btnTestAudioOnly).setOnClickListener(v -> {
            if (!saveAlarmOptions()) return;
            message("Test: TCL za ok. 9 sekund wygasi ekran. Radio musi juz grac.");
            new Thread(() -> {
                LogBook.add(getApplicationContext(), "RECZNY TEST Tylko dzwiek: start");
                LocalAdbClient.Result r = TclAudioOnly.enable(getApplicationContext());
                LogBook.add(getApplicationContext(), "RECZNY TEST Tylko dzwiek: " + r.detail);
                runOnUiThread(this::refresh);
            }, "HRT-Test-AudioOnly").start();
        });
        findViewById(R.id.btnLocalAdb).setOnClickListener(v -> {
            message("Laczenie ADB do 127.0.0.1:5555. Jesli wyskoczy pytanie, zaznacz Zawsze zezwalaj.");
            new Thread(() -> {
                LocalAdbClient.Result r = LocalAdbClient.execute(
                        getApplicationContext(), "getprop ro.build.version.sdk", 11000);
                LogBook.add(getApplicationContext(), "TEST lokalnego ADB: " + r);
                runOnUiThread(() -> {
                    message(r.ok ? "Lokalny ADB autoryzowany — mozna testowac budzik" : "Lokalny ADB: sprawdz dziennik i okno autoryzacji");
                    refresh();
                });
            }, "HRT-ADB-Authorize").start();
        });
        statusText.setOnClickListener(v -> refresh());
        logText.setOnClickListener(v -> refresh());
        refresh();
    }

    private void restoreSettings() {
        SharedPreferences p = Prefs.get(this);
        int station = p.getInt("station", 1);
        radioGroup.check(station == 2 ? R.id.radio2 : station == 3 ? R.id.radio3 : station == 4 ? R.id.radioCustom : R.id.radio1);
        customUrl.setText(p.getString("custom_url", ""));
        volumeStart.setText(Integer.toString(p.getInt("volume_start", 5)));
        volumeTarget.setText(Integer.toString(p.getInt("volume_target", 45)));
        rampMinutes.setText(Integer.toString(p.getInt("ramp_minutes", 10)));
        rampEnabled.setChecked(p.getBoolean("ramp_enabled", true));
        audioOnly.setChecked(p.getBoolean("audio_only", false));
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
    private boolean saveAlarmOptions() {
        try {
            int begin = Integer.parseInt(volumeStart.getText().toString().trim());
            int end = Integer.parseInt(volumeTarget.getText().toString().trim());
            int minutes = Integer.parseInt(rampMinutes.getText().toString().trim());
            if (begin < 0 || begin > 100 || end < 1 || end > 100
                    || minutes < 1 || minutes > 60 || begin > end) {
                message("Podaj start 0-100%, cel 1-100% (cel >= start), czas 1-60 min");
                return false;
            }
            Prefs.get(this).edit()
                    .putInt("volume_start", begin)
                    .putInt("volume_target", end)
                    .putInt("ramp_minutes", minutes)
                    .putBoolean("ramp_enabled", rampEnabled.isChecked())
                    .putBoolean("audio_only", audioOnly.isChecked())
                    .apply();
            return true;
        } catch (NumberFormatException ex) {
            message("Glosnosc i czas musza byc liczbami calkowitymi");
            return false;
        }
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
        if (!storeStation() || !saveAlarmOptions()) return;
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
        if (!storeStation() || !saveAlarmOptions()) return;
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
                + "\nGlosnosc aplikacji: "
                + (p.getBoolean("ramp_enabled", true)
                ? (p.getInt("volume_start", 5) + "% -> " + p.getInt("volume_target", 45)
                    + "% / " + p.getInt("ramp_minutes", 10) + " min")
                : (p.getInt("volume_target", 45) + "% stala"))
                + "\nTylko dzwiek po alarmie: " + (p.getBoolean("audio_only", false) ? "TAK" : "nie")
                + "\nDOTKNIJ STATUSU, BY ODŚWIEŻYĆ.";
        statusText.setText(status);
        logText.setText(p.getString("log", "Brak zdarzeń"));
    }
}
