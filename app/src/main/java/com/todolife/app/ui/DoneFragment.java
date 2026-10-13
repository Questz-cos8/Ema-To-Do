package com.todolife.app.ui;

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
import com.todolife.app.model.Task;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * 「经完成」页：归档区。可以在这里把任务恢复为待完成，或查看完成情况。
 */
public class DoneFragment extends Fragment implements TaskStore.Listener {

    private AppHost host;
    private TaskStore store;
    private Query query;

    private LinearLayout listDone;
    private View empty;
    private TextView tvCount;
    private TextView tvFilterHint;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_done, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        host = AppHost.of((androidx.appcompat.app.AppCompatActivity) requireActivity());
        store = host.store();
        query = host.query();
        store.addListener(this);

        listDone = v.findViewById(R.id.listDone);
        empty = v.findViewById(R.id.emptyDone);
        tvCount = v.findViewById(R.id.tvDoneCount);
        tvFilterHint = v.findViewById(R.id.tvDoneHint);
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
        if (isAdded() && listDone != null) render();
    }

    public void render() {
        if (listDone == null || host == null) return;
        List<Task> done = new ArrayList<>();
        for (Task t : store.all()) {
            if (t.isDone() && query.accept(t)) done.add(t);
        }
        Collections.sort(done, TaskStore.DONE_ORDER);

        ListBuilder.bind(listDone, empty, done, true, host.actions());
        empty.setVisibility(done.isEmpty() ? View.VISIBLE : View.GONE);

        int totalDone = 0;
        for (Task t : store.all()) if (t.isDone()) totalDone++;

        tvCount.setText("已经划掉 " + totalDone + " 件啦！( ･ᴗ･ )੭");
        if (query.isActive()) {
            tvFilterHint.setVisibility(View.VISIBLE);
            tvFilterHint.setText("当前筛选：" + query.describe());
        } else {
            tvFilterHint.setVisibility(View.GONE);
        }
    }
}
