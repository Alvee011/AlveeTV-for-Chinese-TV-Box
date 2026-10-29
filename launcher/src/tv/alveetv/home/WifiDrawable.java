package tv.alveetv.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class WifiDrawable extends Drawable {
    static final int STATE_DISCONNECTED = 0;
    static final int STATE_WIFI_OK = 1;
    static final int STATE_WIFI_NO_INTERNET = 2;
    static final int STATE_ETHERNET = 3;

    private int state = STATE_DISCONNECTED;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    WifiDrawable() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    void setState(int s) {
        if (state != s) {
            state = s;
            invalidateSelf();
        }
    }

    int getWifiState() {
        return state;
    }

    private int color() {
        switch (state) {
            case STATE_WIFI_OK:
                return 0xFF34C759; // Green
            case STATE_WIFI_NO_INTERNET:
                return 0xFFFF3B30; // Red
            case STATE_ETHERNET:
                return 0xFF34C759; // Green (or 0xFFF2F2F3)
            default:
                return 0x66FFFFFF; // Translucent Grey
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        int col = color();
        stroke.setColor(col);
        fill.setColor(col);

        float w = b.width(), h = b.height();
        float cx = b.left + w / 2f, cy = b.top + h / 2f;

        if (state == STATE_ETHERNET) {
            // Draw Ethernet LAN icon: monitor + cable
            float sw = w * 0.09f;
            stroke.setStrokeWidth(sw);

            // Screen frame
            float l = cx - w * 0.35f, t = cy - h * 0.36f;
            float r = cx + w * 0.35f, bot = cy + h * 0.12f;
            arcBounds.set(l, t, r, bot);
            canvas.drawRoundRect(arcBounds, w * 0.08f, w * 0.08f, stroke);

            // Screen stand line
            canvas.drawLine(cx, bot, cx, cy + h * 0.28f, stroke);

            // LAN bus line
            canvas.drawLine(cx - w * 0.32f, cy + h * 0.28f, cx + w * 0.32f, cy + h * 0.28f, stroke);
        } else {
            // Draw Wi-Fi icon: dot + 2 arcs
            float sw = w * 0.11f;
            stroke.setStrokeWidth(sw);

            float baseY = cy + h * 0.28f;

            // Dot
            canvas.drawCircle(cx, baseY, sw * 0.75f, fill);

            // Inner arc
            float r1 = w * 0.22f;
            arcBounds.set(cx - r1, baseY - r1, cx + r1, baseY + r1);
            canvas.drawArc(arcBounds, 225, 90, false, stroke);

            // Outer arc
            float r2 = w * 0.40f;
            arcBounds.set(cx - r2, baseY - r2, cx + r2, baseY + r2);
            canvas.drawArc(arcBounds, 225, 90, false, stroke);
        }
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
