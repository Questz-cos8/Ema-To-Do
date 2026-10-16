package com.todolife.app.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.todolife.app.R;
import com.todolife.app.data.DocStore;
import com.todolife.app.model.Doc;
import com.todolife.app.util.ImageStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 「文件」页：两块内容。
 *
 * <ul>
 *   <li><b>相册笔记</b>：上课拍的板书、老师发的安排截图 —— 缩略图网格，点开看大图；</li>
 *   <li><b>学习文件</b>：讲义、作业要求之类的文档 —— 一行一个，点开交给系统应用。</li>
 * </ul>
 *
 * <p>文件本体都复制进了 App 私有目录，所以外部授权失效也不会变白图。</p>
 *
 * <h3>多选</h3>
 * <p>长按任意一项进入多选：底部弹出一条操作条（已选 N 项 / 全选 / 重命名 / 删除 / 取消），
 * 再点其他项就是加选。选中一个时才显示「重命名」。返回键退出多选。</p>
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

    // 多选
    private View selectionBar;
    private TextView tvSelectCount;
    private TextView btnSelectAll;
    private View btnSelectRename;
    private final Set<String> selected = new LinkedHashSet<>();
    private OnBackPressedCallback backCallback;

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

        selectionBar = v.findViewById(R.id.selectionBar);
        tvSelectCount = v.findViewById(R.id.tvSelectCount);
        btnSelectAll = v.findViewById(R.id.btnSelectAll);
        btnSelectRename = v.findViewById(R.id.btnSelectRename);
        View btnSelectDelete = v.findViewById(R.id.btnSelectDelete);
        View btnSelectCancel = v.findViewById(R.id.btnSelectCancel);

        v.findViewById(R.id.btnAddNote).setOnClickListener(x -> host.addImageNote());
        v.findViewById(R.id.btnAddStudy).setOnClickListener(x -> host.addStudyFile());

        btnSelectAll.setOnClickListener(x -> toggleSelectAll());
        btnSelectRename.setOnClickListener(x -> {
            Doc d = singleSelected();
            if (d != null) askRename(d);
        });
        btnSelectDelete.setOnClickListener(x -> askDeleteSelected());
        btnSelectCancel.setOnClickListener(x -> exitSelection());

        // 多选时按返回键先退出多选，而不是退出 App
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                exitSelection();
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), backCallback);

        render();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (docs != null) docs.removeListener(this);
        selected.clear();
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

        // 选中的可能已经被删掉了，清一下
        selected.removeIf(id -> docs.byId(id) == null);

        tvFilesTotal.setText(getString(R.string.files_total, notes.size() + studies.size()));
        tvNoteCount.setText(getString(R.string.files_count, notes.size()));
        tvStudyCount.setText(getString(R.string.files_count, studies.size()));

        renderNoteGrid(notes);
        renderStudyList(studies);

        emptyNote.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
        emptyStudy.setVisibility(studies.isEmpty() ? View.VISIBLE : View.GONE);

        updateSelectionBar();
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

        boolean sel = selected.contains(d.getId());

        // 图片 + 选中蒙版 + 勾
        FrameLayout imageWrap = new FrameLayout(requireContext());
        imageWrap.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, size));

        CardImageView iv = new CardImageView(requireContext());
        iv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        iv.setCornerRadius(dp(12));
        iv.setContentDescription(d.getName());
        ImageStore.loadInto(iv, d.getPath(), 255, size, size);
        imageWrap.addView(iv);

        if (sel) {
            View overlay = new View(requireContext());
            overlay.setBackgroundResource(R.drawable.bg_selected_overlay);
            imageWrap.addView(overlay, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            TextView check = new TextView(requireContext());
            check.setText("✓");
            check.setBackgroundResource(R.drawable.bg_check_circle);
            check.setGravity(Gravity.CENTER);
            check.setTextColor(0xFFFFFFFF);
            check.setTextSize(10f);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                    dp(20), dp(20), Gravity.TOP | Gravity.END);
            clp.topMargin = dp(5);
            clp.rightMargin = dp(5);
            imageWrap.addView(check, clp);
        }
        cell.addView(imageWrap);

        TextView name = new TextView(requireContext());
        name.setText(d.getName());
        name.setTextSize(10.5f);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(4);
        name.setLayoutParams(nlp);
        cell.addView(name);

        cell.setOnClickListener(x -> onItemClick(d));
        cell.setOnLongClickListener(x -> {
            onItemLongClick(d);
            return true;
        });
        return cell;
    }

    /** 学习文件：一行一个 */
    private void renderStudyList(List<Doc> studies) {
        listStudy.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Doc d : studies) {
            MaterialCardView row = (MaterialCardView) inflater.inflate(R.layout.item_doc, listStudy, false);
            TextView ext = row.findViewById(R.id.tvDocExt);
            TextView name = row.findViewById(R.id.tvDocName);
            TextView meta = row.findViewById(R.id.tvDocMeta);
            View check = row.findViewById(R.id.tvDocCheck);

            ext.setText(d.extLabel());
            name.setText(d.getName());
            meta.setText(sizeText(d.getSize()) + " · " + dateText(d.getAddedAt()));

            boolean sel = selected.contains(d.getId());
            check.setVisibility(sel ? View.VISIBLE : View.GONE);
            row.setStrokeColor(ContextCompat.getColor(requireContext(),
                    sel ? R.color.primary : R.color.stroke));
            row.setStrokeWidth(sel ? dp(2) : dp(1));

            row.setOnClickListener(x -> onItemClick(d));
            row.setOnLongClickListener(x -> {
                onItemLongClick(d);
                return true;
            });
            listStudy.addView(row);
        }
    }

    // ------------------------------------------------------------------ 多选

    private boolean selectionMode() {
        return !selected.isEmpty();
    }

    private void onItemClick(Doc d) {
        if (selectionMode()) {
            toggle(d);
        } else {
            open(d);
        }
    }

    private void onItemLongClick(Doc d) {
        if (selectionMode()) toggle(d);
        else enterSelection(d);
    }

    private void enterSelection(Doc d) {
        selected.add(d.getId());
        render();
    }

    private void toggle(Doc d) {
        if (!selected.remove(d.getId())) selected.add(d.getId());
        render();
    }

    private void exitSelection() {
        if (selected.isEmpty()) return;
        selected.clear();
        render();
    }

    private List<Doc> allDocs() {
        List<Doc> all = new ArrayList<>(docs.byCategory(Doc.CAT_NOTE));
        all.addAll(docs.byCategory(Doc.CAT_STUDY));
        return all;
    }

    private void toggleSelectAll() {
        List<Doc> all = allDocs();
        if (selected.size() >= all.size()) {
            selected.clear();
        } else {
            for (Doc d : all) selected.add(d.getId());
        }
        render();
    }

    private Doc singleSelected() {
        if (selected.size() != 1) return null;
        return docs.byId(selected.iterator().next());
    }

    private void updateSelectionBar() {
        boolean on = selectionMode();
        selectionBar.setVisibility(on ? View.VISIBLE : View.GONE);
        if (backCallback != null) backCallback.setEnabled(on);
        if (!on) return;

        tvSelectCount.setText(getString(R.string.files_selected_count, selected.size()));
        boolean allChosen = selected.size() >= allDocs().size();
        btnSelectAll.setText(allChosen ? R.string.files_select_none : R.string.files_select_all);
        btnSelectRename.setVisibility(selected.size() == 1 ? View.VISIBLE : View.GONE);
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

    private void askRename(Doc d) {
        if (!isAdded()) return;
        EditText et = new EditText(requireContext());
        et.setText(d.getName());
        et.setSelection(et.getText().length());
        et.setTextSize(14f);
        int pad = dp(18);
        et.setPadding(pad, pad / 2, pad, pad / 2);
        new MaterialAlertDialogBuilder(requireContext(), R.style.Theme_TodoLife_AlertDialog)
                .setTitle(R.string.files_rename_title)
                .setView(et)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dlg, w) -> {
                    docs.rename(d.getId(), et.getText().toString());
                    exitSelection();
                })
                .show();
    }

    private void askDeleteSelected() {
        if (!isAdded() || selected.isEmpty()) return;
        final List<String> ids = new ArrayList<>(selected);
        String msg;
        if (ids.size() == 1) {
            Doc only = docs.byId(ids.get(0));
            msg = getString(R.string.files_delete_confirm, only == null ? "" : only.getName());
        } else {
            msg = getString(R.string.files_delete_many, ids.size());
        }
        new MaterialAlertDialogBuilder(requireContext(), R.style.Theme_TodoLife_AlertDialog)
                .setTitle(R.string.action_delete)
                .setMessage(msg)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dlg, w) -> {
                    int n = ids.size();
                    docs.deleteAll(ids);
                    selected.clear();
                    render();
                    toast(n == 1 ? getString(R.string.files_deleted)
                            : getString(R.string.files_deleted_many, n));
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
