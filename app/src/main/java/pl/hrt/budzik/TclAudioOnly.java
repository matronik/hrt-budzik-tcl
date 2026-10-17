package pl.hrt.budzik;

import android.content.Context;

/**
 * TCL 55C6KS, Google TV UI 1920x1080. Sequence experimentally verified on THIS TV.
 * Keep it opt-in: a firmware/UI change might make a tap land on another setting.
 * No user-supplied commands are ever executed; LocalAdbClient has an allowlist.
 */
public final class TclAudioOnly {
    private TclAudioOnly() {}

    static final String ADB_SEQUENCE =
            "input keyevent 3; sleep 1; "
            + "am start -n com.android.tv.settings/.MainSettings; sleep 2; "
            + "input tap 500 510; sleep 2; "
            + "input tap 338 578; sleep 2; "
            + "input tap 338 563; sleep 2; "
            + "input tap 338 596";

    /** Call off main thread; a successful ADB shell response does not verify screen state. */
    public static LocalAdbClient.Result enable(Context ctx) {
        return LocalAdbClient.execute(ctx.getApplicationContext(), ADB_SEQUENCE, 16000);
    }
}
