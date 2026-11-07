package com.meujogo.serverhop;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Pequenos construtores de visual (tema escuro, cartões, botões e chips) usados pela tela e pelo painel. */
final class Ui {

    static final int BG = 0xFF0B1220;
    static final int CARD = 0xFF141E2E;
    static final int STROKE = 0xFF24344D;
    static final int TEXT = 0xFFE8EEF7;
    static final int MUTED = 0xFF93A4BC;
    static final int ACCENT = 0xFF2DD4A8;
    static final int ACCENT_DARK = 0xFF0E4F43;
    static final int ON_ACCENT = 0xFF04201A;
    static final int WARN = 0xFFFFB547;
    static final int DANGER = 0xFFFF6B6B;
    static final int CHIP_OFF = 0xFF1B283B;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable shape(Context c, int fill, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) {
            g.setStroke(Math.max(1, dp(c, strokeDp)), stroke);
        }
        return g;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    /** Parâmetros de linha/coluna com margens em dp. */
    static LinearLayout.LayoutParams params(Context c, int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        l.setBackground(shape(c, CARD, STROKE, 1, 18));
        return l;
    }

    static void paintButton(TextView t, Context c, int fill, int textColor) {
        t.setTextColor(textColor);
        GradientDrawable bg = shape(c, fill, 0, 0, 14);
        GradientDrawable mask = shape(c, 0xFFFFFFFF, 0, 0, 14);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), bg, mask));
    }

    static TextView button(Context c, String label, int fill, int textColor, View.OnClickListener l) {
        TextView t = text(c, label, 15, textColor, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        paintButton(t, c, fill, textColor);
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    static TextView chip(Context c, String label, View.OnClickListener l) {
        TextView t = text(c, label, 14, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        t.setClickable(true);
        t.setOnClickListener(l);
        setChip(t, c, false);
        return t;
    }

    static void setChip(TextView t, Context c, boolean selected) {
        t.setTextColor(selected ? ON_ACCENT : TEXT);
        t.setBackground(selected ? shape(c, ACCENT, 0, 0, 12) : shape(c, CHIP_OFF, STROKE, 1, 12));
    }
}
