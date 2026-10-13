package com.todolife.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 全局外观设置：整体背景图 + 背景透明度 + 卡片默认透明度。
 */
public final class Prefs {

    private static final String NAME = "todolife_prefs_v1";
    public static final String KEY_BG_IMAGE = "bg_image";
    public static final String KEY_BG_ALPHA = "bg_alpha";
    public static final String KEY_CARD_ALPHA = "card_alpha";
    public static final String KEY_CARD_SCRIM = "card_scrim";
    /** 内置默认背景图是否已经铺过一次（铺过就不再重复铺，尊重用户后续的选择） */
    public static final String KEY_BG_SEEDED = "bg_seeded";

    public static final int DEFAULT_BG_ALPHA = 214;
    public static final int DEFAULT_CARD_ALPHA = 150;

    private Prefs() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String bgImage(Context c) {
        return p(c).getString(KEY_BG_IMAGE, "");
    }

    public static void setBgImage(Context c, String path) {
        p(c).edit().putString(KEY_BG_IMAGE, path == null ? "" : path).apply();
    }

    public static int bgAlpha(Context c) {
        return p(c).getInt(KEY_BG_ALPHA, DEFAULT_BG_ALPHA);
    }

    public static void setBgAlpha(Context c, int v) {
        p(c).edit().putInt(KEY_BG_ALPHA, clamp(v)).apply();
    }

    public static int cardAlpha(Context c) {
        return p(c).getInt(KEY_CARD_ALPHA, DEFAULT_CARD_ALPHA);
    }

    public static void setCardAlpha(Context c, int v) {
        p(c).edit().putInt(KEY_CARD_ALPHA, clamp(v)).apply();
    }

    /** 内置默认背景图是否已经铺过 */
    public static boolean bgSeeded(Context c) {
        return p(c).getBoolean(KEY_BG_SEEDED, false);
    }

    public static void setBgSeeded(Context c, boolean v) {
        p(c).edit().putBoolean(KEY_BG_SEEDED, v).apply();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
