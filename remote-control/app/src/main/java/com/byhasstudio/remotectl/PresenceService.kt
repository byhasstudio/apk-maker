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
    private var commandListener: ValueEventListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastHandledTs = 0L
    private var lastHandledCommandTs = 0L

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            DeviceStore.heartbeat(this@PresenceService)
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: kalau proses ini dimatikan sistem (kehabisan
        // memori, dsb.), Android akan mencoba menyalakannya lagi
        // begitu memori tersedia -- tanpa perlu aplikasi dibuka manual.
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Sebagian HP (MIUI, ColorOS, dll.) mematikan service begitu
        // aplikasi ditutup dari recents meski berstatus foreground. Minta
        // sistem menyalakan ulang layanan ini sesaat kemudian -- sekarang
        // berjalan terus selama masih login (lepas dari toggle "hubungkan
        // tanpa kode"), supaya akun admin tetap bisa konek kapan saja.
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
            val restartIntent = Intent(applicationContext, PresenceService::class.java)
            val pendingIntent = android.app.PendingIntent.getService(
                this, 1, restartIntent,
                android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.set(
                android.app.AlarmManager.ELAPSED_REALTIME,
                android.os.SystemClock.elapsedRealtime() + 1000,
                pendingIntent
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())

        DeviceStore.registerPresence(this)
        DeviceStore.registerAutoOffline(this)
        handler.post(heartbeatRunnable)

        listener = DeviceStore.listenIncomingRequest(this) { code, passwordHash, requesterEmail ->
            val now = System.currentTimeMillis()
            if (now == lastHandledTs) return@listenIncomingRequest
            lastHandledTs = now
            handleRequest(code, passwordHash, requesterEmail)
        }

        // Perintah jarak jauh (tidur/matikan/restart/teks) -- tidak butuh
        // sesi kontrol layar aktif sama sekali, cuma butuh layanan ini
        // masih jalan. Node sharedDevices cuma bisa ditulis akun admin
        // (lihat database.rules.json), jadi perintah ini otomatis cuma
        // bisa dikirim akun admin, tidak perlu dicek ulang di sini.
        commandListener = DeviceStore.listenRemoteCommand(this) { type, text ->
            val now = System.currentTimeMillis()
            if (now == lastHandledCommandTs) return@listenRemoteCommand
            lastHandledCommandTs = now
            DeviceStore.clearRemoteCommand(this)
            RemoteCommandRunner.run(this, type, text)
        }
    }

    private fun handleRequest(code: String, passwordHash: String, requesterEmail: String) {
        // Ambil ulang data perangkat (mode + hash kata sandi tersimpan)
        // untuk dicocokkan. Disimpan di node bersama /sharedDevices supaya
        // perangkat yang mau konek bisa login pakai akun apa saja -- jadi
        // kata sandi per perangkat (kalau diaktifkan) adalah satu-satunya
        // penjaga di sini, bukan lagi pemisahan akun -- KECUALI akun admin
        // ([DeviceStore.ADMIN_EMAIL]), yang selalu diizinkan langsung tanpa
        // perlu kata sandi atau fitur "hubungkan tanpa kode" diaktifkan.
        val deviceId = DeviceStore.deviceId(this)
        com.google.firebase.database.FirebaseDatabase.getInstance(
            "https://apk-maker-ceb1f-default-rtdb.asia-southeast1.firebasedatabase.app"
        ).reference
            .child("sharedDevices").child(deviceId)
            .get()
            .addOnSuccessListener { snapshot ->
                val isAdmin = requesterEmail.equals(DeviceStore.ADMIN_EMAIL, ignoreCase = true)
                val quickConnectEnabled = snapshot.child("enabled").getValue(Boolean::class.java) ?: false
                val requiresPassword = snapshot.child("requiresPassword").getValue(Boolean::class.java) ?: true
                val savedHash = snapshot.child("passwordHash").getValue(String::class.java)
                DeviceStore.clearIncomingRequest(this)

                val allowed = when {
                    isAdmin -> true
                    !quickConnectEnabled -> false
                    requiresPassword -> savedHash != null && savedHash == passwordHash
                    else -> true
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
