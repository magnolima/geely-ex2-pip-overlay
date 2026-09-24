package com.geely.pipinstaller;

import android.content.*;
import android.content.pm.PackageInstaller;

public final class InstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        context.startForegroundService(new Intent(context, PipService.class).setAction(PipService.INSTALLED)
                .putExtra("session", intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -2))
                .putExtra("status", intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE))
                .putExtra("detail", intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)));
    }
}
