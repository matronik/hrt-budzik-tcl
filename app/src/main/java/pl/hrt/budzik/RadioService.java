package pl.hrt.budzik;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.PowerManager;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

public class RadioService extends Service {
    public static final String ACTION_PLAY = "pl.hrt.budzik.PLAY";
    public static final String ACTION_ALARM_PLAY = "pl.hrt.budzik.ALARM_PLAY";
    public static final String ACTION_STOP = "pl.hrt.budzik.STOP";
    private static final String CH_RADIO = "hrt_playback";
    private static final String CH_ALARM = "hrt_alarm";
    private static final int NOTIF_ID = 12345;
    private ExoPlayer player;
    private PowerManager.WakeLock cpuWake;
    private final Handler volumeHandler = new Handler(Looper.getMainLooper());
    private Runnable volumeRamp;
    private volatile int playGeneration;
    private boolean audioOnlyScheduled;
    private boolean volumeRampStarted;


    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH_RADIO, "HRT — odtwarzanie", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel alarm = new NotificationChannel(CH_ALARM, "HRT — budzik", NotificationManager.IMPORTANCE_HIGH);
        alarm.setSound(null, null);
        nm.createNotificationChannel(alarm);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            playGeneration++;
            LogBook.add(this, "Odtwarzanie zatrzymane");
            releaseResources();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean alarm = ACTION_ALARM_PLAY.equals(action);
        playGeneration++;
        audioOnlyScheduled = false;
        volumeRampStarted = false;
        try {
            startForegroundForPlayback(alarm);
            String url = Prefs.chosenUrl(this);
            if (!(url.startsWith("https://") || url.startsWith("http://"))) {
                LogBook.add(this, "BŁĄD adresu — wpisz http:// lub https://");
                stopSelf();
                return START_NOT_STICKY;
            }
            releaseResources();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            cpuWake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HRT:stream");
            cpuWake.acquire(); // Release when player is stopped/destroyed.
            player = new ExoPlayer.Builder(this).build();
            // Music/media route is intentional; hardware TV volume stays under pilot control.
            player.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true);
            final int startPercent = clamp(Prefs.get(this).getInt("volume_start", 5), 0, 100);
            final int targetPercent = clamp(Prefs.get(this).getInt("volume_target", 45), 0, 100);
            final int rampMinutes = clamp(Prefs.get(this).getInt("ramp_minutes", 10), 1, 60);
            final boolean rampEnabled = alarm && Prefs.get(this).getBoolean("ramp_enabled", true);
            // ExoPlayer volume = local gain, not the TCL hardware/system volume.
            player.setVolume((rampEnabled ? Math.min(startPercent, targetPercent) : targetPercent) / 100f);
            LogBook.add(this, "Glosnosc aplikacji: "
                    + (rampEnabled ? (startPercent + "% -> " + targetPercent + "% / " + rampMinutes + " min")
                            : (targetPercent + "% stala"))
                    + "; glosnosc TV z pilota pozostaje bez zmian");
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        LogBook.add(RadioService.this, "STREAM GOTOWY / odtwarzanie");
                        if (rampEnabled && !volumeRampStarted) {
                            volumeRampStarted = true;
                            beginVolumeRamp(player, Math.min(startPercent, targetPercent),
                                    targetPercent, rampMinutes);
                        }
                        if (alarm && !audioOnlyScheduled
                                && Prefs.get(RadioService.this).getBoolean("audio_only", false)) {
                            audioOnlyScheduled = true;
                            scheduleAudioOnlyAfterWake(playGeneration);
                        }
                    }
                    if (state == Player.STATE_ENDED) LogBook.add(RadioService.this, "Stream zakończony");
                }
                @Override public void onPlayerError(PlaybackException error) {
                    LogBook.add(RadioService.this, "BŁĄD STREAMU: " + error.getErrorCodeName() + ", " + error.getMessage());
                    stopSelf();
                }
            });
            LogBook.add(this, (alarm ? "Budzik" : "Odtwórz teraz") + ": " + Prefs.stationName(this));
            player.setMediaItem(MediaItem.fromUri(url));
            player.prepare();
            player.play();
        } catch (Exception e) {
            LogBook.add(this, "BŁĄD odtwarzacza: " + e.toString());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startForegroundForPlayback(boolean alarm) {
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, RadioService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, alarm ? CH_ALARM : CH_RADIO)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(alarm ? "Budzik HRT — " + Prefs.stationName(this) : "Radio HRT — " + Prefs.stationName(this))
                .setContentText(alarm ? "Alarm uruchomiony; odtwarzanie radia" : "Odtwarzanie internetowego radia")
                .setOngoing(true).setContentIntent(open)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "Zatrzymaj", stop).build());
        if (alarm) {
            Intent wakeScreen = new Intent(this, AlarmActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent full = PendingIntent.getActivity(this, 3, wakeScreen,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.setCategory(Notification.CATEGORY_ALARM)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setFullScreenIntent(full, true);
            LogBook.add(this, "Wysłano powiadomienie pełnoekranowe (firmware może zablokować ekran)");
        }
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /** Set ExoPlayer gain once per second. Does not alter the system's speaker volume. */
    private void beginVolumeRamp(ExoPlayer activePlayer, int begin, int end, int minutes) {
        if (begin >= end) return;
        final long durationMs = minutes * 60_000L;
        final long startAt = SystemClock.elapsedRealtime();
        volumeRamp = new Runnable() {
            @Override public void run() {
                if (player != activePlayer) return;
                long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - startAt);
                float fraction = Math.min(1f, (float) elapsed / (float) durationMs);
                activePlayer.setVolume((begin + (end - begin) * fraction) / 100f);
                if (fraction < 1f) volumeHandler.postDelayed(this, 1000L);
                else LogBook.add(RadioService.this, "Narastanie glosnosci zakonczone: " + end + "%");
            }
        };
        volumeHandler.postDelayed(volumeRamp, 1000L);
    }

    /** Wait until the stream is ready and the local ADB wake has had time to run. */
    private void scheduleAudioOnlyAfterWake(final int generation) {
        final android.content.Context app = getApplicationContext();
        LogBook.add(this, "Tylko dzwiek: zaplanowano wygaszenie ok. 5 s po starcie streamu");
        new Thread(() -> {
            try {
                Thread.sleep(5000L);
                if (playGeneration != generation) return;
                android.os.PowerManager pm = (android.os.PowerManager) app.getSystemService(POWER_SERVICE);
                if (!pm.isInteractive()) {
                    LogBook.add(app, "Tylko dzwiek: TV nadal nieinteractive; pomijam nawigacje");
                    return;
                }
                LogBook.add(app, "Tylko dzwiek: rozpoczynam sekwencje ADB menu TCL");
                LocalAdbClient.Result r = TclAudioOnly.enable(app);
                LogBook.add(app, "Tylko dzwiek: " + r.detail
                        + "; wynik obrazu do weryfikacji na ekranie TCL");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                LogBook.add(app, "Tylko dzwiek: przerwane");
            } catch (Throwable ex) {
                LogBook.add(app, "Tylko dzwiek: blad " + ex);
            }
        }, "HRT-TCL-AudioOnly").start();
    }

    private void releaseResources() {
        if (volumeRamp != null) {
            volumeHandler.removeCallbacks(volumeRamp);
            volumeRamp = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        if (cpuWake != null) {
            if (cpuWake.isHeld()) cpuWake.release();
            cpuWake = null;
        }
    }
    @Override public void onDestroy() {
        playGeneration++;
        releaseResources();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
