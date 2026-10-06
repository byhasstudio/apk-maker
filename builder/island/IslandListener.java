package __PKG__;

import android.app.Notification;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class IslandListener extends NotificationListenerService {
  private String lastSig = "";
  private long lastTime = 0;

  @Override public void onNotificationPosted(StatusBarNotification sbn) {
    IslandService svc = IslandService.instance;
    if (svc == null) return;
    if (sbn.getPackageName().equals(getPackageName())) return;
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

    String sig = sbn.getKey() + "|" + t + "|" + text;
    long now = System.currentTimeMillis();
    if (sig.equals(lastSig) && now - lastTime < 3000) return;
    lastSig = sig;
    lastTime = now;

    String prefix = media ? "\u266A " : (timer ? "\u23F1 " : "");
    Drawable icon = null;
    try { icon = getPackageManager().getApplicationIcon(sbn.getPackageName()); } catch (Exception ignored) {}
    svc.show(prefix + t, text, icon, n.contentIntent);
  }
}
