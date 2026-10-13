package com.todolife.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.todolife.app.model.Deadline;

/**
 * DDL 状态圆点：颜色直接对应「还剩几天」。
 * 用户要求的红色（≤3 天）就是在这里落地的。
 */
public class DotView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int color = Deadline.COLOR_LONGTERM;
    private boolean ringEnabled = false;
    private boolean hollow = false;

    public DotView(Context context) {
        this(context, null);
    }

    public DotView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DotView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint.setStyle(Paint.Style.FILL);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(1.5f));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** 直接按截止时间刷新颜色（deadline <= 0 表示长线任务） */
    public void bindDeadline(long deadline) {
        setDotColor(Deadline.urgencyColor(deadline));
    }

    public void setDotColor(int c) {
        this.color = c;
        invalidate();
    }

    public int getDotColor() {
        return color;
    }

    /** 置顶任务：外面加一圈 */
    public void setRingEnabled(boolean enabled) {
        this.ringEnabled = enabled;
        invalidate();
    }

    /** 已完成任务：空心 */
    public void setHollow(boolean hollow) {
        this.hollow = hollow;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f - dp(2f);

        if (ringEnabled) {
            ring.setColor(color);
            ring.setAlpha(90);
            canvas.drawCircle(cx, cy, r, ring);
            r -= dp(2.5f);
        }

        if (hollow) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2f));
            paint.setColor(color);
            canvas.drawCircle(cx, cy, r - dp(1f), paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            paint.setColor(color);
            canvas.drawCircle(cx, cy, r, paint);
            // 高光，让圆点看起来更立体
            Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
            hl.setColor(0x33FFFFFF);
            canvas.drawCircle(cx - r * 0.28f, cy - r * 0.3f, r * 0.42f, hl);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int size = (int) dp(12);
        int w = resolveSize(size, widthMeasureSpec);
        int h = resolveSize(size, heightMeasureSpec);
        setMeasuredDimension(Math.max(w, size), Math.max(h, size));
    }
}
