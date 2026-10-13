package com.todolife.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.todolife.app.model.Deadline;
import com.todolife.app.model.Task;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 月视图日历控件：整月网格 + 每天下面的任务小圆点。
 *
 * <p>圆点颜色沿用「待完成」页的规则：暗樱=逾期、樱红=≤3天、杏橙=≤7天、
 * 薄荷=≤14天、灰紫=&gt;14天、淡紫=无 DDL 的长线任务。一天最多画 4 个点，
 * 更多的用一个小「+」提示。</p>
 */
public class CalendarView extends View {

    public interface OnDaySelected {
        void onDaySelected(long dayMillis);
    }

    private static final String[] WEEK_LABELS = {"一", "二", "三", "四", "五", "六", "日"};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<Task> tasks = new ArrayList<>();

    private int year;
    private int month;          // 0-11
    private long selectedMillis;
    private OnDaySelected listener;

    private int cellW;
    private int cellH;
    private int headerH;
    private float density;

    public CalendarView(Context context) {
        this(context, null);
    }

    public CalendarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        textPaint.setTextAlign(Paint.Align.CENTER);
        headerPaint.setTextAlign(Paint.Align.CENTER);
        headerPaint.setFakeBoldText(true);
        Calendar c = Calendar.getInstance();
        year = c.get(Calendar.YEAR);
        month = c.get(Calendar.MONTH);
        selectedMillis = Deadline.startOfDay(System.currentTimeMillis());
    }

    public void setOnDaySelected(OnDaySelected l) {
        this.listener = l;
    }

    public void setTasks(List<Task> list) {
        tasks.clear();
        if (list != null) tasks.addAll(list);
        invalidate();
    }

    public void setMonth(int y, int m) {
        this.year = y;
        this.month = m % 12;
        if (m >= 12 || m < 0) {
            this.year = y + (m / 12);
            this.month = ((m % 12) + 12) % 12;
        }
        invalidate();
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public long getSelectedMillis() {
        return selectedMillis;
    }

    public void setSelectedMillis(long millis) {
        this.selectedMillis = Deadline.startOfDay(millis);
        invalidate();
    }

    public void shiftMonth(int delta) {
        setMonth(year, month + delta);
    }

    /** 本月第一天是周几（把周日转成 7，配合周一开头） */
    private int firstDayOffset() {
        Calendar c = Calendar.getInstance();
        c.set(year, month, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int dow = c.get(Calendar.DAY_OF_WEEK);   // 1=周日 ... 7=周六
        return (dow == Calendar.SUNDAY) ? 6 : dow - 2;
    }

    private int daysInMonth() {
        Calendar c = Calendar.getInstance();
        c.set(year, month, 1);
        return c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize((int) (density * 320), widthMeasureSpec);
        // 表头 26dp + 6 行 × 46dp
        int h = (int) (density * (26 + 6 * 46));
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        cellW = w / 7;
        headerH = (int) (density * 26);
        cellH = (getHeight() - headerH) / 6;

        // ---------- 星期表头 ----------
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(density * 11.5f);
        textPaint.setColor(0xFF9B93A1);
        for (int i = 0; i < 7; i++) {
            float cx = cellW * i + cellW / 2f;
            canvas.drawText(WEEK_LABELS[i], cx, headerH - density * 7, textPaint);
        }

        // ---------- 日期网格 ----------
        int offset = firstDayOffset();
        int total = daysInMonth();
        long todayStart = Deadline.startOfDay(System.currentTimeMillis());

        Calendar dayCal = Calendar.getInstance();
        dayCal.set(year, month, 1);
        dayCal.set(Calendar.HOUR_OF_DAY, 0);
        dayCal.set(Calendar.MINUTE, 0);
        dayCal.set(Calendar.SECOND, 0);
        dayCal.set(Calendar.MILLISECOND, 0);

        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(density * 14f);

        for (int i = 0; i < total; i++) {
            int index = offset + i;
            int col = index % 7;
            int row = index / 7;
            if (row > 5) break;

            long dayStart = dayCal.getTimeInMillis() + (long) i * 24L * 3600 * 1000;
            float cx = cellW * col + cellW / 2f;
            float cy = headerH + cellH * row + cellH * 0.42f;

            boolean isToday = dayStart == todayStart;
            boolean isSelected = dayStart == selectedMillis;

            if (isSelected) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFF2DCE4);
                canvas.drawCircle(cx, cy, Math.min(cellW, cellH) * 0.33f, paint);
            } else if (isToday) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(density * 1.4f);
                paint.setColor(0xFFE8879F);
                canvas.drawCircle(cx, cy, Math.min(cellW, cellH) * 0.33f, paint);
            }

            textPaint.setColor(isSelected ? 0xFFC95F77 : (isToday ? 0xFFE8879F : 0xFF3D3742));
            if (isToday || isSelected) textPaint.setFakeBoldText(true);
            canvas.drawText(String.valueOf(i + 1), cx, cy + density * 5f, textPaint);
            textPaint.setFakeBoldText(false);

            drawDayDots(canvas, dayStart, cx, headerH + cellH * (row + 1) - density * 6f);
        }
    }

    /** 一天下面的小圆点：按紧急度上色，最多 4 个 */
    private void drawDayDots(Canvas canvas, long dayStart, float cx, float dotY) {
        List<Integer> colors = new ArrayList<>();
        for (Task t : tasks) {
            if (!t.hasDeadline()) continue;
            if (Deadline.startOfDay(t.getDeadline()) != dayStart) continue;
            colors.add(Deadline.urgencyColor(t.getDeadline()));
            if (colors.size() >= 4) break;
        }
        if (colors.isEmpty()) return;

        float r = density * 2.1f;
        float gap = density * 5.4f;
        float totalW = (colors.size() - 1) * gap;
        float startX = cx - totalW / 2f;
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < colors.size(); i++) {
            paint.setColor(colors.get(i));
            canvas.drawCircle(startX + i * gap, dotY, r, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int w = getWidth();
            cellW = w / 7;
            headerH = (int) (density * 26);
            cellH = (getHeight() - headerH) / 6;
            float x = event.getX();
            float y = event.getY();
            if (y >= headerH) {
                int col = (int) Math.floor(x / cellW);
                int row = (int) Math.floor((y - headerH) / cellH);
                if (col >= 0 && col < 7 && row >= 0 && row < 6) {
                    int dayNumber = row * 7 + col - firstDayOffset() + 1;
                    if (dayNumber >= 1 && dayNumber <= daysInMonth()) {
                        Calendar c = Calendar.getInstance();
                        c.set(year, month, dayNumber);
                        c.set(Calendar.HOUR_OF_DAY, 0);
                        c.set(Calendar.MINUTE, 0);
                        c.set(Calendar.SECOND, 0);
                        c.set(Calendar.MILLISECOND, 0);
                        selectedMillis = c.getTimeInMillis();
                        invalidate();
                        if (listener != null) listener.onDaySelected(selectedMillis);
                        return true;
                    }
                }
            }
        }
        return true;
    }

    /** 给外部用的辅助：今天的 00:00 */
    public static long today() {
        return Deadline.startOfDay(System.currentTimeMillis());
    }

    /** 未使用但保留：矩形绘制辅助，方便后续画整行高亮 */
    @SuppressWarnings("unused")
    private void drawRowHighlight(Canvas canvas, RectF r) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x0FE8879F);
        canvas.drawRoundRect(r, density * 8, density * 8, paint);
    }
}
