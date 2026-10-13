package com.todolife.app.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 动态视图的 id 生成器。
 *
 * <p>卡片上的标签、统计页的条形图、表单里的可勾选标签都需要运行时生成 View。
 * 用固定高位偏移的 id 可以避免与 XML 里定义的 id 冲突。</p>
 */
public final class Ids {

    private static final int BASE = 0x7A000000;
    private static int next = 1;

    private Ids() {
    }

    public static synchronized int of() {
        if (next > 0x00FFFFFE) next = 1;
        return BASE + (next++);
    }
}
