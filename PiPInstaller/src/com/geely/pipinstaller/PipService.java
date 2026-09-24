package com.geely.pipinstaller;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.os.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PipService extends Service {
    static final String ENABLE = "enable", DISABLE = "disable", REFRESH = "refresh", RECOVER = "recover", INSTALLED = "installed";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    static SharedPreferences state(Context c) { return c.getSharedPreferences("pip-control", MODE_PRIVATE); }
    static void launch(Context c, String action) {
        c.startForegroundService(new Intent(c, PipService.class).setAction(action));
    }
    private SharedPreferences prefs;

    @Override public void onCreate() {
        super.onCreate();
        prefs = state(this);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("operation", "Configuração do PiP", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, new Notification.Builder(this, "operation").setSmallIcon(R.drawable.ic_pip)
                .setContentTitle("PiP Control").setContentText("Verificando a configuração da central")
                .setContentIntent(open).setOngoing(true).build());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? RECOVER : intent.getAction();
        worker.execute(() -> {
            try {
                if (INSTALLED.equals(action)) {
                    if (intent.getIntExtra("session", -2) != prefs.getInt("session", -1) || !prefs.getBoolean("pending", false)) return;
                    if (intent.getIntExtra("status", PackageInstaller.STATUS_FAILURE) != PackageInstaller.STATUS_SUCCESS)
                        throw new IOException("O Android não autorizou a instalação automática do overlay. " + intent.getStringExtra("detail"));
                    prefs.edit().putInt("session", -1).commit();
                    activate(new PlatformBridge(this));
                } else if (RECOVER.equals(action)) {
                    recover("A operação anterior foi interrompida ou excedeu o tempo limite.");
                } else if (prefs.getBoolean("pending", false)) {
                    // A instalacao assincrona ja tem uma operacao em andamento.
                    return;
                } else if (ENABLE.equals(action)) {
                    begin();
                    PlatformBridge bridge = new PlatformBridge(this);
                    status("Verificando compatibilidade…");
                    bridge.checkCompatibility();
                    if (bridge.installed()) {
                        bridge.checkInstalledOverlay();
                        if (bridge.enabled()) { complete(true, "PiP já está habilitado."); return; }
                        activate(bridge);
                    } else {
                        status("Instalando o recurso de PiP…");
                        installOverlay();
                    }
                } else if (DISABLE.equals(action)) {
                    begin();
                    PlatformBridge bridge = new PlatformBridge(this);
                    if (!bridge.installed()) { complete(false, "A correção não está instalada."); return; }
                    // Reversao permitida mesmo quando o hash da SystemUI mudou.
                    prefs.edit().putBoolean("rollback", true).commit();
                    bridge.setEnabled(false);
                    status("Aplicando a desativação…");
                    bridge.restartAndVerify(false);
                    complete(false, "PiP desabilitado. A configuração original foi restaurada.");
                } else {
                    inspect();
                }
            } catch (Exception e) {
                android.util.Log.e("PiPControl", "Operation failed", e);
                if (prefs.getBoolean("pending", false) || prefs.getBoolean("rollback", false)) recover(message(e));
                else {
                    prefs.edit().putBoolean("busy", false).putBoolean("compatible", false)
                            .putString("message", message(e)).apply();
                }
            } finally {
                if (!prefs.getBoolean("pending", false)) {
                    stopForeground(true);
                    stopSelf(startId);
                }
            }
        });
        return START_NOT_STICKY;
    }

    private void inspect() throws Exception {
        prefs.edit().putBoolean("busy", true).apply();
        if (prefs.getBoolean("rollback", false)) { recover("Há uma recuperação pendente."); return; }
        PlatformBridge bridge = new PlatformBridge(this);
        boolean enabled = bridge.enabled();
        prefs.edit().putBoolean("enabled", enabled).putBoolean("privileged", true).apply();
        try {
            bridge.checkCompatibility();
            if (bridge.installed()) bridge.checkInstalledOverlay();
            prefs.edit().putBoolean("compatible", true).putString("message", enabled
                    ? "Correção habilitada nesta central."
                    : "Central compatível. Pronto para ativar o PiP.").apply();
        } catch (Exception e) {
            prefs.edit().putBoolean("compatible", false).putString("message", message(e)).apply();
        }
        prefs.edit().putBoolean("busy", false).apply();
    }

    private void begin() {
        if (!prefs.edit().putBoolean("pending", true).putBoolean("busy", true).commit())
            throw new IllegalStateException("Não foi possível salvar o estado de recuperação.");
        getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 90000, recoveryIntent());
    }

    private PendingIntent recoveryIntent() {
        return PendingIntent.getBroadcast(this, 2, new Intent(this, RecoveryReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void installOverlay() throws Exception {
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(PlatformBridge.OVERLAY);
        int id = installer.createSession(params);
        prefs.edit().putInt("session", id).commit();
        try (PackageInstaller.Session session = installer.openSession(id);
             InputStream input = getAssets().open("pip-overlay-platform.apk");
             OutputStream output = session.openWrite("base.apk", 0, -1)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            session.fsync(output);
        }
        try (PackageInstaller.Session session = installer.openSession(id)) {
            Intent callback = new Intent(this, InstallReceiver.class).setAction("com.geely.pipinstaller.INSTALL_RESULT");
            PendingIntent result = PendingIntent.getBroadcast(this, id, callback, PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(result.getIntentSender());
        }
    }

    private void activate(PlatformBridge bridge) throws Exception {
        bridge.checkCompatibility();
        bridge.checkInstalledOverlay();
        // Gravado antes da mutacao para permitir recuperacao apos morte do processo/boot.
        if (!prefs.edit().putBoolean("rollback", true).commit()) throw new IOException("Falha ao preparar recuperação.");
        status("Ativando PiP. A interface vai reiniciar brevemente…");
        bridge.setEnabled(true);
        bridge.restartAndVerify(true);
        complete(true, "PiP habilitado. Abra um vídeo e teste os controles da janela.");
    }

    private void complete(boolean enabled, String message) {
        prefs.edit().putBoolean("enabled", enabled).putBoolean("pending", false)
                .putBoolean("rollback", false).putBoolean("busy", false).putInt("session", -1)
                .putString("message", message).commit();
        getSystemService(AlarmManager.class).cancel(recoveryIntent());
    }

    private void recover(String reason) {
        if (!prefs.getBoolean("pending", false) && !prefs.getBoolean("rollback", false)) return;
        int session = prefs.getInt("session", -1);
        if (session >= 0) try { getPackageManager().getPackageInstaller().abandonSession(session); } catch (Exception ignored) { }
        boolean needsRollback = prefs.getBoolean("rollback", false);
        try {
            PlatformBridge bridge = new PlatformBridge(this);
            if (needsRollback) {
                status("Restaurando a configuração original…");
                bridge.setEnabled(false);
                bridge.restartAndVerify(false);
            }
            complete(bridge.enabled(), reason + (needsRollback ? " A correção foi desativada por segurança." : " O overlay não foi ativado."));
        } catch (Exception failure) {
            android.util.Log.e("PiPControl", "Recovery failed", failure);
            prefs.edit().putBoolean("pending", false).putBoolean("busy", false).putBoolean("rollback", needsRollback)
                    .putBoolean("compatible", false).putInt("session", -1).putString("message",
                    reason + " Recuperação não confirmada: " + message(failure) + " Use Disable PiP ou reinicie a central.").commit();
            // Uma nova tentativa pode ser feita pelo usuario; o boot tambem verifica rollback.
            getSystemService(AlarmManager.class).cancel(recoveryIntent());
        }
    }

    private void status(String text) { prefs.edit().putString("message", text).apply(); }
    private static String message(Throwable e) {
        while (e.getCause() != null) e = e.getCause();
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
