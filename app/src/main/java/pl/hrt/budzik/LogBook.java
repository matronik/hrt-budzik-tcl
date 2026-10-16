package pl.hrt.budzik;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class LogBook {
    private LogBook() {}
    public static synchronized void add(Context ctx, String message) {
        String line = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(new Date()) + "  " + message;
        Log.i("HRT_BUDZIK", line);
        SharedPreferences p = Prefs.get(ctx);
        String old = p.getString("log", "");
        String all = line + "\n" + old;
        if (all.length() > 9000) all = all.substring(0, 9000);
        p.edit().putString("log", all).apply();
    }
}
