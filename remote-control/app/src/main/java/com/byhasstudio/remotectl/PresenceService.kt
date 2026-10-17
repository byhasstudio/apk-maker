package com.byhasstudio.remotectl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.firebase.database.ValueEventListener

/**
 * Berjalan di latar belakang selama fitur "hubungkan tanpa kode"
 * aktif: menjaga status online perangkat ini, dan mendengarkan
 * permintaan konek dari perangkat lain yang login dengan akun yang
 * sama. Kalau kata sandi cocok, layanan ini membuka activity
 * transparan (CaptureRequestActivity) untuk memicu dialog izin rekam
 * layar sistem -- satu-satunya langkah yang TIDAK bisa dilewati
 * tanpa sentuhan pengguna, karena itu batasan keamanan Android
 * sendiri, bukan kode aplikasi ini.
 */
class PresenceService : Service() {

    companion object {
        private const val CHANNEL_ID = "remotectl_presence"
        private const val NOTIF_ID = 77
        private const val HEARTBEAT_MS = 30_000L
    }

    private var listener: ValueEventListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastHandledTs = 0L

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            DeviceStore.heartbeat(this@PresenceService)
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())

        handler.post(heartbeatRunnable)

        listener = DeviceStore.listenIncomingRequest(this) { code, passwordHash ->
            val now = System.currentTimeMillis()
            if (now == lastHandledTs) return@listenIncomingRequest
            lastHandledTs = now
            handleRequest(code, passwordHash)
        }
    }

    private fun handleRequest(code: String, passwordHash: String) {
        // Ambil ulang data perangkat (mode + hash kata sandi tersimpan)
        // untuk dicocokkan. Jalur ini hanya bisa ditulis oleh akun yang
        // sama (dikunci di aturan database), jadi mode tanpa kata sandi
        // tetap aman walau tidak ada verifikasi tambahan di sini.
        val deviceId = DeviceStore.deviceId(this)
        com.google.firebase.database.FirebaseDatabase.getInstance(
            "https://apk-maker-ceb1f-default-rtdb.asia-southeast1.firebasedatabase.app"
        ).reference
            .child("users").child(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return)
            .child("devices").child(deviceId)
            .get()
            .addOnSuccessListener { snapshot ->
                val requiresPassword = snapshot.child("requiresPassword").getValue(Boolean::class.java) ?: true
                val savedHash = snapshot.child("passwordHash").getValue(String::class.java)
                DeviceStore.clearIncomingRequest(this)

                val allowed = if (requiresPassword) {
                    savedHash != null && savedHash == passwordHash
                } else {
                    true
                }
                if (allowed) {
                    val intent = Intent(this, CaptureRequestActivity::class.java).apply {
                        putExtra(CaptureRequestActivity.EXTRA_SESSION_CODE, code)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Hubungkan tanpa kode", NotificationManager.IMPORTANCE_MIN
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote Ctl aktif")
            .setContentText("Siap menerima koneksi tanpa kode")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(heartbeatRunnable)
        DeviceStore.setOffline(this)
    }
}
