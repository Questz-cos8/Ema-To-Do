package com.todolife.app.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * 一份存进 App 的文件（图片笔记 or 学习资料）。
 *
 * <p>文件本体复制到私有目录（{@code filesDir/docs}），这里只记元信息。
 * 复制而不是记住 content:// uri：外部授权随时可能失效，重启后图就白了。</p>
 */
public class Doc {

    /** 相册笔记：上课拍的板书、老师发的安排截图之类 */
    public static final int CAT_NOTE = 0;
    /** 学习文件：学校发的文档、讲义之类 */
    public static final int CAT_STUDY = 1;

    private String id = UUID.randomUUID().toString();

    /** 展示用的名字（一般是原文件名） */
    private String name = "";

    private int category = CAT_NOTE;

    /** 私有目录里的绝对路径 */
    private String path = "";

    private String mime = "";
    private long size = 0;
    private long addedAt = System.currentTimeMillis();

    public boolean isImage() {
        if (mime != null && mime.startsWith("image/")) return true;
        String lower = getPath().toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp")
                || lower.endsWith(".heic") || lower.endsWith(".heif");
    }

    /** 用于文件卡片右侧的小字：PDF / DOCX / JPG … */
    public String extLabel() {
        int dot = getPath().lastIndexOf('.');
        if (dot >= 0 && dot < getPath().length() - 1) {
            String ext = getPath().substring(dot + 1).toUpperCase();
            if (ext.length() <= 5) return ext;
        }
        if (mime != null && mime.contains("/")) {
            String sub = mime.substring(mime.indexOf('/') + 1);
            if (!sub.isEmpty()) return sub.toUpperCase();
        }
        return "FILE";
    }

    @NonNull
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("category", category);
            o.put("path", path);
            o.put("mime", mime);
            o.put("size", size);
            o.put("addedAt", addedAt);
        } catch (JSONException e) {
            // 都是强类型字段，理论上不会失败；真失败也不能让导入/删除崩掉
        }
        return o;
    }

    @NonNull
    public static Doc fromJson(@Nullable JSONObject o) {
        Doc d = new Doc();
        if (o == null) return d;
        String id = o.optString("id", "");
        d.id = (id == null || id.isEmpty()) ? UUID.randomUUID().toString() : id;
        d.name = o.optString("name", "");
        d.category = o.optInt("category", CAT_NOTE);
        d.path = o.optString("path", "");
        d.mime = o.optString("mime", "");
        d.size = o.optLong("size", 0);
        d.addedAt = o.optLong("addedAt", System.currentTimeMillis());
        return d;
    }

    public String getId() { return id; }

    public void setId(String v) { this.id = v == null ? "" : v; }

    public String getName() { return name == null ? "" : name; }

    public void setName(String v) { this.name = v == null ? "" : v; }

    public int getCategory() { return category; }

    public void setCategory(int v) { this.category = v; }

    public String getPath() { return path == null ? "" : path; }

    public void setPath(String v) { this.path = v == null ? "" : v; }

    public String getMime() { return mime == null ? "" : mime; }

    public void setMime(String v) { this.mime = v == null ? "" : v; }

    public long getSize() { return size; }

    public void setSize(long v) { this.size = v; }

    public long getAddedAt() { return addedAt; }

    public void setAddedAt(long v) { this.addedAt = v; }
}
