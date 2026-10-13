package com.todolife.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 图片仓库：把用户选择的背景图复制到 App 私有目录，并做小尺寸解码 + 内存缓存。
 *
 * <p>为什么不直接保存 content:// Uri：很多图片来源（相册、第三方 App）不会把读权限
 * 永久授予我们，重启后图片就变白了。复制到私有目录最稳。</p>
 */
public final class ImageStore {

    private static final String DIR = "images";
    private static final int TARGET_MAX = 1080;

    private static final Map<String, Bitmap> CACHE = new HashMap<>();
    private static final int CACHE_LIMIT = 12;

    private ImageStore() {
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /**
     * 把外部图片复制进私有目录。
     *
     * @return 持久化后的绝对路径，失败返回 null
     */
    public static String persist(Context context, Uri source) {
        if (context == null || source == null) return null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = context.getContentResolver().openInputStream(source);
            if (in == null) return null;
            File target = new File(dir(context), "img_" + System.currentTimeMillis() + ".jpg");
            out = new FileOutputStream(target);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
            return target.getAbsolutePath();
        } catch (Exception e) {
            return null;
        } finally {
            close(in);
            close(out);
        }
    }

    private static void close(java.io.Closeable c) {
        try {
            if (c != null) c.close();
        } catch (IOException ignored) {
        }
    }

    /** 内置默认背景图：assets 里的文件名 = 私有目录里的文件名 */
    private static final String DEFAULT_BG_NAME = "default_bg.webp";

    /**
     * 把内置的默认背景图复制到私有目录。
     *
     * <p>为什么要复制出来、而不是直接读 assets：整个背景图逻辑都按「文件路径」工作
     * （看图、换图、清除、删旧图都是同一条路）。复制一份能让默认图和用户自己选的图
     * 走完全相同的代码路径，不必为默认图单开一条分支。</p>
     *
     * <p>文件已存在时直接返回，不重复复制。</p>
     *
     * @return 复制后的绝对路径；失败返回 null
     */
    public static String seedDefaultBackground(Context context) {
        if (context == null) return null;
        File target = new File(dir(context), DEFAULT_BG_NAME);
        if (target.exists() && target.length() > 0) return target.getAbsolutePath();
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = context.getAssets().open(DEFAULT_BG_NAME);
            out = new FileOutputStream(target);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
            return target.getAbsolutePath();
        } catch (Exception e) {
            // 复制到一半失败会留下半个文件，删掉，下次还能重来
            if (target.exists()) target.delete();
            return null;
        } finally {
            close(in);
            close(out);
        }
    }

    /** 按需采样解码，避免 OOM */
    public static Bitmap decode(Context context, String path, int reqWidth, int reqHeight) {
        return decode(context, path, reqWidth, reqHeight, TARGET_MAX);
    }

    /**
     * 解码图片，支持两种来源：
     * <ul>
     *   <li>文件路径（App 私有目录里的图）</li>
     *   <li>{@code content://} uri（刚选好、还没复制进来的图）</li>
     * </ul>
     */
    public static Bitmap decode(Context context, String pathOrUri, int reqWidth, int reqHeight, int maxSide) {
        if (pathOrUri == null || pathOrUri.isEmpty()) return null;
        boolean isUri = pathOrUri.startsWith("content:") || pathOrUri.startsWith("file:");
        if (!isUri) {
            Bitmap cached = CACHE.get(pathOrUri);
            if (cached != null && !cached.isRecycled()) return cached;
        }

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try {
            if (isUri) {
                InputStream bounds = context.getContentResolver()
                        .openInputStream(Uri.parse(pathOrUri));
                if (bounds == null) return null;
                BitmapFactory.decodeStream(bounds, null, o);
                bounds.close();
            } else {
                BitmapFactory.decodeFile(pathOrUri, o);
            }
        } catch (Exception e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;

        int target = Math.max(maxSide, Math.max(reqWidth, reqHeight));
        int sample = 1;
        while (o.outWidth / (sample * 2) >= target / 2 && o.outHeight / (sample * 2) >= target / 2) {
            sample *= 2;
        }

        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = Math.max(1, sample);
        o2.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bmp;
        try {
            if (isUri) {
                InputStream in = context.getContentResolver().openInputStream(Uri.parse(pathOrUri));
                if (in == null) return null;
                bmp = BitmapFactory.decodeStream(in, null, o2);
                in.close();
            } else {
                bmp = BitmapFactory.decodeFile(pathOrUri, o2);
            }
        } catch (Exception e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        }
        if (bmp != null && !isUri) {
            if (CACHE.size() >= CACHE_LIMIT) CACHE.clear();
            CACHE.put(pathOrUri, bmp);
        }
        return bmp;
    }

    /**
     * 把背景图加载进 ImageView；失败时退化为透明。
     *
     * <p>关键：如果 View 上已经是同一张图，就**什么都不做**。
     * {@code setImageBitmap()} 会把 ImageView 的 alpha 重置成 255，紧接着再
     * {@code setImageAlpha(半透明)} 才设回来——中间那一帧就是「背景图突然变亮」的闪。
     * 所以这里先比对，同一张图直接返回。</p>
     */
    public static void loadInto(ImageView view, String path, int alpha, int reqW, int reqH) {
        if (view == null) return;
        Bitmap bmp = decode(view.getContext(), path, reqW, reqH);
        if (bmp == null) {
            view.setImageDrawable(new ColorDrawable(0x00000000));
            return;
        }
        Drawable current = view.getDrawable();
        if (current instanceof BitmapDrawable
                && ((BitmapDrawable) current).getBitmap() == bmp) {
            // 同一张图：只确保透明度正确，不重设 bitmap
            if (view.getImageAlpha() != Math.max(0, Math.min(255, alpha))) {
                view.setImageAlpha(Math.max(0, Math.min(255, alpha)));
            }
            return;
        }
        // 先设透明度再设图，避免「先清晰后透明」的一帧
        view.setImageAlpha(Math.max(0, Math.min(255, alpha)));
        view.setImageBitmap(bmp);
        view.setImageAlpha(Math.max(0, Math.min(255, alpha)));
    }

    /**
     * 把裁好的位图存进 App 私有目录。
     *
     * @return 保存后的路径；失败返回 null
     */
    public static String saveBitmap(Context context, Bitmap bitmap) {
        if (context == null || bitmap == null || bitmap.isRecycled()) return null;
        FileOutputStream out = null;
        try {
            File target = new File(dir(context), "crop_" + System.currentTimeMillis() + ".jpg");
            out = new FileOutputStream(target);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            return target.getAbsolutePath();
        } catch (Exception e) {
            return null;
        } finally {
            close(out);
        }
    }

    /** 删除不再被引用的图片文件 */
    public static void deleteIfUnused(Context context, String path, java.util.Collection<String> stillUsed) {
        if (path == null || path.isEmpty()) return;
        if (stillUsed != null && stillUsed.contains(path)) return;
        File f = new File(path);
        if (f.exists() && f.getAbsolutePath().startsWith(dir(context).getAbsolutePath())) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        CACHE.remove(path);
    }

    public static Drawable placeholder() {
        return new ColorDrawable(0x00000000);
    }
}
