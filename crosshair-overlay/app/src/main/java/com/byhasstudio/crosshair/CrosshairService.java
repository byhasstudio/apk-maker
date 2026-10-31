package com.byhasstudio.crosshair;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * Menampilkan crosshair di jendela overlay (TYPE_APPLICATION_OVERLAY) di atas semua aplikasi.
 * Berjalan sebagai foreground service dan START_STICKY, jadi tetap tampil permanen sampai
 * dimatikan lewat saklar di aplikasi atau tombol "Matikan" di notifikasi. Jendelanya tidak bisa
 * disentuh (sentuhan tembus ke aplikasi di bawahnya), kecuali saat mode geser posisi.
 */
public class CrosshairService extends Service {
  public static final String PREFS = "crosshair";
  public static final String ACTION_RELOAD = "crosshair.RELOAD";
  public static final String ACTION_EDIT = "crosshair.EDIT";
  public static final String ACTION_STOP = "crosshair.STOP";
  public static final String EXTRA_ON = "on";
  public static volatile boolean running = false;
  public static volatile boolean editing = false;

  private static final String CH_SERVICE = "crosshair_service";
  private static final String CH_UPDATE = "crosshair_update";
  private static final int ID_SERVICE = 1;
  private static final int ID_UPDATE = 2;
  private static final long CHECK_EVERY_MS = 6L * 60L * 60L * 1000L;
  private static final long LOCK_AFTER_DRAG_MS = 4000L;
  private static final long LOCK_IDLE_MS = 20000L;
  private static final int BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
      | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
      | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
      | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

  private final Handler main = new Handler(Looper.getMainLooper());
  private WindowManager wm;
  private WindowManager.LayoutParams lp;
  private CrosshairView view;
  private int screenW, screenH, box;
  private boolean failed = false;
  private float downX, downY;
  private int startX, startY;

  private final Runnable lockRun = new Runnable() {
    @Override public void run() { setEdit(false); }
  };

  private final Runnable updateRun = new Runnable() {
    @Override public void run() {
      if (prefs().getBoolean("autoupd", true)) {
        new Thread(new Runnable() {
          @Override public void run() {
            try {
              final UpdateChecker.Info info = UpdateChecker.check(CrosshairService.this);
              if (info != null) {
                main.post(new Runnable() {
                  @Override public void run() { notifyUpdate(info); }
                });
              }
            } catch (Exception ignored) {
            }
          }
        }).start();
      }
      main.postDelayed(this, CHECK_EVERY_MS);
    }
  };

  private final View.OnTouchListener drag = new View.OnTouchListener() {
    @Override public boolean onTouch(View v, MotionEvent ev) {
      if (!editing) return false;
      switch (ev.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          main.removeCallbacks(lockRun);
          downX = ev.getRawX();
          downY = ev.getRawY();
          startX = lp.x;
          startY = lp.y;
          return true;
        case MotionEvent.ACTION_MOVE:
          lp.x = clampI(startX + Math.round(ev.getRawX() - downX), -box / 2, screenW - box / 2);
          lp.y = clampI(startY + Math.round(ev.getRawY() - downY), -box / 2, screenH - box / 2);
          try { wm.updateViewLayout(view, lp); } catch (Exception ignored) {}
          return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          savePos();
          main.postDelayed(lockRun, LOCK_AFTER_DRAG_MS);
          return true;
        default:
          return true;
      }
    }
  };

  private SharedPreferences prefs() {
    return getSharedPreferences(PREFS, MODE_PRIVATE);
  }

  private static int clampI(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  @Override public IBinder onBind(Intent i) { return null; }

  @Override public void onCreate() {
    super.onCreate();
    wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    try {
      startFg();
    } catch (Exception e) {
      failed = true;
      stopSelf();
      return;
    }
    if (!Settings.canDrawOverlays(this)) {
      failed = true;
      stopSelf();
      return;
    }
    try {
      build();
    } catch (Exception e) {
      failed = true;
      stopSelf();
      return;
    }
    running = true;
    editing = false;
    main.postDelayed(updateRun, 60000L);
  }

  @Override public int onStartCommand(Intent i, int flags, int startId) {
    if (failed || view == null) return START_NOT_STICKY;
    String a = i == null ? null : i.getAction();
    if (ACTION_STOP.equals(a)) {
      prefs().edit().putBoolean("enabled", false).apply();
      stopSelf();
      return START_NOT_STICKY;
    }
    if (ACTION_RELOAD.equals(a)) {
      applyAll();
    } else if (ACTION_EDIT.equals(a)) {
      setEdit(i.hasExtra(EXTRA_ON) ? i.getBooleanExtra(EXTRA_ON, false) : !editing);
    }
    return START_STICKY;
  }

  @Override public void onDestroy() {
    running = false;
    editing = false;
    main.removeCallbacksAndMessages(null);
    if (view != null) {
      try { wm.removeView(view); } catch (Exception ignored) {}
      view = null;
    }
    super.onDestroy();
  }

  @Override public void onConfigurationChanged(Configuration c) {
    super.onConfigurationChanged(c);
    // layar diputar: hitung ulang posisi setelah sistem selesai menyesuaikan ukuran layar
    main.postDelayed(new Runnable() {
      @Override public void run() { applyAll(); }
    }, 250L);
  }

  /* ---------- notifikasi ---------- */

  private void startFg() {
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    nm.createNotificationChannel(new NotificationChannel(CH_SERVICE, "Crosshair aktif", NotificationManager.IMPORTANCE_MIN));
    nm.createNotificationChannel(new NotificationChannel(CH_UPDATE, "Pembaruan aplikasi", NotificationManager.IMPORTANCE_DEFAULT));
    Notification n = buildNotification();
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(ID_SERVICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    } else {
      startForeground(ID_SERVICE, n);
    }
  }

  private PendingIntent svc(int code, String action) {
    return PendingIntent.getService(this, code, new Intent(this, CrosshairService.class).setAction(action),
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private Notification buildNotification() {
    PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    return new Notification.Builder(this, CH_SERVICE)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle("Crosshair aktif")
        .setContentText(editing ? "Seret crosshair ke posisi yang kamu mau" : "Tampil di atas aplikasi lain")
        .setContentIntent(open)
        .setOngoing(true)
        .addAction(0, editing ? "Kunci posisi" : "Atur posisi", svc(1, ACTION_EDIT))
        .addAction(0, "Matikan", svc(2, ACTION_STOP))
        .build();
  }

  private void notifyUpdate(UpdateChecker.Info info) {
    SharedPreferences p = prefs();
    if (!p.getBoolean("autoupd", true)) return;
    if (info.version.equals(p.getString("notified", ""))) return;
    p.edit().putString("notified", info.version).apply();
    Intent in = new Intent(this, MainActivity.class)
        .putExtra(MainActivity.EXTRA_UPDATE, true)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent pi = PendingIntent.getActivity(this, 3, in, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification n = new Notification.Builder(this, CH_UPDATE)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle("Pembaruan tersedia: v" + info.version)
        .setContentText("Ketuk untuk memperbarui Crosshair")
        .setContentIntent(pi)
        .setAutoCancel(true)
        .build();
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    nm.notify(ID_UPDATE, n);
  }

  /* ---------- jendela overlay ---------- */

  private void build() {
    SharedPreferences p = prefs();
    box = CrosshairView.boxPx(p, getResources().getDisplayMetrics().density);
    view = new CrosshairView(this);
    view.apply(p);
    view.setOnTouchListener(drag);
    lp = new WindowManager.LayoutParams(
        box, box, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        BASE_FLAGS | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
    lp.gravity = Gravity.TOP | Gravity.LEFT;
    lp.setTitle("Crosshair");
    if (Build.VERSION.SDK_INT >= 30) {
      lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
    } else if (Build.VERSION.SDK_INT >= 28) {
      lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    }
    place(p);
    wm.addView(view, lp);
  }

  /** Terapkan ulang semua pengaturan (ukuran, gaya, posisi, mode geser) ke jendela yang sudah ada. */
  private void applyAll() {
    if (view == null || lp == null) return;
    SharedPreferences p = prefs();
    box = CrosshairView.boxPx(p, getResources().getDisplayMetrics().density);
    view.apply(p);
    view.setEdit(editing);
    lp.width = box;
    lp.height = box;
    lp.flags = BASE_FLAGS | (editing ? 0 : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    place(p);
    try { wm.updateViewLayout(view, lp); } catch (Exception ignored) {}
  }

  /** Posisi disimpan sebagai pecahan layar dari titik tengah, jadi tetap pas saat layar diputar. */
  private void place(SharedPreferences p) {
    DisplayMetrics dm = new DisplayMetrics();
    wm.getDefaultDisplay().getRealMetrics(dm);
    screenW = dm.widthPixels;
    screenH = dm.heightPixels;
    float fx = Math.max(-0.5f, Math.min(0.5f, p.getFloat("fx", 0f)));
    float fy = Math.max(-0.5f, Math.min(0.5f, p.getFloat("fy", 0f)));
    lp.x = Math.round(screenW / 2f + fx * screenW - box / 2f);
    lp.y = Math.round(screenH / 2f + fy * screenH - box / 2f);
  }

  private void savePos() {
    if (screenW <= 0 || screenH <= 0) return;
    float fx = (lp.x + box / 2f) / screenW - 0.5f;
    float fy = (lp.y + box / 2f) / screenH - 0.5f;
    prefs().edit().putFloat("fx", fx).putFloat("fy", fy).apply();
  }

  private void setEdit(boolean on) {
    if (view == null) return;
    editing = on;
    main.removeCallbacks(lockRun);
    if (on) {
      main.postDelayed(lockRun, LOCK_IDLE_MS);
      Toast.makeText(this, "Seret crosshair ke posisi yang kamu mau", Toast.LENGTH_SHORT).show();
    }
    applyAll();
    try {
      NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      nm.notify(ID_SERVICE, buildNotification());
    } catch (Exception ignored) {}
  }
}
