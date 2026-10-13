package com.todolife.app.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.todolife.app.R;
import com.todolife.app.data.TaskStore;
import com.todolife.app.model.Deadline;
import com.todolife.app.model.Task;
import com.todolife.app.reminder.ReminderScheduler;
import com.todolife.app.util.Chips;

/**
 * 点击卡片后弹出的「详细信息」面板：展示全部字段，并集中放快捷操作。
 */
public class DetailSheet implements TaskStore.Listener {

    public interface DetailActions {
        void onEdit(Task task);

        void onDelete(Task task);

        void onPickCardImage(Task task);

        void onDismissed();
    }

    private final BottomSheetDialog dialog;
    private final TaskStore store;
    private final DetailActions actions;
    private String taskId;

    private final DotView dot;
    private final TextView tvTitle;
    private final TextView btnClose;
    private final TextView tvZone;
    private final LinearLayout tagRow;
    private final TextView tvNote;
    private final TextView tvStage;
    private final TextView tvRemind;
    private final TextView tvCreated;
    private final TextView tvUpdated;
    private final Button btnEdit;
    private final Button btnStage;
    private final Button btnBg;
    private final Button btnRemind;
    private final Button btnPin;
    private final Button btnDelete;
    private final Button btnDone;
    private final android.widget.ImageView imgPreview;
    private final com.google.android.material.card.MaterialCardView imgPreviewCard;

    public DetailSheet(Context context, TaskStore store, Task task, DetailActions actions) {
        this.store = store;
        this.actions = actions;
        this.taskId = task.getId();

        dialog = new BottomSheetDialog(context);
        View root = LayoutInflater.from(context).inflate(R.layout.sheet_detail, null);
        dialog.setContentView(root);

        dot = root.findViewById(R.id.dotDetail);
        tvTitle = root.findViewById(R.id.tvDetailTitle);
        btnClose = root.findViewById(R.id.btnDetailClose);
        tvZone = root.findViewById(R.id.tvDetailZone);
        tagRow = root.findViewById(R.id.tagRowDetail);
        tvNote = root.findViewById(R.id.tvDetailNote);
        tvStage = root.findViewById(R.id.tvDetailStage);
        tvRemind = root.findViewById(R.id.tvDetailRemind);
        tvCreated = root.findViewById(R.id.tvDetailCreated);
        tvUpdated = root.findViewById(R.id.tvDetailUpdated);
        btnEdit = root.findViewById(R.id.btnDetailEdit);
        btnStage = root.findViewById(R.id.btnDetailStage);
        btnBg = root.findViewById(R.id.btnDetailBg);
        btnRemind = root.findViewById(R.id.btnDetailRemindBtn);
        btnPin = root.findViewById(R.id.btnDetailPin);
        btnDelete = root.findViewById(R.id.btnDetailDelete);
        btnDone = root.findViewById(R.id.btnDetailDone);
        imgPreview = root.findViewById(R.id.imgCardPreview);
        imgPreviewCard = root.findViewById(R.id.cardDetailImagePreview);

        btnClose.setOnClickListener(v -> dismiss());

        btnEdit.setOnClickListener(v -> {
            Task t = current();
            if (t != null && actions != null) actions.onEdit(t);
        });
        btnStage.setOnClickListener(v -> cycleStage());
        btnBg.setOnClickListener(v -> {
            Task t = current();
            if (t != null && actions != null) actions.onPickCardImage(t);
        });
        btnRemind.setOnClickListener(v -> pickReminder(context));
        btnPin.setOnClickListener(v -> {
            Task t = current();
            if (t == null) return;
            t.setPinned(!t.isPinned());
            store.upsert(t);
            refresh();
        });
        btnDelete.setOnClickListener(v -> {
            Task t = current();
            if (t != null && actions != null) actions.onDelete(t);
        });
        btnDone.setOnClickListener(v -> {
            Task t = current();
            if (t == null) return;
            store.markDone(t.getId(), !t.isDone());
            refresh();
        });

        dialog.setOnDismissListener(d -> {
            store.removeListener(this);
            if (actions != null) actions.onDismissed();
        });

        refresh();
    }

    public void show() {
        store.addListener(this);
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String id) {
        this.taskId = id;
        refresh();
    }

    private Task current() {
        return store.byId(taskId);
    }

    @Override
    public void onStoreChanged() {
        refresh();
    }

    public void refresh() {
        Task t = current();
        if (t == null) {
            dismiss();
            return;
        }
        tvTitle.setText(t.getTitle().isEmpty() ? "还没写标题呢…" : t.getTitle());
        dot.bindDeadline(t.getDeadline());
        dot.setHollow(t.isDone());
        dot.setRingEnabled(t.isPinned());

        String zone;
        if (t.isDone()) {
            zone = "里件已经划掉啦～（" + Deadline.relativeDay(t.getDoneAt()) + "）";
        } else if (t.hasDeadline()) {
            zone = "截止时间：" + Deadline.full(t.getDeadline(), !t.isAllDay())
                    + "\n" + Deadline.label(t.getDeadline())
                    + " · 紧急程度：" + Deadline.urgencyLevel(t.getDeadline());
        } else {
            zone = "长线任务呀，没有截止日期～";
        }
        tvZone.setText(zone);

        tagRow.removeAllViews();
        for (String tag : t.allTags()) {
            TextView chip = Chips.make(dialog.getContext(), tag);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Chips.dp(dialog.getContext(), 6);
            chip.setLayoutParams(lp);
            tagRow.addView(chip);
        }

        tvNote.setText(TextUtils.isEmpty(t.getNote().trim())
                ? "这里还空着～长按卡片或点「编辑」，把步骤和链接写进来吧！(´･ω･`)"
                : t.getNote());

        tvStage.setText(t.stageName());
        tvRemind.setText(Deadline.remindLabel(t.getRemindBeforeMinutes()));
        tvCreated.setText(Deadline.full(t.getCreatedAt(), false));
        tvUpdated.setText(Deadline.relativeDay(t.getUpdatedAt()));

        btnDone.setText(t.isDone() ? "再拿回来 ↺" : "划掉它！");
        btnPin.setText(t.isPinned() ? "取消置顶" : "置顶");

        boolean hasImage = t.hasCardImage();
        imgPreviewCard.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        if (hasImage) {
            com.todolife.app.util.ImageStore.loadInto(imgPreview, t.getCardImage(),
                    Math.max(40, t.getCardImageAlpha()), Chips.dp(dialog.getContext(), 400),
                    Chips.dp(dialog.getContext(), 160));
        }
    }

    private void cycleStage() {
        Task t = current();
        if (t == null) return;
        t.setStage((t.getStage() + 1) % Task.STAGES.length);
        store.upsert(t);
        refresh();
    }

    private void pickReminder(Context context) {
        final Task t = current();
        if (t == null) return;
        if (!t.hasDeadline()) {
            Toast.makeText(context, R.string.copy_reminder_need_ddl, Toast.LENGTH_SHORT).show();
            return;
        }
        final int[] values = {-1, 0, 10, 60, 180, 1440, 4320};
        final String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = Deadline.remindLabel(values[i]);
        new AlertDialog.Builder(context)
                .setTitle("什么时候叫你呀？")
                .setSingleChoiceItems(labels, indexOf(values, t.getRemindBeforeMinutes()),
                        (d, which) -> {
                            t.setRemindBeforeMinutes(values[which]);
                            store.upsert(t);
                            ReminderScheduler.schedule(context, t);
                            refresh();
                            Toast.makeText(context, R.string.copy_reminder_set,
                                    Toast.LENGTH_SHORT).show();
                            d.dismiss();
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private int indexOf(int[] arr, int value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) return i;
        }
        return 0;
    }

    /** 供外部在编辑保存后重新定位 */
    public void rebind(String id) {
        this.taskId = id;
        refresh();
    }

    /** 阻止误触关闭 */
    public void setCancelable(boolean cancelable) {
        dialog.setCancelable(cancelable);
    }

    static void noop(DialogInterface d) {
    }
}
