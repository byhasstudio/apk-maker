package com.byhasstudio.crosshair;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Mengecek GitHub Releases (repo publik, tanpa token). Workflow "Build Crosshair Overlay APK"
 * selalu memperbarui rilis bertag crosshair-latest berisi APK versi terbaru, jadi cukup satu
 * permintaan kecil. Kalau rilis itu tidak ada, dicari dari daftar rilis bertag crosshair-v*.
 * Hanya APK dari repo ini yang diterima, dan Android sendiri menolak APK yang tanda tangannya
 * berbeda.
 */
public final class UpdateChecker {
  private static final String API = "https://api.github.com/repos/byhasstudio/apk-maker/releases";
  private static final String DL_PREFIX = "https://github.com/byhasstudio/apk-maker/releases/download/";
  private static final Pattern APK = Pattern.compile("^Crosshair-(\\d+\\.\\d+\\.\\d+)\\.apk$");

  public static final class Info {
    public String version;
    public String changelog;
    public String apkUrl;
    public long size;
  }

  public interface Progress {
    void on(long done, long total);
  }

  private UpdateChecker() {}

  public static String currentVersion(Context c) {
    try {
      String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
      return v == null ? "0.0.0" : v;
    } catch (Exception e) {
      return "0.0.0";
    }
  }

  /** Mengembalikan null kalau sudah terbaru. Melempar exception kalau gagal terhubung. */
  public static Info check(Context c) throws Exception {
    Info best = null;
    String one = get(API + "/tags/crosshair-latest");
    if (one != null) best = parse(new JSONObject(one));
    if (best == null) {
      String list = get(API + "?per_page=100");
      if (list != null) {
        JSONArray arr = new JSONArray(list);
        for (int i = 0; i < arr.length(); i++) {
          JSONObject r = arr.getJSONObject(i);
          if (!r.optString("tag_name").startsWith("crosshair-")) continue;
          Info x = parse(r);
          if (x != null && (best == null || isNewer(x.version, best.version))) best = x;
        }
      }
    }
    if (best == null) return null;
    return isNewer(best.version, currentVersion(c)) ? best : null;
  }

  private static Info parse(JSONObject r) {
    JSONArray assets = r.optJSONArray("assets");
    if (assets == null) return null;
    for (int i = 0; i < assets.length(); i++) {
      JSONObject a = assets.optJSONObject(i);
      if (a == null) continue;
      Matcher m = APK.matcher(a.optString("name"));
      if (!m.matches()) continue;
      String url = a.optString("browser_download_url");
      if (!url.startsWith(DL_PREFIX)) continue;
      Info info = new Info();
      info.version = m.group(1);
      info.apkUrl = url;
      info.size = a.optLong("size", 0L);
      String body = r.optString("body", "").trim();
      info.changelog = body.length() == 0 ? "Pembaruan tersedia." : body;
      return info;
    }
    return null;
  }

  public static boolean isNewer(String remote, String current) {
    String[] r = remote.split("\\.");
    String[] c = current.split("\\.");
    for (int i = 0; i < Math.max(r.length, c.length); i++) {
      int rv = i < r.length ? num(r[i]) : 0;
      int cv = i < c.length ? num(c[i]) : 0;
      if (rv != cv) return rv > cv;
    }
    return false;
  }

  private static int num(String s) {
    try {
      return Integer.parseInt(s.trim());
    } catch (Exception e) {
      return 0;
    }
  }

  private static String get(String u) throws IOException {
    HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
    try {
      c.setRequestProperty("Accept", "application/vnd.github+json");
      c.setRequestProperty("User-Agent", "CrosshairOverlay");
      c.setConnectTimeout(8000);
      c.setReadTimeout(10000);
      int code = c.getResponseCode();
      if (code == 404) return null;
      if (code != 200) throw new IOException("HTTP " + code);
      InputStream in = c.getInputStream();
      try {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bo.write(buf, 0, n);
        return bo.toString("UTF-8");
      } finally {
        in.close();
      }
    } finally {
      c.disconnect();
    }
  }

  /** Mengunduh APK ke cache aplikasi. Dipanggil dari thread latar belakang. */
  public static File download(Context ctx, Info info, Progress cb) throws IOException {
    if (info.apkUrl == null || !info.apkUrl.startsWith(DL_PREFIX)) throw new IOException("URL tidak valid");
    File dir = new File(ctx.getCacheDir(), "updates");
    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Tidak bisa membuat folder");
    File out = new File(dir, "crosshair-update.apk");
    File tmp = new File(dir, "crosshair-update.part");
    HttpURLConnection c = (HttpURLConnection) new URL(info.apkUrl).openConnection();
    try {
      c.setRequestProperty("User-Agent", "CrosshairOverlay");
      c.setConnectTimeout(10000);
      c.setReadTimeout(20000);
      int code = c.getResponseCode();
      if (code != 200) throw new IOException("HTTP " + code);
      long total = c.getContentLengthLong();
      if (total <= 0) total = info.size;
      InputStream in = c.getInputStream();
      OutputStream os = new FileOutputStream(tmp);
      try {
        byte[] buf = new byte[32768];
        long done = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
          os.write(buf, 0, n);
          done += n;
          if (cb != null) cb.on(done, total);
        }
        os.flush();
        if (total > 0 && done != total) throw new IOException("Unduhan tidak lengkap");
      } finally {
        try {
          in.close();
        } catch (IOException ignored) {
        }
        os.close();
      }
    } finally {
      c.disconnect();
    }
    if (out.exists() && !out.delete()) throw new IOException("Tidak bisa menimpa file lama");
    if (!tmp.renameTo(out)) throw new IOException("Gagal menyimpan file");
    return out;
  }
}
