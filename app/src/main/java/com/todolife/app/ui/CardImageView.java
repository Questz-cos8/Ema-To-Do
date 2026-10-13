package com.todolife.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * 卡片背景图专用视图。
 *
 * <p>做两件事：</p>
 * <ol>
 *   <li><b>圆角裁剪</b>：{@link #getOutline} 给出圆角矩形轮廓，{@link #onDraw} 再用同一个圆角路径
 *       {@code clipPath} 裁一次画布，双保险，四角一定不会溢出卡片。</li>
 *   <li><b>铺满并多放大一点</b>：自己算缩放矩阵（cover 铺满后再乘 {@link #extraZoom}），
 *       保证图片完全盖住控件、四边不露底色。</li>
 * </ol>
 */
public class CardImageView extends AppCompatImageView {

    /** 默认比「铺满」再多 8% */
    private static final float DEFAULT_EXTRA_ZOOM = 1.08f;

    private final Path clipPath = new Path();
    private final RectF bounds = new RectF();
    private float radius;
    private float extraZoom = DEFAULT_EXTRA_ZOOM;

    public CardImageView(Context context) {
        this(context, null);
    }

    public CardImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CardImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        radius = 18f * getResources().getDisplayMetrics().density;
        // 自己算矩阵，必须用 MATRIX 模式
        setScaleType(ScaleType.MATRIX);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    /** 圆角半径（像素） */
    public void setCornerRadius(float px) {
        this.radius = px;
        invalidateOutline();
        invalidate();
    }

    /** 额外放大倍率：1.08 = 比铺满再多 8% */
    public void setExtraZoom(float zoom) {
        this.extraZoom = zoom <= 0 ? 1f : zoom;
        updateMatrix();
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        updateMatrix();
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        updateMatrix();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bounds.set(0, 0, w, h);
        clipPath.reset();
        clipPath.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        invalidateOutline();
        updateMatrix();
    }

    /** 计算 cover + extraZoom 的缩放矩阵；图片始终居中 */
    private void updateMatrix() {
        Drawable d = getDrawable();
        int vw = getWidth();
        int vh = getHeight();
        if (!(d instanceof BitmapDrawable) || vw <= 0 || vh <= 0) {
            super.setImageMatrix(null);
            return;
        }
        Bitmap bmp = ((BitmapDrawable) d).getBitmap();
        if (bmp == null || bmp.isRecycled() || bmp.getWidth() <= 0 || bmp.getHeight() <= 0) {
            super.setImageMatrix(null);
            return;
        }
        float scale = Math.max((float) vw / bmp.getWidth(), (float) vh / bmp.getHeight()) * extraZoom;
        float dx = (vw - bmp.getWidth() * scale) / 2f;
        float dy = (vh - bmp.getHeight() * scale) / 2f;
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(dx, dy);
        super.setImageMatrix(m);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bounds.isEmpty()) {
            super.onDraw(canvas);
            return;
        }
        int saved = canvas.save();
        canvas.clipPath(clipPath);
        super.onDraw(canvas);
        canvas.restoreToCount(saved);
    }
}
