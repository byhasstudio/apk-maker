package com.byhasstudio.remotectl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Base64
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.ValueEventListener
import kotlin.math.hypot

/**
 * Berjalan di perangkat PENGONTROL. Menyambung ke sesi lewat kode 6
 * digit, menampilkan cuplikan layar yang dikirim host, dan menerjemahkan
 * sentuhan pengguna di layar menjadi perintah tap/swipe yang dikirim
 * balik ke host lewat Firebase Realtime Database.
 *
 * Mendukung zoom: cubit 2 jari untuk memperbesar/perkecil dan geser
 * tampilan (zoom murni lokal, tidak mengirim perintah apa pun ke host),
 * sentuhan 1 jari tetap dikirim sebagai tap/swipe seperti biasa -- posisi
 * dihitung ulang lewat matrix zoom yang sedang aktif supaya tetap akurat
 * walau sedang di-zoom. Ketuk 2x cepat untuk reset ke tampilan awal.
 */
class ControlActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRECONNECT_CODE = "preconnect_code"
        private const val MIN_ZOOM = 1f
        private const val MAX_ZOOM = 5f
    }

    private var sessionCode: String? = null
    private var remoteWidth = 0
    private var remoteHeight = 0

    private var frameListener: ValueEventListener? = null
    private var metaListener: ValueEventListener? = null

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var isGesture = false

    // Matrix tampilan: hasil kali base-fit (gambar penuh di tengah layar)
    // dengan zoom/pan tambahan dari gestur pengguna.
    private val matrix = Matrix()
    private var zoom = 1f
    private var lastImgW = -1
    private var lastImgH = -1

    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        val editCode = findViewById<EditText>(R.id.editCode)
        val imgScreen = findViewById<ImageView>(R.id.imgScreen)
        val status = findViewById<TextView>(R.id.txtControlStatus)

        scaleDetector = ScaleGestureDetector(this, ScaleListener(imgScreen))
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetZoom(imgScreen)
                return true
            }
        })

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

        findViewById<android.widget.TextView>(R.id.txtSavedDevices).setOnClickListener {
            startActivity(android.content.Intent(this, DeviceListActivity::class.java))
        }

        // Dibuka dari DeviceListActivity (hubungkan tanpa kode): kode
        // sesi sudah dibuat dan dikirim ke perangkat tujuan lewat
        // permintaan konek, tinggal menunggu perangkat itu menerima.
        val preconnectCode = intent.getStringExtra(EXTRA_PRECONNECT_CODE)
        if (preconnectCode != null) {
            editCode.setText(preconnectCode)
            status.text = "Menunggu perangkat menerima permintaan..."
            connect(preconnectCode, status)
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
                runOnUiThread { onNewFrame(bitmap) }
            }
        }
    }

    private fun onNewFrame(bitmap: Bitmap) {
        val imageView = findViewById<ImageView>(R.id.imgScreen)
        imageView.setImageBitmap(bitmap)

        // Hanya reset matrix ke posisi fit-tengah kalau ini cuplikan
        // pertama atau dimensinya berubah -- supaya posisi zoom/pan yang
        // sedang dipakai pengguna tidak ter-reset tiap cuplikan baru masuk.
        if (bitmap.width != lastImgW || bitmap.height != lastImgH) {
            lastImgW = bitmap.width
            lastImgH = bitmap.height
            imageView.post { resetZoom(imageView) }
        } else {
            imageView.imageMatrix = matrix
        }
    }

    private fun resetZoom(imageView: ImageView) {
        val d = imageView.drawable ?: return
        val viewW = imageView.width.toFloat()
        val viewH = imageView.height.toFloat()
        val imgW = d.intrinsicWidth.toFloat()
        val imgH = d.intrinsicHeight.toFloat()
        if (viewW <= 0 || viewH <= 0 || imgW <= 0 || imgH <= 0) return

        val fitScale = minOf(viewW / imgW, viewH / imgH)
        val dx = (viewW - imgW * fitScale) / 2f
        val dy = (viewH - imgH * fitScale) / 2f

        matrix.reset()
        matrix.postScale(fitScale, fitScale)
        matrix.postTranslate(dx, dy)
        zoom = 1f
        imageView.imageMatrix = matrix
    }

    private inner class ScaleListener(private val imageView: ImageView) :
        ScaleGestureDetector.SimpleOnScaleGestureListener() {

        private var lastFocusX = 0f
        private var lastFocusY = 0f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newZoom = (zoom * detector.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
            val appliedFactor = newZoom / zoom
            zoom = newZoom

            matrix.postScale(appliedFactor, appliedFactor, detector.focusX, detector.focusY)
            matrix.postTranslate(detector.focusX - lastFocusX, detector.focusY - lastFocusY)
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY

            imageView.imageMatrix = matrix
            return true
        }
    }

    private fun handleTouch(imageView: ImageView, event: MotionEvent) {
        val code = sessionCode ?: return
        if (remoteWidth == 0 || remoteHeight == 0) return
        if (imageView.drawable == null) return

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Jari kedua turun -- ini gestur zoom/pan, bukan niat tap/swipe.
                isGesture = true
            }
            MotionEvent.ACTION_DOWN -> {
                isGesture = false
                val host = toHostCoords(imageView, event.x, event.y) ?: return
                downX = host.first
                downY = host.second
                downTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                if (isGesture || event.pointerCount > 1) {
                    isGesture = false
                    return
                }
                val host = toHostCoords(imageView, event.x, event.y) ?: return
                val (hostX, hostY) = host
                val duration = System.currentTimeMillis() - downTime
                val moved = hypot((hostX - downX).toDouble(), (hostY - downY).toDouble())
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

    /** Ubah titik di koordinat layar (view) jadi koordinat layar asli host, lewat inverse matrix zoom saat ini. */
    private fun toHostCoords(imageView: ImageView, viewX: Float, viewY: Float): Pair<Float, Float>? {
        val drawable = imageView.drawable ?: return null
        val imgW = drawable.intrinsicWidth.toFloat()
        val imgH = drawable.intrinsicHeight.toFloat()
        if (imgW <= 0 || imgH <= 0) return null

        val inverse = Matrix()
        if (!matrix.invert(inverse)) return null
        val pts = floatArrayOf(viewX, viewY)
        inverse.mapPoints(pts)

        val imgX = pts[0].coerceIn(0f, imgW)
        val imgY = pts[1].coerceIn(0f, imgH)

        return Pair(imgX / imgW * remoteWidth, imgY / imgH * remoteHeight)
    }

    override fun onDestroy() {
        super.onDestroy()
        val code = sessionCode ?: return
        frameListener?.let { SessionRelay.removeListener(code, "frame", it) }
        metaListener?.let { SessionRelay.removeListener(code, "meta", it) }
    }
}
