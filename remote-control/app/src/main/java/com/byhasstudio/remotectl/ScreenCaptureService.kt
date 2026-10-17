package com.byhasstudio.remotectl

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream

/**
 * Menangkap layar perangkat secara berkala (bukan video real-time penuh,
 * melainkan cuplikan JPEG beberapa kali per detik) dan mengirimkannya ke
 * Firebase Realtime Database supaya perangkat pengontrol bisa menampilkannya.
 * Pendekatan ini sengaja dipilih supaya tidak butuh server TURN/STUN
 * terpisah seperti WebRTC murni -- cocok untuk versi pertama yang sederhana.
 */
class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_SESSION_CODE = "session_code"
        private const val CHANNEL_ID = "remotectl_capture"
        private const val NOTIF_ID = 42

        // Resolusi dan kualitas dinaikkan untuk gambar yang lebih tajam.
        // Nilai ini tetap dibatasi supaya ukuran payload per frame masih
        // wajar dikirim lewat Realtime Database (bukan video streaming
        // sungguhan, jadi tidak bisa 4K/60fps tanpa server khusus).
        private const val TARGET_WIDTH = 720
        private const val JPEG_QUALITY = 62
        private const val FRAME_INTERVAL_MS = 120L
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var sessionCode: String
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private var lastSendAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        sessionCode = intent.getStringExtra(EXTRA_SESSION_CODE) ?: return START_NOT_STICKY
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA)

        startForeground(NOTIF_ID, buildNotification())

        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        handler = Handler(handlerThread.looper)

        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        // WAJIB sejak Android 14 (API 34): daftarkan callback sebelum
        // createVirtualDisplay dipanggil, kalau tidak sistem akan
        // melempar IllegalStateException dan proses ini langsung mati.
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, handler)

        try {
            startCapture()
        } catch (t: Throwable) {
            notifyFailure(t)
            stopSelf()
        }
        return START_STICKY
    }

    private fun startCapture() {
        val metrics = DisplayMetrics()
        windowManagerDefaultDisplayMetrics(metrics)

        val scale = TARGET_WIDTH.toFloat() / metrics.widthPixels
        val width = TARGET_WIDTH
        val height = (metrics.heightPixels * scale).toInt().coerceAtLeast(1)

        SessionRelay.publishMeta(sessionCode, metrics.widthPixels, metrics.heightPixels)

        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "RemoteCtlCapture",
            width, height, metrics.densityDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, handler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (now - lastSendAt < FRAME_INTERVAL_MS) {
                    return@setOnImageAvailableListener
                }
                lastSendAt = now

                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)

                val out = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                SessionRelay.publishFrame(sessionCode, b64)

                bitmap.recycle()
                cropped.recycle()
            } catch (t: Throwable) {
                // Satu frame gagal diproses tidak boleh menjatuhkan seluruh
                // layanan -- cukup lewati frame ini dan lanjut ke berikutnya.
            } finally {
                image.close()
            }
        }, handler)

        // Dengarkan perintah sentuh/geser dari controller dan teruskan ke
        // RemoteAccessibilityService lewat broadcast lokal.
        SessionRelay.listenCommands(sessionCode) { snapshot ->
            RemoteAccessibilityService.instance?.handleRemoteCommand(snapshot)
        }
    }

    private fun windowManagerDefaultDisplayMetrics(out: DisplayMetrics) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(out)
    }

    private fun notifyFailure(t: Throwable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Berbagi layar jarak jauh", NotificationManager.IMPORTANCE_HIGH
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gagal mulai berbagi layar")
            .setContentText(t.message ?: t.javaClass.simpleName)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID + 1, notif)
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Berbagi layar jarak jauh", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Berbagi layar aktif")
            .setContentText("Perangkat ini sedang dikontrol dari jarak jauh")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        if (::handlerThread.isInitialized) handlerThread.quitSafely()
    }
}
