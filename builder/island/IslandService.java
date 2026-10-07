package __PKG__;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

public class IslandService extends Service {
  public static volatile IslandService instance;
  public static volatile boolean camFound = false;
  public static final String ACTION_RELOAD = "island.RELOAD";
  private static final String CHANNEL = "island_service";
  private static final long SHOW_MS = 4500;
  private static final int MAX_ROWS = 3;
  private static final int BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
      | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
      | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
      | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

  private static class Entry {
    String title, text;
    Drawable avatar, badge;
    PendingIntent pi;
  }

  private final Handler main = new Handler(Looper.getMainLooper());
  private final GradientDrawable bg = new GradientDrawable();
  private final ArrayList<Entry> entries = new ArrayList<Entry>();
  private WindowManager wm;
  private WindowManager.LayoutParams wlp;
  private FrameLayout root, island;
  private LinearLayout content;
  private int cW, cH, eW, screenW;
  private int camX = 0, camCy = -1, camW = 0, camH = 0, offX = 0, offY = 0;
  private boolean camTried = false, expanded = false, openingFx = false;
  private int tc, sc, dc, ring;

  // simulasi pegas (lebar & tinggi island)
  private float curW, curH, vw, vh, tw, th, kw, dw, kh, dh;
  private boolean springing = false;
  private long lastNs = 0;
  private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
    @Override public void doFrame(long ns) { step(ns); }
  };

  private final Runnable collapseRun = new Runnable() { public void run() { collapse(); } };

  /** Bentuk squircle ala iOS (superellipse, n=5) untuk ukuran w x h. */
  static Path squircle(float w, float h) {
    Path p = new Path();
    final int N = 120;
    final double e = 2.0 / 5.0;
    for (int i = 0; i < N; i++) {
      double t = 2 * Math.PI * i / N;
      double c = Math.cos(t), sn = Math.sin(t);
      float x = (float) (w / 2 + Math.signum(c) * Math.pow(Math.abs(c), e) * w / 2);
      float y = (float) (h / 2 + Math.signum(sn) * Math.pow(Math.abs(sn), e) * h / 2);
      if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
    }
    p.close();
    return p;
  }

  /** Ikon adaptif digambar penuh (tanpa topeng bulat/kotak bawaan HP) supaya bisa dipotong squircle. */
  static Drawable flat(Drawable d, int size) {
    try {
      if (d == null || Build.VERSION.SDK_INT < 26 || !(d instanceof AdaptiveIconDrawable)) return d;
      AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
      Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(b);
      int o = size / 4;  // lapisan 108dp, bagian terlihat 72dp
      if (ad.getBackground() != null) { ad.getBackground().setBounds(-o, -o, size + o, size + o); ad.getBackground().draw(c); }
      if (ad.getForeground() != null) { ad.getForeground().setBounds(-o, -o, size + o, size + o); ad.getForeground().draw(c); }
      return new BitmapDrawable(b);
    } catch (Exception e) {
      return d;
    }
  }

  /** Lama island terbuka (ms), bisa diatur di pengaturan: 2 - 20 detik. */
  private long showMs() {
    int v = getSharedPreferences("island", MODE_PRIVATE).getInt("dur", (int) SHOW_MS);
    return Math.max(2000, Math.min(20000, v));
  }

  private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

  @Override public IBinder onBind(Intent i) { return null; }

  @Override public void onCreate() {
    super.onCreate();
    instance = this;
    wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    try {
      startFg();
      build();
    } catch (Exception e) {
      stopSelf();
    }
  }

  @Override public int onStartCommand(Intent i, int flags, int startId) {
    if (i != null && ACTION_RELOAD.equals(i.getAction())) {
      destroyView();
      try { build(); } catch (Exception e) { stopSelf(); }
    }
    return START_STICKY;
  }

  @Override public void onDestroy() {
    destroyView();
    instance = null;
    super.onDestroy();
  }

  private void startFg() {
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Dynamic Island", NotificationManager.IMPORTANCE_MIN));
    Notification n = new Notification.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Dynamic Island aktif")
        .build();
    if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    else startForeground(1, n);
  }

  /* ---------- gaya & ukuran ---------- */

  private void applyStyle(SharedPreferences p) {
    int base = p.getInt("bg", 0xFF000000);
    int a = Math.max(40, Math.min(100, p.getInt("op", 100))) * 255 / 100;
    int r = Color.red(base), g = Color.green(base), b = Color.blue(base);
    bg.setColor(Color.argb(a, r, g, b));
    float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
    if (lum > 0.12f) bg.setStroke(dp(1), Color.argb(90, 255, 255, 255)); else bg.setStroke(0, Color.TRANSPARENT);
    boolean light = lum > 0.6f;
    ring = Color.rgb(r, g, b);
    tc = light ? 0xFF1C1C1E : Color.WHITE;
    sc = light ? 0xFF55555C : 0xFFB0B0B8;
    dc = light ? 0xFF77777E : 0xFF8E8E93;
  }

  private void geometry(SharedPreferences p) {
    int base = camCy >= 0 ? camH + dp(4) : dp(32);
    cH = Math.max(dp(26), base + dp(p.getInt("dh", 0)));
    cW = Math.max(dp(p.getInt("cw", 110)), camW + dp(20));
    eW = Math.min(dp(p.getInt("ew", 340)), screenW - dp(36));
  }

  // sudut besar seperti iOS: satu baris = pill penuh, beberapa baris = sudut ~42dp
  private float radiusFor(int h) { return Math.min(h / 2f, dp(42)); }

  private int statusBarH() {
    int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
    return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
  }

  private int cutoutMode() {
    return Build.VERSION.SDK_INT >= 30
        ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
  }

  /**
   * Posisi window: pill dipusatkan tepat di kamera. Saat melebar, kamera di pojok -> island ke tengah layar.
   * Saat island tertutup (pill saja), window dibuat tidak bisa disentuh supaya tarik-turun panel notifikasi tetap lancar.
   */
  private void applyPos(boolean exp) {
    int cy = camCy >= 0 ? camCy : statusBarH() / 2;
    wlp.y = Math.max(0, cy - cH / 2 + offY);
    wlp.x = (exp && Math.abs(camX) > dp(40)) ? offX : camX + offX;
    wlp.flags = BASE_FLAGS | (exp ? 0 : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
  }

  private void place(boolean exp) {
    if (root == null || wlp == null) return;
    applyPos(exp);
    try { wm.updateViewLayout(root, wlp); } catch (Exception ignored) {}
  }

  /* ---------- membangun island ---------- */

  private void build() {
    SharedPreferences p = getSharedPreferences("island", MODE_PRIVATE);
    screenW = getResources().getDisplayMetrics().widthPixels;
    offX = dp(p.getInt("cx", 0));
    offY = dp(p.getInt("dy", 0));
    applyStyle(p);
    geometry(p);
    expanded = false; openingFx = false; springing = false;
    curW = cW; curH = cH; vw = 0; vh = 0;
    bg.setCornerRadius(radiusFor(cH));

    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(18), dp(10), dp(18), dp(10));
    content.setAlpha(0f);
    content.setPivotX(eW / 2f);
    content.setPivotY(0f);
    content.setLayoutParams(new FrameLayout.LayoutParams(eW, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

    island = new FrameLayout(this);
    island.setBackground(bg);
    island.setClipToOutline(true);
    island.setOutlineProvider(new ViewOutlineProvider() {
      @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radiusFor(v.getHeight())); }
    });
    island.setLayoutParams(new FrameLayout.LayoutParams(cW, cH, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
    island.setPivotX(cW / 2f);
    island.setPivotY(0f);
    island.addView(content);
    island.setOnTouchListener(new View.OnTouchListener() {
      private float downY;
      @Override public boolean onTouch(View v, MotionEvent ev) {
        switch (ev.getActionMasked()) {
          case MotionEvent.ACTION_DOWN:
            downY = ev.getY();
            v.animate().cancel();
            v.animate().scaleX(0.965f).scaleY(0.965f).setDuration(120).setInterpolator(new DecelerateInterpolator()).start();
            return true;
          case MotionEvent.ACTION_UP:
            release(v);
            if (downY - ev.getY() > dp(18)) collapse();                       // geser ke atas = tutup
            else if (ev.getX() >= 0 && ev.getX() <= v.getWidth() && ev.getY() >= 0 && ev.getY() <= v.getHeight()) tapAt(ev.getY());
            return true;
          case MotionEvent.ACTION_CANCEL:
            release(v);
            return true;
          default:
            return true;
        }
      }
    });

    // ruang ekstra di kiri/kanan/bawah supaya efek membesar (denyut) tidak terpotong window
    root = new FrameLayout(this);
    root.setClipChildren(false);
    root.setClipToPadding(false);
    root.setPadding(dp(10), 0, dp(10), dp(10));
    root.addView(island);

    wlp = new WindowManager.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, BASE_FLAGS, PixelFormat.TRANSLUCENT);
    wlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
    if (Build.VERSION.SDK_INT >= 28) wlp.layoutInDisplayCutoutMode = cutoutMode();
    applyPos(false);
    wm.addView(root, wlp);

    if (!camTried) { camTried = true; detectCamera(); }
  }

  private void release(View v) {
    v.animate().cancel();
    v.animate().scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(new OvershootInterpolator(2.2f)).start();
  }

  private void destroyView() {
    main.removeCallbacks(collapseRun);
    springing = false;
    try { Choreographer.getInstance().removeFrameCallback(frame); } catch (Exception ignored) {}
    if (root != null) { try { wm.removeView(root); } catch (Exception ignored) {} }
    root = null; island = null; content = null;
  }

  /* ---------- deteksi posisi kamera asli (lubang kamera / notch) ---------- */

  private void detectCamera() {
    if (Build.VERSION.SDK_INT < 28) return;
    try {
      final View probe = new View(this);
      WindowManager.LayoutParams pl = new WindowManager.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
              | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          PixelFormat.TRANSLUCENT);
      pl.layoutInDisplayCutoutMode = cutoutMode();
      probe.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
        @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
          readCutout(ins.getDisplayCutout());
          main.post(new Runnable() { public void run() { try { wm.removeView(probe); } catch (Exception ignored) {} } });
          return ins;
        }
      });
      wm.addView(probe, pl);
    } catch (Exception ignored) {}
  }

  private void readCutout(DisplayCutout dcut) {
    if (dcut == null) return;
    int sh = getResources().getDisplayMetrics().heightPixels;
    Rect best = null;
    for (Rect r : dcut.getBoundingRects()) {
      if (r.top > sh / 5) continue;
      if (r.width() > screenW * 2 / 3) continue;
      if (best == null || r.width() < best.width()) best = r;
    }
    if (best == null) return;
    camX = best.centerX() - screenW / 2;
    camCy = best.centerY();
    camW = best.width();
    camH = best.height();
    camFound = true;
    main.post(new Runnable() { public void run() { onCamera(); } });
  }

  private void onCamera() {
    if (island == null) return;
    geometry(getSharedPreferences("island", MODE_PRIVATE));
    ViewGroup.LayoutParams clp = content.getLayoutParams();
    clp.width = eW;
    content.setLayoutParams(clp);
    content.setPivotX(eW / 2f);
    if (!expanded && !springing) { curW = cW; curH = cH; applySize(cW, cH); }
    place(expanded);
  }

  /* ---------- animasi pegas ---------- */

  private void applySize(float w, float h) {
    if (island == null) return;
    ViewGroup.LayoutParams lp = island.getLayoutParams();
    lp.width = Math.max(1, (int) w);
    lp.height = Math.max(1, (int) h);
    island.setLayoutParams(lp);
    island.setPivotX(lp.width / 2f);
    bg.setCornerRadius(radiusFor(lp.height));
    island.invalidateOutline();
  }

  /** Pegas sungguhan (bukan kurva tetap): membuka agak membal, menutup lebih kencang dan tenang. */
  private void springTo(float w, float h, boolean opening) {
    tw = w; th = h;
    if (opening) { kw = 230f; dw = 16f; kh = 270f; dh = 18f; }
    else         { kw = 420f; dw = 29f; kh = 420f; dh = 29f; }
    if (!springing) {
      springing = true;
      lastNs = 0;
      Choreographer.getInstance().postFrameCallback(frame);
    }
  }

  private void step(long ns) {
    if (!springing || island == null) { springing = false; return; }
    float dt = lastNs == 0 ? 1f / 60f : Math.min((ns - lastNs) / 1e9f, 1f / 30f);
    lastNs = ns;
    for (int i = 0; i < 4; i++) {
      float d = dt / 4f;
      vw += (-kw * (curW - tw) - dw * vw) * d; curW += vw * d;
      vh += (-kh * (curH - th) - dh * vh) * d; curH += vh * d;
    }
    boolean done = Math.abs(curW - tw) < 0.4f && Math.abs(curH - th) < 0.4f && Math.abs(vw) < 3f && Math.abs(vh) < 3f;
    if (done) {
      curW = tw; curH = th; vw = 0; vh = 0; springing = false;
      if (openingFx) { openingFx = false; content.setAlpha(1f); content.setScaleX(1f); content.setScaleY(1f); }
    }
    applySize(curW, curH);
    contentProgress();
    if (springing) Choreographer.getInstance().postFrameCallback(frame);
  }

  /** Isi muncul pelan-pelan mengikuti seberapa lebar island sudah terbuka (dan sedikit membesar dari 90%). */
  private void contentProgress() {
    if (!openingFx || content == null) return;
    float span = th - cH;
    float p = span <= 0 ? 1f : (curH - cH) / span;
    float t = Math.max(0f, Math.min(1f, (p - 0.30f) / 0.45f));
    float a = t * t * (3f - 2f * t);
    content.setAlpha(a);
    float s = 0.9f + 0.1f * a;
    content.setScaleX(s);
    content.setScaleY(s);
  }

  /* ---------- isi notifikasi ---------- */

  private View makeRow(final Entry e, boolean single) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(8), 0, dp(8));
    row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    // avatar (foto profil -> bulat + lencana ikon aplikasi di pojok, seperti iOS)
    FrameLayout box = new FrameLayout(this);
    box.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
    final boolean circle = e.badge != null;
    ImageView av = new ImageView(this);
    av.setLayoutParams(new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.START));
    av.setScaleType(ImageView.ScaleType.CENTER_CROP);
    av.setClipToOutline(true);
    av.setOutlineProvider(new ViewOutlineProvider() {
      @Override public void getOutline(View v, Outline o) {
        if (circle) o.setOval(0, 0, v.getWidth(), v.getHeight());
        else if (v.getWidth() > 0) o.setConvexPath(squircle(v.getWidth(), v.getHeight()));
      }
    });
    av.setImageDrawable(circle ? e.avatar : flat(e.avatar, 160));
    box.addView(av);
    if (e.badge != null) {
      ImageView bd = new ImageView(this);
      bd.setLayoutParams(new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.BOTTOM | Gravity.END));
      bd.setScaleType(ImageView.ScaleType.CENTER_CROP);
      bd.setPadding(dp(2), dp(2), dp(2), dp(2));
      GradientDrawable rg = new GradientDrawable();
      rg.setColor(ring);
      rg.setCornerRadius(0);
      bd.setBackground(rg);
      bd.setClipToOutline(true);
      bd.setOutlineProvider(new ViewOutlineProvider() {
        @Override public void getOutline(View v, Outline o) { if (v.getWidth() > 0) o.setConvexPath(squircle(v.getWidth(), v.getHeight())); }
      });
      bd.setImageDrawable(flat(e.badge, 96));
      box.addView(bd);
    }
    if (e.avatar == null) box.setVisibility(View.GONE);
    row.addView(box);

    // teks
    LinearLayout col = new LinearLayout(this);
    col.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    cl.setMarginStart(e.avatar == null ? 0 : dp(12));
    col.setLayoutParams(cl);

    LinearLayout line = new LinearLayout(this);
    line.setOrientation(LinearLayout.HORIZONTAL);
    line.setGravity(Gravity.CENTER_VERTICAL);
    TextView tv = new TextView(this);
    tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    tv.setTextColor(tc);
    tv.setMaxLines(1);
    tv.setEllipsize(TextUtils.TruncateAt.END);
    tv.setText(e.title);
    TextView tm = new TextView(this);
    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    tl.setMarginStart(dp(8));
    tm.setLayoutParams(tl);
    tm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
    tm.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    tm.setTextColor(dc);
    tm.setText("sekarang");
    line.addView(tv);
    line.addView(tm);

    TextView xv = new TextView(this);
    xv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    xv.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    xv.setTextColor(sc);
    xv.setMaxLines(single ? 2 : 1);
    xv.setEllipsize(TextUtils.TruncateAt.END);
    xv.setText(e.text);
    if (e.text == null || e.text.length() == 0) xv.setVisibility(View.GONE);

    col.addView(line);
    col.addView(xv);
    row.addView(col);
    return row;
  }

  /** Semua notifikasi ada di SATU island yang sama: teks baru muncul di bawah teks sebelumnya. */
  private void rebuildRows() {
    content.removeAllViews();
    boolean single = entries.size() == 1;
    for (Entry e : entries) content.addView(makeRow(e, single));
  }

  private int contentHeight() {
    content.measure(View.MeasureSpec.makeMeasureSpec(eW, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    return Math.max(dp(64), content.getMeasuredHeight());
  }

  /** Baris masuk: naik sedikit dari bawah, avatarnya "pop" membal. */
  private void rowIn(View row, long delay, boolean fade) {
    row.setTranslationY(dp(10));
    if (fade) row.setAlpha(0f);
    row.animate().translationY(0f).alpha(1f).setStartDelay(delay).setDuration(340)
        .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    View av = ((ViewGroup) row).getChildAt(0);
    av.setScaleX(0.6f);
    av.setScaleY(0.6f);
    av.animate().scaleX(1f).scaleY(1f).setStartDelay(delay + 40).setDuration(420)
        .setInterpolator(new OvershootInterpolator(1.8f)).start();
  }

  /** Denyut kecil saat ada notifikasi baru masuk ke island yang sudah terbuka. */
  private void pulse() {
    island.animate().cancel();
    island.animate().scaleX(1.035f).scaleY(1.035f).setDuration(110).setInterpolator(new DecelerateInterpolator())
        .withEndAction(new Runnable() { public void run() {
          if (island != null) island.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(2.5f)).start();
        } }).start();
  }

  public void show(final String title, final String text, final Drawable avatar, final Drawable badge, final PendingIntent pi) {
    main.post(new Runnable() { public void run() {
      if (root == null) return;
      Entry e = new Entry();
      e.title = title; e.text = text; e.avatar = avatar; e.badge = badge; e.pi = pi;
      if (!expanded) entries.clear();
      entries.add(e);
      while (entries.size() > MAX_ROWS) entries.remove(0);
      playSound();
      if (!expanded) { expandFresh(); return; }
      if (openingFx) { openingFx = false; content.setAlpha(1f); content.setScaleX(1f); content.setScaleY(1f); }
      rebuildRows();
      springTo(eW, contentHeight(), true);
      rowIn(content.getChildAt(content.getChildCount() - 1), 60, true);
      pulse();
      main.removeCallbacks(collapseRun);
      main.postDelayed(collapseRun, showMs());
    } });
  }

  private void expandFresh() {
    rebuildRows();
    int h = contentHeight();
    expanded = true;
    openingFx = true;
    place(true);
    content.animate().cancel();
    content.setPivotX(eW / 2f);
    content.setAlpha(0f);
    content.setScaleX(0.9f);
    content.setScaleY(0.9f);
    for (int i = 0; i < content.getChildCount(); i++) rowIn(content.getChildAt(i), 120 + i * 60, false);
    springTo(eW, h, true);
    main.removeCallbacks(collapseRun);
    main.postDelayed(collapseRun, showMs());
  }

  private void collapse() {
    if (island == null) return;
    expanded = false;
    openingFx = false;
    main.removeCallbacks(collapseRun);
    content.animate().cancel();
    content.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setStartDelay(0).setDuration(120).start();
    springTo(cW, cH, false);
    place(false);
  }

  private void tapAt(float y) {
    if (!expanded) return;
    float yy = y - content.getTop() - content.getTranslationY();
    PendingIntent pi = null;
    for (int i = 0; i < content.getChildCount() && i < entries.size(); i++) {
      View r = content.getChildAt(i);
      if (yy >= r.getTop() && yy < r.getBottom()) { pi = entries.get(i).pi; break; }
    }
    // saklar "ketuk membuka aplikasi": kalau mati, ketukan hanya menutup island
    if (!getSharedPreferences("island", MODE_PRIVATE).getBoolean("tapopen", true)) pi = null;
    if (pi != null) { try { pi.send(); } catch (Exception ignored) {} }
    collapse();
  }

  private Ringtone tone;

  private void playSound() {
    try {
      SharedPreferences p = getSharedPreferences("island", MODE_PRIVATE);
      if (!p.getBoolean("snd", true)) return;
      AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
      if (am != null && am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return;
      Uri def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
      String saved = p.getString("suri", "");
      Ringtone r = null;
      if (saved != null && saved.length() > 0) {
        try { r = RingtoneManager.getRingtone(this, Uri.parse(saved)); } catch (Exception ignored) {}
      }
      if (r == null) r = RingtoneManager.getRingtone(this, def);   // nada pilihan hilang/rusak -> pakai bawaan HP
      if (r == null) return;
      if (tone != null) { try { tone.stop(); } catch (Exception ignored) {} }
      if (Build.VERSION.SDK_INT >= 28) r.setVolume(Math.max(0, Math.min(100, p.getInt("svol", 100))) / 100f);
      tone = r;
      r.play();
    } catch (Exception ignored) {}
  }
}
