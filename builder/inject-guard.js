// Menanam penjaga status ke MainActivity APK. Saat aplikasi dibuka/dilanjutkan, APK menanyakan
// status ke Worker. Jika APK diblokir admin: muncul pop up, tampilan dikosongkan, aplikasi ditutup.
// Status "diblokir" disimpan di HP, jadi tetap terblokir walau offline.
const fs = require("fs"), path = require("path");
const e = process.env;
const worker = (e.WORKER_URL || "").replace(/\/+$/, "");
const job = e.JOB_ID || "";
if (!/^https:\/\/[^\s"\\]+$/.test(worker)) throw new Error("WORKER_URL tidak valid");
if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(job)) throw new Error("JOB_ID tidak valid");

function find(dir) {
  for (const n of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, n.name);
    if (n.isDirectory()) { const r = find(p); if (r) return r; }
    else if (n.name === "MainActivity.java") return p;
  }
  return null;
}
const file = find("android/app/src/main/java");
if (!file) throw new Error("MainActivity.java tidak ditemukan");
const pkg = (fs.readFileSync(file, "utf8").match(/^package\s+([\w.]+);/m) || [])[1];
if (!pkg) throw new Error("package MainActivity tidak ditemukan");

const java = `package ${pkg};

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class MainActivity extends BridgeActivity {
  private static final String STATUS_URL = "${worker}/api/app-status/${job}";
  private static final String PREF = "app_guard";
  private AlertDialog dialog;
  private boolean blocked = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    SharedPreferences sp = getSharedPreferences(PREF, Context.MODE_PRIVATE);
    if (sp.getBoolean("banned", false)) block(sp.getString("reason", ""));
    check();
  }

  @Override
  public void onResume() {
    super.onResume();
    check();
  }

  private void check() {
    new Thread(() -> {
      try {
        HttpURLConnection c = (HttpURLConnection) new URL(STATUS_URL).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Cache-Control", "no-cache");
        if (c.getResponseCode() != 200) return;
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        JSONObject o = new JSONObject(sb.toString());
        final boolean banned = o.optBoolean("banned", false);
        final String reason = o.optString("reason", "");
        getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
          .putBoolean("banned", banned).putString("reason", reason).apply();
        runOnUiThread(() -> {
          if (banned) block(reason);
          else if (blocked) recreate();
        });
      } catch (Exception ignored) {
        // tidak ada jaringan: pakai status tersimpan
      }
    }).start();
  }

  private void block(String reason) {
    if (isFinishing()) return;
    blocked = true;
    try {
      if (getBridge() != null && getBridge().getWebView() != null) {
        WebView wv = getBridge().getWebView();
        wv.stopLoading();
        wv.loadUrl("about:blank");
        wv.setVisibility(View.INVISIBLE);
      }
    } catch (Exception ignored) {}
    if (dialog != null && dialog.isShowing()) return;
    String msg = "Aplikasi ini telah diblokir karena melanggar ketentuan dan tidak dapat digunakan lagi.";
    if (reason != null && reason.length() > 0) msg += "\\n\\nAlasan: " + reason;
    dialog = new AlertDialog.Builder(this)
      .setTitle("Aplikasi diblokir")
      .setMessage(msg)
      .setCancelable(false)
      .setPositiveButton("Tutup", (d, which) -> finishAndRemoveTask())
      .create();
    dialog.setCanceledOnTouchOutside(false);
    dialog.show();
  }
}
`;
fs.writeFileSync(file, java);
console.log("Penjaga status ditanam di", file);
