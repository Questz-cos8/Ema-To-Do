package com.todolife.app.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一条任务。
 *
 * <p>核心约定：{@link #deadline} 为 0 表示「长线任务」（没有截止日期）。判断一条任务属于哪个区域，
 * 只看这个字段，与标签无关。</p>
 *
 * <p>标签分三类用途：</p>
 * <ul>
 *   <li>{@link #required}：必做 / 选做（true = 必做）</li>
 *   <li>{@link #school}：学校 / 个人（true = 学校）</li>
 *   <li>{@link #customTags}：自由标签，用于任意分类</li>
 * </ul>
 */
public class Task {

    public static final long NO_DEADLINE = 0L;

    /** 状态：未完成 */
    public static final int STATUS_TODO = 0;
    /** 状态：已完成 */
    public static final int STATUS_DONE = 1;

    /** 进度阶段 */
    public static final String[] STAGES = new String[]{"未开始", "进行中", "待收尾"};

    private String id = UUID.randomUUID().toString();
    private String title = "";
    private String note = "";

    /** 截止时间（毫秒时间戳，当天 23:59 视为截止时刻）；0 = 无 DDL */
    private long deadline = NO_DEADLINE;

    /** 是否为「全天」截止（没有具体时刻） */
    private boolean allDay = true;

    private int status = STATUS_TODO;
    private long createdAt = System.currentTimeMillis();
    private long updatedAt = System.currentTimeMillis();
    private long doneAt = NO_DEADLINE;

    /** 必做 true / 选做 false */
    private boolean required = true;
    /** 学校 true / 个人 false */
    private boolean school = true;

    private List<String> customTags = new ArrayList<>();

    /** 进度阶段：0 未开始 / 1 进行中 / 2 待收尾 */
    private int stage = 0;

    /** 重要程度 0 普通 / 1 重要 / 2 紧急（手动置顶用） */
    private int priority = 0;

    /** 置顶 */
    private boolean pinned = false;

    /** 卡片背景图（持久化后的 file:// 路径），空 = 使用默认样式 */
    private String cardImage = "";

    /** 卡片背景透明度 0..255（255 = 完全不透明），默认 150 让文字仍然清晰 */
    private int cardImageAlpha = 150;

    /** 提醒提前量（分钟），-1 = 不提醒 */
    private int remindBeforeMinutes = -1;

    // ------------------------------------------------------------------ 便捷判断

    public boolean hasDeadline() {
        return deadline > NO_DEADLINE;
    }

    public boolean isDone() {
        return status == STATUS_DONE;
    }

    public boolean isLongTerm() {
        return !hasDeadline();
    }

    public boolean isOverdue() {
        return hasDeadline() && !isDone() && deadline < System.currentTimeMillis();
    }

    public boolean isToday() {
        return hasDeadline() && Deadline.daysLeft(deadline) == 0;
    }

    public String stageName() {
        if (stage < 0 || stage >= STAGES.length) return STAGES[0];
        return STAGES[stage];
    }

    public String necessityName() {
        return required ? "必做" : "选做";
    }

    public String scopeName() {
        return school ? "学校" : "个人";
    }

    /** 卡片上显示的标签集合（前 4 个用于绘制） */
    @NonNull
    public List<String> allTags() {
        List<String> out = new ArrayList<>();
        out.add(necessityName());
        out.add(scopeName());
        for (String t : customTags) {
            if (t != null && !t.trim().isEmpty()) out.add(t.trim());
        }
        return out;
    }

    public void touch() {
        updatedAt = System.currentTimeMillis();
    }

    public Task copy() {
        Task t = new Task();
        t.id = id;
        t.title = title;
        t.note = note;
        t.deadline = deadline;
        t.allDay = allDay;
        t.status = status;
        t.createdAt = createdAt;
        t.updatedAt = updatedAt;
        t.doneAt = doneAt;
        t.required = required;
        t.school = school;
        t.customTags = new ArrayList<>(customTags);
        t.stage = stage;
        t.priority = priority;
        t.pinned = pinned;
        t.cardImage = cardImage;
        t.cardImageAlpha = cardImageAlpha;
        t.remindBeforeMinutes = remindBeforeMinutes;
        return t;
    }

    // ------------------------------------------------------------------ JSON

    @NonNull
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("title", title);
            o.put("note", note);
            o.put("deadline", deadline);
            o.put("allDay", allDay);
            o.put("status", status);
            o.put("createdAt", createdAt);
            o.put("updatedAt", updatedAt);
            o.put("doneAt", doneAt);
            o.put("required", required);
            o.put("school", school);
            JSONArray arr = new JSONArray();
            for (String t : customTags) {
                if (t != null) arr.put(t);
            }
            o.put("tags", arr);
            o.put("stage", stage);
            o.put("priority", priority);
            o.put("pinned", pinned);
            o.put("cardImage", cardImage);
            o.put("cardImageAlpha", cardImageAlpha);
            o.put("remind", remindBeforeMinutes);
        } catch (JSONException e) {
            // 全部字段都是本类的强类型字段，理论上不会失败；真的失败也不能让
            // 「删除 / 保存任务」这种用户操作跟着崩掉，所以这里吞掉异常。
        }
        return o;
    }

    @NonNull
    public static Task fromJson(@Nullable JSONObject o) {
        Task t = new Task();
        if (o == null) return t;
        String id = o.optString("id", "");
        if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
        t.id = id;
        t.title = o.optString("title", "");
        t.note = o.optString("note", "");
        t.deadline = o.optLong("deadline", NO_DEADLINE);
        t.allDay = o.optBoolean("allDay", true);
        t.status = o.optInt("status", STATUS_TODO);
        t.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        t.updatedAt = o.optLong("updatedAt", t.createdAt);
        t.doneAt = o.optLong("doneAt", NO_DEADLINE);
        t.required = o.optBoolean("required", true);
        t.school = o.optBoolean("school", true);
        t.stage = o.optInt("stage", 0);
        t.priority = o.optInt("priority", 0);
        t.pinned = o.optBoolean("pinned", false);
        t.cardImage = o.optString("cardImage", "");
        t.cardImageAlpha = o.optInt("cardImageAlpha", 150);
        t.remindBeforeMinutes = o.optInt("remind", -1);

        JSONArray arr = o.optJSONArray("tags");
        t.customTags = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (s != null && !s.trim().isEmpty()) t.customTags.add(s.trim());
            }
        }
        return t;
    }

    // ------------------------------------------------------------------ getters / setters

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getTitle() { return title == null ? "" : title; }

    public void setTitle(String title) { this.title = title == null ? "" : title; }

    public String getNote() { return note == null ? "" : note; }

    public void setNote(String note) { this.note = note == null ? "" : note; }

    public long getDeadline() { return deadline; }

    public void setDeadline(long deadline) { this.deadline = deadline; }

    public boolean isAllDay() { return allDay; }

    public void setAllDay(boolean allDay) { this.allDay = allDay; }

    public int getStatus() { return status; }

    public void setStatus(int status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }

    public void setCreatedAt(long v) { this.createdAt = v; }

    public long getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(long v) { this.updatedAt = v; }

    public long getDoneAt() { return doneAt; }

    public void setDoneAt(long v) { this.doneAt = v; }

    public boolean isRequired() { return required; }

    public void setRequired(boolean v) { this.required = v; }

    public boolean isSchool() { return school; }

    public void setSchool(boolean v) { this.school = v; }

    public List<String> getCustomTags() { return customTags == null ? new ArrayList<>() : customTags; }

    public void setCustomTags(List<String> tags) { this.customTags = tags == null ? new ArrayList<>() : tags; }

    public int getStage() { return stage; }

    public void setStage(int stage) { this.stage = stage; }

    public int getPriority() { return priority; }

    public void setPriority(int priority) { this.priority = priority; }

    public boolean isPinned() { return pinned; }

    public void setPinned(boolean v) { this.pinned = v; }

    public String getCardImage() { return cardImage == null ? "" : cardImage; }

    public void setCardImage(String v) { this.cardImage = v == null ? "" : v; }

    public boolean hasCardImage() { return !getCardImage().isEmpty(); }

    public int getCardImageAlpha() { return cardImageAlpha; }

    public void setCardImageAlpha(int v) { this.cardImageAlpha = Math.max(0, Math.min(255, v)); }

    public int getRemindBeforeMinutes() { return remindBeforeMinutes; }

    public void setRemindBeforeMinutes(int v) { this.remindBeforeMinutes = v; }

    public boolean hasReminder() { return remindBeforeMinutes >= 0 && hasDeadline(); }
}
