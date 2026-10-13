package com.todolife.app.ui;

import com.todolife.app.data.TaskStore;
import com.todolife.app.model.Deadline;
import com.todolife.app.model.Task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 示例数据：让第一次打开的人立刻看懂「两个区域 + 标签 + 圆点颜色」的用法。
 * 文案用轻松的语气，顺便把五种圆点颜色都覆盖到。
 */
public final class SampleData {

    private SampleData() {
    }

    public static void insert(TaskStore store) {
        long now = System.currentTimeMillis();

        // 1. 三天内到期的学校作业 —— 樱红圆点
        store.upsert(task("高数作业第 7 章（要交纸质版）",
                "课本 P123 第 3、5、7、9 题\n写到作业本上，下课前交给课代表",
                Deadline.endOfDay(now + TimeUnit.DAYS.toMillis(2)),
                true, true, 1, 0, Arrays.asList("作业")));

        // 2. 已经逾期的个人小事 —— 暗樱圆点
        store.upsert(task("把图书馆的书还掉",
                "《线性代数辅导》已经借了 30 天啦…今天一定要还，不然要罚款",
                Deadline.endOfDay(now - TimeUnit.DAYS.toMillis(1)),
                true, false, 0, 0, Arrays.asList("生活")));

        // 3. 一周内到期的考试复习 —— 杏橙圆点
        store.upsert(task("物理期中考复习",
                "第 1~4 章：先把公式过一遍，再做两套卷子\n错题整理到本子上",
                Deadline.endOfDay(now + TimeUnit.DAYS.toMillis(6)),
                true, true, 2, 1, Arrays.asList("考试")));

        // 4. 两周后的小组项目 —— 薄荷圆点
        store.upsert(task("小组项目：需求文档初稿",
                "负责写「用户场景」和「功能列表」两节，写完发给组长汇总～",
                Deadline.endOfDay(now + TimeUnit.DAYS.toMillis(12)),
                true, true, 0, 0, Arrays.asList("项目")));

        // 5. 一个多月后的报名 —— 灰紫圆点
        store.upsert(task("暑假实习报名",
                "简历改一版，截止前投出去就好，不用急",
                Deadline.endOfDay(now + TimeUnit.DAYS.toMillis(40)),
                false, false, 0, 0, Arrays.asList("实习")));

        // 6. 长线任务：没有 DDL —— 淡紫圆点
        store.upsert(task("每天背 20 个单词",
                "用 App 每天打卡，重点是别断；每周日复盘一次",
                Task.NO_DEADLINE, true, false, 1, 1, Arrays.asList("阅读", "习惯")));

        // 7. 长线任务：个人选做
        store.upsert(task("练吉他：把《晴天》前奏弹顺",
                "每天 20 分钟，先慢速分段练，再连起来",
                Task.NO_DEADLINE, false, false, 0, 0, Arrays.asList("生活", "运动")));

        // 8. 一条已经划掉的，方便看「已完成」页
        Task done = task("提交选课表",
                "教务系统里确认三个志愿都提交好了",
                Deadline.endOfDay(now - TimeUnit.DAYS.toMillis(3)),
                true, true, 0, 0, Arrays.asList("作业"));
        done.setStatus(Task.STATUS_DONE);
        done.setDoneAt(now - TimeUnit.DAYS.toMillis(1));
        store.upsert(done);
    }

    private static Task task(String title, String note, long deadline,
                            boolean required, boolean school, int stage, int priority,
                            List<String> tags) {
        Task t = new Task();
        t.setTitle(title);
        t.setNote(note);
        t.setDeadline(deadline);
        t.setAllDay(true);
        t.setRequired(required);
        t.setSchool(school);
        t.setStage(stage);
        t.setPriority(priority);
        t.setCustomTags(new ArrayList<>(tags));
        return t;
    }
}
