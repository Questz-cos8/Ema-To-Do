package com.todolife.app.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

import com.todolife.app.model.Tag;

/**
 * 统一风格的「小药丸」标签视图，卡片、详情页、筛选栏共用。
 */
public final class Chips {

    private Chips() {
    }

    public static TextView make(Context c, String text) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextSize(11f);
        tv.setGravity(Gravity.CENTER);
        tv.setIncludeFontPadding(false);
        int padH = dp(c, 9);
        int padV = dp(c, 4);
        tv.setPadding(padH, padV, padH, padV);
        tv.setTag(text);
        style(tv, c, text);
        return tv;
    }

    /** 按标签语义上色（樱粉主题） */
    public static void style(TextView tv, Context c, String tag) {
        int bg;
        int fg;
        if (Tag.REQUIRED.equals(tag)) {
            bg = 0x26EE7B92;
            fg = 0xFFC95F77;
        } else if (Tag.OPTIONAL.equals(tag)) {
            bg = 0x1F8C8493;
            fg = 0xFF6F6878;
        } else if (Tag.SCHOOL.equals(tag)) {
            bg = 0x21A99BD4;
            fg = 0xFF7A6BB0;
        } else if (Tag.PERSONAL.equals(tag)) {
            bg = 0x217FCBA6;
            fg = 0xFF4E9C79;
        } else {
            bg = 0x149B93A1;
            fg = 0xFF7A7285;
        }
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c, 9));
        d.setColor(bg);
        tv.setBackground(d);
        tv.setTextColor(fg);
    }

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
