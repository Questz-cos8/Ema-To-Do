package com.todolife.app.reminder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.todolife.app.R;
import com.todolife.app.data.TaskStore;
import com.todolife.app.model.Task;
import com.todolife.app.ui.MainActivity;

import java.util.concurrent.TimeUnit;

/**
 * 提醒：每条任务最多一个精确闹钟，到点弹通知。
 * 没装 WorkManager，直接用 AlarmManager，少一个依赖。
 */
public final class ReminderScheduler {

    public static final String CHANNEL_ID = "todo_reminder";
    static final String EXTRA_TASK_ID = "task_id";

    private ReminderScheduler() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notify_channel_name), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(context.getString(R.string.notify_channel_desc));
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }

    private static PendingIntent intentFor(Context context, Task task) {
        Intent i = new Intent(context, ReminderReceiver.class);
        i.setAction("com.todolife.app.REMIND." + task.getId());
        i.putExtra(EXTRA_TASK_ID, task.getId());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, task.getId().hashCode(), i, flags);
    }

    /** 为任务安排提醒；没有提醒设置或没有 DDL 时会先取消旧的 */
    public static void schedule(Context context, Task task) {
        if (context == null || task == null) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = intentFor(context, task);
        if (!task.hasReminder()) {
            am.cancel(pi);
            return;
        }
        long trigger = task.getDeadline() - TimeUnit.MINUTES.toMillis(task.getRemindBeforeMinutes());
        // 如果提醒时间已经过去但截止还没到，就 10 秒后立刻提醒一次
        if (trigger < System.currentTimeMillis()) {
            trigger = task.getDeadline() > System.currentTimeMillis()
                    ? System.currentTimeMillis() + 10_000L : 0L;
        }
        if (trigger <= 0) {
            am.cancel(pi);
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.set(AlarmManager.RTC_WAKEUP, trigger, pi);   // 退化为不精确
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }

    public static void cancel(Context context, Task task) {
        if (context == null || task == null) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(intentFor(context, task));
    }

    /** 重启 / 更新后重建全部提醒 */
    public static void rescheduleAll(Context context) {
        TaskStore store = TaskStore.get(context);
        for (Task t : store.all()) {
            if (t.hasReminder() && !t.isDone()) schedule(context, t);
        }
    }

    static void notifyNow(Context context, Task task) {
        ensureChannel(context);
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra(MainActivity.EXTRA_OPEN_TASK_ID, task.getId());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(context, task.getId().hashCode() + 7, open, flags);

        String title = task.getTitle().isEmpty() ? "有件事要做哦～" : task.getTitle();
        String text = com.todolife.app.model.Deadline.label(task.getDeadline())
                + " · " + task.necessityName() + " · " + task.scopeName();

        Notification n = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_todo)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        text + (task.getNote().isEmpty() ? "" : "\n" + task.getNote())))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(content)
                .build();
        try {
            NotificationManagerCompat.from(context).notify(task.getId().hashCode(), n);
        } catch (SecurityException ignored) {
            // 用户没给通知权限，忽略
        }
    }

    /** 闹钟触发 */
    public static class ReminderReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String id = intent.getStringExtra(EXTRA_TASK_ID);
            Task task = TaskStore.get(context).byId(id);
            if (task == null || task.isDone()) return;
            notifyNow(context, task);
        }
    }

    /** 开机 / 应用更新后重建提醒 */
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
                rescheduleAll(context);
            }
        }
    }
}
