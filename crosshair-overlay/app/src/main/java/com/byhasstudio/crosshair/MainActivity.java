package com.byhasstudio.crosshair;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.Locale;

/** Layar pengaturan: saklar on/off, posisi, tampilan, dan pembaruan otomatis. */
public class MainActivity extends Activity {
  public static final String EXTRA_UPDATE = "update";

  private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
  private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
  private static final int BG = 0xFF0F1218;
  private static final int CARD = 0xFF1A1F2B;
  private static final int TXT = 0xFFE8EBF2;
  private static final int MUT = 0xFF9AA3B5;
  private static final int ACC = 0xFF0A84FF;
  private static final int[] PRESETS = {
      0xFFFF2D2D, 0xFF00E676, 0xFFFFEB3B, 0xFF00E5FF, 0xFFFFFFFF, 0xFFFF40FF, 0xFFFF9100, 0xFF2979FF
  };

  private interface Fmt { String f(int v); }
  private interface Val { void on(int v); }

  private final Handler ui = new Handler(Looper.getMainLooper());
  private SharedPreferences p;
  private CrosshairView preview;
  private Switch swOn;
  private TextView tvStatus;
  private Button btnPerm;
  private SeekBar sbX, sbY;
  private LinearLayout colorRow;
  private GradientDrawable[] chips;
  private EditText etHex;
  private boolean building = true;
  private boolean checking = false;
  private File pendingApk;
  private AlertDialog dlProgress;
  private AlertDialog updateDialog;

  private final Fmt posFmt = v -> v == 500 ? "tengah" : String.format(Locale.US, "%+.1f%%", (v - 500) / 10f);
  private final Runnable statusRun = this::refreshStatus;
  private final Runnable reloadRun = new Runnable() {
    @Override public void run() {
      if (CrosshairService.running) {
        startService(new Intent(MainActivity.this, CrosshairService.class).setAction(CrosshairService.ACTION_RELOAD));
      }
    }
  };

  /* ---------- siklus hidup ---------- */

  @Override protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    p = getSharedPreferences(CrosshairService.PREFS, MODE_PRIVATE);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    buildUi();
    askNotifOnce();
  }

  @Override protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    if (i != null && i.getBooleanExtra(EXTRA_UPDATE, false)) checkUpdate(true);
  }

  @Override protected void onResume() {
    super.onResume();
    boolean ov = canOverlay();
    boolean en = p.getBoolean("enabled", false);
    if (en && ov && !CrosshairService.running) startSvc();
    building = true;
    swOn.setChecked(en && ov);
    sbX.setProgress(clamp(Math.round(p.getFloat("fx", 0f) * 1000f) + 500, 0, 1000));
    sbY.setProgress(clamp(Math.round(p.getFloat("fy", 0f) * 1000f) + 500, 0, 1000));
    building = false;
    refreshStatus();
    ui.postDelayed(statusRun, 600);
    boolean hadPending = pendingApk != null;
    if (pendingApk != null && canInstall()) {
      File f = pendingApk;
      pendingApk = null;
      installApk(f);
    }
    // Cek pembaruan setiap aplikasi dibuka (onResume jalan tiap layar ini tampil, bukan cuma saat pertama dibuat)
    if (!hadPending && p.getBoolean("autoupd", true)) checkUpdate(false);
  }

  @Override protected void onDestroy() {
    ui.removeCallbacksAndMessages(null);
    if (dlProgress != null && dlProgress.isShowing()) dlProgress.dismiss();
    if (updateDialog != null && updateDialog.isShowing()) updateDialog.dismiss();
    super.onDestroy();
  }

  /* ---------- helper tampilan ---------- */

  private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

  private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

  private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

  private LinearLayout.LayoutParams top(int dpTop) {
    LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(MATCH, WRAP);
    l.topMargin = dp(dpTop);
    return l;
  }

  private TextView text(String s, int sp, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    t.setTextColor(color);
    return t;
  }

  private Button btn(String label, View.OnClickListener l) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextColor(Color.WHITE);
    GradientDrawable g = new GradientDrawable();
    g.setColor(ACC);
    g.setCornerRadius(dp(12));
    b.setBackground(g);
    b.setOnClickListener(l);
    return b;
  }

  private LinearLayout card(LinearLayout root, String title) {
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable g = new GradientDrawable();
    g.setColor(CARD);
    g.setCornerRadius(dp(18));
    c.setBackground(g);
    c.setPadding(dp(16), dp(14), dp(16), dp(16));
    root.addView(c, top(12));
    if (title != null) {
      TextView t = text(title, 16, TXT);
      t.setTypeface(Typeface.DEFAULT_BOLD);
      c.addView(t);
    }
    return c;
  }

  /** Baris slider: judul dan nilai di atas, slider di bawah. */
  private SeekBar seek(LinearLayout parent, String title, final int min, int max, int cur, final Fmt fmt, final Val cb) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    TextView t = text(title, 14, TXT);
    row.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
    final TextView val = text(fmt.f(cur), 14, MUT);
    row.addView(val);
    parent.addView(row, top(12));
    SeekBar sb = new SeekBar(this);
    sb.setMax(max - min);
    sb.setProgress(cur - min);
    sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar s, int prog, boolean fromUser) {
        val.setText(fmt.f(prog + min));
        if (fromUser && !building) cb.on(prog + min);
      }
      @Override public void onStartTrackingTouch(SeekBar s) {}
      @Override public void onStopTrackingTouch(SeekBar s) {}
    });
    parent.addView(sb, new LinearLayout.LayoutParams(MATCH, WRAP));
    return sb;
  }

  private CheckBox check(LinearLayout parent, String label, boolean on, final String key) {
    CheckBox cb = new CheckBox(this);
    cb.setText(label);
    cb.setTextColor(TXT);
    cb.setChecked(on);
    cb.setOnCheckedChangeListener((view, checked) -> {
      if (building) return;
      p.edit().putBoolean(key, checked).apply();
      refreshPreview();
      reload();
    });
    parent.addView(cb, top(6));
    return cb;
  }

  private void buildUi() {
    building = true;
    ScrollView sv = new ScrollView(this);
    sv.setBackgroundColor(BG);
    sv.setFillViewport(true);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(16), dp(20), dp(16), dp(28));
    sv.addView(root, new ViewGroup.LayoutParams(MATCH, WRAP));
    setContentView(sv);

    TextView h = text("\uD83C\uDFAF Crosshair Overlay", 24, TXT);
    h.setTypeface(Typeface.DEFAULT_BOLD);
    root.addView(h);
    root.addView(text("Crosshair tampil permanen di atas aplikasi lain sampai kamu matikan.", 13, MUT), top(4));

    // pratinjau
    LinearLayout c0 = card(root, null);
    FrameLayout pv = new FrameLayout(this);
    GradientDrawable pg = new GradientDrawable();
    pg.setColor(0xFF2A3040);
    pg.setCornerRadius(dp(14));
    pv.setBackground(pg);
    preview = new CrosshairView(this);
    preview.apply(p);
    pv.addView(preview, new FrameLayout.LayoutParams(dp(216), dp(216), Gravity.CENTER));
    c0.addView(pv, new LinearLayout.LayoutParams(MATCH, dp(160)));

    // saklar utama
    LinearLayout c1 = card(root, null);
    swOn = new Switch(this);
    swOn.setText("Crosshair aktif");
    swOn.setTextColor(TXT);
    swOn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
    c1.addView(swOn);
    tvStatus = text("", 13, MUT);
    c1.addView(tvStatus, top(4));
    btnPerm = btn("Izinkan tampil di atas aplikasi lain", x -> askOverlay());
    c1.addView(btnPerm, top(10));
    swOn.setOnCheckedChangeListener((view, on) -> {
      if (building) return;
      if (on) {
        if (!canOverlay()) {
          building = true;
          swOn.setChecked(false);
          building = false;
          askOverlay();
          return;
        }
        p.edit().putBoolean("enabled", true).apply();
        startSvc();
      } else {
        p.edit().putBoolean("enabled", false).apply();
        stopService(new Intent(MainActivity.this, CrosshairService.class));
      }
      ui.postDelayed(statusRun, 500);
    });

    // posisi
    LinearLayout c2 = card(root, "Posisi");
    c2.addView(text("Geser langsung: sentuh dan seret crosshair di layar (terkunci otomatis beberapa detik setelah dilepas). Atau pakai slider dan tombol panah untuk geser halus.", 12, MUT), top(4));
    c2.addView(btn("\u270B Geser langsung di layar", x -> toggleEdit()), top(10));
    sbX = seek(c2, "Horizontal", 0, 1000, clamp(Math.round(p.getFloat("fx", 0f) * 1000f) + 500, 0, 1000), posFmt, val -> setPos(val, sbY.getProgress()));
    sbY = seek(c2, "Vertikal", 0, 1000, clamp(Math.round(p.getFloat("fy", 0f) * 1000f) + 500, 0, 1000), posFmt, val -> setPos(sbX.getProgress(), val));
    nudgeRow(c2);

    // tampilan
    LinearLayout c3 = card(root, "Tampilan");
    Spinner sp = new Spinner(this);
    ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, CrosshairView.STYLES);
    ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    sp.setAdapter(ad);
    sp.setSelection(clamp(p.getInt("style", 0), 0, CrosshairView.STYLES.length - 1));
    sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
        if (building) return;
        setInt("style", pos);
      }
      @Override public void onNothingSelected(AdapterView<?> parent) {}
    });
    c3.addView(sp, top(8));
    seek(c3, "Ukuran", 8, 200, p.getInt("size", 36), v -> v + " dp", v -> setInt("size", v));
    seek(c3, "Ketebalan", 1, 12, p.getInt("thick", 2), v -> v + " dp", v -> setInt("thick", v));
    seek(c3, "Celah tengah", 0, 60, p.getInt("gap", 4), v -> v + " dp", v -> setInt("gap", v));
    seek(c3, "Transparansi (buram)", 10, 100, p.getInt("alpha", 100), v -> v + "%", v -> setInt("alpha", v));
    check(c3, "Garis tepi hitam (lebih jelas di latar terang)", p.getBoolean("outline", true), "outline");
    c3.addView(text("Warna", 14, TXT), top(12));
    buildColors(c3);
    LinearLayout hr = new LinearLayout(this);
    hr.setOrientation(LinearLayout.HORIZONTAL);
    hr.setGravity(Gravity.CENTER_VERTICAL);
    etHex = new EditText(this);
    etHex.setHint("#RRGGBB");
    etHex.setHintTextColor(MUT);
    etHex.setTextColor(TXT);
    etHex.setSingleLine(true);
    etHex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    etHex.setFilters(new InputFilter[] { new InputFilter.LengthFilter(7) });
    hr.addView(etHex, new LinearLayout.LayoutParams(0, WRAP, 1f));
    LinearLayout.LayoutParams apl = new LinearLayout.LayoutParams(WRAP, WRAP);
    apl.leftMargin = dp(8);
    hr.addView(btn("Terapkan", x -> applyHex()), apl);
    c3.addView(hr, top(6));

    // pembaruan
    LinearLayout c4 = card(root, "Pembaruan");
    c4.addView(text("Versi terpasang: v" + UpdateChecker.currentVersion(this), 13, MUT), top(4));
    check(c4, "Cek pembaruan otomatis setiap aplikasi dibuka", p.getBoolean("autoupd", true), "autoupd");
    c4.addView(btn("Cek pembaruan sekarang", x -> checkUpdate(true)), top(8));

    building = false;
    refreshStatus();
  }

  private void nudgeRow(LinearLayout parent) {
    LinearLayout r = new LinearLayout(this);
    r.setOrientation(LinearLayout.HORIZONTAL);
    String[] labels = { "\u25C0", "\u25B2", "\u25BC", "\u25B6", "Tengah" };
    for (int i = 0; i < labels.length; i++) {
      final int k = i;
      Button bt = btn(labels[i], x -> {
        switch (k) {
          case 0: nudge(-5, 0); break;
          case 1: nudge(0, -5); break;
          case 2: nudge(0, 5); break;
          case 3: nudge(5, 0); break;
          default: setPos(500, 500); break;
        }
      });
      bt.setMinWidth(0);
      bt.setMinimumWidth(0);
      bt.setPadding(0, 0, 0, 0);
      LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, WRAP, 1f);
      l.leftMargin = i == 0 ? 0 : dp(6);
      r.addView(bt, l);
    }
    parent.addView(r, top(10));
  }

  private void buildColors(LinearLayout parent) {
    colorRow = new LinearLayout(this);
    colorRow.setOrientation(LinearLayout.HORIZONTAL);
    chips = new GradientDrawable[PRESETS.length];
    for (int i = 0; i < PRESETS.length; i++) {
      final int col = PRESETS[i];
      View chip = new View(this);
      GradientDrawable g = new GradientDrawable();
      g.setColor(col);
      g.setCornerRadius(dp(10));
      chips[i] = g;
      chip.setBackground(g);
      chip.setOnClickListener(x -> selectColor(col));
      LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, dp(34), 1f);
      l.leftMargin = i == 0 ? 0 : dp(6);
      colorRow.addView(chip, l);
    }
    parent.addView(colorRow, top(8));
    markChips();
  }

  private void markChips() {
    int cur = p.getInt("color", 0xFF00E676) | 0xFF000000;
    for (int i = 0; i < PRESETS.length; i++) {
      if (PRESETS[i] == cur) chips[i].setStroke(dp(3), Color.WHITE);
      else chips[i].setStroke(dp(1), 0x55FFFFFF);
    }
  }

  /* ---------- aksi ---------- */

  private void setInt(String key, int val) {
    p.edit().putInt(key, val).apply();
    refreshPreview();
    reload();
  }

  private void selectColor(int c) {
    p.edit().putInt("color", c | 0xFF000000).apply();
    markChips();
    refreshPreview();
    reload();
  }

  private void applyHex() {
    String s = etHex.getText().toString().trim();
    if (!s.startsWith("#")) s = "#" + s;
    try {
      selectColor(Color.parseColor(s));
    } catch (IllegalArgumentException e) {
      toast("Kode warna tidak valid. Contoh: #FF0000");
    }
  }

  private void nudge(int dx, int dy) {
    setPos(clamp(sbX.getProgress() + dx, 0, 1000), clamp(sbY.getProgress() + dy, 0, 1000));
  }

  private void setPos(int x, int y) {
    sbX.setProgress(x);
    sbY.setProgress(y);
    p.edit().putFloat("fx", (x - 500) / 1000f).putFloat("fy", (y - 500) / 1000f).apply();
    reload();
  }

  private void refreshPreview() {
    if (preview != null) preview.apply(p);
  }

  private void reload() {
    ui.removeCallbacks(reloadRun);
    ui.postDelayed(reloadRun, 40);
  }

  private boolean canOverlay() { return Settings.canDrawOverlays(this); }

  private void startSvc() {
    try {
      startForegroundService(new Intent(this, CrosshairService.class));
    } catch (Exception e) {
      toast("Tidak bisa menyalakan crosshair. Coba buka aplikasi lagi.");
    }
  }

  private void toggleEdit() {
    if (!canOverlay()) { askOverlay(); return; }
    p.edit().putBoolean("enabled", true).apply();
    Intent i = new Intent(this, CrosshairService.class)
        .setAction(CrosshairService.ACTION_EDIT)
        .putExtra(CrosshairService.EXTRA_ON, !CrosshairService.editing);
    try {
      if (CrosshairService.running) startService(i); else startForegroundService(i);
    } catch (Exception e) {
      toast("Tidak bisa masuk mode geser.");
      return;
    }
    building = true;
    swOn.setChecked(true);
    building = false;
    ui.postDelayed(statusRun, 500);
  }

  private void refreshStatus() {
    if (tvStatus == null) return;
    boolean ov = canOverlay();
    btnPerm.setVisibility(ov ? View.GONE : View.VISIBLE);
    String s;
    if (!ov) s = "Izin \"tampil di atas aplikasi lain\" belum diberikan.";
    else if (CrosshairService.editing) s = "Mode geser: seret crosshair ke posisi yang kamu mau.";
    else if (CrosshairService.running) s = "Aktif: crosshair tampil di atas semua aplikasi.";
    else s = "Nonaktif.";
    tvStatus.setText(s);
  }

  private void askOverlay() {
    new AlertDialog.Builder(this)
        .setTitle("Izin tampil di atas aplikasi lain")
        .setMessage("Crosshair butuh izin ini supaya bisa muncul di atas aplikasi lain. Aktifkan di layar berikutnya, lalu kembali ke sini.")
        .setPositiveButton("Buka pengaturan", (d, w) -> {
          try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
          } catch (Exception e) {
            toast("Buka Pengaturan > Aplikasi > Crosshair > Tampil di atas aplikasi lain.");
          }
        })
        .setNegativeButton("Nanti", null)
        .show();
  }

  private void askNotifOnce() {
    if (Build.VERSION.SDK_INT < 33) return;
    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
    if (p.getBoolean("askedNotif", false)) return;
    p.edit().putBoolean("askedNotif", true).apply();
    requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 11);
  }

  /* ---------- pembaruan otomatis ---------- */

  private void checkUpdate(final boolean manual) {
    if (checking) return;
    checking = true;
    if (manual) toast("Memeriksa pembaruan...");
    new Thread(() -> {
      UpdateChecker.Info found = null;
      Exception err = null;
      try {
        found = UpdateChecker.check(MainActivity.this);
      } catch (Exception e) {
        err = e;
      }
      final UpdateChecker.Info info = found;
      final Exception fail = err;
      runOnUiThread(() -> {
        checking = false;
        if (isFinishing() || isDestroyed()) return;
        if (fail != null) {
          if (manual) toast("Gagal memeriksa pembaruan. Cek koneksi internet.");
          return;
        }
        if (info == null) {
          if (manual) toast("Sudah versi terbaru (v" + UpdateChecker.currentVersion(MainActivity.this) + ")");
          return;
        }
        showUpdateDialog(info);
      });
    }).start();
  }

  private void showUpdateDialog(final UpdateChecker.Info info) {
    if (updateDialog != null && updateDialog.isShowing()) return;
    if (dlProgress != null && dlProgress.isShowing()) return;
    String log = info.changelog;
    if (log.length() > 700) log = log.substring(0, 700) + "...";
    updateDialog = new AlertDialog.Builder(this)
        .setTitle("Pembaruan tersedia: v" + info.version)
        .setMessage("Versi kamu: v" + UpdateChecker.currentVersion(this) + "\n\n" + log)
        .setPositiveButton("Update sekarang", (d, w) -> startDownload(info))
        .setNegativeButton("Nanti", null)
        .show();
  }

  private void startDownload(final UpdateChecker.Info info) {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(24), dp(16), dp(24), dp(8));
    final TextView tv = text("Mengunduh v" + info.version + "...", 14, TXT);
    final ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    pb.setMax(100);
    pb.setIndeterminate(true);
    box.addView(tv);
    box.addView(pb, top(10));
    dlProgress = new AlertDialog.Builder(this).setTitle("Memperbarui").setView(box).setCancelable(false).create();
    dlProgress.show();
    new Thread(() -> {
      try {
        final File f = UpdateChecker.download(MainActivity.this, info, (done, total) -> runOnUiThread(() -> {
          if (total > 0) {
            int pct = (int) (done * 100L / total);
            pb.setIndeterminate(false);
            pb.setProgress(pct);
            tv.setText("Mengunduh v" + info.version + "... " + pct + "%");
          }
        }));
        runOnUiThread(() -> {
          dismissProgress();
          installApk(f);
        });
      } catch (Exception e) {
        runOnUiThread(() -> {
          dismissProgress();
          toast("Gagal mengunduh pembaruan. Coba lagi.");
        });
      }
    }).start();
  }

  private void dismissProgress() {
    try {
      if (dlProgress != null && dlProgress.isShowing()) dlProgress.dismiss();
    } catch (Exception ignored) {}
    dlProgress = null;
  }

  private boolean canInstall() {
    return getPackageManager().canRequestPackageInstalls();
  }

  /** Android tidak mengizinkan aplikasi memasang dirinya tanpa persetujuan: pengguna tetap mengetuk "Update" sekali. */
  private void installApk(final File apk) {
    if (!canInstall()) {
      pendingApk = apk;
      new AlertDialog.Builder(this)
          .setTitle("Izin pemasangan")
          .setMessage("Agar bisa memperbarui dirinya sendiri, izinkan aplikasi ini memasang aplikasi. Aktifkan di layar berikutnya, lalu kembali ke sini.")
          .setPositiveButton("Buka pengaturan", (d, w) -> startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))))
          .setNegativeButton("Batal", (d, w) -> pendingApk = null)
          .show();
      return;
    }
    try {
      Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
      Intent i = new Intent(Intent.ACTION_VIEW);
      i.setDataAndType(uri, "application/vnd.android.package-archive");
      i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(i);
    } catch (Exception e) {
      toast("Tidak bisa membuka pemasang. Coba lagi.");
    }
  }
}
