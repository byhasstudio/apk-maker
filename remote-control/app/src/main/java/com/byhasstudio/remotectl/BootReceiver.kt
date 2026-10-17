package com.byhasstudio.remotectl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Menyalakan lagi PresenceService otomatis setelah HP di-restart,
 * kalau fitur "hubungkan tanpa kode" sebelumnya aktif -- supaya
 * pengguna tidak perlu buka aplikasi secara manual lagi setelah HP
 * mati/nyala ulang.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (QuickConnectPrefs.isEnabled(context)) {
            ContextCompat.startForegroundService(context, Intent(context, PresenceService::class.java))
        }
    }
}
