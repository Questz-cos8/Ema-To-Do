package com.todolife.app.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.todolife.app.R;
import com.todolife.app.data.TaskStore;
import com.todolife.app.model.Deadline;
import com.todolife.app.model.Task;
import com.todolife.app.util.BackupIO;
import com.todolife.app.util.ImageStore;
import com.todolife.app.util.Prefs;

/**
 * 「设置」页：全局背景图与透明度、卡片默认透明度、数据备份、示例数据。
 */
public class SettingsFragment extends Fragment {

    private AppHost host;
    private TaskStore store;
    private ActivityResultLauncher<String[]> openDoc;

    private SeekBar seekBgAlpha;
    private SeekBar seekCardAlpha;
    private TextView tvBgAlphaLabel;
    private TextView tvCardAlphaLabel;
    private TextView tvReviewToday;
    private TextView tvReviewWeek;
    private TextView tvReviewLeft;
    private ImageView preview;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        host = AppHost.of((androidx.appcompat.app.AppCompatActivity) requireActivity());
        store = host.store();

        // 注册发生在 Fragment 视图创建阶段，早于 Activity 的 STARTED，是安全的；
        // 仍然保留生命周期检查，避免结果回来时 Fragment 已不在。
        openDoc = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (isAdded()) importFrom(uri);
                });

        seekBgAlpha = v.findViewById(R.id.seekBgAlpha);
        seekCardAlpha = v.findViewById(R.id.seekCardAlpha);
        tvBgAlphaLabel = v.findViewById(R.id.tvBgPreviewLabel);
        tvCardAlphaLabel = v.findViewById(R.id.tvCardAlphaLabel);
        tvReviewToday = v.findViewById(R.id.tvReviewToday);
        tvReviewWeek = v.findViewById(R.id.tvReviewWeek);
        tvReviewLeft = v.findViewById(R.id.tvReviewLeft);
        preview = v.findViewById(R.id.imgBgPreview);

        seekBgAlpha.setProgress(Prefs.bgAlpha(requireContext()));
        seekCardAlpha.setProgress(Prefs.cardAlpha(requireContext()));

        seekBgAlpha.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                Prefs.setBgAlpha(requireContext(), progress);
                updateLabels();
                host.refreshAll();
            }
        });
        seekCardAlpha.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                Prefs.setCardAlpha(requireContext(), progress);
                updateLabels();
            }
        });

        v.findViewById(R.id.btnPickBg).setOnClickListener(view -> {
            if (!isAdded()) return;
            host.pickAppBackground(path -> {
                if (!isAdded()) return;
                if (path == null) {
                    Toast.makeText(requireContext(), R.string.copy_no_image,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                String old = Prefs.bgImage(requireContext());
                if (!old.isEmpty() && !old.equals(path)) {
                    ImageStore.deleteIfUnused(requireContext(), old, null);
                }
                Prefs.setBgImage(requireContext(), path);
                host.refreshAll();
                updateLabels();
            });
        });

        v.findViewById(R.id.btnClearBg).setOnClickListener(view -> {
            String old = Prefs.bgImage(requireContext());
            ImageStore.deleteIfUnused(requireContext(), old, null);
            Prefs.setBgImage(requireContext(), "");
            host.refreshAll();
            updateLabels();
        });

        v.findViewById(R.id.btnExport).setOnClickListener(view -> {
            String where = BackupIO.export(requireContext(), store.exportJson());
            if (where != null) {
                Toast.makeText(requireContext(), getString(R.string.copy_export_ok, where),
                        Toast.LENGTH_LONG).show();
            }
        });

        v.findViewById(R.id.btnImport).setOnClickListener(view -> {
            try {
                openDoc.launch(new String[]{"application/json", "text/plain", "*/*"});
            } catch (Exception e) {
                Toast.makeText(requireContext(), R.string.copy_cant_open_picker,
                        Toast.LENGTH_SHORT).show();
            }
        });

        v.findViewById(R.id.btnSample).setOnClickListener(view ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("放几条示例进来看看？ (・ω・)")
                        .setMessage("会加几条演示任务，方便你一眼看清两个区域和标签怎么用～ ✨")
                        .setPositiveButton("好呀", (d, w) -> {
                            SampleData.insert(store);
                            host.refreshAll();
                            Toast.makeText(requireContext(), R.string.copy_sample_done,
                                    Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.action_cancel, null)
                        .show());

        v.findViewById(R.id.btnClearDone).setOnClickListener(view ->
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.settings_clear_done)
                        .setMessage(R.string.confirm_clear_done)
                        .setPositiveButton(R.string.action_delete, (d, w) -> {
                            store.clearDone();
                            host.refreshAll();
                        })
                        .setNegativeButton(R.string.action_cancel, null)
                        .show());

        updateLabels();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLabels();
    }

    private void updateLabels() {
        if (seekBgAlpha == null) return;
        tvBgAlphaLabel.setText("背景图清楚一点还是淡一点？现在 " + seekBgAlpha.getProgress()
                + "（越大越清楚）");
        tvCardAlphaLabel.setText("新卡片上图片的默认清楚度：" + seekCardAlpha.getProgress());
        renderReview();
        String path = Prefs.bgImage(requireContext());
        if (path.isEmpty()) {
            preview.setVisibility(View.GONE);
        } else {
            preview.setVisibility(View.VISIBLE);
            ImageStore.loadInto(preview, path, Math.max(40, seekBgAlpha.getProgress()), 600, 300);
        }
    }

    /** 一行式回顾：今天划掉几条 / 本周划掉几条 / 还剩几条 */
    private void renderReview() {
        if (tvReviewToday == null) return;
        long todayStart = Deadline.startOfDay(System.currentTimeMillis());
        long weekStart = todayStart - 6L * 24 * 3600 * 1000;
        int todayDone = 0;
        int weekDone = 0;
        int left = 0;
        for (Task t : store.all()) {
            if (t.isDone()) {
                if (t.getDoneAt() >= todayStart) todayDone++;
                if (t.getDoneAt() >= weekStart) weekDone++;
            } else {
                left++;
            }
        }
        tvReviewToday.setText(String.valueOf(todayDone));
        tvReviewWeek.setText(String.valueOf(weekDone));
        tvReviewLeft.setText(String.valueOf(left));
    }

    private void importFrom(Uri uri) {
        if (uri == null) return;
        String json = BackupIO.read(requireContext(), uri);
        if (json == null) {
            Toast.makeText(requireContext(), R.string.copy_import_failed, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("备份要怎么用呀？ (・ω・)")
                .setMessage("「合并」：把备份里的任务加进来，现在的都留着～\n"
                        + "「覆盖」：用备份把现在的全部换掉哦。")
                .setPositiveButton("合并", (d, w) -> {
                    int n = store.importJson(json);
                    if (n < 0) {
                        Toast.makeText(requireContext(), R.string.copy_import_failed,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    host.refreshAll();
                    Toast.makeText(requireContext(), getString(R.string.copy_import_merge, n),
                            Toast.LENGTH_LONG).show();
                })
                .setNeutralButton("覆盖", (d, w) -> {
                    if (store.replaceAll(json)) {
                        host.refreshAll();
                        Toast.makeText(requireContext(), R.string.copy_import_replace,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), R.string.copy_import_failed,
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** 只关心进度变化的 SeekBar 监听 */
    abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
