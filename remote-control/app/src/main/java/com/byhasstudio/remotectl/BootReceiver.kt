package com.byhasstudio.remotectl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Menyalakan lagi PresenceService otomatis setelah HP di-restart, kalau
 * perangkat ini masih login -- supaya perangkat tetap "terlihat" dan
 * bisa diakses akun admin (lihat DeviceStore.ADMIN_EMAIL) tanpa perlu
 * buka aplikasi secara manual lagi setelah HP mati/nyala ulang. Dulu
 * cuma dinyalakan kalau "hubungkan tanpa kode" aktif -- sekarang
 * dinyalakan tiap kali masih login, lepas dari toggle itu.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val loggedIn = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
        if (loggedIn || QuickConnectPrefs.isEnabled(context)) {
            ContextCompat.startForegroundService(context, Intent(context, PresenceService::class.java))
        }
    }
}
