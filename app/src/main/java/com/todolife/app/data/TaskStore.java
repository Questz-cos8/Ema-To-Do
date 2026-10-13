package com.todolife.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.todolife.app.model.Deadline;
import com.todolife.app.model.Task;
import com.todolife.app.model.Tag;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 全部任务的唯一数据源（内存 + JSON 持久化）。
 *
 * <p>刻意不引入数据库：任务规模在几百条量级，用 SharedPreferences 存一段 JSON 已经完全够用，
 * 同时让备份 / 恢复变成「导出这段 JSON」这么简单。</p>
 */
public final class TaskStore {

    private static final String PREF = "todolife_store_v1";
    private static final String KEY_TASKS = "tasks_json";
    private static final String KEY_TAGS = "custom_tags_json";
    private static final String KEY_SEEDED = "seeded";

    private static TaskStore INSTANCE;

    public interface Listener {
        void onStoreChanged();
    }

    private final Context appContext;
    private final SharedPreferences prefs;
    private final List<Task> tasks = new ArrayList<>();
    private final List<String> customTagPool = new ArrayList<>();
    private final Set<Listener> listeners = new LinkedHashSet<>();

    private TaskStore(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized TaskStore get(Context context) {
        if (INSTANCE == null) INSTANCE = new TaskStore(context);
        return INSTANCE;
    }

    // ------------------------------------------------------------ 监听

    public void addListener(Listener l) {
        if (l != null) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.onStoreChanged();
        }
    }

    // ------------------------------------------------------------ 读写

    public String exportJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "TodoLife");
            root.put("version", 1);
            root.put("exportedAt", System.currentTimeMillis());
            JSONArray arr = new JSONArray();
            for (Task t : tasks) arr.put(t.toJson());
            root.put("tasks", arr);
            JSONArray tags = new JSONArray();
            for (String s : customTagPool) tags.put(s);
            root.put("customTags", tags);
            return root.toString(2);
        } catch (JSONException e) {
            return "{}";
        }
    }

    /** 导入备份；返回成功导入的任务条数，-1 表示格式不正确 */
    public int importJson(String json) {
        if (json == null) return -1;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("tasks");
            if (arr == null) return -1;
            List<Task> imported = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) imported.add(Task.fromJson(o));
            }
            JSONArray tags = root.optJSONArray("customTags");
            if (tags != null) {
                for (int i = 0; i < tags.length(); i++) {
                    String s = tags.optString(i, "");
                    if (s != null && !s.trim().isEmpty()) addTagToPool(s, false);
                }
            }
            save();
            return imported.size();
        } catch (JSONException e) {
            return -1;
        }
    }

    /** 用备份完整替换当前数据 */
    public boolean replaceAll(String json) {
        if (json == null) return false;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("tasks");
            if (arr == null) return false;
            List<Task> imported = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) imported.add(Task.fromJson(o));
            }
            JSONArray tags = root.optJSONArray("customTags");
            if (tags != null) {
                for (int i = 0; i < tags.length(); i++) {
                    String s = tags.optString(i, "");
                    if (s != null && !s.trim().isEmpty()) addTagToPool(s, false);
                }
            }
            tasks.clear();
            tasks.addAll(imported);
            save();
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    private void load() {
        tasks.clear();
        customTagPool.clear();
        String raw = prefs.getString(KEY_TASKS, null);
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) tasks.add(Task.fromJson(o));
                }
            } catch (JSONException ignored) {
            }
        }
        String tagRaw = prefs.getString(KEY_TAGS, null);
        if (tagRaw != null && !tagRaw.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(tagRaw);
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "");
                    if (s != null && !s.trim().isEmpty()) customTagPool.add(s.trim());
                }
            } catch (JSONException ignored) {
            }
        }
        if (customTagPool.isEmpty()) customTagPool.addAll(Tag.DEFAULT_CUSTOM);
        if (tasks.isEmpty() && !prefs.getBoolean(KEY_SEEDED, false)) {
            prefs.edit().putBoolean(KEY_SEEDED, true).apply();
        }
    }

    public void save() {
        JSONArray arr = new JSONArray();
        for (Task t : tasks) arr.put(t.toJson());
        JSONArray tags = new JSONArray();
        for (String s : customTagPool) tags.put(s);
        prefs.edit()
                .putString(KEY_TASKS, arr.toString())
                .putString(KEY_TAGS, tags.toString())
                .apply();
        notifyChanged();
    }

    // ------------------------------------------------------------ 增删改

    public List<Task> all() {
        return Collections.unmodifiableList(tasks);
    }

    public Task byId(String id) {
        if (id == null) return null;
        for (Task t : tasks) {
            if (id.equals(t.getId())) return t;
        }
        return null;
    }

    /** 新增或按 id 覆盖保存 */
    public void upsert(Task task) {
        if (task == null) return;
        task.touch();
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId().equals(task.getId())) {
                tasks.set(i, task);
                for (String c : task.getCustomTags()) addTagToPool(c, false);
                save();
                return;
            }
        }
        tasks.add(task);
        for (String c : task.getCustomTags()) addTagToPool(c, false);
        save();
    }

    public void delete(String id) {
        Task t = byId(id);
        if (t != null) {
            tasks.remove(t);
            save();
        }
    }

    public void markDone(String id, boolean done) {
        Task t = byId(id);
        if (t == null) return;
        t.setStatus(done ? Task.STATUS_DONE : Task.STATUS_TODO);
        t.setDoneAt(done ? System.currentTimeMillis() : Task.NO_DEADLINE);
        t.touch();
        save();
    }

    public void clearDone() {
        for (int i = tasks.size() - 1; i >= 0; i--) {
            if (tasks.get(i).isDone()) tasks.remove(i);
        }
        save();
    }

    // ------------------------------------------------------------ 标签池

    public List<String> customTagPool() {
        return Collections.unmodifiableList(customTagPool);
    }

    public void addTagToPool(String tag, boolean persist) {
        if (tag == null) return;
        String s = tag.trim();
        if (s.isEmpty()) return;
        for (String existing : customTagPool) {
            if (existing.equalsIgnoreCase(s)) return;
        }
        customTagPool.add(s);
        if (persist) save();
    }

    public void removeTagFromPool(String tag) {
        if (tag == null) return;
        for (int i = customTagPool.size() - 1; i >= 0; i--) {
            if (customTagPool.get(i).equalsIgnoreCase(tag.trim())) customTagPool.remove(i);
        }
        save();
    }

    // ------------------------------------------------------------ 排序

    /** 待办排序：置顶 → 逾期/临近优先 → 有 DDL 优先 → 截止时间 → 创建时间 */
    public static final Comparator<Task> TODO_ORDER = (a, b) -> {
        if (a.isPinned() != b.isPinned()) return a.isPinned() ? -1 : 1;
        int da = a.hasDeadline() ? Deadline.daysLeft(a.getDeadline()) : Integer.MAX_VALUE;
        int db = b.hasDeadline() ? Deadline.daysLeft(b.getDeadline()) : Integer.MAX_VALUE;
        if (da != db) return Integer.compare(da, db);
        if (a.getPriority() != b.getPriority()) return Integer.compare(b.getPriority(), a.getPriority());
        long ca = a.getCreatedAt();
        long cb = b.getCreatedAt();
        return Long.compare(cb, ca);
    };

    /** 已完成排序：最近完成的排前面 */
    public static final Comparator<Task> DONE_ORDER = (a, b) -> Long.compare(b.getDoneAt(), a.getDoneAt());
}
