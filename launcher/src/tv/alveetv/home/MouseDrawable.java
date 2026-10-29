package tv.alveetv.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class MouseDrawable extends Drawable {
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF body = new RectF();
    private final RectF wheel = new RectF();

    MouseDrawable() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(0xFFF2F2F3);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFFF2F2F3);
    }

    void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        float w = b.width(), h = b.height();
        float cx = b.left + w / 2f, cy = b.top + h / 2f;

        float sw = w * 0.09f;
        stroke.setStrokeWidth(sw);

        // Mouse body contour (capsule/pill shape)
        float bw = w * 0.28f, bh = h * 0.42f;
        body.set(cx - bw, cy - bh, cx + bw, cy + bh);
        canvas.drawRoundRect(body, bw, bw, stroke);

        // Center scroll wheel
        float ww = w * 0.055f, whTop = cy - bh * 0.70f, whBot = cy - bh * 0.15f;
        wheel.set(cx - ww, whTop, cx + ww, whBot);
        canvas.drawRoundRect(wheel, ww, ww, fill);

        // Click separator line above scroll wheel
        canvas.drawLine(cx, cy - bh, cx, whTop, stroke);
    }

    @Override
    public void setAlpha(int alpha) {
        stroke.setAlpha(alpha);
        fill.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        stroke.setColorFilter(colorFilter);
        fill.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

