package com.geely.pipinstaller;

import android.content.*;

public final class RecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        SharedPreferences state = PipService.state(context);
        if (state.getBoolean("pending", false) || state.getBoolean("rollback", false))
            PipService.launch(context, PipService.RECOVER);
    }
}
