package com.todolife.app.ui;

import androidx.appcompat.app.AppCompatActivity;

import com.todolife.app.data.Query;
import com.todolife.app.data.TaskStore;
import com.todolife.app.model.Task;

/**
 * 页面（Fragment）与宿主 Activity 之间的契约：
 * 筛选条件、数据源、以及所有会改数据的动作都由 Activity 统一提供，
 * 这样四个页面看到的状态永远一致。
 */
public interface AppHost {

    TaskStore store();

    Query query();

    /** 弹「新建任务」面板 */
    void openNewTask();

    /** 弹「编辑任务」面板 */
    void openEditTask(Task task);

    /** 弹详细信息面板 */
    void openDetail(Task task);

    /**
     * 选一张全局背景图。
     *
     * <p>统一走 Activity 里注册好的 launcher —— 在 Activity STARTED 之后再调用
     * registerForActivityResult 会抛 IllegalStateException，所以选图入口只能有这一条。</p>
     */
    void pickAppBackground(ImagePicked callback);

    /** 选图结果回调 */
    interface ImagePicked {
        /** @param path 已复制到私有目录的路径；取消或失败为 null */
        void onPicked(String path);
    }

    /** 「文件」页：加一张相册笔记（从相册/图片里挑） */
    void addImageNote();

    /** 「文件」页：加一份学习文件（从文件管理器里挑，任意类型） */
    void addStudyFile();

    /** 数据变化后刷新所有页面 */
    void refreshAll();

    /** 便捷取用 */
    static AppHost of(AppCompatActivity activity) {
        if (activity instanceof AppHost) return (AppHost) activity;
        throw new IllegalStateException("Activity 必须实现 AppHost: " + activity);
    }

    /** 卡片 / 详情页上的动作集 */
    TaskActions actions();
}
