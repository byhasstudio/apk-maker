package com.byhasstudio.crosshair;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

/** Menyalakan lagi crosshair setelah HP restart atau setelah aplikasi diperbarui, selama belum dimatikan. */
public class BootReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c, Intent i) {
    if (i == null || i.getAction() == null) return;
    String a = i.getAction();
    boolean ok = Intent.ACTION_BOOT_COMPLETED.equals(a)
        || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
        || "android.intent.action.QUICKBOOT_POWERON".equals(a);
    if (!ok) return;
    SharedPreferences p = c.getSharedPreferences(CrosshairService.PREFS, Context.MODE_PRIVATE);
    if (!p.getBoolean("enabled", false)) return;
    if (!Settings.canDrawOverlays(c)) return;
    try {
      c.startForegroundService(new Intent(c, CrosshairService.class));
    } catch (Exception ignored) {
    }
  }
}
