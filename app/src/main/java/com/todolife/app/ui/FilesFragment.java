package com.todolife.app.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.todolife.app.R;
import com.todolife.app.data.DocStore;
import com.todolife.app.model.Doc;
import com.todolife.app.util.ImageStore;

import java.io.File;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 「文件」页：两块内容。
 *
 * <ul>
 *   <li><b>相册笔记</b>：上课拍的板书、老师发的安排截图 —— 缩略图网格，点开看大图；</li>
 *   <li><b>学习文件</b>：讲义、作业要求之类的文档 —— 一行一个，点开交给系统应用。</li>
 * </ul>
 *
 * <p>文件本体都复制进了 App 私有目录，所以外部授权失效也不会变白图。</p>
 */
public class FilesFragment extends Fragment implements DocStore.Listener {

    private static final int GRID_COLUMNS = 3;

    private AppHost host;
    private DocStore docs;

    private TextView tvFilesTotal;
    private TextView tvNoteCount;
    private TextView tvStudyCount;
    private LinearLayout gridNote;
    private LinearLayout listStudy;
    private View emptyNote;
    private View emptyStudy;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_files, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        host = AppHost.of((androidx.appcompat.app.AppCompatActivity) requireActivity());
        docs = DocStore.get(requireContext());
        docs.addListener(this);

        tvFilesTotal = v.findViewById(R.id.tvFilesTotal);
        tvNoteCount = v.findViewById(R.id.tvNoteCount);
        tvStudyCount = v.findViewById(R.id.tvStudyCount);
        gridNote = v.findViewById(R.id.gridNote);
        listStudy = v.findViewById(R.id.listStudy);
        emptyNote = v.findViewById(R.id.emptyNote);
        emptyStudy = v.findViewById(R.id.emptyStudy);

        v.findViewById(R.id.btnAddNote).setOnClickListener(x -> host.addImageNote());
        v.findViewById(R.id.btnAddStudy).setOnClickListener(x -> host.addStudyFile());

        render();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (docs != null) docs.removeListener(this);
    }

    @Override
    public void onDocsChanged() {
        if (isAdded() && gridNote != null) render();
    }

    // ------------------------------------------------------------------ 渲染

    /** 数据变化 / 页面刷新时重绘 */
    public void render() {
        if (gridNote == null || docs == null) return;

        List<Doc> notes = docs.byCategory(Doc.CAT_NOTE);
        List<Doc> studies = docs.byCategory(Doc.CAT_STUDY);

        tvFilesTotal.setText(getString(R.string.files_total, notes.size() + studies.size()));
        tvNoteCount.setText(getString(R.string.files_count, notes.size()));
        tvStudyCount.setText(getString(R.string.files_count, studies.size()));

        renderNoteGrid(notes);
        renderStudyList(studies);

        emptyNote.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
        emptyStudy.setVisibility(studies.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** 相册笔记：三列缩略图，每行自己拼一个 LinearLayout */
    private void renderNoteGrid(List<Doc> notes) {
        gridNote.removeAllViews();
        if (notes.isEmpty()) return;

        int gap = dp(8);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int pagePad = getResources().getDimensionPixelSize(R.dimen.page_padding);
        int cell = Math.max(dp(72), (screenW - pagePad * 2 - gap * 2) / GRID_COLUMNS);

        LinearLayout row = null;
        for (int i = 0; i < notes.size(); i++) {
            if (i % GRID_COLUMNS == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = gap;
                row.setLayoutParams(rlp);
                gridNote.addView(row);
            }
            boolean lastInRow = (i % GRID_COLUMNS == GRID_COLUMNS - 1) || (i == notes.size() - 1);
            row.addView(noteCell(notes.get(i), cell, lastInRow ? 0 : gap));
        }
    }

    private View noteCell(Doc d, int size, int rightMargin) {
        LinearLayout cell = new LinearLayout(requireContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = rightMargin;
        cell.setLayoutParams(lp);

        CardImageView iv = new CardImageView(requireContext());
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, size);
        iv.setLayoutParams(ilp);
        iv.setCornerRadius(dp(12));
        iv.setContentDescription(d.getName());
        ImageStore.loadInto(iv, d.getPath(), 255, size, size);
        cell.addView(iv);

        TextView name = new TextView(requireContext());
        name.setText(d.getName());
        name.setTextSize(10.5f);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setTextColor(0xFF8C8493);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(4);
        name.setLayoutParams(nlp);
        cell.addView(name);

        cell.setOnClickListener(x -> open(d));
        cell.setOnLongClickListener(x -> {
            showMenu(d);
            return true;
        });
        return cell;
    }

    /** 学习文件：一行一个 */
    private void renderStudyList(List<Doc> studies) {
        listStudy.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Doc d : studies) {
            View row = inflater.inflate(R.layout.item_doc, listStudy, false);
            TextView ext = row.findViewById(R.id.tvDocExt);
            TextView name = row.findViewById(R.id.tvDocName);
            TextView meta = row.findViewById(R.id.tvDocMeta);

            ext.setText(d.extLabel());
            name.setText(d.getName());
            meta.setText(sizeText(d.getSize()) + " · " + dateText(d.getAddedAt()));

            row.setOnClickListener(x -> open(d));
            row.setOnLongClickListener(x -> {
                showMenu(d);
                return true;
            });
            listStudy.addView(row);
        }
    }

    // ------------------------------------------------------------------ 操作

    /** 交给系统应用打开（图片会进相册/看图器，文档会进对应的阅读器） */
    private void open(Doc d) {
        File f = new File(d.getPath());
        if (!f.exists()) {
            toast(getString(R.string.files_missing));
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, d.getMime().isEmpty() ? "*/*" : d.getMime());
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.files_no_viewer));
        } catch (Exception e) {
            toast(getString(R.string.files_import_failed));
        }
    }

    private void showMenu(Doc d) {
        if (!isAdded()) return;
        String[] items = {
                getString(R.string.files_action_open),
                getString(R.string.files_action_rename),
                getString(R.string.files_action_delete)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(d.getName())
                .setItems(items, (dlg, which) -> {
                    if (which == 0) open(d);
                    else if (which == 1) askRename(d);
                    else askDelete(d);
                })
                .show();
    }

    private void askRename(Doc d) {
        if (!isAdded()) return;
        EditText et = new EditText(requireContext());
        et.setText(d.getName());
        et.setSelection(et.getText().length());
        et.setTextSize(14f);
        int pad = dp(18);
        et.setPadding(pad, pad / 2, pad, pad / 2);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.files_rename_title)
                .setView(et)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dlg, w) ->
                        docs.rename(d.getId(), et.getText().toString()))
                .show();
    }

    private void askDelete(Doc d) {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_delete)
                .setMessage(getString(R.string.files_delete_confirm, d.getName()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dlg, w) -> {
                    docs.delete(d.getId());
                    toast(getString(R.string.files_deleted));
                })
                .show();
    }

    private void toast(String s) {
        if (isAdded()) Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ 小工具

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String sizeText(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0);
    }

    private static String dateText(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日";
    }
}
