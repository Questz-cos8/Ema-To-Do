package com.todolife.app.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager.widget.PagerAdapter;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.todolife.app.R;
import com.todolife.app.data.Query;
import com.todolife.app.data.DocStore;
import com.todolife.app.data.TaskStore;
import com.todolife.app.model.Deadline;
import com.todolife.app.model.Doc;
import com.todolife.app.model.Task;
import com.todolife.app.reminder.ReminderScheduler;
import com.todolife.app.util.ImagePicker;
import com.todolife.app.util.ImageStore;
import com.todolife.app.util.Prefs;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 唯一的 Activity：底部四个分区（待完成 / 已完成 / 统计 / 设置）+ 全局背景 + 新建按钮。
 * 所有改数据的动作都集中在这里，四个页面共享同一份状态。
 */
public class MainActivity extends AppCompatActivity implements AppHost, TaskStore.Listener, TaskActions {

    public static final String EXTRA_OPEN_TASK_ID = "open_task_id";

    private TaskStore store;
    private final Query query = new Query();

    private androidx.viewpager.widget.ViewPager pager;
    /** 标记「正在由翻页反向同步底栏高亮」，避免底栏回调再触发一次 setCurrentItem */
    private boolean syncingTab;
    private BottomNavigationView bottomNav;
    private View fab;
    private ImageView appBg;
    private View appBgTint;
    private View undoTip;
    private TextView tvUndoText;
    private TextView tvToolbarDate;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastDeletedId;
    private final Runnable hideUndo = () -> {
        if (undoTip != null) undoTip.setVisibility(View.GONE);
        lastDeletedId = null;
        pendingDeletedJson = null;
    };

    /**
     * 选图回调。
     *
     * <p>关键：ActivityResultLauncher 必须在 Activity 还没 STARTED 时（也就是 onCreate 里）
     * 注册完。之前是「点一次按钮 new 一个 ImagePicker」，那时 Activity 已经 RESUMED，
     * ActivityResultRegistry.register() 会直接抛 IllegalStateException 导致闪退——
     * 这就是「改卡片背景图闪退」的根因。现在改成只在 onCreate 注册两个 launcher，
     * 用 pending 回调转接，任何时候点都不会再抛。</p>
     *
     * <p>这两个 launcher 只在 onCreate 里注册一次，别处绝对不要再 registerForActivityResult
     * （在 Activity STARTED 之后注册会直接抛 IllegalStateException）。</p>
     */
    private ActivityResultLauncher<String> cardImageLauncher;
    private ActivityResultLauncher<String> appBgLauncher;
    private ImagePicker.Callback pendingImageCallback;
    /** 「文件」页的两个入口，同样只在 onCreate 注册一次 */
    private ActivityResultLauncher<String> imageNoteLauncher;
    private ActivityResultLauncher<String[]> studyFileLauncher;

    /** 当前打开着的面板，用于把选图结果回填到正确的面板上 */
    private EditSheet activeEditSheet;
    private DetailSheet activeDetailSheet;

    private ActivityResultLauncher<String> notifyPermission;

    // ------------------------------------------------------------------ 生命周期

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = TaskStore.get(this);
        store.addListener(this);
        store.save();

        appBg = findViewById(R.id.imgAppBg);
        appBgTint = findViewById(R.id.appBgTint);
        undoTip = findViewById(R.id.undoTip);
        tvUndoText = findViewById(R.id.tvUndoText);
        tvToolbarDate = findViewById(R.id.tvToolbarDate);
        pager = findViewById(R.id.pager);
        bottomNav = findViewById(R.id.bottomNav);
        fab = findViewById(R.id.fabAdd);

        // 选图 launcher 必须在这里（Activity 尚未 STARTED）注册好
        cardImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::dispatchPickedImage);
        appBgLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::dispatchPickedImage);
        // 「文件」页：相册笔记（只挑图片）与学习文件（任意类型）
        imageNoteLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> importDoc(Doc.CAT_NOTE, uri, R.string.files_added_note));
        studyFileLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> importDoc(Doc.CAT_STUDY, uri, R.string.files_added_study));

        // 底部导航自己撑住系统手势条/导航栏的安全区
        final View navView = findViewById(R.id.bottomNav);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(navView, (v, insets) -> {
            int bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
            // 页面区铺满到底，内容从底栏下面穿过，中间不会出现"带子"
            applyBottomBars(v.getHeight() + bottom);
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(navView);
        navView.post(() -> applyBottomBars(navView.getHeight()));

        notifyPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted) {
                        Toast.makeText(this, R.string.copy_no_notify_permission,
                                Toast.LENGTH_SHORT).show();
                    }
                });

        // 用系统自带的 ViewPager：内部是普通滚动容器，翻页不会像 ViewPager2 那样
        // 贴着手指急停吸附；配合 FragmentPagerAdapter，页面常驻、不重建视图，手感更顺。
        pager.setAdapter(new PagesAdapter(getSupportFragmentManager()));
        pager.setOffscreenPageLimit(4);

        bottomNav.setOnItemSelectedListener(item -> {
            // 由翻页联动过来的高亮变化，不要再回头设置一次 currentItem
            if (syncingTab) return true;
            int index = 0;
            int id = item.getItemId();
            if (id == R.id.tab_calendar) index = 1;
            else if (id == R.id.tab_done) index = 2;
            else if (id == R.id.tab_files) index = 3;
            else if (id == R.id.tab_settings) index = 4;
            pager.setCurrentItem(index, false);
            return true;
        });

        pager.addOnPageChangeListener(new androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                /*
                 * 这里只做「轻」的事：底栏高亮 + 悬浮按钮显隐。
                 *
                 * 绝对不要在这里重建列表（refreshAll）：ViewPager 是在开始滑动动画**之前**
                 * 回调 onPageSelected 的，如果这里做重活，动画的头几帧会被丢掉，
                 * 松手后画面就直接「瞬移」到下一页，而不是滑过去。
                 */
                int menuId = R.id.tab_todo;
                if (position == 1) menuId = R.id.tab_calendar;
                else if (position == 2) menuId = R.id.tab_done;
                else if (position == 3) menuId = R.id.tab_files;
                else if (position == 4) menuId = R.id.tab_settings;
                if (bottomNav.getSelectedItemId() != menuId) {
                    syncingTab = true;
                    bottomNav.setSelectedItemId(menuId);
                    syncingTab = false;
                }
                // 新建任务的悬浮按钮只在前三页用；文件页各自有「添加」，设置页不需要
                fab.setVisibility(position >= 3 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                // 等滑动彻底停稳再刷新内容，不跟翻页动画抢 UI 线程
                if (state == androidx.viewpager.widget.ViewPager.SCROLL_STATE_IDLE) {
                    applyBackground();
                    refreshAll();
                }
            }
        });

        findViewById(R.id.fabAdd).setOnClickListener(v -> openNewTask());
        findViewById(R.id.btnUndo).setOnClickListener(v -> undoDelete());

        ReminderScheduler.ensureChannel(this);
        tvToolbarDate.setText(todayText());
        ensureDefaultBackground();
        applyBackground();
        refreshAll();

        String openId = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_OPEN_TASK_ID);
        if (openId != null) {
            Task t = store.byId(openId);
            if (t != null) openDetail(t);
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        String openId = intent.getStringExtra(EXTRA_OPEN_TASK_ID);
        if (openId != null) {
            Task t = store.byId(openId);
            if (t != null) openDetail(t);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 注意：这里**不要**再调 applyBackground()。
        // 背景在 onCreate 已经铺好，回到前台时它本来就没变；
        // 之前每次 onResume 都重刷一遍，会造成「切出去切回来背景闪一下」。
        // 真正需要更新背景的场景（换图、拖动透明度）都由 applyBackground() 自己处理。
        refreshAll();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        store.removeListener(this);
        handler.removeCallbacks(hideUndo);
    }

    @Override
    public void onStoreChanged() {
        // 卡片上的 DDL 文案依赖「今天」，跨天时也要刷新
        runOnUiThread(this::refreshAll);
    }

    // ------------------------------------------------------------------ AppHost

    @Override
    public TaskStore store() {
        return store;
    }

    @Override
    public Query query() {
        return query;
    }

    @Override
    public TaskActions actions() {
        return this;
    }

    @Override
    public void refreshAll() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment f : new ArrayList<>(fragments)) {
            if (f instanceof TaskListFragment) ((TaskListFragment) f).render();
            else if (f instanceof CalendarFragment) ((CalendarFragment) f).render();
            else if (f instanceof DoneFragment) ((DoneFragment) f).render();
            else if (f instanceof FilesFragment) ((FilesFragment) f).render();
        }
        applyBackground();
    }

    /**
     * 底部布局：页面区**不再给底栏留边距**，内容直接铺到屏幕底部、从悬浮底栏下面穿过去，
     * 所以不存在「两块之间的带子」。靠各页面自己的滚动底部内边距，保证最后一条能滚出来看到。
     */
    private void applyBottomBars(int navHeightPx) {
        if (pager != null) {
            ViewGroup.LayoutParams lp = pager.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mp = (ViewGroup.MarginLayoutParams) lp;
                if (mp.bottomMargin != 0) {
                    mp.bottomMargin = 0;
                    pager.setLayoutParams(mp);
                }
            }
        }
    }

    /**
     * 铺内置的默认背景图（只在用户还没自己选过背景时执行一次）。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>已经铺过一次就不再铺 —— 之后用户手动换图、或点「清除背景图」，
     *       都不会被默认图覆盖回来；</li>
     *   <li>用户已经选过背景图则不动他的选择。</li>
     * </ul>
     */
    private void ensureDefaultBackground() {
        if (Prefs.bgSeeded(this)) return;
        Prefs.setBgSeeded(this, true);
        if (!Prefs.bgImage(this).isEmpty()) return;
        String path = ImageStore.seedDefaultBackground(this);
        if (path == null) return;
        Prefs.setBgImage(this, path);
        Prefs.setBgAlpha(this, 214);
    }

    /**
     * 全局背景图 + 透明度；没图时用浅色底。
     *
     * <p><b>关键设计：背景图始终完全不透明，透明度全部交给蒙版层的颜色 alpha 控制。</b></p>
     *
     * <p>为什么不用 {@code appBg.setImageAlpha(alpha)}：那是 <b>drawable 的内部状态</b>，
     * 窗口重建 / drawable 重新附着时会回到默认值 255（完全不透明），
     * 要等下一帧才被设回去——表现就是「背景图先清晰一下、再变半透明」。
     * 这就是前几次都没修掉的那个闪。</p>
     *
     * <p>数学上两者等价（半透明图 + 蒙版 ≡ 不透明图 + 更淡的蒙版），
     * 但蒙版用 {@code setBackgroundColor()} 设定，是普通 View 属性，不会被任何重建重置。</p>
     */
    private String appliedBgPath = "___unset___";
    private int appliedTintArgb = 0;

    private void applyBackground() {
        if (appBg == null) return;
        String path = Prefs.bgImage(this);
        int tintArgb;
        if (path.isEmpty()) {
            tintArgb = 0xF2FDF7F8;                      // 没图：不透明浅底
        } else {
            // 图始终不透明，用蒙版盖住 (255 - alpha)，剩下的就是用户看到的透明度
            int veil = Math.max(0, Math.min(255, 255 - Prefs.bgAlpha(this)));
            tintArgb = (veil << 24) | 0x00FDF7F8;
        }

        if (!path.equals(appliedBgPath)) {
            appliedBgPath = path;
            if (path.isEmpty()) {
                appBg.setImageDrawable(null);
            } else {
                // alpha 传 255：图片按原样铺上去，不做任何 drawable 级透明
                ImageStore.loadInto(appBg, path, 255, 1080, 1920);
            }
            appliedTintArgb = 0;                        // 强制下面把蒙版对齐一次
        }
        if (appliedTintArgb != tintArgb) {
            appliedTintArgb = tintArgb;
            appBgTint.setBackgroundColor(tintArgb);
        }
    }

    // ------------------------------------------------------------------ 背景

    private String todayText() {
        Calendar c = Calendar.getInstance();
        String[] week = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        return String.format(Locale.getDefault(), "%d月%d日 · %s",
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                week[c.get(Calendar.DAY_OF_WEEK) - 1]);
    }

    // ------------------------------------------------------------------ 动作

    @Override
    public void pickAppBackground(AppHost.ImagePicked callback) {
        launchImagePicker(appBgLauncher, uriString -> {
            if (uriString == null) {
                if (callback != null) callback.onPicked(null);
                return;
            }
            String path = ImageStore.persist(this, android.net.Uri.parse(uriString));
            if (callback != null) callback.onPicked(path);
        });
    }

    @Override
    public void openNewTask() {
        activeEditSheet = new EditSheet(this, store, null, task -> {
            refreshAll();
            Toast.makeText(this, R.string.copy_saved, Toast.LENGTH_SHORT).show();
        }, () -> requestCardImageForEdit());
        activeEditSheet.show();
    }

    @Override
    public void openEditTask(Task task) {
        if (task == null) return;
        Task fresh = store.byId(task.getId());
        if (fresh == null) return;
        activeEditSheet = new EditSheet(this, store, fresh, t -> {
            refreshAll();
            if (activeDetailSheet != null) activeDetailSheet.refresh();
        }, () -> requestCardImageForEdit());
        activeEditSheet.show();
    }

    private void requestCardImageForEdit() {
        EditSheet sheet = activeEditSheet;
        if (sheet == null) return;
        launchImagePicker(cardImageLauncher, uriString -> {
            String path = uriString == null ? null
                    : ImageStore.persist(this, android.net.Uri.parse(uriString));
            // 结果回来时面板可能已经被关掉或重建了，做一次身份校验
            if (sheet == activeEditSheet && !isFinishing() && !isDestroyed()) {
                sheet.setPickedImage(path);
            }
        });
    }

    /**
     * 统一的选图入口：先记下回调再启动系统选图界面。
     * 回调返回 null 表示取消或复制失败。
     */
    private void launchImagePicker(ActivityResultLauncher<String> launcher, ImagePicker.Callback cb) {
        if (isFinishing() || isDestroyed()
                || getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
            return;
        }
        pendingImageCallback = cb;
        try {
            launcher.launch("image/*");
        } catch (Exception e) {
            pendingImageCallback = null;
            Toast.makeText(this, R.string.copy_cant_open_picker, Toast.LENGTH_SHORT).show();
        }
    }

    /** 系统选图返回后的统一处理：拿到 uri 后交给等待中的回调 */
    private void dispatchPickedImage(@Nullable android.net.Uri uri) {
        ImagePicker.Callback cb = pendingImageCallback;
        pendingImageCallback = null;
        if (cb == null) return;
        if (isFinishing() || isDestroyed()) return;
        try {
            cb.onPicked(uri == null ? null : uri.toString());
        } catch (Exception e) {
            Toast.makeText(this, R.string.copy_image_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void openDetail(Task task) {
        if (task == null) return;
        Task fresh = store.byId(task.getId());
        if (fresh == null) return;
        if (activeDetailSheet != null) activeDetailSheet.dismiss();
        activeDetailSheet = new DetailSheet(this, store, fresh, new DetailSheet.DetailActions() {
            @Override
            public void onEdit(Task t) {
                openEditTask(t);
            }

            @Override
            public void onDelete(Task t) {
                deleteTask(t);
            }

            @Override
            public void onPickCardImage(Task t) {
                requestCardImage(t);
            }

            @Override
            public void onDismissed() {
                activeDetailSheet = null;
            }
        });
        activeDetailSheet.show();
    }

    @Override
    public void onOpen(Task task) {
        openDetail(task);
    }

    @Override
    public void onEdit(Task task) {
        openEditTask(task);
    }

    @Override
    public void onToggleDone(Task task) {
        if (task == null) return;
        boolean nowDone = !task.isDone();
        store.markDone(task.getId(), nowDone);
        if (nowDone) {
            ReminderScheduler.cancel(this, task);
            Toast.makeText(this, R.string.copy_done, Toast.LENGTH_SHORT).show();
        } else {
            Task t = store.byId(task.getId());
            if (t != null && t.hasReminder()) ReminderScheduler.schedule(this, t);
        }
        refreshAll();
    }

    @Override
    public void onPickCardImage(Task task) {
        requestCardImage(task);
    }

    private void requestCardImage(Task task) {
        if (task == null) return;
        final String id = task.getId();
        launchImagePicker(cardImageLauncher, uriString -> {
            if (uriString == null) {
                Toast.makeText(this, R.string.copy_no_image, Toast.LENGTH_SHORT).show();
                return;
            }
            // 选完直接用（不再裁切）；卡片那边会按 cover + 8% 放大铺满
            String saved = ImageStore.persist(this, android.net.Uri.parse(uriString));
            if (saved == null) {
                Toast.makeText(this, R.string.copy_image_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            applyCardImage(id, saved);
        });
    }

    /** 把选好的图写进任务，并清理旧图 */
    private void applyCardImage(String id, String path) {
        Task t = store.byId(id);
        if (t == null) return;
        String old = t.getCardImage();
        t.setCardImage(path);
        store.upsert(t);
        if (!old.isEmpty() && !old.equals(path)) {
            ImageStore.deleteIfUnused(this, old, usedCardImages());
        }
        refreshAll();
        if (activeDetailSheet != null && id.equals(activeDetailSheet.getTaskId())) {
            activeDetailSheet.refresh();
        }
        if (activeEditSheet != null && activeEditSheet.getWorking().getId().equals(id)) {
            activeEditSheet.setPickedImage(path);
        }
        Toast.makeText(this, R.string.copy_card_bg_done, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSetReminder(Task task) {
        requestReminder(task);
    }

    private void requestReminder(Task task) {
        final Task t = store.byId(task.getId());
        if (t == null) return;
        if (!t.hasDeadline()) {
            Toast.makeText(this, R.string.copy_reminder_need_ddl, Toast.LENGTH_SHORT).show();
            return;
        }
        ensureNotifyPermission();
        final int[] values = {-1, 0, 10, 60, 180, 1440, 4320};
        final String[] labels = new String[values.length];
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = Deadline.remindLabel(values[i]);
            if (values[i] == t.getRemindBeforeMinutes()) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("什么时候叫你呀？⏰（" + Deadline.label(t.getDeadline()) + "）")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    t.setRemindBeforeMinutes(values[which]);
                    store.upsert(t);
                    ReminderScheduler.schedule(this, t);
                    refreshAll();
                    if (activeDetailSheet != null) activeDetailSheet.refresh();
                    Toast.makeText(this, R.string.copy_reminder_set, Toast.LENGTH_SHORT).show();
                    d.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void ensureNotifyPermission() {        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    @Override
    public void addImageNote() {
        try {
            imageNoteLauncher.launch("image/*");
        } catch (Exception e) {
            Toast.makeText(this, R.string.files_import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void addStudyFile() {
        try {
            // 不限类型：学校发的东西什么格式都有
            studyFileLauncher.launch(new String[]{"*/*"});
        } catch (Exception e) {
            Toast.makeText(this, R.string.files_import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** 选完之后的统一下场：复制进私有目录 → 提示 */
    private void importDoc(int category, @Nullable android.net.Uri uri, int doneRes) {
        if (uri == null || isFinishing() || isDestroyed()) return;
        Doc d = DocStore.get(this).importFrom(category, uri);
        if (d == null) {
            Toast.makeText(this, R.string.files_import_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, doneRes, Toast.LENGTH_SHORT).show();
        // DocStore.save() 已经通知过监听者，这里不用再手动刷新
    }

    @Override
    public void onDelete(Task task) {
        deleteTask(task);
    }

    @Override
    public void onReorderLongTerm(List<String> orderedIds) {
        // 落库后 store.save() 会通知所有监听者重绘（列表顺序也就跟着更新）
        store.applyLongTermOrder(orderedIds);
    }

    private void deleteTask(Task task) {
        if (task == null) return;
        ReminderScheduler.cancel(this, task);
        // 记住被删任务的 JSON，方便「撤销」
        pendingDeletedJson = task.toJson().toString();
        lastDeletedId = task.getId();
        store.delete(task.getId());
        if (activeDetailSheet != null) activeDetailSheet.dismiss();
        refreshAll();
        showUndo("删掉一张「" + shortTitle(task.getTitle()) + "」…要拿回来吗？");
    }

    private String shortTitle(String title) {
        if (title == null) return "";
        return title.length() > 10 ? title.substring(0, 10) + "…" : title;
    }

    /** 当前仍被任务引用的卡片背景图（包括编辑面板里还没保存的那张） */
    private java.util.Collection<String> usedCardImages() {
        List<String> paths = new ArrayList<>();
        for (Task t : store.all()) {
            if (t.hasCardImage()) paths.add(t.getCardImage());
        }
        if (activeEditSheet != null && activeEditSheet.hasPickedImage()) {
            paths.add(activeEditSheet.getPickedImagePath());
        }
        return paths;
    }

    private void showUndo(String message) {
        if (undoTip == null) return;
        tvUndoText.setText(message);
        undoTip.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideUndo);
        handler.postDelayed(hideUndo, 4500);
    }

    private void undoDelete() {
        if (pendingDeletedJson == null) return;
        store.importJson("{\"tasks\":[" + pendingDeletedJson + "]}");
        pendingDeletedJson = null;
        lastDeletedId = null;
        undoTip.setVisibility(View.GONE);
        handler.removeCallbacks(hideUndo);
        refreshAll();
        Toast.makeText(this, R.string.copy_undo, Toast.LENGTH_SHORT).show();
    }

    private String pendingDeletedJson;

    // ------------------------------------------------------------------ 页面

    private static class PagesAdapter extends androidx.fragment.app.FragmentPagerAdapter {
        PagesAdapter(androidx.fragment.app.FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 1:
                    return new CalendarFragment();
                case 2:
                    return new DoneFragment();
                case 3:
                    return new FilesFragment();
                case 4:
                    return new SettingsFragment();
                default:
                    return new TaskListFragment();
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    }
}
