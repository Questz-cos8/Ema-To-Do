package com.todolife.app.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;

import com.todolife.app.model.Deadline;

import java.util.Calendar;

/**
 * 日期 / 时间选择：只选日期时，截止时刻自动落到当天 23:59。
 * 选完日期会紧跟着弹出时间选择，可以直接跳过（保持全天）。
 */
public final class DateTimePicker {

    public interface OnPicked {
        /** @param millis 选中的截止时间戳；@param withTime 是否指定了具体时刻 */
        void onPicked(long millis, boolean withTime);
    }

    private DateTimePicker() {
    }

    /**
     * @param askTime true 时选完日期继续问时间
     */
    public static void show(Context context, long initial, boolean askTime, OnPicked callback) {
        Calendar c = Calendar.getInstance();
        if (initial > 0) c.setTimeInMillis(initial);

        DatePickerDialog dp = new DatePickerDialog(context,
                (view, year, month, day) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.setTimeInMillis(initial > 0 ? initial : System.currentTimeMillis());
                    picked.set(Calendar.YEAR, year);
                    picked.set(Calendar.MONTH, month);
                    picked.set(Calendar.DAY_OF_MONTH, day);
                    if (!askTime) {
                        callback.onPicked(Deadline.endOfDay(picked.getTimeInMillis()), false);
                        return;
                    }
                    TimePickerDialog tp = new TimePickerDialog(context,
                            (tv, hour, minute) -> {
                                picked.set(Calendar.HOUR_OF_DAY, hour);
                                picked.set(Calendar.MINUTE, minute);
                                picked.set(Calendar.SECOND, 0);
                                picked.set(Calendar.MILLISECOND, 0);
                                callback.onPicked(picked.getTimeInMillis(), true);
                            },
                            picked.get(Calendar.HOUR_OF_DAY), picked.get(Calendar.MINUTE), true);
                    tp.setTitle("几点交呀？⏰（取消就是整天都算）");
                    tp.setOnCancelListener(d ->
                            callback.onPicked(Deadline.endOfDay(picked.getTimeInMillis()), false));
                    tp.show();
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dp.setTitle("哪天要交呀？ ( ˙▿˙ )");
        dp.show();
    }

    /** 只选时间 */
    public static void showTime(Context context, long base, OnPicked callback) {
        Calendar c = Calendar.getInstance();
        if (base > 0) c.setTimeInMillis(base);
        TimePickerDialog tp = new TimePickerDialog(context, (view, hour, minute) -> {
            Calendar picked = Calendar.getInstance();
            picked.setTimeInMillis(base > 0 ? base : System.currentTimeMillis());
            picked.set(Calendar.HOUR_OF_DAY, hour);
            picked.set(Calendar.MINUTE, minute);
            picked.set(Calendar.SECOND, 0);
            picked.set(Calendar.MILLISECOND, 0);
            callback.onPicked(picked.getTimeInMillis(), true);
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true);
        tp.setTitle("几点交呀？⏰");
        tp.show();
    }

    /** 今天 / 明天 / 三天后 / 一周后 / 两周后 / 一个月后 */
    public static void showQuickPicker(Context context, OnPicked callback) {
        final String[] labels = {"今天 23:59", "明天 23:59", "3 天后", "一周后", "两周后", "一个月后"};
        new android.app.AlertDialog.Builder(context)
                .setTitle("挑一个吧～ (๑´ㅂ`๑)")
                .setItems(labels, (d, which) -> {
                    Calendar c = Calendar.getInstance();
                    switch (which) {
                        case 0: break;
                        case 1: c.add(Calendar.DAY_OF_MONTH, 1); break;
                        case 2: c.add(Calendar.DAY_OF_MONTH, 3); break;
                        case 3: c.add(Calendar.DAY_OF_MONTH, 7); break;
                        case 4: c.add(Calendar.DAY_OF_MONTH, 14); break;
                        default: c.add(Calendar.MONTH, 1); break;
                    }
                    callback.onPicked(Deadline.endOfDay(c.getTimeInMillis()), false);
                })
                .show();
    }
}
