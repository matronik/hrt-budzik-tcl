package pl.hrt.budzik;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private Prefs() {}
    public static final String FILE = "hrt_settings";
    public static final String URL1 = "https://playerservices.streamtheworld.com/api/livestream-redirect/PROGRAM1AAC.aac";
    public static final String URL2 = "https://playerservices.streamtheworld.com/api/livestream-redirect/PROGRAM2AAC.aac";
    public static final String URL3 = "https://playerservices.streamtheworld.com/api/livestream-redirect/PROGRAM3AAC.aac";
    public static SharedPreferences get(Context ctx) { return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    public static String chosenUrl(Context ctx) {
        SharedPreferences p = get(ctx);
        int station = p.getInt("station", 1);
        if (station == 2) return URL2;
        if (station == 3) return URL3;
        if (station == 4) return p.getString("custom_url", "").trim();
        return URL1;
    }
    public static String stationName(Context ctx) {
        int station = get(ctx).getInt("station", 1);
        return station == 4 ? "Własny stream" : "HR" + station;
    }
}
