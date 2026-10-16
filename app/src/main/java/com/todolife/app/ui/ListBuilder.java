package com.todolife.app.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.todolife.app.R;
import com.todolife.app.model.Deadline;
import com.todolife.app.model.Tag;
import com.todolife.app.model.Task;
import com.todolife.app.util.Chips;
import com.todolife.app.util.Contrast;
import com.todolife.app.util.ImageStore;

import java.util.List;

/**
 * 把一个区域（有 DDL / 长线 / 已完成）里的任务渲染成一叠紧凑卡片。
 *
 * <p>一行式布局：圆点 · 标题 · 标签圆点 · DDL · 完成按钮，高度约 54dp；
 * 详细内容全部收在点击后的详情面板里。</p>
 */
public final class ListBuilder {

    private ListBuilder() {
    }

    public static void bind(LinearLayout container, View emptyView, List<Task> tasks,
                            boolean doneList, TaskActions actions) {
        bind(container, emptyView, tasks, doneList, actions, false);
    }

    /**
     * @param draggable 是否允许「长按拖拽排序」——只有长线任务区开启
     */
    public static void bind(LinearLayout container, View emptyView, List<Task> tasks,
                            boolean doneList, TaskActions actions, boolean draggable) {
        Context c = container.getContext();
        container.removeAllViews();
        if (tasks == null || tasks.isEmpty()) {
            if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
            return;
        }
        if (emptyView != null) emptyView.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(c);
        for (Task t : tasks) {
            View v = inflater.inflate(R.layout.item_task, container, false);
            bindRow(v, t, doneList, container, draggable, actions);
            container.addView(v);
        }
    }

    private static void bindRow(View v, Task t, boolean doneList,
                               LinearLayout container, boolean draggable, TaskActions actions) {
        Context c = v.getContext();
        // 拖拽排序完成后要按「行的顺序」把任务读回来，所以每行挂上自己的任务 id
        v.setTag(R.id.row_task, t.getId());

        /*
         * 两个概念要分开：
         *   doneList  —— 整页都是「已完成」（已完成页），DDL 那行显示「划掉啦 · 日期」
         *   doneStyle —— 这一条要以「已完成」的样子显示（划掉 + 变灰 + 空心点 + 可还原图标）
         *
         * 日历某一天是混合的（有完成有没完成），所以日历要按单条任务判断，
         * 不能整页套用一种样式。
         */
        boolean doneStyle = doneList || t.isDone();

        MaterialCardView card = v.findViewById(R.id.cardRoot);
        View imageWrap = v.findViewById(R.id.cardImageWrap);
        ImageView cardImage = v.findViewById(R.id.imgCardBg);
        DotView dot = v.findViewById(R.id.dot);
        TextView title = v.findViewById(R.id.tvTitle);
        TextView pin = v.findViewById(R.id.tvPin);
        LinearLayout tagDots = v.findViewById(R.id.tagDots);
        TextView deadline = v.findViewById(R.id.tvDeadline);
        TextView deadlineDate = v.findViewById(R.id.tvDeadlineDate);
        TextView stage = v.findViewById(R.id.tvStage);
        TextView btnDone = v.findViewById(R.id.btnCardDone);

        // ---------- 标题 ----------
        title.setText(t.getTitle().isEmpty() ? "还没写标题呢…" : t.getTitle());
        if (doneStyle) {
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(ContextCompat.getColor(c, R.color.text_secondary));
        } else {
            title.setPaintFlags(title.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            title.setTextColor(ContextCompat.getColor(c, R.color.text_primary));
        }

        // ---------- 圆点：颜色 = 紧急程度 ----------
        dot.bindDeadline(t.getDeadline());
        dot.setHollow(doneStyle);
        dot.setRingEnabled(t.isPinned());

        // ---------- 标签圆点（4 个小圆点代替长标签） ----------
        tagDots.removeAllViews();
        List<String> tags = t.allTags();
        int shown = Math.min(tags.size(), 4);
        for (int i = 0; i < shown; i++) {
            tagDots.addView(tagDot(c, tags.get(i)));
        }

        // ---------- DDL 文案 ----------
        if (doneList) {
            deadline.setText("划掉啦 · " + Deadline.relativeDay(t.getDoneAt()));
            deadline.setTextColor(ContextCompat.getColor(c, R.color.secondary));
            deadlineDate.setVisibility(View.GONE);
        } else {
            deadlineDate.setVisibility(View.VISIBLE);
            if (t.hasDeadline()) {
                deadline.setText(Deadline.label(t.getDeadline()));
                // 已完成的不再用紧急度颜色喊「已过期」，统一压成灰色
                deadline.setTextColor(doneStyle
                        ? ContextCompat.getColor(c, R.color.text_hint)
                        : Deadline.urgencyColor(t.getDeadline()));
                deadlineDate.setText(Deadline.full(t.getDeadline(), false));
            } else {
                // 长线任务不写「慢慢来就好」，只留一个淡紫的「长线」标记
                deadline.setText("长线");
                deadline.setTextColor(doneStyle
                        ? ContextCompat.getColor(c, R.color.text_hint)
                        : Deadline.COLOR_LONGTERM);
                deadlineDate.setVisibility(View.GONE);
            }
        }

        // ---------- 进度（只在进行中/待收尾时显示，减少噪音） ----------
        if (!doneStyle && t.getStage() > 0) {
            // 「进行中」在卡片上带省略号，读起来像还在跑
            stage.setText(t.getStage() == 1 ? "进行中..." : t.stageName());
            stage.setVisibility(View.VISIBLE);
        } else {
            stage.setVisibility(View.GONE);
        }

        pin.setVisibility(t.isPinned() && !doneStyle ? View.VISIBLE : View.GONE);

        // ---------- 卡片背景 ----------
        int radius = Chips.dp(c, 18);
        if (t.hasCardImage()) {
            cardImage.setVisibility(View.VISIBLE);
            // 圆角由 CardImageView 自己裁（重写 outline + clipPath，双保险）
            if (cardImage instanceof CardImageView) {
                ((CardImageView) cardImage).setCornerRadius(radius);
            }
            ImageStore.loadInto(cardImage, t.getCardImage(), t.getCardImageAlpha(),
                    Chips.dp(c, 360), Chips.dp(c, 120));
            imageWrap.setBackground(null);
            imageWrap.setClipToOutline(false);
            card.setCardBackgroundColor(0xFFFFFFFF);
        } else {
            cardImage.setVisibility(View.GONE);
            int[] grad = Contrast.softGradient(t.getId() + t.getTitle());
            GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, grad);
            d.setCornerRadius(radius);
            imageWrap.setBackground(d);
            imageWrap.setClipToOutline(true);
            card.setCardBackgroundColor(0xFFFFFFFF);
        }
        v.setAlpha(doneStyle ? 0.78f : 1f);

        // ---------- 完成按钮 ----------
        btnDone.setText(doneStyle ? "↺" : "✓");

        card.setOnClickListener(view -> {
            if (actions != null) actions.onOpen(t);
        });
        if (draggable) {
            // 长线任务区：长按留给「拖拽排序」（在 ListGestures 里处理），
            // 想编辑就点卡片 → 详情 → 编辑。
        } else {
            card.setOnLongClickListener(view -> {
                if (actions != null) actions.onEdit(t);
                return true;
            });
        }
        btnDone.setOnClickListener(view -> {
            if (actions != null) actions.onToggleDone(t);
        });
        ListGestures.attach(v, card, t, container, draggable, actions);
    }

    /** 标签 → 一个小色点（必做/选做/学校/个人各一色，自由标签用淡紫灰） */
    private static View tagDot(Context c, String tag) {
        View v = new View(c);
        int size = Chips.dp(c, 8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.rightMargin = Chips.dp(c, 4);
        v.setLayoutParams(lp);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(colorForTag(tag));
        v.setBackground(d);
        v.setContentDescription(tag);
        return v;
    }

    private static int colorForTag(String tag) {
        if (Tag.REQUIRED.equals(tag)) return 0xFFEE7B92;
        if (Tag.OPTIONAL.equals(tag)) return 0xFFC9C3D4;
        if (Tag.SCHOOL.equals(tag)) return 0xFFA99BD4;
        if (Tag.PERSONAL.equals(tag)) return 0xFF7FCBA6;
        return 0xFFB8B2C6;
    }
}
