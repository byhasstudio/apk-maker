package com.byhasstudio.remotectl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.ValueEventListener

/**
 * Berjalan di perangkat PENGONTROL. Menyambung ke sesi lewat kode 6
 * digit, menampilkan cuplikan layar yang dikirim host, dan menerjemahkan
 * sentuhan pengguna di layar menjadi perintah tap/swipe yang dikirim
 * balik ke host lewat Firebase Realtime Database.
 */
class ControlActivity : AppCompatActivity() {

    private var sessionCode: String? = null
    private var remoteWidth = 0
    private var remoteHeight = 0

    private var frameListener: ValueEventListener? = null
    private var metaListener: ValueEventListener? = null

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        val editCode = findViewById<EditText>(R.id.editCode)
        val imgScreen = findViewById<ImageView>(R.id.imgScreen)
        val status = findViewById<TextView>(R.id.txtControlStatus)

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            val code = editCode.text.toString().trim()
            if (code.length != 6) {
                status.text = "Masukkan kode 6 digit yang valid"
                return@setOnClickListener
            }
            connect(code, status)
        }

        imgScreen.setOnTouchListener { view, event ->
            handleTouch(view as ImageView, event)
            true
        }
    }

    private fun connect(code: String, status: TextView) {
        sessionCode = code
        status.text = "Menyambung ke $code..."

        metaListener = SessionRelay.listenMeta(code) { w, h ->
            remoteWidth = w
            remoteHeight = h
            status.text = "Tersambung ($w x $h)"
        }

        frameListener = SessionRelay.listenFrames(code) { b64 ->
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                runOnUiThread { findViewById<ImageView>(R.id.imgScreen).setImageBitmap(bitmap) }
            }
        }
    }

    private fun handleTouch(imageView: ImageView, event: MotionEvent) {
        val code = sessionCode ?: return
        if (remoteWidth == 0 || remoteHeight == 0) return

        // Konversi koordinat dari area ImageView (yang memakai fitCenter)
        // ke koordinat layar asli perangkat host.
        val drawable = imageView.drawable ?: return
        val viewW = imageView.width.toFloat()
        val viewH = imageView.height.toFloat()
        val imgW = drawable.intrinsicWidth.toFloat()
        val imgH = drawable.intrinsicHeight.toFloat()
        if (imgW <= 0 || imgH <= 0) return

        val scale = minOf(viewW / imgW, viewH / imgH)
        val offsetX = (viewW - imgW * scale) / 2f
        val offsetY = (viewH - imgH * scale) / 2f

        val relX = ((event.x - offsetX) / scale).coerceIn(0f, imgW)
        val relY = ((event.y - offsetY) / scale).coerceIn(0f, imgH)

        val hostX = relX / imgW * remoteWidth
        val hostY = relY / imgH * remoteHeight

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = hostX
                downY = hostY
                downTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val duration = System.currentTimeMillis() - downTime
                val moved = kotlin.math.hypot((hostX - downX).toDouble(), (hostY - downY).toDouble())
                if (moved < 15) {
                    SessionRelay.sendCommand(code, mapOf("type" to "tap", "x" to downX, "y" to downY))
                } else {
                    SessionRelay.sendCommand(
                        code,
                        mapOf(
                            "type" to "swipe",
                            "x" to downX, "y" to downY,
                            "x2" to hostX, "y2" to hostY,
                            "durationMs" to duration.coerceAtLeast(50)
                        )
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val code = sessionCode ?: return
        frameListener?.let { SessionRelay.removeListener(code, "frame", it) }
        metaListener?.let { SessionRelay.removeListener(code, "meta", it) }
    }
}
