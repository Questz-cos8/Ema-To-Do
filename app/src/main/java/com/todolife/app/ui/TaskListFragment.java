package com.todolife.app.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.todolife.app.R;
import com.todolife.app.data.Query;
import com.todolife.app.data.TaskStore;
import com.todolife.app.model.Deadline;
import com.todolife.app.model.Tag;
import com.todolife.app.model.Task;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 「待完成」页：两个固定区域。
 *
 * <ul>
 *   <li>区域一「有截止日期 · DDL」：圆点颜色 = 剩余天数（≤3 天红色），按剩余天数升序</li>
 *   <li>区域二「长线任务 · 无 DDL」：没有截止日期的长期任务</li>
 * </ul>
 *
 * 顶部只有标签筛选条与高级筛选。
 */
public class TaskListFragment extends Fragment implements TaskStore.Listener {

    private AppHost host;
    private TaskStore store;
    private Query query;

    private TextView tvGreeting;
    private TextView tvSubtitle;
    private LinearLayout filterRow;
    private LinearLayout listDeadline;
    private LinearLayout listLongTerm;
    private View emptyDeadline;
    private View emptyLongTerm;
    private View emptyAll;
    private TextView tvEmptyTitle;
    private TextView tvEmptySub;
    private TextView tvZoneDeadlineCount;
    private TextView tvZoneLongCount;
    private View zoneDeadline;
    private View zoneLongTerm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        host = AppHost.of((androidx.appcompat.app.AppCompatActivity) requireActivity());
        store = host.store();
        query = host.query();
        store.addListener(this);

        tvGreeting = v.findViewById(R.id.tvGreeting);
        tvSubtitle = v.findViewById(R.id.tvSubtitle);
        filterRow = v.findViewById(R.id.filterRow);
        listDeadline = v.findViewById(R.id.listDeadline);
        listLongTerm = v.findViewById(R.id.listLongTerm);
        emptyDeadline = v.findViewById(R.id.emptyDeadline);
        emptyLongTerm = v.findViewById(R.id.emptyLongTerm);
        emptyAll = v.findViewById(R.id.emptyAll);
        tvEmptyTitle = v.findViewById(R.id.tvEmptyTitle);
        tvEmptySub = v.findViewById(R.id.tvEmptySub);
        tvZoneDeadlineCount = v.findViewById(R.id.tvZoneDeadlineCount);
        tvZoneLongCount = v.findViewById(R.id.tvZoneLongCount);
        zoneDeadline = v.findViewById(R.id.zoneDeadline);
        zoneLongTerm = v.findViewById(R.id.zoneLongTerm);

        v.findViewById(R.id.btnFilter).setOnClickListener(view -> showAdvancedFilter());

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
        if (isAdded() && listDeadline != null) render();
    }

    private List<Task> visibleTodos() {
        List<Task> out = new ArrayList<>();
        for (Task t : store.all()) {
            if (t.isDone()) continue;
            if (query.accept(t)) out.add(t);
        }
        Collections.sort(out, TaskStore.TODO_ORDER);
        return out;
    }

    /** 数据变化 / 筛选变化后重绘整个页面 */
    public void render() {
        if (listDeadline == null || host == null) return;
        TaskActions actions = host.actions();
        List<Task> visible = visibleTodos();

        List<Task> withDdl = new ArrayList<>();
        List<Task> longTerm = new ArrayList<>();
        for (Task t : visible) {
            if (t.hasDeadline()) withDdl.add(t);
            else longTerm.add(t);
        }

        ListBuilder.bind(listDeadline, emptyDeadline, withDdl, false, actions);
        ListBuilder.bind(listLongTerm, emptyLongTerm, longTerm, false, actions);

        tvZoneDeadlineCount.setText(String.valueOf(withDdl.size()));
        tvZoneLongCount.setText(String.valueOf(longTerm.size()));

        boolean nothing = visible.isEmpty();
        emptyAll.setVisibility(nothing ? View.VISIBLE : View.GONE);
        zoneDeadline.setVisibility(nothing ? View.GONE : View.VISIBLE);
        zoneLongTerm.setVisibility(nothing ? View.GONE : View.VISIBLE);
        if (nothing) {
            if (query.isActive()) {
                tvEmptyTitle.setText(R.string.empty_filter_title);
                tvEmptySub.setText(R.string.empty_filter_sub);
            } else {
                tvEmptyTitle.setText(R.string.empty_todo_title);
                tvEmptySub.setText(R.string.empty_todo_sub);
            }
        }

        renderHeader();
        renderFilterRow();
    }

    private void renderHeader() {
        Calendar c = Calendar.getInstance();
        // 大标题：按时段来一句问候（艾玛的语气）
        int hour = c.get(Calendar.HOUR_OF_DAY);
        String greet;
        if (hour >= 5 && hour < 11) greet = getString(R.string.greet_morning);
        else if (hour >= 11 && hour < 14) greet = getString(R.string.greet_noon);
        else if (hour >= 14 && hour < 18) greet = getString(R.string.greet_afternoon);
        else if (hour >= 18 && hour < 23) greet = getString(R.string.greet_evening);
        else greet = getString(R.string.greet_night);
        tvGreeting.setText(greet);

        String[] week = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        String today = String.format(Locale.getDefault(), "%d月%d日 %s",
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                week[c.get(Calendar.DAY_OF_WEEK) - 1]);

        int todo = 0, overdue = 0, todayDue = 0, soon = 0;
        for (Task t : store.all()) {
            if (t.isDone()) continue;
            todo++;
            if (t.isOverdue()) overdue++;
            if (t.hasDeadline()) {
                int d = Deadline.daysLeft(t.getDeadline());
                if (d == 0) todayDue++;
                if (d >= 0 && d <= 3) soon++;
            }
        }
        StringBuilder sb = new StringBuilder(today).append(" · 还剩 ").append(todo).append(" 件事");
        if (todayDue > 0) sb.append("，今天要交 ").append(todayDue);
        if (soon > 0) sb.append(" · 3 天内 ").append(soon);
        if (overdue > 0) sb.append(" · 有 ").append(overdue).append(" 件在拖后腿！");
        if (query.isActive()) sb.append("\n筛选：").append(query.describe());
        tvSubtitle.setText(sb.toString());
    }

    private void renderFilterRow() {
        if (filterRow == null || host == null) return;
        Context c = requireContext();
        filterRow.removeAllViews();

        List<String> names = new ArrayList<>();
        names.add("全部");
        names.add(Tag.REQUIRED);
        names.add(Tag.OPTIONAL);
        names.add(Tag.SCHOOL);
        names.add(Tag.PERSONAL);
        names.addAll(store.customTagPool());
        names.add("仅今天");
        names.add("仅逾期");

        for (String name : names) {
            boolean active;
            if ("仅逾期".equals(name)) active = query.isOnlyOverdue();
            else if ("仅今天".equals(name)) active = query.isOnlyToday();
            else active = name.equals(query.getTag());

            TextView tv = pill(c, name, active);
            tv.setOnClickListener(view -> {
                if ("仅逾期".equals(name)) {
                    query.setOnlyOverdue(!query.isOnlyOverdue());
                } else if ("仅今天".equals(name)) {
                    query.setOnlyToday(!query.isOnlyToday());
                } else {
                    query.setTag(name);
                    query.setOnlyOverdue(false);
                    query.setOnlyToday(false);
                }
                render();
            });
            filterRow.addView(tv);
        }
    }

    private TextView pill(Context c, String text, boolean active) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextSize(12.5f);
        tv.setPadding(dp(12), dp(6), dp(12), dp(6));
        tv.setBackgroundResource(active ? R.drawable.bg_selected_pill : R.drawable.bg_unselected_pill);
        tv.setTextColor(active ? 0xFFFFFFFF : 0xFF6F6878);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void showAdvancedFilter() {
        final String[] options = {"只看必做", "只看选做", "只看学校", "只看个人",
                "只看已逾期", "只看今天截止", "全部清掉，重新看"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("想先看哪一些呀？ ( ᵕᴗᵕ )")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: query.setTag(Tag.REQUIRED); break;
                        case 1: query.setTag(Tag.OPTIONAL); break;
                        case 2: query.setTag(Tag.SCHOOL); break;
                        case 3: query.setTag(Tag.PERSONAL); break;
                        case 4: query.setOnlyOverdue(true); break;
                        case 5: query.setOnlyToday(true); break;
                        default:
                            query.clear();
                            break;
                    }
                    render();
                })
                .setMessage("排序是先看置顶，再看快到期的哦～ (｡◕‿◕｡)")
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
