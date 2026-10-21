package com.byhasstudio.remotectl

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Eksekusi perintah jarak jauh (tidur/matikan/restart/teks) yang dikirim
 * akun admin lewat DeviceStore.sendRemoteCommand -- dijalankan dari
 * PresenceService begitu perintah terdeteksi, tidak butuh sesi kontrol
 * layar aktif sama sekali.
 *
 * BATASAN PENTING: Android (tidak seperti Windows) TIDAK mengizinkan
 * aplikasi biasa mematikan atau me-restart perangkat tanpa akses root --
 * ini batasan keamanan sistem operasi sendiri, bukan kode aplikasi ini.
 * "matikan" dan "restart" di bawah cuma akan berhasil di HP yang sudah
 * di-root; di HP normal (tidak di-root), perintah ini akan gagal diam-diam
 * (tidak ada efek apa pun). "Tidur" (kunci layar) dan "teks" selalu
 * berfungsi di HP mana pun tanpa root, karena keduanya memakai
 * Accessibility Service yang memang sudah diizinkan pengguna.
 */
object RemoteCommandRunner {

    fun run(context: Context, type: String, text: String) {
        when (type) {
            "sleep" -> lockScreen()
            "poweroff" -> tryRootShell("reboot -p")
            "restart" -> tryRootShell("reboot")
            "text" -> showText(context, text)
        }
    }

    /** Kunci layar lewat Accessibility Service -- butuh API 28+ (Android 9), sudah dipastikan di-set minSdk proyek ini. */
    private fun lockScreen() {
        RemoteAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }

    /**
     * Tampilkan teks yang diketik admin sebagai Toast besar & tahan lama di
     * layar -- dipilih karena tidak butuh izin tambahan apa pun (beda dari
     * overlay/"tampil di atas aplikasi lain" yang butuh izin manual dulu),
     * jadi selalu bisa langsung dipakai begitu "hubungkan tanpa kode" aktif.
     */
    private fun showText(context: Context, text: String) {
        val shown = text.ifBlank { "(tidak ada teks)" }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, shown, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Coba jalankan lewat akses root -- cuma berhasil di HP yang sudah
     * di-root dan sudah mengizinkan aplikasi ini lewat aplikasi super user
     * (mis. Magisk). Di HP normal (mayoritas pengguna), ini akan gagal
     * (IOException "su: not found" atau sejenisnya) dan dibiarkan saja --
     * tidak ada cara lain buat aplikasi biasa mematikan/restart Android
     * tanpa root.
     */
    private fun tryRootShell(command: String) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor()
        } catch (e: Exception) {
            // Diam-diam gagal di HP tanpa root -- lihat catatan BATASAN di atas.
        }
    }
}
