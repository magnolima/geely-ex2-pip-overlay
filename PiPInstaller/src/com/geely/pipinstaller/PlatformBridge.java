package com.geely.pipinstaller;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import java.io.*;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class PlatformBridge {
    static final String OVERLAY = "com.geely.ex2.mica.pipoverlayprobe";
    static final String SYSTEM_UI = "com.android.systemui";
    static final String SYSTEM_HASH = "0C9AB49B1CCF83133B3CBCAB1CC1D980E97C6472FE9D06F65E2BEF07D0737332";
    static final String OVERLAY_HASH = "47F434857D7F2E175ECED4F591342667AA31B91A61636EEE0FFF19BAE68EF725";
    private final Context context;
    private final Class<?> overlayInterface;
    private final Object overlayManager;

    PlatformBridge(Context context) throws Exception {
        this.context = context;
        if (Build.VERSION.SDK_INT != 28) throw new IOException("Esta versão foi preparada para Android 9.");
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(SYSTEM_UI, 0);
        if (android.os.Process.myUid() / 100000 != 0)
            throw new IOException("Instale e abra o app no usuário principal da central.");
        if (info.uid != android.os.Process.myUid())
            throw new IOException("A assinatura ou identidade do app não corresponde à SystemUI.");
        for (String permission : new String[]{"android.permission.INSTALL_PACKAGES",
                "android.permission.CHANGE_OVERLAY_PACKAGES", "android.permission.DUMP"}) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
                throw new IOException("Permissão de sistema indisponível: " + permission);
        }
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "overlay");
        if (binder == null) throw new IOException("Gerenciador de overlays indisponível.");
        overlayInterface = Class.forName("android.content.om.IOverlayManager");
        overlayManager = Class.forName("android.content.om.IOverlayManager$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    void checkCompatibility() throws Exception {
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(SYSTEM_UI, 0);
        try (InputStream input = new FileInputStream(info.sourceDir)) {
            if (!SYSTEM_HASH.equals(sha256(input)))
                throw new IOException("Esta versão da central ainda não foi validada. Nenhuma alteração foi feita.");
        }
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
            throw new IOException("A central não declara suporte ao PiP.");
        try (InputStream input = context.getAssets().open("pip-overlay-platform.apk")) {
            if (!OVERLAY_HASH.equals(sha256(input))) throw new IOException("O overlay incorporado não passou na verificação.");
        }
    }

    boolean installed() {
        try { context.getPackageManager().getPackageInfo(OVERLAY, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    void checkInstalledOverlay() throws Exception {
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(OVERLAY, 0);
        try (InputStream input = new FileInputStream(info.sourceDir)) {
            if (!OVERLAY_HASH.equals(sha256(input)))
                throw new IOException("Há outra versão do overlay instalada. Não será substituída automaticamente.");
        }
    }

    boolean enabled() throws Exception {
        Object info = overlayInterface.getMethod("getOverlayInfo", String.class, int.class)
                .invoke(overlayManager, OVERLAY, 0);
        return info != null && (Boolean) info.getClass().getMethod("isEnabled").invoke(info);
    }

    void setEnabled(boolean value) throws Exception {
        boolean accepted = (Boolean) overlayInterface.getMethod("setEnabled", String.class, boolean.class, int.class)
                .invoke(overlayManager, OVERLAY, value, 0);
        if (!accepted || enabled() != value) throw new IOException("O sistema não confirmou a alteração do overlay.");
    }

    int systemUiPid() {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (SYSTEM_UI.equals(process.processName) && process.uid == android.os.Process.myUid())
                return process.pid;
        }
        return -1;
    }

    void restartAndVerify(boolean pipExpected) throws Exception {
        int oldPid = systemUiPid();
        if (oldPid <= 0 || oldPid == android.os.Process.myPid())
            throw new IOException("Não foi possível identificar a SystemUI para reiniciá-la.");
        android.os.Process.killProcess(oldPid);
        int currentPid = -1;
        for (int attempt = 0; attempt < 10; attempt++) {
            Thread.sleep(700);
            currentPid = systemUiPid();
            if (currentPid > 0 && currentPid != oldPid) break;
        }
        if (currentPid <= 0 || currentPid == oldPid)
            throw new IOException("A SystemUI não reiniciou automaticamente. Reinicie a central após desativar a correção.");
        Thread.sleep(1200);
        String dump = dumpSystemUi();
        boolean loaded = dump.contains("dumping service: com.android.systemui.pip.PipUI")
                && dump.contains("PipManager");
        if (dump.trim().isEmpty() || !dump.contains("com.android.systemui/.SystemUIService"))
            throw new IOException("Não foi possível verificar a SystemUI.");
        if (loaded != pipExpected) throw new IOException("A SystemUI não carregou a configuração esperada.");
        Thread.sleep(1000);
        if (systemUiPid() != currentPid) throw new IOException("A SystemUI reiniciou durante a verificação.");
    }

    private String dumpSystemUi() throws Exception {
        File output = new File(context.getCacheDir(), "systemui-check.txt");
        java.lang.Process process = new ProcessBuilder("/system/bin/dumpsys", "activity", "service",
                "com.android.systemui/.SystemUIService").redirectErrorStream(true).redirectOutput(output).start();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Tempo limite ao verificar a SystemUI.");
        }
        if (process.exitValue() != 0) throw new IOException("O sistema recusou a verificação da SystemUI.");
        try (InputStream input = new FileInputStream(output); ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1 && result.size() < 1024 * 1024) result.write(buffer, 0, count);
            return result.toString("UTF-8");
        }
    }

    static String sha256(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[32768]; int count;
        while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02X", value & 255));
        return result.toString();
    }
}
