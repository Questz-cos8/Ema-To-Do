package com.todolife.app.util;

import com.todolife.app.model.Deadline;
import com.todolife.app.model.Task;

/**
 * 卡片等深色/图片背景上的文字配色。
 *
 * <p>带背景图的卡片需要「自适应」文字颜色：用户选的照片可能很亮也可能很暗。
 * 这里用亮度做判断，并在卡片上叠加对应方向的半透明蒙版，保证文字始终读得清。</p>
 */
public final class Contrast {

    /** 卡片背景图上的默认蒙版不透明度（偏白，保证深色文字可读） */
    public static final int VEIL_LIGHT = 170;
    public static final int VEIL_DARK = 165;

    private Contrast() {
    }

    public static int luminance(int color) {
        double r = android.graphics.Color.red(color) / 255.0;
        double g = android.graphics.Color.green(color) / 255.0;
        double b = android.graphics.Color.blue(color) / 255.0;
        return (int) (0.2126 * r * 255 + 0.7152 * g * 255 + 0.0722 * b * 255);
    }

    /** 文字颜色：亮底 → 深色字；暗底 → 白字 */
    public static int textOn(int background) {
        return luminance(background) > 140 ? 0xFF1B1D28 : 0xFFFFFFFF;
    }

    public static int secondaryOn(int background) {
        return luminance(background) > 140 ? 0xFF5A6270 : 0xE6FFFFFF;
    }

    /** 蒙版颜色：背景亮 → 白蒙版；背景暗 → 黑蒙版 */
    public static int veilOn(int background) {
        return luminance(background) > 140
                ? android.graphics.Color.argb(VEIL_LIGHT, 0xFF, 0xFF, 0xFF)
                : android.graphics.Color.argb(VEIL_DARK, 0x0A, 0x0C, 0x14);
    }

    /**
     * 没手动设置优先级颜色时，用「剩余时间越少越暖」的渐变作为卡片强调色。
     * 返回 null 表示使用默认卡片底色。
     */
    public static Integer autoAccent(Task t) {
        if (t == null || t.isDone()) return null;
        if (!t.hasDeadline()) return null;
        int d = Deadline.daysLeft(t.getDeadline());
        if (d < 0) return 0xFFFFEBEE;
        if (d <= 3) return 0xFFFFF1F1;
        if (d <= 7) return 0xFFFFF7E8;
        if (d <= 14) return 0xFFF1FAF2;
        return null;
    }

    /** 由字符串派生的稳定柔和渐变色（卡片没有图片时的视觉区分） */
    public static int[] softGradient(String seed) {
        int[][] palettes = new int[][]{
                {0xFFF3F6FF, 0xFFEDF1FF},
                {0xFFF1FBF9, 0xFFECF8F5},
                {0xFFFFF8F1, 0xFFFFF3E8},
                {0xFFF8F5FF, 0xFFF3EFFF},
                {0xFFF2FAFF, 0xFFEBF6FF},
                {0xFFFFF5F7, 0xFFFFEDF1},
        };
        int idx = Math.abs((seed == null ? "" : seed).hashCode()) % palettes.length;
        return palettes[idx];
    }
}
