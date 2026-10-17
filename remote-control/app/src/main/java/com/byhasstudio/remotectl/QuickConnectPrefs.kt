package com.byhasstudio.remotectl

import android.content.Context

/**
 * Penanda lokal (tersimpan di HP, bukan di Firebase) kalau fitur
 * hubungkan-tanpa-kode sedang aktif. Dipakai BootReceiver dan
 * PresenceService.onTaskRemoved untuk tahu apakah layanan perlu
 * dinyalakan ulang, tanpa perlu menunggu koneksi internet dulu.
 */
object QuickConnectPrefs {
    private const val PREFS = "remotectl_prefs"
    private const val KEY_ENABLED = "quick_connect_enabled"

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
}
