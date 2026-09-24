package com.geely.pipinstaller;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private TextView badge, description;
    private Button enable, disable;
    private ProgressBar progress;
    private final Runnable render = new Runnable() {
        @Override public void run() { update(); handler.postDelayed(this, 700); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(12, 22, 28));
        getWindow().setNavigationBarColor(Color.rgb(12, 22, 28));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(12, 22, 28));
        LinearLayout outer = new LinearLayout(this);
        outer.setGravity(Gravity.CENTER);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(28), dp(24), dp(28), dp(24));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(30), dp(26), dp(30), dp(26));
        card.setBackground(background(0xff172b33, 24));
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(56), dp(760));
        outer.addView(card, new LinearLayout.LayoutParams(width, -2));
        card.addView(text("PiP Control", 32, 0xfff1f8f6, true));
        TextView subtitle = text("Vídeos em uma janela que acompanha você.", 18, 0xffafc5cd, false);
        subtitle.setPadding(0, dp(8), 0, dp(22)); card.addView(subtitle);
        badge = text("VERIFICANDO", 14, 0xff66e0ba, true); card.addView(badge);
        description = text("Verificando a central…", 18, 0xfff1f8f6, false);
        description.setMinHeight(dp(76)); description.setPadding(0, dp(12), 0, dp(14)); card.addView(description);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true); card.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(0, dp(20), 0, dp(16));
        enable = button("Enable PiP", 0xff66e0ba, 0xff102a31);
        disable = button("Disable PiP", 0xff29434e, 0xfff1f8f6);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(64), 1);
        left.setMargins(0, 0, dp(12), 0);
        buttons.addView(enable, left); buttons.addView(disable, new LinearLayout.LayoutParams(0, dp(64), 1));
        card.addView(buttons);
        TextView note = text("Use com o veículo parado. A interface da central reinicia brevemente ao aplicar uma alteração.", 14, 0xffafc5cd, false);
        card.addView(note);
        TextView footer = text("PiP Control 1.0 · Geely IHU629G · Android 9", 12, 0xff7e9ba6, false);
        footer.setPadding(0, dp(20), 0, 0); card.addView(footer);
        scroll.addView(outer); setContentView(scroll);
        enable.setOnClickListener(view -> act(PipService.ENABLE));
        disable.setOnClickListener(view -> act(PipService.DISABLE));
        update();
    }

    private void act(String action) {
        enable.setEnabled(false); disable.setEnabled(false);
        PipService.launch(this, action);
    }
    @Override protected void onResume() {
        super.onResume();
        if (!PipService.state(this).getBoolean("pending", false)) PipService.launch(this, PipService.REFRESH);
        handler.post(render);
    }
    @Override protected void onPause() { handler.removeCallbacks(render); super.onPause(); }
    private void update() {
        SharedPreferences s = PipService.state(this);
        boolean busy = s.getBoolean("busy", true), enabled = s.getBoolean("enabled", false);
        boolean compatible = s.getBoolean("compatible", false), recovery = s.getBoolean("rollback", false);
        badge.setText(busy ? "EM ANDAMENTO" : recovery ? "RECUPERAÇÃO NECESSÁRIA" : enabled ? "PIP HABILITADO" : compatible ? "PRONTO PARA ATIVAR" : "INDISPONÍVEL");
        description.setText(s.getString("message", "Verificando a central…"));
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        enable.setEnabled(!busy && compatible && !enabled && !recovery);
        disable.setEnabled(!busy && s.getBoolean("privileged", false) && (enabled || recovery));
        enable.setAlpha(enable.isEnabled() ? 1f : .4f); disable.setAlpha(disable.isEnabled() ? 1f : .4f);
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private Button button(String label, int background, int foreground) {
        Button view = new Button(this); view.setText(label); view.setTextSize(18); view.setAllCaps(false);
        view.setTextColor(foreground); view.setBackground(background(background, 14)); return view;
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
