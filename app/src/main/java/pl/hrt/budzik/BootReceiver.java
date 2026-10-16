package pl.hrt.budzik;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        LogBook.add(context, "System: " + intent.getAction());
        try {
            if (AlarmScheduler.hasExactPermission(context)) AlarmScheduler.scheduleNextWeekly(context);
            else LogBook.add(context, "Alarm tygodniowy czeka na uprawnienie alarmów");
        } catch (Exception e) {
            LogBook.add(context, "BŁĄD odtworzenia harmonogramu: " + e);
        }
    }
}
