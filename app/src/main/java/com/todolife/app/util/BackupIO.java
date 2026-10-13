package com.todolife.app.util;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 备份导出 / 导入：导出为一段可读的 JSON 文本文件，方便自己保存或换机迁移。
 *
 * <p>Android 10 及以上写入系统「下载」目录（不需要存储权限）；
 * 更老的系统写入应用外部私有目录（也不需要权限）。</p>
 */
public final class BackupIO {

    private BackupIO() {
    }

    /** @return 保存位置（给人看的说明）；失败返回 null */
    public static String export(Context context, String json) {
        String name = "清单备份_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date()) + ".json";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = context.getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return null;
                OutputStream os = context.getContentResolver().openOutputStream(uri);
                if (os == null) return null;
                os.write(data);
                os.flush();
                os.close();
                cv.clear();
                cv.put(MediaStore.Downloads.IS_PENDING, 0);
                context.getContentResolver().update(uri, cv, null, null);
                return "下载/" + name;
            }
            File dir = context.getExternalFilesDir(null);
            if (dir == null) dir = context.getFilesDir();
            File out = new File(dir, name);
            FileOutputStream fos = new FileOutputStream(out);
            fos.write(data);
            fos.flush();
            fos.close();
            return out.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    /** 从选中的备份文件读取 JSON 文本 */
    public static String read(Context context, Uri uri) {
        if (uri == null) return null;
        try {
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
