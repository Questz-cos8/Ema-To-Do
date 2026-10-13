package com.todolife.app.ui;

import com.todolife.app.model.Task;

/**
 * 卡片 / 详情页上所有会改变数据的操作的统一回调。
 * 由 {@link MainActivity} 实现，再交给各个页面。
 */
public interface TaskActions {

    /** 点击卡片：打开详细信息 */
    void onOpen(Task task);

    /** 编辑 */
    void onEdit(Task task);

    /** 完成 / 取消完成 */
    void onToggleDone(Task task);

    /** 选择卡片背景图 */
    void onPickCardImage(Task task);

    /** 设置提醒 */
    void onSetReminder(Task task);

    /** 删除 */
    void onDelete(Task task);
}
