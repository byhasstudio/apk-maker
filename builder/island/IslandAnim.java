package __PKG__;

import android.accessibilityservice.AccessibilityService;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.HashSet;
import java.util.List;

/**
 * Animasi buka/tutup aplikasi ala iOS untuk SEMUA aplikasi di HP.
 * Cara kerja: layanan aksesibilitas mendeteksi pindah launcher <-> aplikasi, lalu menggambar
 * kartu yang membesar dari ikon (buka) / mengecil ke ikon (tutup) sebagai overlay.
 */
public class IslandAnim extends AccessibilityService {
  public static volatile IslandAnim instance;

  private final HashSet<String> homes = new HashSet<String>();
  private final HashSet<String> imes = new HashSet<String>();
  private String last = null;
  private WindowManager wm;
  private FrameLayout overlay;
  private ValueAnimator anim;

  private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

  @Override protected void onServiceConnected() {
    super.onServiceConnected();
    instance = this;
    wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    loadHomes();
  }

  @Override public boolean onUnbind(Intent i) {
    cleanup();
    instance = null;
    return super.onUnbind(i);
  }

  @Override public void onInterrupt() { cleanup(); }

  private void loadHomes() {
    homes.clear();
    imes.clear();
    try {
      PackageManager pm = getPackageManager();
      Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
      List<ResolveInfo> l = pm.queryIntentActivities(h, PackageManager.MATCH_DEFAULT_ONLY | PackageManager.MATCH_ALL);
      for (ResolveInfo r : l) homes.add(r.activityInfo.packageName);
    } catch (Exception ignored) {}
    try {
      InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      for (InputMethodInfo ii : im.getInputMethodList()) imes.add(ii.getPackageName());
    } catch (Exception ignored) {}
  }

  @Override public void onAccessibilityEvent(AccessibilityEvent ev) {
    if (ev == null || ev.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
    CharSequence p = ev.getPackageName();
    CharSequence c = ev.getClassName();
    if (p == null || c == null) return;
    String pkg = p.toString(), cls = c.toString();

    // abaikan panel notifikasi, keyboard, dialog, popup, menu
    if (pkg.equals("com.android.systemui") || pkg.equals("android") || imes.contains(pkg)) return;
    if (cls.startsWith("android.widget.") || cls.startsWith("android.view.") || cls.startsWith("android.inputmethodservice.")
        || cls.startsWith("android.app.Dialog") || cls.startsWith("android.app.AlertDialog") || cls.contains("Dialog")
        || cls.contains("Popup")) return;

    String prev = last;
    last = pkg;
    if (prev == null || prev.equals(pkg)) return;

    SharedPreferences sp = getSharedPreferences("island", MODE_PRIVATE);
    if (!sp.getBoolean("anim", false)) return;

    boolean prevHome = homes.contains(prev), nowHome = homes.contains(pkg);
    if (prevHome && !nowHome) play(pkg, true);        // buka aplikasi dari launcher
    else if (!prevHome && nowHome) play(prev, false); // tutup aplikasi ke launcher
  }

  /* ---------- efek ---------- */

  private int avgColor(Drawable d) {
    try {
      int s = 24;
      Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
      Canvas cv = new Canvas(b);
      d.setBounds(0, 0, s, s);
      d.draw(cv);
      long r = 0, g = 0, bl = 0, n = 0;
      for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) {
        int px = b.getPixel(x, y);
        if (Color.alpha(px) < 200) continue;
        r += Color.red(px); g += Color.green(px); bl += Color.blue(px); n++;
      }
      b.recycle();
      if (n == 0) return 0xFF2C2C2E;
      return Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
    } catch (Exception e) {
      return 0xFF2C2C2E;
    }
  }

  private void cleanup() {
    if (anim != null) { anim.removeAllListeners(); anim.cancel(); anim = null; }
    if (overlay != null) { try { wm.removeView(overlay); } catch (Exception ignored) {} overlay = null; }
  }

  private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
  private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

  private void play(String pkg, final boolean opening) {
    cleanup();
    try {
      DisplayMetrics dm = new DisplayMetrics();
      wm.getDefaultDisplay().getRealMetrics(dm);
      final int W = dm.widthPixels, H = dm.heightPixels;

      Drawable icon = IslandService.flat(getPackageManager().getApplicationIcon(pkg), 192);
      int color = avgColor(icon);

      final float px = W / 2f, py = H * 0.58f;                 // titik asal: kira-kira area ikon
      final float sx0 = dp(60) / (float) W, sy0 = dp(60) / (float) H;

      final GradientDrawable gd = new GradientDrawable();
      gd.setColor(color);
      final View card = new View(this);
      card.setBackground(gd);
      card.setLayerType(View.LAYER_TYPE_HARDWARE, null);
      card.setPivotX(px);
      card.setPivotY(py);

      final ImageView ic = new ImageView(this);
      ic.setImageDrawable(icon);
      ic.setClipToOutline(true);
      ic.setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override public void getOutline(View v, android.graphics.Outline o) {
          if (v.getWidth() > 0) o.setConvexPath(IslandService.squircle(v.getWidth(), v.getHeight()));
        }
      });
      int isz = dp(60);

      overlay = new FrameLayout(this);
      overlay.setClipChildren(false);
      overlay.addView(card, new FrameLayout.LayoutParams(W, H, Gravity.TOP | Gravity.START));
      FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(isz, isz, Gravity.TOP | Gravity.START);
      ilp.leftMargin = (int) (px - isz / 2f);
      ilp.topMargin = (int) (py - isz / 2f);
      overlay.addView(ic, ilp);

      WindowManager.LayoutParams lp = new WindowManager.LayoutParams(W, H,
          WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
              | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
              | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          PixelFormat.TRANSLUCENT);
      lp.gravity = Gravity.TOP | Gravity.START;
      if (android.os.Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
      lp.windowAnimations = 0;
      wm.addView(overlay, lp);

      // kurva ala iOS: cepat di awal, mendarat halus
      final PathInterpolator ease = opening ? new PathInterpolator(0.22f, 0.9f, 0.25f, 1f) : new PathInterpolator(0.3f, 0.0f, 0.2f, 1f);
      anim = ValueAnimator.ofFloat(0f, 1f);
      anim.setDuration(opening ? 430 : 360);
      anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
        @Override public void onAnimationUpdate(ValueAnimator va) {
          float f = (Float) va.getAnimatedValue();
          float g = ease.getInterpolation(f);
          float t = opening ? g : 1f - g;                       // 0 = ukuran ikon, 1 = layar penuh
          float sx = lerp(sx0, 1f, t), sy = lerp(sy0, 1f, t);
          card.setScaleX(sx);
          card.setScaleY(sy);
          float visR = lerp(dp(14), dp(38), t);                 // sudut tampak: kecil di ikon, besar di layar penuh
          gd.setCornerRadius(visR / Math.max(0.02f, Math.min(sx, sy)));
          float ca, ia;
          if (opening) {
            ca = f < 0.6f ? 1f : 1f - clamp01((f - 0.6f) / 0.4f);
            ia = 1f - clamp01(f / 0.3f);
            ic.setScaleX(1f + 0.5f * g); ic.setScaleY(1f + 0.5f * g);
          } else {
            ca = f < 0.12f ? 0.95f * (f / 0.12f) : (f < 0.72f ? 0.95f : 0.95f * (1f - clamp01((f - 0.72f) / 0.28f)));
            ia = clamp01((f - 0.45f) / 0.35f) * (1f - clamp01((f - 0.8f) / 0.2f));
            ic.setScaleX(1.5f - 0.5f * g); ic.setScaleY(1.5f - 0.5f * g);
          }
          card.setAlpha(ca);
          ic.setAlpha(ia);
        }
      });
      anim.addListener(new AnimatorListenerAdapter() {
        @Override public void onAnimationEnd(Animator a) { cleanup(); }
      });
      anim.start();
    } catch (Exception e) {
      cleanup();
    }
  }
}
