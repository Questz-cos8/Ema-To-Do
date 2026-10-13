package com.todolife.app.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 标签体系。
 *
 * <p>「必做 / 选做」与「学校 / 个人」是固定维度（每条任务都必须属于其中一个），
 * 属于任务自身的字段而不是自由标签，因此任何页面都能稳定地按这两个维度筛选。
 * 自由标签（如「物理」「期末」）可任意增删。</p>
 */
public final class Tag {

    public static final String REQUIRED = "必做";
    public static final String OPTIONAL = "选做";
    public static final String SCHOOL = "学校";
    public static final String PERSONAL = "个人";

    /** 自由标签默认建议（首次启动写入） */
    public static final List<String> DEFAULT_CUSTOM =
            Arrays.asList("考试", "作业", "项目", "实验", "阅读", "生活", "运动", "实习");

    private Tag() {
    }

    /** 全部已使用过的标签名（固定维度 + 自定义），用于筛选面板 */
    public static List<String> allFilterNames(List<Task> tasks, List<String> customPool) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(REQUIRED);
        set.add(OPTIONAL);
        set.add(SCHOOL);
        set.add(PERSONAL);
        if (customPool != null) set.addAll(customPool);
        if (tasks != null) {
            for (Task t : tasks) {
                for (String c : t.getCustomTags()) {
                    if (c != null && !c.trim().isEmpty()) set.add(c.trim());
                }
            }
        }
        return new ArrayList<>(set);
    }

    /** 判定任务是否命中某个筛选标签 */
    public static boolean matches(Task t, String tag) {
        if (t == null || tag == null || tag.isEmpty() || "全部".equals(tag)) return true;
        if (REQUIRED.equals(tag)) return t.isRequired();
        if (OPTIONAL.equals(tag)) return !t.isRequired();
        if (SCHOOL.equals(tag)) return t.isSchool();
        if (PERSONAL.equals(tag)) return !t.isSchool();
        for (String c : t.getCustomTags()) {
            if (tag.equalsIgnoreCase(c)) return true;
        }
        return false;
    }
}
