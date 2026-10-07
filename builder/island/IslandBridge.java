package __PKG__;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Dipasang ke WebView sebagai window.AndroidIsland. */
public class IslandBridge {
  private final Activity a;
  private static Ringtone prev;

  public IslandBridge(Activity a) { this.a = a; }

  private boolean canOverlay() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(a); }

  private SharedPreferences sp() { return a.getSharedPreferences("island", Context.MODE_PRIVATE); }

  @JavascriptInterface
  public String getState() {
    try {
      JSONObject o = new JSONObject();
      String en = Settings.Secure.getString(a.getContentResolver(), "enabled_notification_listeners");
      o.put("overlay", canOverlay());
      o.put("listener", en != null && en.contains(a.getPackageName()));
      o.put("running", IslandService.instance != null);
      o.put("supported", Build.VERSION.SDK_INT >= 26);
      o.put("cam", IslandService.instance != null && IslandService.camFound);
      String ax = Settings.Secure.getString(a.getContentResolver(), "enabled_accessibility_services");
      o.put("a11y", ax != null && ax.contains(a.getPackageName() + "/" + a.getPackageName() + ".IslandAnim"));
      return o.toString();
    } catch (Exception e) {
      return "{}";
    }
  }

  @JavascriptInterface
  public void openOverlay() {
    a.runOnUiThread(new Runnable() { public void run() {
      try {
        a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.getPackageName())));
      } catch (Exception ignored) {}
    } });
  }

  @JavascriptInterface
  public void openListener() {
    a.runOnUiThread(new Runnable() { public void run() {
      try { a.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); } catch (Exception ignored) {}
    } });
  }

  @JavascriptInterface
  public void requestNotif() {
    a.runOnUiThread(new Runnable() { public void run() {
      if (Build.VERSION.SDK_INT >= 33) a.requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, 7433);
    } });
  }

  @JavascriptInterface
  public void start() {
    a.runOnUiThread(new Runnable() { public void run() {
      if (Build.VERSION.SDK_INT < 26) { Toast.makeText(a, "Butuh Android 8.0 ke atas", Toast.LENGTH_SHORT).show(); return; }
      if (!canOverlay()) { Toast.makeText(a, "Berikan izin overlay dulu", Toast.LENGTH_SHORT).show(); return; }
      a.startForegroundService(new Intent(a, IslandService.class));
    } });
  }

  @JavascriptInterface
  public void stop() {
    a.runOnUiThread(new Runnable() { public void run() { a.stopService(new Intent(a, IslandService.class)); } });
  }

  @JavascriptInterface
  public void test(String title, String text) {
    final String t = title, x = text;
    a.runOnUiThread(new Runnable() { public void run() {
      IslandService s = IslandService.instance;
      if (s == null) return;
      try { s.show(t, x, a.getPackageManager().getApplicationIcon(a.getApplicationInfo()), null, null); } catch (Exception ignored) {}
    } });
  }

  /** hex warna, buram (%), geser vertikal (dp), lebar pill (dp), lebar melebar (dp), geser horizontal (dp), tambah tinggi pill (dp) */
  @JavascriptInterface
  public void setStyle(String hex, int op, int dy, int cw, int ew, int cx, int dh) {
    try {
      int c = Color.parseColor(hex) | 0xFF000000;
      sp().edit().putInt("bg", c).putInt("op", op).putInt("dy", dy).putInt("cw", cw).putInt("ew", ew)
          .putInt("cx", cx).putInt("dh", dh).apply();
    } catch (Exception ignored) {}
  }

  @JavascriptInterface
  public void reload() {
    a.runOnUiThread(new Runnable() { public void run() {
      if (IslandService.instance != null) a.startService(new Intent(a, IslandService.class).setAction(IslandService.ACTION_RELOAD));
    } });
  }

  /* ---------- filter aplikasi & suara ---------- */

  /** Daftar aplikasi yang punya ikon peluncur: [{"n":"nama","p":"paket"}, ...] */
  @JavascriptInterface
  public String getApps() {
    try {
      PackageManager pm = a.getPackageManager();
      Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
      List<ResolveInfo> l = pm.queryIntentActivities(i, 0);
      ArrayList<String[]> list = new ArrayList<String[]>();
      HashSet<String> seen = new HashSet<String>();
      for (ResolveInfo r : l) {
        String pkg = r.activityInfo.packageName;
        if (pkg.equals(a.getPackageName()) || !seen.add(pkg)) continue;
        list.add(new String[] { String.valueOf(r.loadLabel(pm)), pkg });
      }
      Collections.sort(list, new Comparator<String[]>() {
        @Override public int compare(String[] x, String[] y) { return x[0].compareToIgnoreCase(y[0]); }
      });
      JSONArray arr = new JSONArray();
      for (String[] s : list) {
        JSONObject o = new JSONObject();
        o.put("n", s[0]);
        o.put("p", s[1]);
        arr.put(o);
      }
      return arr.toString();
    } catch (Exception e) {
      return "[]";
    }
  }

  /** Ikon aplikasi sebagai data URL PNG kecil (dimuat satu per satu saat terlihat). */
  @JavascriptInterface
  public String getIcon(String pkg) {
    try {
      int s = 96;
      Drawable d = IslandService.flat(a.getPackageManager().getApplicationIcon(pkg), s);
      Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(b);
      c.clipPath(IslandService.squircle(s, s));
      d.setBounds(0, 0, s, s);
      d.draw(c);
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      b.compress(Bitmap.CompressFormat.PNG, 100, bo);
      return "data:image/png;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    } catch (Exception e) {
      return "";
    }
  }

  @JavascriptInterface
  public String getFilter() {
    try {
      SharedPreferences p = sp();
      JSONObject o = new JSONObject();
      o.put("mode", p.getInt("fmode", 0));
      JSONArray arr = new JSONArray();
      Set<String> s = p.getStringSet("fapps", null);
      if (s != null) for (String x : s) arr.put(x);
      o.put("apps", arr);
      o.put("snd", p.getBoolean("snd", true));
      o.put("anim", p.getBoolean("anim", false));
      o.put("hide", p.getBoolean("hidenotif", false));
      o.put("tapopen", p.getBoolean("tapopen", true));
      String su = p.getString("suri", "");
      o.put("suri", su == null ? "" : su);
      o.put("svol", p.getInt("svol", 100));
      String nm = "Bawaan HP";
      if (su != null && su.length() > 0) {
        try { nm = RingtoneManager.getRingtone(a, Uri.parse(su)).getTitle(a); } catch (Exception ignored) {}
      }
      o.put("sname", nm);
      return o.toString();
    } catch (Exception e) {
      return "{}";
    }
  }

  /** mode 0 = semua aplikasi, 1 = hanya aplikasi di daftar (JSON array nama paket). */
  @JavascriptInterface
  public void setFilter(int mode, String json) {
    try {
      HashSet<String> s = new HashSet<String>();
      JSONArray arr = new JSONArray(json);
      for (int i = 0; i < arr.length(); i++) s.add(arr.getString(i));
      sp().edit().putInt("fmode", mode).putStringSet("fapps", s).apply();
    } catch (Exception ignored) {}
  }

  /** true = ketuk notifikasi membuka aplikasinya; false = ketukan hanya menutup island. */
  @JavascriptInterface
  public void setTapOpen(boolean on) { sp().edit().putBoolean("tapopen", on).apply(); }

  /** Animasi buka/tutup ala iOS untuk semua aplikasi (butuh layanan aksesibilitas aktif). */
  @JavascriptInterface
  public void setAnim(boolean on) { sp().edit().putBoolean("anim", on).apply(); }

  @JavascriptInterface
  public void openAccessibility() {
    a.runOnUiThread(new Runnable() { public void run() {
      try { a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Exception ignored) {}
    } });
  }

  /** true = notifikasi asli dihapus dari bayangan notifikasi (hanya yang tampil di island). */
  @JavascriptInterface
  public void setHideNotif(boolean on) { sp().edit().putBoolean("hidenotif", on).apply(); }

  @JavascriptInterface
  public void setSound(boolean on) { sp().edit().putBoolean("snd", on).apply(); }

  /** Daftar nada notifikasi di HP: [{"t":"judul","u":"uri"}]; entri pertama = bawaan HP (uri kosong). */
  @JavascriptInterface
  public String getSounds() {
    JSONArray arr = new JSONArray();
    try {
      JSONObject d = new JSONObject();
      d.put("t", "Bawaan HP");
      d.put("u", "");
      arr.put(d);
      RingtoneManager rm = new RingtoneManager(a);
      rm.setType(RingtoneManager.TYPE_NOTIFICATION);
      Cursor c = rm.getCursor();
      while (c.moveToNext()) {
        JSONObject o = new JSONObject();
        o.put("t", c.getString(RingtoneManager.TITLE_COLUMN_INDEX));
        o.put("u", rm.getRingtoneUri(c.getPosition()).toString());
        arr.put(o);
      }
    } catch (Exception ignored) {}
    return arr.toString();
  }

  /** Dengarkan contoh nada (uri kosong = nada bawaan HP) dengan volume yang sedang diatur. */
  @JavascriptInterface
  public void previewSound(String uri) {
    try {
      if (prev != null) { try { prev.stop(); } catch (Exception ignored) {} }
      Uri u = (uri == null || uri.length() == 0) ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) : Uri.parse(uri);
      Ringtone r = RingtoneManager.getRingtone(a, u);
      if (r == null) return;
      if (Build.VERSION.SDK_INT >= 28) r.setVolume(Math.max(0, Math.min(100, sp().getInt("svol", 100))) / 100f);
      prev = r;
      r.play();
    } catch (Exception ignored) {}
  }

  @JavascriptInterface
  public void setSoundUri(String uri) { sp().edit().putString("suri", uri == null ? "" : uri).apply(); }

  @JavascriptInterface
  public void setSoundVol(int v) { sp().edit().putInt("svol", Math.max(0, Math.min(100, v))).apply(); }
}
