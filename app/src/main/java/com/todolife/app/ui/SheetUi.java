package com.todolife.app.ui;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

/**
 * 底部弹出面板的公共行为：带系统返回键处理、拖拽把手、以及「点一下就存」的提示。
 */
public final class SheetUi {

    public interface OnClose {
        void onClosed();
    }

    private SheetUi() {
    }

    public static void toast(Context c, String msg) {
        if (c == null || msg == null) return;
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static void hide(View v) {
        if (v != null) v.setVisibility(View.GONE);
    }

    public static void show(View v) {
        if (v != null) v.setVisibility(View.VISIBLE);
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static void setVisible(View v, boolean visible) {
        if (v != null) v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
