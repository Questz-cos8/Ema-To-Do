package com.todolife.app.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/**
 * 少量常用的 View 样式助手，避免每处都写一遍 setBackgroundTintList。
 */
public final class Ui {

    private Ui() {
    }

    public static void bg(TextView v, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(v.getResources().getDisplayMetrics().density * 18);
        d.setColor(color);
        v.setBackground(d);
    }

    public static void buttonBg(Button b, int color) {
        b.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    public static void buttonOutline(Button b, int color) {
        b.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        b.setTextColor(color);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(b.getResources().getDisplayMetrics().density * 19);
        d.setStroke((int) b.getResources().getDisplayMetrics().density, color);
        b.setBackground(d);
    }

    public static void tint(ProgressBar p, int color) {
        p.setProgressTintList(ColorStateList.valueOf(color));
    }

    public static void alpha(ImageView v, int alpha) {
        v.setImageAlpha(Math.max(0, Math.min(255, alpha)));
    }

    public static int color(Context c, int res) {
        return ContextCompat.getColor(c, res);
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
