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
            LogBook.add(this, "Odtwarzanie zatrzymane");
            releaseResources();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean alarm = ACTION_ALARM_PLAY.equals(action);
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
            player.setVolume(0.6f); // Prevent unexpectedly loud alarms; 60% of device playback gain.
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) LogBook.add(RadioService.this, "STREAM GOTOWY / odtwarzanie");
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

    private void releaseResources() {
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
        releaseResources();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
