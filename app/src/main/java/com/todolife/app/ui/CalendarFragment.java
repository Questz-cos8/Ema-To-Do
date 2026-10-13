package com.todolife.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.todolife.app.R;
import com.todolife.app.data.TaskStore;
import com.todolife.app.model.Deadline;
import com.todolife.app.model.Task;
import com.todolife.app.util.ImageStore;
import com.todolife.app.util.Prefs;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 「日历」页：整月俯瞰。
 *
 * <p>网格里每天下面用小圆点显示当天任务（颜色= DDL 紧急度），点某天就在下面列出
 * 那天的任务，点卡片照样进详情。左右箭头换月，右上角一键回到今天。</p>
 */
public class CalendarFragment extends Fragment implements TaskStore.Listener {

    private static final String[] WEEK_LONG = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private AppHost host;
    private TaskStore store;

    private CalendarView calendar;
    private TextView tvMonthLabel;
    private TextView tvMonthSummary;
    private TextView tvCalendarSubtitle;
    private TextView tvSelectedDay;
    private TextView tvDayCount;
    private LinearLayout listDay;
    private View emptyDay;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        host = AppHost.of((androidx.appcompat.app.AppCompatActivity) requireActivity());
        store = host.store();
        store.addListener(this);

        calendar = v.findViewById(R.id.calendar);
        tvMonthLabel = v.findViewById(R.id.tvMonthLabel);
        tvMonthSummary = v.findViewById(R.id.tvMonthSummary);
        tvCalendarSubtitle = v.findViewById(R.id.tvCalendarSubtitle);
        tvSelectedDay = v.findViewById(R.id.tvSelectedDay);
        tvDayCount = v.findViewById(R.id.tvDayCount);
        listDay = v.findViewById(R.id.listDay);
        emptyDay = v.findViewById(R.id.emptyDay);

        calendar.setOnDaySelected(day -> renderDay());

        v.findViewById(R.id.btnPrevMonth).setOnClickListener(x -> {
            calendar.shiftMonth(-1);
            render();
        });
        v.findViewById(R.id.btnNextMonth).setOnClickListener(x -> {
            calendar.shiftMonth(1);
            render();
        });
        v.findViewById(R.id.btnBackToday).setOnClickListener(x -> {
            Calendar now = Calendar.getInstance();
            calendar.setMonth(now.get(Calendar.YEAR), now.get(Calendar.MONTH));
            calendar.setSelectedMillis(System.currentTimeMillis());
            render();
        });

        render();
    }

    /*
     * 这里刻意不做 render()。
     *
     * 本页在 onViewCreated 就注册了 TaskStore 监听，数据一变就会重绘，
     * 所以 onResume 里再画一遍是重复劳动；而翻页时 ViewPager 会**在滑动动画开始前**
     * 同步调用新页面的 onResume —— 这份重复劳动正好卡在动画开头，
     * 表现为松手后画面「瞬移」到下一页。内容刷新交给数据监听和滑动停稳后的刷新。
     */

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (store != null) store.removeListener(this);
    }

    @Override
    public void onStoreChanged() {
        if (isAdded() && calendar != null) render();
    }

    public void render() {
        if (calendar == null || host == null) return;
        calendar.setTasks(store.all());

        tvMonthLabel.setText(String.format(Locale.getDefault(), "%d 年 %d 月",
                calendar.getYear(), calendar.getMonth() + 1));

        renderMonthSummary();
        renderDay();
        renderSubtitle();
    }

    private void renderSubtitle() {
        int monthTasks = 0;
        for (Task t : tasksOfMonth(calendar.getYear(), calendar.getMonth())) monthTasks++;
        tvCalendarSubtitle.setText(monthTasks == 0
                ? "这个月还没有安排呢～"
                : "这个月一共有 " + monthTasks + " 件事");
    }

    private void renderMonthSummary() {
        int total = 0;
        int done = 0;
        for (Task t : tasksOfMonth(calendar.getYear(), calendar.getMonth())) {
            total++;
            if (t.isDone()) done++;
        }
        tvMonthSummary.setText(total == 0
                ? "这个月空空的～"
                : "本月 " + total + " 件 · 已完成 " + done + " 件");
    }

    private void renderDay() {
        long day = calendar.getSelectedMillis();
        List<Task> ofDay = tasksOfDay(day);
        Collections.sort(ofDay, TaskStore.TODO_ORDER);

        int done = 0;
        for (Task t : ofDay) if (t.isDone()) done++;

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(day);
        String label = String.format(Locale.getDefault(), "%d 月 %d 日 %s",
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                WEEK_LONG[c.get(Calendar.DAY_OF_WEEK) - 1]);
        if (Deadline.startOfDay(System.currentTimeMillis()) == day) label = "今天 · " + label;
        tvSelectedDay.setText(label);

        boolean empty = ofDay.isEmpty();
        tvDayCount.setText(empty ? "" : "共 " + ofDay.size() + " 件"
                + (done > 0 ? "（完成 " + done + "）" : ""));

        ListBuilder.bind(listDay, emptyDay, ofDay, false, host.actions());
    }

    /** 某天的任务（按截止日期落在这一天算） */
    private List<Task> tasksOfDay(long dayMillis) {
        long start = Deadline.startOfDay(dayMillis);
        List<Task> out = new ArrayList<>();
        for (Task t : store.all()) {
            if (!t.hasDeadline()) continue;
            if (Deadline.startOfDay(t.getDeadline()) == start) out.add(t);
        }
        return out;
    }

    private List<Task> tasksOfMonth(int year, int month) {
        List<Task> out = new ArrayList<>();
        for (Task t : store.all()) {
            if (!t.hasDeadline()) continue;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(t.getDeadline());
            if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month) out.add(t);
        }
        return out;
    }
}
