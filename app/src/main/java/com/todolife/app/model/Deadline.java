package com.todolife.app.model;

import android.graphics.Color;

import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 截止日期（DDL）相关的纯逻辑：剩余天数、颜色档位、文案。
 *
 * <p>颜色档位是本 App 的核心视觉规则：</p>
 * <ul>
 *   <li>已逾期 —— 深红</li>
 *   <li>≤ 3 天 —— 红色（用户要求的「仅剩三天标为红色」）</li>
 *   <li>≤ 7 天 —— 橙色</li>
 *   <li>≤ 14 天 —— 绿色</li>
 *   <li>&gt; 14 天 —— 灰色</li>
 * </ul>
 */
public final class Deadline {

    public static final int COLOR_OVERDUE = Color.parseColor("#E53935");
    public static final int COLOR_URGENT = Color.parseColor("#FF5252");
    public static final int COLOR_SOON = Color.parseColor("#FFA726");
    public static final int COLOR_OK = Color.parseColor("#43A047");
    public static final int COLOR_FAR = Color.parseColor("#7E8AA2");
    public static final int COLOR_LONGTERM = Color.parseColor("#4C6FFF");

    private static final long DAY_MS = TimeUnit.DAYS.toMillis(1);

    private Deadline() {
    }

    /** 当天 00:00 的时间戳 */
    public static long startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 当天 23:59:59.999 的时间戳 —— 只选日期时用它作为截止时刻 */
    public static long endOfDay(long millis) {
        return startOfDay(millis) + DAY_MS - 1;
    }

    /**
     * 距离截止还剩多少个「自然日」。
     * 0 = 今天截止，1 = 明天，-1 = 昨天（已逾期一天）。
     */
    public static int daysLeft(long deadline) {
        if (deadline <= 0) return Integer.MAX_VALUE;
        long today = startOfDay(System.currentTimeMillis());
        long target = startOfDay(deadline);
        return (int) Math.round((double) (target - today) / (double) DAY_MS);
    }

    /** 是否已逾期（按日期比较） */
    public static boolean isOverdue(long deadline) {
        return deadline > 0 && daysLeft(deadline) < 0;
    }

    /**
     * 剩余天数 -&gt; 圆点颜色。
     * 用户要求：仅剩三天以内为红色。
     */
    public static int urgencyColor(long deadline) {
        if (deadline <= 0) return COLOR_LONGTERM;
        int d = daysLeft(deadline);
        if (d < 0) return COLOR_OVERDUE;
        if (d <= 3) return COLOR_URGENT;
        if (d <= 7) return COLOR_SOON;
        if (d <= 14) return COLOR_OK;
        return COLOR_FAR;
    }

    /** 颜色档位的中文名，用于详细页说明 */
    public static String urgencyLevel(long deadline) {
        if (deadline <= 0) return "长线";
        int d = daysLeft(deadline);
        if (d < 0) return "已逾期";
        if (d <= 3) return "紧急（≤3天）";
        if (d <= 7) return "临近（≤7天）";
        if (d <= 14) return "正常（≤14天）";
        return "充裕（>14天）";
    }

    /** 卡片上的 DDL 文案，例如「今天截止」「还差 3 天」「已逾期 2 天」 */
    public static String label(long deadline) {
        if (deadline <= 0) return "长线任务";
        int d = daysLeft(deadline);
        if (d == 0) return "今天截止";
        if (d == 1) return "明天截止";
        if (d == 2) return "后天截止";
        if (d > 0) return "还差 " + d + " 天";
        return "已逾期 " + (-d) + " 天";
    }

    /** 完整日期时间文案，例如「2026-10-15 23:59」 */
    public static String full(long millis, boolean withTime) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        String date = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        if (!withTime) return date;
        return String.format(Locale.getDefault(), "%s %02d:%02d", date,
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    /** 相对日期，用于「完成于」等 */
    public static String relativeDay(long millis) {
        int d = daysLeft(millis);
        if (d == 0) return "今天";
        if (d == -1) return "昨天";
        if (d == -2) return "前天";
        return full(millis, false);
    }

    /** 把「X 天后」的提醒提前量变成可读文案 */
    public static String remindLabel(int minutes) {
        if (minutes < 0) return "不提醒";
        if (minutes == 0) return "截止时提醒";
        if (minutes < 60) return "提前 " + minutes + " 分钟";
        int hours = minutes / 60;
        if (hours < 24) return "提前 " + hours + " 小时";
        int days = hours / 24;
        if (hours % 24 == 0) return "提前 " + days + " 天";
        return "提前 " + days + " 天 " + (hours % 24) + " 小时";
    }
}
