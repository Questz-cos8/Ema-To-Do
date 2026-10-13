package com.todolife.app.util;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * 图片选择器：用系统自带的选图界面（ACTION_GET_CONTENT），不引入第三方库。
 * 选中的图片会被复制到 App 私有目录，重启后依然可用。
 */
public final class ImagePicker {

    public interface Callback {
        /**
         * @param path 复制到私有目录后的路径，取消 / 失败时为 null
         */
        void onPicked(String path);
    }

    private final ActivityResultLauncher<String> launcher;
    private Callback pending;

    public ImagePicker(ActivityResultCaller caller, Activity host) {
        this.launcher = caller.registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    Callback cb = pending;
                    pending = null;
                    if (cb == null) return;
                    if (uri == null) {
                        cb.onPicked(null);
                        return;
                    }
                    String path = ImageStore.persist(host, uri);
                    cb.onPicked(path);
                });
    }

    /** 打开系统选图界面 */
    public void pick(Callback callback) {
        this.pending = callback;
        try {
            launcher.launch("image/*");
        } catch (Exception e) {
            pending = null;
            if (callback != null) callback.onPicked(null);
        }
    }

    /** 在详情/编辑面板里预览已选的卡片背景 */
    public static void preview(Context c, ImageView view, String path, int alpha) {
        if (view == null) return;
        if (path == null || path.isEmpty()) {
            view.setImageDrawable(null);
            return;
        }
        ImageStore.loadInto(view, path, alpha, 400, 240);
    }

    /** 给 AlertDialog 使用的小工具：把 Uri 转成可读路径 */
    public static String describe(Uri uri) {
        return uri == null ? "" : uri.toString();
    }

    public static BottomSheetDialog sheet(Activity a, int layoutRes) {
        BottomSheetDialog d = new BottomSheetDialog(a);
        d.setContentView(layoutRes);
        return d;
    }
}
