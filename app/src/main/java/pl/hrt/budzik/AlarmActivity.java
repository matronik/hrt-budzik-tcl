package pl.hrt.budzik;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;

public class AlarmActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_alarm);
        LogBook.add(this, "ALARM ACTIVITY WIDOCZNA — ekran wybudzony");
        Button stop = findViewById(R.id.stopAlarm);
        stop.requestFocus();
        stop.setOnClickListener(v -> {
            startService(new Intent(this, RadioService.class).setAction(RadioService.ACTION_STOP));
            finish();
        });
    }
}
