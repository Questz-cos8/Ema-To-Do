package com.todolife.app.data;

import com.todolife.app.model.Task;
import com.todolife.app.model.Tag;

/**
 * 当前列表的筛选条件：关键词 + 标签 + 是否只看必做等。
 * 条件由 {@code MainActivity} 统一持有，各页面共享。
 */
public class Query {

    private String keyword = "";
    private String tag = "全部";
    private boolean onlyOverdue = false;
    private boolean onlyToday = false;

    public String getKeyword() {
        return keyword == null ? "" : keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? "" : keyword;
    }

    public String getTag() {
        return tag == null ? "全部" : tag;
    }

    public void setTag(String tag) {
        this.tag = tag == null ? "全部" : tag;
    }

    public boolean isOnlyOverdue() {
        return onlyOverdue;
    }

    public void setOnlyOverdue(boolean v) {
        onlyOverdue = v;
    }

    public boolean isOnlyToday() {
        return onlyToday;
    }

    public void setOnlyToday(boolean v) {
        onlyToday = v;
    }

    public boolean isActive() {
        return !getKeyword().isEmpty() || !"全部".equals(getTag()) || onlyOverdue || onlyToday;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (!getKeyword().isEmpty()) sb.append("“").append(getKeyword()).append("” ");
        if (!"全部".equals(getTag())) sb.append("#").append(getTag()).append(" ");
        if (onlyOverdue) sb.append("仅逾期 ");
        if (onlyToday) sb.append("仅今天 ");
        return sb.toString().trim();
    }

    public void clear() {
        keyword = "";
        tag = "全部";
        onlyOverdue = false;
        onlyToday = false;
    }

    /** 判断任务是否通过当前筛选 */
    public boolean accept(Task t) {
        if (t == null) return false;
        if (onlyOverdue && !t.isOverdue()) return false;
        if (onlyToday && !(t.hasDeadline()
                && com.todolife.app.model.Deadline.daysLeft(t.getDeadline()) == 0
                && !t.isDone())) return false;
        if (!"全部".equals(getTag()) && !Tag.matches(t, getTag())) return false;
        String k = getKeyword().trim();
        if (k.isEmpty()) return true;
        String lower = k.toLowerCase();
        if (t.getTitle().toLowerCase().contains(lower)) return true;
        if (t.getNote().toLowerCase().contains(lower)) return true;
        for (String tag : t.allTags()) {
            if (tag.toLowerCase().contains(lower)) return true;
        }
        return false;
    }
}
