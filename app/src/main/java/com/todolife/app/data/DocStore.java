package com.todolife.app.data;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.todolife.app.model.Doc;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 「文件」页的数据源：相册笔记 + 学习文件的元信息（JSON 持久化）+ 私有目录里的文件本体。
 *
 * <p>和 {@link TaskStore} 一个路子：不引数据库，几百条记录用一段 JSON 足够，
 * 导入的文件统一放在 {@code filesDir/docs/}。</p>
 */
public final class DocStore {

    private static final String PREF = "todolife_docs_v1";
    private static final String KEY_DOCS = "docs_json";
    private static final String DIR = "docs";

    private static DocStore INSTANCE;

    public interface Listener {
        void onDocsChanged();
    }

    private final Context appContext;
    private final SharedPreferences prefs;
    private final List<Doc> docs = new ArrayList<>();
    private final Set<Listener> listeners = new LinkedHashSet<>();

    private DocStore(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized DocStore get(Context context) {
        if (INSTANCE == null) INSTANCE = new DocStore(context);
        return INSTANCE;
    }

    // ------------------------------------------------------------ 监听

    public void addListener(Listener l) {
        if (l != null) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        for (Listener l : new ArrayList<>(listeners)) l.onDocsChanged();
    }

    // ------------------------------------------------------------ 读写

    private void load() {
        String raw = prefs.getString(KEY_DOCS, null);
        if (raw == null || raw.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                if (arr.optJSONObject(i) != null) docs.add(Doc.fromJson(arr.optJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
    }

    public void save() {
        JSONArray arr = new JSONArray();
        for (Doc d : docs) arr.put(d.toJson());
        prefs.edit().putString(KEY_DOCS, arr.toString()).apply();
        notifyChanged();
    }

    /** 某个分区的内容，新加的排前面 */
    public List<Doc> byCategory(int category) {
        List<Doc> out = new ArrayList<>();
        for (Doc d : docs) {
            if (d.getCategory() == category) out.add(d);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.getAddedAt(), a.getAddedAt()));
        return out;
    }

    public int count(int category) {
        int n = 0;
        for (Doc d : docs) if (d.getCategory() == category) n++;
        return n;
    }

    public Doc byId(String id) {
        if (id == null) return null;
        for (Doc d : docs) if (id.equals(d.getId())) return d;
        return null;
    }

    // ------------------------------------------------------------ 导入 / 删除

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /**
     * 把系统选中的文件复制进私有目录，并登记一条记录。
     *
     * @return 成功返回这条记录；失败（比如读不到 uri）返回 null
     */
    public Doc importFrom(int category, Uri uri) {
        if (uri == null) return null;
        ContentResolver cr = appContext.getContentResolver();
        String name = displayName(cr, uri);
        String mime = cr.getType(uri);
        if (mime == null) mime = guessMime(name);

        File target = new File(dir(appContext), "doc_" + System.currentTimeMillis() + extOf(name, mime));
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = cr.openInputStream(uri);
            if (in == null) return null;
            out = new FileOutputStream(target);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } catch (Exception e) {
            if (target.exists()) target.delete();
            return null;
        } finally {
            close(in);
            close(out);
        }

        Doc d = new Doc();
        // 相册笔记用「日期 + 时间」当名字：相机/截图给的是 15227.jpg 这种，完全认不出是哪天拍的
        d.setName(category == Doc.CAT_NOTE ? timeName(name, mime) : name);
        d.setCategory(category);
        d.setPath(target.getAbsolutePath());
        d.setMime(mime);
        d.setSize(target.length());
        d.setAddedAt(System.currentTimeMillis());
        docs.add(d);
        save();
        return d;
    }

    public void delete(String id) {
        Doc d = byId(id);
        if (d == null) return;
        File f = new File(d.getPath());
        if (f.exists()) f.delete();
        docs.remove(d);
        save();
    }

    /**
     * 批量删除（多选模式下用）。
     *
     * <p>逐个调 {@link #delete} 会触发 N 次 save + N 次重绘，所以这里合成一次。</p>
     */
    public void deleteAll(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        boolean changed = false;
        for (String id : ids) {
            Doc d = byId(id);
            if (d == null) continue;
            File f = new File(d.getPath());
            if (f.exists()) f.delete();
            docs.remove(d);
            changed = true;
        }
        if (changed) save();
    }

    public void rename(String id, String newName) {
        Doc d = byId(id);
        if (d == null) return;
        String n = newName == null ? "" : newName.trim();
        if (n.isEmpty()) return;
        d.setName(n);
        save();
    }

    // ------------------------------------------------------------ 小工具

    private static void close(java.io.Closeable c) {
        try {
            if (c != null) c.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * 相册笔记的展示名：用导入时刻的「日期 + 时间」。
     *
     * <p>相机和截图给的名字是 {@code 15227.jpg} 这种，过几天完全认不出是哪天的板书，
     * 所以这里统一换成看得懂的名字（扩展名保留，方便看出是什么格式）。</p>
     */
    private static String timeName(String originalName, String mime) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.getDefault())
                .format(new Date());
        return stamp + extOf(originalName, mime);
    }

    private static String displayName(ContentResolver cr, Uri uri) {
        Cursor cur = null;
        try {
            cur = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                int idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String s = cur.getString(idx);
                    if (s != null && !s.trim().isEmpty()) return s.trim();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return "未命名文件";
    }

    private static String extOf(String name, String mime) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot).toLowerCase(Locale.getDefault());
            if (ext.length() <= 8) return ext;
        }
        String fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        return fromMime == null ? "" : "." + fromMime;
    }

    private static String guessMime(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.getDefault());
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (m != null) return m;
        }
        return "application/octet-stream";
    }
}
