package __PKG__;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
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
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

public class IslandService extends Service {
  public static volatile IslandService instance;
  public static final String ACTION_RELOAD = "island.RELOAD";
  private static final String CHANNEL = "island_service";
  private static final long SHOW_MS = 4500;

  private final Handler main = new Handler(Looper.getMainLooper());
  private WindowManager wm;
  private LinearLayout root;
  private FrameLayout island;
  private static final int MAX_EXTRA = 2;           // notifikasi tambahan yang ditumpuk di bawah
  private final ArrayList<View> extras = new ArrayList<View>();
  private int colTop, colBottom, tc, sc;
  private LinearLayout content;
  private ImageView iconView;
  private TextView titleView, textView;
  private final GradientDrawable bg = new GradientDrawable();
  private float curW, curH;
  private int cW, cH, eW, eH;
  private boolean expanded, hasContent;
  private PendingIntent curIntent;
  private ValueAnimator anim;
  private final Runnable collapseRun = new Runnable() { public void run() { collapse(); } };

  private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

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

  private void applyStyle(SharedPreferences p) {
    int base = p.getInt("bg", 0xFFCDB4FF);
    int a = Math.max(40, Math.min(100, p.getInt("op", 82))) * 255 / 100;
    int r = Color.red(base), g = Color.green(base), b = Color.blue(base);
    int top = Color.argb(a, r + (255 - r) * 3 / 10, g + (255 - g) * 3 / 10, b + (255 - b) * 3 / 10);
    int bottom = Color.argb(a, r, g, b);
    bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
    bg.setColors(new int[] { top, bottom });
    bg.setStroke(dp(1), Color.argb(110, 255, 255, 255));
    boolean light = (0.299 * r + 0.587 * g + 0.114 * b) / 255 > 0.6;
    colTop = top; colBottom = bottom;
    tc = light ? 0xFF1C1C1E : Color.WHITE;
    sc = light ? 0xFF55555C : 0xFFD0D0D6;
    titleView.setTextColor(tc);
    textView.setTextColor(sc);
  }

  private void build() {
    SharedPreferences p = getSharedPreferences("island", MODE_PRIVATE);
    cW = dp(p.getInt("cw", 110)); cH = dp(32);
    eW = dp(p.getInt("ew", 330)); eH = dp(76);
    expanded = false; hasContent = false;
    curW = cW; curH = cH;
    bg.setCornerRadius(cH / 2f);

    iconView = new ImageView(this);
    iconView.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
    iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
    iconView.setClipToOutline(true);
    iconView.setOutlineProvider(new ViewOutlineProvider() {
      @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(11)); }
    });
    titleView = new TextView(this);
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    titleView.setTypeface(Typeface.DEFAULT_BOLD);
    titleView.setMaxLines(1);
    titleView.setEllipsize(TextUtils.TruncateAt.END);
    textView = new TextView(this);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
    textView.setMaxLines(1);
    textView.setEllipsize(TextUtils.TruncateAt.END);

    LinearLayout col = new LinearLayout(this);
    col.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    cl.setMarginStart(dp(12));
    col.setLayoutParams(cl);
    col.addView(titleView);
    col.addView(textView);

    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.HORIZONTAL);
    content.setGravity(Gravity.CENTER_VERTICAL);
    content.setPadding(dp(18), 0, dp(18), 0);
    content.setAlpha(0f);
    content.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    content.addView(iconView);
    content.addView(col);
    applyStyle(p);

    island = new FrameLayout(this);
    island.setBackground(bg);
    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(cW, cH);
    ilp.gravity = Gravity.CENTER_HORIZONTAL;
    island.setLayoutParams(ilp);
    island.addView(content);
    island.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onTap(); } });
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER_HORIZONTAL);
    extras.clear();
    root.addView(island);

    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT);
    lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
    lp.y = dp(p.getInt("top", 8));
    wm.addView(root, lp);
  }

  private void destroyView() {
    main.removeCallbacks(collapseRun);
    if (anim != null) anim.cancel();
    if (root != null) { try { wm.removeView(root); } catch (Exception ignored) {} }
    extras.clear();
    root = null; island = null;
  }

  private void applySize(float w, float h) {
    if (island == null) return;
    curW = w; curH = h;
    ViewGroup.LayoutParams lp = island.getLayoutParams();
    lp.width = Math.max(1, (int) w);
    lp.height = Math.max(1, (int) h);
    island.setLayoutParams(lp);
    bg.setCornerRadius(Math.min(lp.height / 2f, dp(26)));
  }

  /** Animasi pegas: melebihi target sedikit lalu memantul balik. */
  private void animateTo(final float tw, final float th) {
    if (anim != null) anim.cancel();
    final float sw = curW, sh = curH;
    anim = ValueAnimator.ofFloat(0f, 1f);
    anim.setDuration(650);
    anim.setInterpolator(new TimeInterpolator() {
      @Override public float getInterpolation(float t) {
        if (t >= 1f) return 1f;
        return (float) (1 - Math.exp(-6.5 * t) * Math.cos(t * Math.PI * 3.0));
      }
    });
    anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override public void onAnimationUpdate(ValueAnimator a) {
        float f = (Float) a.getAnimatedValue();
        applySize(sw + (tw - sw) * f, sh + (th - sh) * f);
      }
    });
    anim.start();
  }

  public void show(final String title, final String text, final Drawable icon, final PendingIntent pi) {
    main.post(new Runnable() { public void run() {
      if (root == null) return;
      if (expanded && hasContent) {
        addExtra(title, text, icon, pi);
        main.removeCallbacks(collapseRun);
        main.postDelayed(collapseRun, SHOW_MS);
        return;
      }
      curIntent = pi; hasContent = true;
      titleView.setText(title);
      textView.setText(text);
      iconView.setImageDrawable(icon);
      iconView.setVisibility(icon == null ? View.GONE : View.VISIBLE);
      expand();
      main.removeCallbacks(collapseRun);
      main.postDelayed(collapseRun, SHOW_MS);
    } });
  }

  private void expand() {
    expanded = true;
    animateTo(eW, eH);
    content.animate().cancel();
    content.setTranslationY(-dp(6));
    content.animate().alpha(1f).translationY(0f).setStartDelay(110).setDuration(260)
        .setInterpolator(new DecelerateInterpolator()).start();
  }

  private void collapse() {
    expanded = false;
    main.removeCallbacks(collapseRun);
    clearExtras();
    content.animate().cancel();
    content.animate().alpha(0f).translationY(-dp(4)).setStartDelay(0).setDuration(130).start();
    animateTo(cW, cH);
  }

  private void onTap() {
    if (expanded) {
      if (curIntent != null) { try { curIntent.send(); } catch (Exception ignored) {} }
      collapse();
    } else if (hasContent) {
      expand();
      main.postDelayed(collapseRun, SHOW_MS);
    }
  }

  /** Notifikasi baru saat island sudah terbuka: muncul sebagai kartu di bawah yang sebelumnya. */
  private void addExtra(String title, String text, Drawable icon, final PendingIntent pi) {
    if (extras.size() >= MAX_EXTRA) fadeRemove(extras.remove(0));
    View c = makeCard(title, text, icon, pi);
    extras.add(c);
    root.addView(c);
    c.setAlpha(0f);
    c.setTranslationY(-dp(8));
    c.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
  }

  private View makeCard(String title, String text, Drawable icon, final PendingIntent pi) {
    GradientDrawable g = new GradientDrawable();
    g.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
    g.setColors(new int[] { colTop, colBottom });
    g.setStroke(dp(1), Color.argb(110, 255, 255, 255));
    g.setCornerRadius(dp(26));

    ImageView iv = new ImageView(this);
    iv.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
    iv.setClipToOutline(true);
    iv.setOutlineProvider(new ViewOutlineProvider() {
      @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(11)); }
    });
    iv.setImageDrawable(icon);
    iv.setVisibility(icon == null ? View.GONE : View.VISIBLE);

    TextView tv = new TextView(this);
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    tv.setTypeface(Typeface.DEFAULT_BOLD);
    tv.setMaxLines(1);
    tv.setEllipsize(TextUtils.TruncateAt.END);
    tv.setTextColor(tc);
    tv.setText(title);
    TextView xv = new TextView(this);
    xv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
    xv.setMaxLines(1);
    xv.setEllipsize(TextUtils.TruncateAt.END);
    xv.setTextColor(sc);
    xv.setText(text);

    LinearLayout col = new LinearLayout(this);
    col.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    cl.setMarginStart(dp(12));
    col.setLayoutParams(cl);
    col.addView(tv);
    col.addView(xv);

    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(18), 0, dp(18), 0);
    row.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    row.addView(iv);
    row.addView(col);

    FrameLayout card = new FrameLayout(this);
    card.setBackground(g);
    card.addView(row);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(eW, eH);
    lp.topMargin = dp(6);
    lp.gravity = Gravity.CENTER_HORIZONTAL;
    card.setLayoutParams(lp);
    card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
      if (pi != null) { try { pi.send(); } catch (Exception ignored) {} }
      collapse();
    } });
    return card;
  }

  private void fadeRemove(final View v) {
    v.animate().cancel();
    v.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() { public void run() {
      if (root != null) { try { root.removeView(v); } catch (Exception ignored) {} }
    } }).start();
  }

  private void clearExtras() {
    for (View v : new ArrayList<View>(extras)) fadeRemove(v);
    extras.clear();
  }
}
