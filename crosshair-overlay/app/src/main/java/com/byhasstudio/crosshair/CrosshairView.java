package com.byhasstudio.crosshair;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.view.View;

/** Menggambar crosshair sesuai pengaturan. Dipakai di jendela overlay dan di pratinjau aplikasi. */
public class CrosshairView extends View {
  public static final String[] STYLES = {
      "Plus (+)", "Titik", "Lingkaran", "Plus + titik", "Bentuk T", "Silang (X)", "Lingkaran + titik"
  };

  private final Paint main = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float density;
  private int style = 0;
  private float size = 100f, thick = 4f, gap = 10f, outlinePx = 2f;
  private boolean outline = true, edit = false;
  private int opacity = 100;

  public CrosshairView(Context c) {
    super(c);
    density = c.getResources().getDisplayMetrics().density;
    edge.setColor(Color.BLACK);
    frame.setStyle(Paint.Style.STROKE);
    frame.setColor(Color.WHITE);
    frame.setStrokeWidth(2f * density);
    frame.setPathEffect(new DashPathEffect(new float[] { 10f * density, 6f * density }, 0f));
  }

  /** Ukuran jendela (px) yang cukup untuk crosshair + area sentuh saat mode geser. */
  public static int boxPx(SharedPreferences p, float density) {
    int size = Math.max(8, Math.min(200, p.getInt("size", 36)));
    return Math.round(Math.max(size + 16, 72) * density);
  }

  public void apply(SharedPreferences p) {
    style = Math.max(0, Math.min(STYLES.length - 1, p.getInt("style", 0)));
    size = Math.max(8, Math.min(200, p.getInt("size", 36))) * density;
    thick = Math.max(1, Math.min(12, p.getInt("thick", 2))) * density;
    gap = Math.max(0, Math.min(60, p.getInt("gap", 4))) * density;
    outline = p.getBoolean("outline", true);
    opacity = Math.max(10, Math.min(100, p.getInt("alpha", 100)));
    main.setColor(p.getInt("color", 0xFF00E676) | 0xFF000000);
    outlinePx = Math.max(1f, density);
    float maxGap = size / 2f - 2f;
    if (gap > maxGap) gap = Math.max(0f, maxGap);
    updateAlpha();
    invalidate();
  }

  public void setEdit(boolean e) {
    edit = e;
    updateAlpha();
    invalidate();
  }

  private void updateAlpha() {
    setAlpha(edit ? 1f : opacity / 100f);
  }

  @Override protected void onDraw(Canvas c) {
    if (edit) c.drawColor(0x55000000);
    if (outline) shapes(c, edge, outlinePx);
    shapes(c, main, 0f);
    if (edit) {
      float m = frame.getStrokeWidth();
      c.drawRect(m, m, getWidth() - m, getHeight() - m, frame);
    }
  }

  private float dotR() {
    return Math.max(thick * 0.9f, size / 14f);
  }

  private void shapes(Canvas c, Paint p, float grow) {
    float cx = getWidth() / 2f, cy = getHeight() / 2f, h = size / 2f, t = thick / 2f;
    p.setStyle(Paint.Style.FILL);
    switch (style) {
      case 0:
        plus(c, p, cx, cy, h, t, gap, grow, true);
        break;
      case 1:
        c.drawCircle(cx, cy, dotR() + grow, p);
        break;
      case 2:
        ring(c, p, cx, cy, h, grow);
        break;
      case 3:
        plus(c, p, cx, cy, h, t, gap, grow, true);
        c.drawCircle(cx, cy, dotR() + grow, p);
        break;
      case 4:
        plus(c, p, cx, cy, h, t, gap, grow, false);
        break;
      case 5:
        cross(c, p, cx, cy, h, gap, grow);
        break;
      default:
        ring(c, p, cx, cy, h, grow);
        c.drawCircle(cx, cy, dotR() + grow, p);
        break;
    }
  }

  private void plus(Canvas c, Paint p, float cx, float cy, float h, float t, float g, float grow, boolean top) {
    g = Math.min(g, h - 1f);
    c.drawRect(cx - h - grow, cy - t - grow, cx - g + grow, cy + t + grow, p);
    c.drawRect(cx + g - grow, cy - t - grow, cx + h + grow, cy + t + grow, p);
    if (top) c.drawRect(cx - t - grow, cy - h - grow, cx + t + grow, cy - g + grow, p);
    c.drawRect(cx - t - grow, cy + g - grow, cx + t + grow, cy + h + grow, p);
  }

  private void ring(Canvas c, Paint p, float cx, float cy, float h, float grow) {
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(thick + 2f * grow);
    c.drawCircle(cx, cy, Math.max(1f, h - thick / 2f), p);
    p.setStyle(Paint.Style.FILL);
  }

  private void cross(Canvas c, Paint p, float cx, float cy, float h, float g, float grow) {
    float d = h * 0.7071f;
    float gd = Math.max(0f, Math.min(g * 0.7071f, d - 1f));
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(thick + 2f * grow);
    p.setStrokeCap(Paint.Cap.BUTT);
    c.drawLine(cx - d, cy - d, cx - gd, cy - gd, p);
    c.drawLine(cx + gd, cy + gd, cx + d, cy + d, p);
    c.drawLine(cx + d, cy - d, cx + gd, cy - gd, p);
    c.drawLine(cx - gd, cy + gd, cx - d, cy + d, p);
    p.setStyle(Paint.Style.FILL);
  }
}
