package __PKG__;

import android.app.Notification;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Set;

public class IslandListener extends NotificationListenerService {
  private String lastSig = "";
  private long lastTime = 0;

  @Override public void onNotificationPosted(StatusBarNotification sbn) {
    IslandService svc = IslandService.instance;
    if (svc == null) return;
    String pkg = sbn.getPackageName();
    if (pkg.equals(getPackageName())) return;

    // filter aplikasi: 0 = semua aplikasi, 1 = hanya aplikasi yang dipilih
    SharedPreferences sp = getSharedPreferences("island", MODE_PRIVATE);
    if (sp.getInt("fmode", 0) == 1) {
      Set<String> allow = sp.getStringSet("fapps", null);
      if (allow == null || !allow.contains(pkg)) return;
    }

    Notification n = sbn.getNotification();
    if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

    boolean media = Notification.CATEGORY_TRANSPORT.equals(n.category);
    boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
    boolean timer = Notification.CATEGORY_ALARM.equals(n.category) || "stopwatch".equals(n.category);
    if (ongoing && !media && !timer) return;

    Bundle ex = n.extras;
    if (ex == null) return;
    CharSequence t = ex.getCharSequence(Notification.EXTRA_TITLE);
    if (t == null) return;
    CharSequence x = ex.getCharSequence(Notification.EXTRA_TEXT);
    if (x == null) x = ex.getCharSequence(Notification.EXTRA_SUB_TEXT);
    String text = x == null ? "" : x.toString();

    // sembunyikan notifikasi asli HP supaya tidak tabrakan dengan island (musik/timer/tidak-bisa-dihapus tetap dibiarkan)
    boolean hide = sp.getBoolean("hidenotif", false) && !media && !timer && !ongoing
        && (n.flags & Notification.FLAG_NO_CLEAR) == 0;

    String sig = sbn.getKey() + "|" + t + "|" + text;
    long now = System.currentTimeMillis();
    if (sig.equals(lastSig) && now - lastTime < 3000) {
      if (hide) { try { cancelNotification(sbn.getKey()); } catch (Exception ignored) {} }
      return;
    }
    lastSig = sig;
    lastTime = now;

    String prefix = media ? "\u266A " : (timer ? "\u23F1 " : "");

    Drawable appIcon = null;
    try { appIcon = getPackageManager().getApplicationIcon(pkg); } catch (Exception ignored) {}

    // foto profil pengirim (WhatsApp dll) / sampul album; kalau ada, ikon aplikasi jadi lencana kecil
    Drawable avatar = null;
    try {
      Icon li = n.getLargeIcon();
      if (li != null) avatar = li.loadDrawable(this);
    } catch (Exception ignored) {}

    if (avatar != null && appIcon != null) svc.show(prefix + t, text, avatar, appIcon, n.contentIntent);
    else svc.show(prefix + t, text, appIcon, null, n.contentIntent);
    if (hide) { try { cancelNotification(sbn.getKey()); } catch (Exception ignored) {} }
  }
}
