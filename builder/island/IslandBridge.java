package __PKG__;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import org.json.JSONObject;

/** Dipasang ke WebView sebagai window.AndroidIsland. */
public class IslandBridge {
  private final Activity a;

  public IslandBridge(Activity a) { this.a = a; }

  private boolean canOverlay() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(a); }

  @JavascriptInterface
  public String getState() {
    try {
      JSONObject o = new JSONObject();
      String en = Settings.Secure.getString(a.getContentResolver(), "enabled_notification_listeners");
      o.put("overlay", canOverlay());
      o.put("listener", en != null && en.contains(a.getPackageName()));
      o.put("running", IslandService.instance != null);
      o.put("supported", Build.VERSION.SDK_INT >= 26);
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
      try { s.show(t, x, a.getPackageManager().getApplicationIcon(a.getApplicationInfo()), null); } catch (Exception ignored) {}
    } });
  }

  @JavascriptInterface
  public void setStyle(String hex, int op, int top, int cw, int ew) {
    try {
      int c = Color.parseColor(hex) | 0xFF000000;
      SharedPreferences.Editor e = a.getSharedPreferences("island", Context.MODE_PRIVATE).edit();
      e.putInt("bg", c).putInt("op", op).putInt("top", top).putInt("cw", cw).putInt("ew", ew).apply();
    } catch (Exception ignored) {}
  }

  @JavascriptInterface
  public void reload() {
    a.runOnUiThread(new Runnable() { public void run() {
      if (IslandService.instance != null) a.startService(new Intent(a, IslandService.class).setAction(IslandService.ACTION_RELOAD));
    } });
  }
}
