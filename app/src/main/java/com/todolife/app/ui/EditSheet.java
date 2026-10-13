package com.todolife.app.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.todolife.app.R;
import com.todolife.app.data.TaskStore;
import com.todolife.app.model.Deadline;
import com.todolife.app.model.Task;
import com.todolife.app.model.Tag;
import com.todolife.app.reminder.ReminderScheduler;
import com.todolife.app.util.Prefs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 新建 / 编辑任务的面板。
 *
 * <p>字段：标题、区域（有 DDL / 长线）、截止日期与时刻、备注、标签（必做/选做、学校/个人、自由标签）、
 * 进度、优先级、提醒、卡片背景图与透明度。</p>
 */
public class EditSheet {

    public interface OnSaved {
        void onSaved(Task task);
    }

    public interface ImageRequests {
        /** 请求选图；结果通过 {@link #setPickedImage(String)} 回填 */
        void requestCardImage();
    }

    private final BottomSheetDialog dialog;
    private final TaskStore store;
    private final Task working;
    private final boolean creating;
    private final OnSaved onSaved;
    private final ImageRequests imageRequests;

    private final EditText etTitle;
    private final EditText etNote;
    private final EditText etNewTag;
    private final RadioGroup radioZone;
    private final RadioButton radioDeadline;
    private final View blockDeadline;
    private final LinearLayout quickDeadlineRow;
    private final TextView tvDeadlinePreview;
    private final LinearLayout chipTags;
    private final LinearLayout chipRemind;
    private final RadioGroup radioStage;
    private final RadioGroup radioPriority;
    private final LinearLayout blockCardAlpha;
    private final SeekBar seekCardAlpha;
    private final TextView tvCardAlphaLabel;
    private final com.google.android.material.card.MaterialCardView cardImagePreview;
    private final android.widget.ImageView imgCardPreview;
    private final TextView tvCardBgHint;

    private final Set<String> selectedTags = new LinkedHashSet<>();
    private long deadline;
    private boolean allDay;
    private String cardImage;
    private int remindMinutes;

    public EditSheet(Context context, TaskStore store, @Nullable Task editing,
                     OnSaved onSaved, ImageRequests imageRequests) {
        this.store = store;
        this.onSaved = onSaved;
        this.imageRequests = imageRequests;
        this.creating = editing == null;
        this.working = creating ? new Task() : editing.copy();

        this.deadline = working.getDeadline();
        this.allDay = working.isAllDay();
        this.cardImage = working.getCardImage();
        this.remindMinutes = working.getRemindBeforeMinutes();
        selectedTags.addAll(working.getCustomTags());

        dialog = new BottomSheetDialog(context);
        View root = LayoutInflater.from(context).inflate(R.layout.sheet_edit, null);
        dialog.setContentView(root);
        dialog.getBehavior().setPeekHeight((int) (context.getResources()
                .getDisplayMetrics().heightPixels * 0.92f));

        etTitle = root.findViewById(R.id.etTitle);
        etNote = root.findViewById(R.id.etNote);
        etNewTag = root.findViewById(R.id.etNewTag);
        radioZone = root.findViewById(R.id.radioZone);
        radioDeadline = root.findViewById(R.id.radioDeadline);
        blockDeadline = root.findViewById(R.id.blockDeadline);
        quickDeadlineRow = root.findViewById(R.id.quickDeadlineRow);
        tvDeadlinePreview = root.findViewById(R.id.tvDeadlinePreview);
        chipTags = root.findViewById(R.id.chipTags);
        chipRemind = root.findViewById(R.id.chipRemind);
        radioStage = root.findViewById(R.id.radioStage);
        radioPriority = root.findViewById(R.id.radioPriority);
        blockCardAlpha = root.findViewById(R.id.blockCardAlpha);
        seekCardAlpha = root.findViewById(R.id.seekCardAlpha);
        tvCardAlphaLabel = root.findViewById(R.id.tvCardAlphaLabel);
        cardImagePreview = root.findViewById(R.id.cardImagePreview);
        imgCardPreview = root.findViewById(R.id.imgCardPreview);
        tvCardBgHint = root.findViewById(R.id.tvCardBgHint);

        TextView sheetTitle = root.findViewById(R.id.tvSheetTitle);
        sheetTitle.setText(creating ? R.string.tab_new_title : R.string.tab_edit_title);

        etTitle.setText(working.getTitle());
        etNote.setText(working.getNote());

        radioZone.setOnCheckedChangeListener((g, id) -> refreshZone());
        ((RadioButton) root.findViewById(R.id.radioLongTerm)).setChecked(!working.hasDeadline());
        radioDeadline.setChecked(working.hasDeadline());
        if (!working.hasDeadline()) {
            // 新建长线任务时，给一个「明天」的默认截止日期备用
            deadline = Deadline.endOfDay(System.currentTimeMillis() + 24L * 3600 * 1000);
        }

        buildQuickDeadlineButtons();
        buildTagChips();
        buildRemindChips();
        buildStageRadios(radioStage);
        buildPriorityRadios(radioPriority);

        root.findViewById(R.id.btnPickDate).setOnClickListener(v ->
                DateTimePicker.show(context, deadline, true, (millis, withTime) -> {
                    deadline = millis;
                    allDay = !withTime;
                    refreshZone();
                }));
        root.findViewById(R.id.btnPickTime).setOnClickListener(v ->
                DateTimePicker.showTime(context, deadline, (millis, withTime) -> {
                    deadline = millis;
                    allDay = false;
                    refreshZone();
                }));

        root.findViewById(R.id.btnAddTag).setOnClickListener(v -> {
            String t = etNewTag.getText().toString().trim();
            if (t.isEmpty()) return;
            store.addTagToPool(t, true);
            rebuildTagChips(null);
            toggleTag(t);
            etNewTag.setText("");
        });

        Button btnBg = root.findViewById(R.id.btnCardBg);
        btnBg.setOnClickListener(v -> {
            if (imageRequests != null) imageRequests.requestCardImage();
        });
        root.findViewById(R.id.btnCardBgClear).setOnClickListener(v -> {
            cardImage = "";
            refreshCardBlock();
        });

        seekCardAlpha.setProgress(Math.max(0, Math.min(255, working.getCardImageAlpha())));
        seekCardAlpha.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateCardAlphaLabel();
                refreshCardPreview();
            }
        });

        root.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        Button save = root.findViewById(R.id.btnSave);
        save.setText(creating ? dialog.getContext().getString(R.string.action_save) : "改好啦～");
        save.setOnClickListener(v -> doSave());

        refreshZone();
        refreshCardBlock();
    }

    public void show() {
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public Task getWorking() {
        return working;
    }

    /** 编辑面板里当前选中的图片路径（可能还没保存） */
    public String getPickedImagePath() {
        return cardImage == null ? "" : cardImage;
    }

    public boolean hasPickedImage() {
        return cardImage != null && !cardImage.isEmpty();
    }

    /** 选图完成后回填 */
    public void setPickedImage(String path) {
        if (path == null || path.isEmpty()) {
            Toast.makeText(dialog.getContext(), R.string.copy_no_image, Toast.LENGTH_SHORT).show();
            return;
        }
        // 换图时删除旧图；已经保存过的那张由 Activity 在保存后统一清理
        String previous = cardImage;
        if (previous != null && !previous.isEmpty() && !previous.equals(path)
                && !previous.equals(working.getCardImage())) {
            com.todolife.app.util.ImageStore.deleteIfUnused(dialog.getContext(), previous, null);
        }
        cardImage = path;
        refreshCardBlock();
    }

    // ------------------------------------------------------------------ 内部

    private void refreshZone() {
        boolean hasDdl = radioDeadline.isChecked();
        blockDeadline.setVisibility(hasDdl ? View.VISIBLE : View.GONE);
        if (hasDdl) {
            int color = Deadline.urgencyColor(deadline);
            tvDeadlinePreview.setText("截止：" + Deadline.full(deadline, !allDay)
                    + "\n" + Deadline.label(deadline) + "（" + Deadline.urgencyLevel(deadline) + "）");            tvDeadlinePreview.setTextColor(color);
        }
    }

    private void buildQuickDeadlineButtons() {
        quickDeadlineRow.removeAllViews();
        final Object[][] quick = new Object[][]{
                {"今天", 0}, {"明天", 1}, {"+3天", 3}, {"+7天", 7}, {"+14天", 14}, {"+30天", 30}
        };
        for (Object[] pair : quick) {
            TextView b = new TextView(dialog.getContext());
            b.setText((String) pair[0]);
            b.setTextSize(12.5f);
            b.setGravity(android.view.Gravity.CENTER);
            b.setTextColor(0xFF8C8493);
            b.setBackgroundResource(R.drawable.bg_text_chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
            lp.rightMargin = dp(4);
            b.setLayoutParams(lp);
            int days = (Integer) pair[1];
            b.setOnClickListener(v -> {
                deadline = Deadline.endOfDay(System.currentTimeMillis()
                        + days * 24L * 3600 * 1000);
                allDay = true;
                refreshZone();
            });
            quickDeadlineRow.addView(b);
        }
    }

    private void buildTagChips() {
        List<String> pool = new ArrayList<>();
        pool.add(Tag.REQUIRED);
        pool.add(Tag.OPTIONAL);
        pool.add(Tag.SCHOOL);
        pool.add(Tag.PERSONAL);
        pool.addAll(store.customTagPool());
        chipTags.removeAllViews();
        for (String name : pool) {
            final TextView chip = chipFor(name, Tag.REQUIRED.equals(name) || Tag.SCHOOL.equals(name));
            final boolean fixed = Tag.REQUIRED.equals(name) || Tag.OPTIONAL.equals(name)
                    || Tag.SCHOOL.equals(name) || Tag.PERSONAL.equals(name);
            chip.setOnClickListener(v -> {
                boolean nowChecked = !isChipChecked(v);
                setChipChecked((TextView) v, nowChecked);
                if (nowChecked && fixed) {
                    // 固定维度的互斥：选中「必做」就把「选做」取消，反之亦然
                    String opposite = null;
                    if (Tag.REQUIRED.equals(name)) opposite = Tag.OPTIONAL;
                    else if (Tag.OPTIONAL.equals(name)) opposite = Tag.REQUIRED;
                    else if (Tag.SCHOOL.equals(name)) opposite = Tag.PERSONAL;
                    else if (Tag.PERSONAL.equals(name)) opposite = Tag.SCHOOL;
                    if (opposite != null) {
                        for (int i = 0; i < chipTags.getChildCount(); i++) {
                            View child = chipTags.getChildAt(i);
                            Object t = child.getTag();
                            if (t instanceof String && opposite.equals(t)) {
                                setChipChecked((TextView) child, false);
                            }
                        }
                    }
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            lp.bottomMargin = dp(6);
            chip.setLayoutParams(lp);
            chipTags.addView(chip);
        }
    }

    private void rebuildTagChips(String keepChecked) {
        List<String> checked = new ArrayList<>();
        for (int i = 0; i < chipTags.getChildCount(); i++) {
            View child = chipTags.getChildAt(i);
            if (isChipChecked(child) && child.getTag() instanceof String) {
                checked.add((String) child.getTag());
            }
        }
        if (keepChecked != null) checked.add(keepChecked);
        buildTagChips();
        for (int i = 0; i < chipTags.getChildCount(); i++) {
            View child = chipTags.getChildAt(i);
            Object t = child.getTag();
            if (t instanceof String) {
                setChipChecked((TextView) child, checked.contains(t));
            }
        }
    }

    /**
     * 标签胶囊：用普通 TextView 实现，选中状态存在 view 的 tag 里，
     * 背景用两套 drawable 切换。不用 Material Chip —— 它的自动配色会掺进主题的
     * colorSecondary，在这套自定义配色下渲染出的颜色不可控（之前就是它变成品红）。
     */
    private TextView chipFor(String name, boolean checked) {
        androidx.appcompat.widget.AppCompatTextView chip =
                new androidx.appcompat.widget.AppCompatTextView(dialog.getContext());
        chip.setText(name);
        chip.setTextSize(13f);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setMinWidth(0);
        chip.setMinimumWidth(0);
        chip.setMinHeight(dp(36));
        chip.setMinimumHeight(0);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setTag(name);
        setChipChecked(chip, checked);
        return chip;
    }

    /** 切换胶囊的选中外观：选中=实心樱粉+白字，未选中=极淡粉底+深灰字 */
    private void setChipChecked(TextView chip, boolean checked) {
        chip.setBackgroundResource(checked
                ? R.drawable.bg_tag_checked : R.drawable.bg_tag_unchecked);
        chip.setTextColor(checked ? 0xFFFFFFFF : 0xFF8C8493);
        chip.setTag(R.id.tag_chip_state, checked ? Boolean.TRUE : Boolean.FALSE);
    }

    private boolean isChipChecked(View v) {
        Object o = v.getTag(R.id.tag_chip_state);
        return o instanceof Boolean && (Boolean) o;
    }

    private void toggleTag(String tag) {
        if (selectedTags.contains(tag)) selectedTags.remove(tag);
        else selectedTags.add(tag);
    }

    private void buildRemindChips() {
        final int[][] options = new int[][]{
                {-1, 0}, {0, 0}, {10, 0}, {60, 0}, {180, 0}, {1440, 0}, {4320, 0}
        };
        final String[] labels = {"不提醒", "截止时", "提前10分钟", "提前1小时",
                "提前3小时", "提前1天", "提前3天"};
        chipRemind.removeAllViews();
        for (int i = 0; i < options.length; i++) {
            final TextView chip = chipFor(labels[i], options[i][0] == remindMinutes);
            chip.setTextSize(12.5f);
            final int minutes = options[i][0];
            chip.setOnClickListener(v -> {
                // 单选：先把所有兄弟取消，再选中自己
                for (int k = 0; k < chipRemind.getChildCount(); k++) {
                    View child = chipRemind.getChildAt(k);
                    if (child instanceof TextView) setChipChecked((TextView) child, false);
                }
                setChipChecked((TextView) v, true);
                remindMinutes = minutes;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            lp.bottomMargin = dp(6);
            chip.setLayoutParams(lp);
            chipRemind.addView(chip);
        }
    }

    private void buildStageRadios(RadioGroup group) {
        group.removeAllViews();
        group.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < Task.STAGES.length; i++) {
            RadioButton rb = new RadioButton(dialog.getContext());
            rb.setText(Task.STAGES[i]);
            rb.setTextSize(13.5f);
            rb.setId(1000 + i);
            rb.setChecked(i == working.getStage());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            rb.setLayoutParams(lp);
            group.addView(rb);
        }
    }

    private void buildPriorityRadios(RadioGroup group) {
        group.removeAllViews();
        group.setOrientation(RadioGroup.HORIZONTAL);
        final String[] labels = {"普通", "重要", "紧急"};
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(dialog.getContext());
            rb.setText(labels[i]);
            rb.setTextSize(13.5f);
            rb.setId(2000 + i);
            rb.setChecked(i == working.getPriority());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            rb.setLayoutParams(lp);
            group.addView(rb);
        }
    }

    private void refreshCardBlock() {
        boolean hasImage = cardImage != null && !cardImage.isEmpty();
        blockCardAlpha.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        cardImagePreview.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        tvCardBgHint.setText(hasImage ? "这张已经有图啦～" : "还没有图呢，先用淡淡的颜色代替～");
        updateCardAlphaLabel();
        refreshCardPreview();
    }

    private void updateCardAlphaLabel() {
        tvCardAlphaLabel.setText("图片清楚一点还是淡一点？现在 " + seekCardAlpha.getProgress()
                + "（越大越清楚）");
    }

    private void refreshCardPreview() {
        if (cardImage == null || cardImage.isEmpty()) return;
        com.todolife.app.util.ImageStore.loadInto(imgCardPreview, cardImage,
                Math.max(30, seekCardAlpha.getProgress()), dp(400), dp(200));
    }

    private void doSave() {
        String title = etTitle.getText().toString().trim();
        if (TextUtils.isEmpty(title)) {
            Toast.makeText(dialog.getContext(), R.string.need_title, Toast.LENGTH_SHORT).show();
            etTitle.requestFocus();
            return;
        }
        View selectedStage = radioStage.findViewById(radioStage.getCheckedRadioButtonId());
        View selectedPriority = radioPriority.findViewById(radioPriority.getCheckedRadioButtonId());

        Task t = creating ? new Task() : working;
        t.setTitle(title);
        t.setNote(etNote.getText().toString().trim());
        if (!radioDeadline.isChecked()) {
            t.setDeadline(Task.NO_DEADLINE);
            t.setRemindBeforeMinutes(-1);
        } else {
            t.setDeadline(deadline <= 0 ? Deadline.endOfDay(System.currentTimeMillis()) : deadline);
            t.setAllDay(allDay);
            t.setRemindBeforeMinutes(remindMinutes);
        }

        // 标签：固定维度写进布尔字段，其余进自由标签
        boolean required = true;
        boolean school = true;
        List<String> customs = new ArrayList<>();
        for (int i = 0; i < chipTags.getChildCount(); i++) {
            View child = chipTags.getChildAt(i);
            if (!isChipChecked(child)) continue;
            Object tagObj = child.getTag();
            if (!(tagObj instanceof String)) continue;
            String name = (String) tagObj;
            if (Tag.REQUIRED.equals(name)) required = true;
            else if (Tag.OPTIONAL.equals(name)) required = false;
            else if (Tag.SCHOOL.equals(name)) school = true;
            else if (Tag.PERSONAL.equals(name)) school = false;
            else customs.add(name);
        }
        for (String s : selectedTags) {
            if (!customs.contains(s)) customs.add(s);
        }
        t.setRequired(required);
        t.setSchool(school);
        t.setCustomTags(customs);

        if (selectedStage != null && selectedStage.getId() >= 1000) {
            t.setStage(selectedStage.getId() - 1000);
        }
        if (selectedPriority != null && selectedPriority.getId() >= 2000) {
            t.setPriority(selectedPriority.getId() - 2000);
        }
        if (cardImage != null && !cardImage.isEmpty()) {
            t.setCardImage(cardImage);
            t.setCardImageAlpha(seekCardAlpha.getProgress());
        } else {
            t.setCardImage("");
            t.setCardImageAlpha(Prefs.cardAlpha(dialog.getContext()));
        }

        store.upsert(t);
        ReminderScheduler.schedule(dialog.getContext(), t);
        dialog.dismiss();
        if (onSaved != null) onSaved.onSaved(t);
    }

    private int dp(int v) {
        return Math.round(v * dialog.getContext().getResources().getDisplayMetrics().density);
    }

    /** 只关心进度的 SeekBar 监听 */
    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
