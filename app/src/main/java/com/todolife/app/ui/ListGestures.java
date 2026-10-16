package com.todolife.app.ui;

import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;

import androidx.core.widget.NestedScrollView;

import com.google.android.material.card.MaterialCardView;
import com.todolife.app.R;
import com.todolife.app.model.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * 列表行上的一套手势。写在一起是因为一个 View 只能挂一个 OnTouchListener：
 *
 * <ul>
 *   <li><b>横向左拖</b>：露出删除底，拖过卡片宽度 35% 就删掉，否则弹回；</li>
 *   <li><b>长按</b>：进入拖拽排序（只有长线任务区开启）。</li>
 * </ul>
 *
 * <h3>拖拽是怎么做的</h3>
 *
 * <p>列表是 LinearLayout 而不是 RecyclerView，所以这套动画得自己算：</p>
 * <ol>
 *   <li>长按后把该行「抬起来」（加 translationZ 画在其他行上面）并跟着手指走；</li>
 *   <li>其余行按「被拖行当前中心」算出的目标位置平移让出空槽，带轻微回弹所以看起来是弹开的；</li>
 *   <li>松手时把被拖行补间进目标槽位，再落库、重绘。</li>
 * </ol>
 *
 * <p><b>所有位置都在「容器坐标系」里算</b>（即 {@code getTop()} 那一套，不受滚动影响），
 * 滚动量单独补偿：手指停住不动、列表自动往上滚时，被拖行仍然黏在手指下。</p>
 */
final class ListGestures {

    /** 左滑删除阈值：卡片宽度的比例 */
    private static final float SWIPE_THRESHOLD_RATIO = 0.35f;
    /** 长按判定时长 */
    private static final int LONG_PRESS_MS = 320;
    /** 位移超过这个值（dp）就认为用户想滚动或横滑，取消长按 */
    private static final int MOVE_SLOP_DP = 8;
    /** 拖到距可见区上下边缘这么近时开始自动滚动 */
    private static final int AUTO_SCROLL_EDGE_DP = 80;
    /** 自动滚动步长（dp / 帧） */
    private static final int AUTO_SCROLL_STEP_DP = 5;
    /** 兄弟行让位的动画时长 */
    private static final int SHIFT_MS = 190;
    /** 松手后补间到槽位的时长 */
    private static final int DROP_MS = 140;

    private ListGestures() {
    }

    static void attach(View rowRoot, MaterialCardView card, Task task,
                       LinearLayout container, boolean draggable, TaskActions actions) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Drag drag = new Drag();
        final float[] downX = {0f};
        final float[] downY = {0f};
        final float[] lastX = {0f};
        final float[] lastY = {0f};
        final boolean[] swiping = {false};

        final Runnable longPressCheck = () -> {
            if (swiping[0] || drag.active) return;
            int slop = dp(rowRoot, MOVE_SLOP_DP);
            if (Math.abs(lastX[0] - downX[0]) > slop || Math.abs(lastY[0] - downY[0]) > slop) {
                return;   // 长按期间动过手指，当作滚动/滑动
            }
            drag.start(rowRoot, container, actions, lastY[0]);
            if (drag.active) {
                rowRoot.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        };

        rowRoot.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = lastX[0] = event.getRawX();
                    downY[0] = lastY[0] = event.getRawY();
                    swiping[0] = false;
                    if (draggable) handler.postDelayed(longPressCheck, LONG_PRESS_MS);
                    break;

                case MotionEvent.ACTION_MOVE: {
                    lastX[0] = event.getRawX();
                    lastY[0] = event.getRawY();
                    if (drag.active) {
                        drag.moveTo(lastY[0]);
                        return true;
                    }
                    float dx = lastX[0] - downX[0];
                    float dy = lastY[0] - downY[0];
                    if (Math.abs(dx) > dp(view, MOVE_SLOP_DP) || Math.abs(dy) > dp(view, MOVE_SLOP_DP)) {
                        handler.removeCallbacks(longPressCheck);
                    }
                    if (!swiping[0] && Math.abs(dx) > 24 && Math.abs(dx) > Math.abs(dy)) {
                        swiping[0] = true;
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (swiping[0]) {
                        card.setTranslationX(Math.min(0f, dx));
                        return true;
                    }
                    break;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    handler.removeCallbacks(longPressCheck);
                    if (drag.active) {
                        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) drag.cancel();
                        else drag.finish();
                        return true;
                    }
                    if (swiping[0]) {
                        lastX[0] = event.getRawX();
                        lastY[0] = event.getRawY();
                        float dx = lastX[0] - downX[0];
                        float dy = lastY[0] - downY[0];
                        boolean delete = Math.abs(dx) >= Math.abs(dy)
                                && dx < -view.getWidth() * SWIPE_THRESHOLD_RATIO;
                        swiping[0] = false;
                        snapBack(card);
                        if (delete && actions != null) actions.onDelete(task);
                        return true;
                    }
                    break;
                }

                default:
                    break;
            }
            return false;
        });
    }

    private static void snapBack(View card) {
        card.animate().translationX(0f).setDuration(150).start();
    }

    private static int dp(View v, int value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ 拖拽状态

    /**
     * 一次拖拽的状态与算法。位置一律用「相对容器」的坐标：
     * 行的 {@code getTop()} 不随滚动变化，所以这套计算在列表滚动时依然成立。
     */
    private static final class Drag {

        boolean active;

        private View row;
        private LinearLayout container;
        private TaskActions actions;
        private NestedScrollView scroller;
        /** 拖拽期间临时关掉裁剪的那个父容器，松手要还原 */
        private ViewGroup clipTarget;

        /** 参与拖拽的行（按初始顺序）及其初始 top / 高度 */
        private final List<View> rows = new ArrayList<>();
        private final List<Integer> tops = new ArrayList<>();
        private final List<Integer> heights = new ArrayList<>();

        private int gap;
        private int fromIndex;
        private int toIndex;
        private int baseTop;
        private int rowTop;
        private int rowHeight;
        private float fingerStartY;
        private int scrollAtStart;
        private float lastRawY;

        private final Handler autoScroll = new Handler(Looper.getMainLooper());
        private Runnable autoScrollTask;

        void start(View rowRoot, LinearLayout list, TaskActions taskActions, float pressRawY) {
            if (list == null || list.getChildCount() < 2) return;
            this.row = rowRoot;
            this.container = list;
            this.actions = taskActions;
            this.fingerStartY = pressRawY;
            this.lastRawY = pressRawY;

            rows.clear();
            tops.clear();
            heights.clear();
            gap = 0;
            for (int i = 0; i < list.getChildCount(); i++) {
                View child = list.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                rows.add(child);
                tops.add(child.getTop());
                heights.add(child.getHeight());
                if (gap == 0 && child.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                    gap = ((LinearLayout.LayoutParams) child.getLayoutParams()).bottomMargin;
                }
            }
            fromIndex = rows.indexOf(rowRoot);
            if (fromIndex < 0 || rows.size() < 2) return;

            toIndex = fromIndex;
            baseTop = tops.get(0);
            rowTop = tops.get(fromIndex);
            rowHeight = heights.get(fromIndex);
            scroller = findScroller(list);
            scrollAtStart = scroller == null ? 0 : scroller.getScrollY();

            active = true;

            // 抬起来：画在其他行之上，并允许越出容器边界
            list.setClipChildren(false);
            if (list.getParent() instanceof ViewGroup) {
                clipTarget = (ViewGroup) list.getParent();
                clipTarget.setClipChildren(false);
            }
            rowRoot.setTranslationZ(dp(rowRoot, 12));
            rowRoot.setScaleX(1.02f);
            rowRoot.setScaleY(1.02f);
            if (rowRoot.getParent() != null) {
                rowRoot.getParent().requestDisallowInterceptTouchEvent(true);
            }
            rowRoot.setPressed(false);
        }

        void moveTo(float rawY) {
            if (!active) return;
            lastRawY = rawY;
            int scrollNow = scroller == null ? 0 : scroller.getScrollY();
            // 手指位移 + 列表自身滚动的补偿 = 被拖行在容器里的真实位移
            float moved = (rawY - fingerStartY) + (scrollNow - scrollAtStart);
            row.setTranslationY(moved);

            float center = rowTop + moved + rowHeight / 2f;
            int index = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (i == fromIndex) continue;
                if (tops.get(i) + heights.get(i) / 2f < center) index++;
            }
            if (index != toIndex) {
                toIndex = index;
                restack();
            }
            updateAutoScroll(rawY);
        }

        /** 按新的插入位置重新堆叠，给每一行算目标 translationY */
        private void restack() {
            List<Integer> order = newOrder();
            int y = baseTop;
            for (int slot = 0; slot < order.size(); slot++) {
                int i = order.get(slot);
                if (i == fromIndex) {
                    y += heights.get(i) + gap;   // 被拖行跟手，不参与让位
                    continue;
                }
                View v = rows.get(i);
                float target = y - tops.get(i);
                if (Math.abs(v.getTranslationY() - target) > 0.5f) {
                    v.animate()
                            .translationY(target)
                            .setDuration(SHIFT_MS)
                            .setInterpolator(new OvershootInterpolator(0.9f))
                            .start();
                }
                y += heights.get(i) + gap;
            }
        }

        /** 新顺序：去掉被拖行、再插到 toIndex 处 */
        private List<Integer> newOrder() {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                if (i != fromIndex) order.add(i);
            }
            order.add(Math.max(0, Math.min(order.size(), toIndex)), fromIndex);
            return order;
        }

        private void updateAutoScroll(float rawY) {
            if (scroller == null) return;
            int[] loc = new int[2];
            scroller.getLocationOnScreen(loc);
            int edge = dp(scroller, AUTO_SCROLL_EDGE_DP);
            final int dir;
            if (rawY < loc[1] + edge) dir = -1;
            else if (rawY > loc[1] + scroller.getHeight() - edge) dir = 1;
            else dir = 0;

            if (dir == 0) {
                stopAutoScroll();
                return;
            }
            if (autoScrollTask != null) return;
            final int step = dp(scroller, AUTO_SCROLL_STEP_DP);
            autoScrollTask = new Runnable() {
                @Override
                public void run() {
                    if (!active) return;
                    int before = scroller.getScrollY();
                    scroller.scrollBy(0, dir * step);
                    if (scroller.getScrollY() == before) {   // 到顶/到底了
                        stopAutoScroll();
                        return;
                    }
                    moveTo(lastRawY);
                    autoScroll.postDelayed(this, 16);
                }
            };
            autoScroll.postDelayed(autoScrollTask, 16);
        }

        private void stopAutoScroll() {
            if (autoScrollTask != null) {
                autoScroll.removeCallbacks(autoScrollTask);
                autoScrollTask = null;
            }
        }

        /** 松手：补间到目标槽位 → 落库 */
        void finish() {
            if (!active) return;
            active = false;
            stopAutoScroll();

            if (toIndex == fromIndex) {
                cancel();
                return;
            }
            List<Integer> order = newOrder();
            int target = baseTop;
            for (int slot = 0; slot < order.size(); slot++) {
                int i = order.get(slot);
                if (i == fromIndex) break;
                target += heights.get(i) + gap;
            }
            row.animate()
                    .translationY(target - rowTop)
                    .setDuration(DROP_MS)
                    .withEndAction(this::commit)
                    .start();
        }

        /** 手势被打断：什么都不改，弹回原位 */
        void cancel() {
            active = false;
            stopAutoScroll();
            for (View v : rows) {
                v.animate().translationY(0f).setDuration(DROP_MS).start();
            }
            row.animate().translationZ(0f).scaleX(1f).scaleY(1f).setDuration(DROP_MS).start();
            restoreState();
        }

        private void commit() {
            List<String> ids = new ArrayList<>();
            for (int i : newOrder()) {
                Object tag = rows.get(i).getTag(R.id.row_task);
                if (tag instanceof String) ids.add((String) tag);
            }
            restoreState();
            if (actions != null && ids.size() == rows.size()) {
                actions.onReorderLongTerm(ids);
            }
        }

        private void restoreState() {
            row.setTranslationZ(0f);
            row.setScaleX(1f);
            row.setScaleY(1f);
            for (View v : rows) v.setTranslationY(0f);
            if (scroller != null) scroller.requestDisallowInterceptTouchEvent(false);
            restoreClip();
        }

        private void restoreClip() {
            if (container == null) return;
            container.setClipChildren(true);
            if (clipTarget != null) clipTarget.setClipChildren(true);
        }

        private static NestedScrollView findScroller(View v) {
            View p = v;
            while (p != null) {
                if (p instanceof NestedScrollView) return (NestedScrollView) p;
                p = p.getParent() instanceof View ? (View) p.getParent() : null;
            }
            return null;
        }

        private static int dp(View v, int value) {
            return Math.round(value * v.getResources().getDisplayMetrics().density);
        }
    }
}
