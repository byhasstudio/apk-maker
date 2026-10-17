package com.apkmaker.remote

/*
 * RemoteControlModule.kt
 * ------------------------------------------------------------------
 * Satu file berisi semua yang dibutuhkan untuk fitur remote-control
 * ala AnyDesk: screen capture (host), input injection (host), dan
 * koneksi WebRTC (host <-> viewer). Ditempel ke proyek builder/ dan
 * dipanggil dari WebView lewat JavascriptInterface, mengikuti pola
 * yang sudah dipakai untuk fitur "overlay" / "island" di apk-maker.
 *
 * CARA PASANG (ringkas):
 * 1. Taruh file ini di: app/src/main/java/com/apkmaker/remote/
 *    (sesuaikan package di baris atas kalau package app kamu beda)
 * 2. Di AndroidManifest.xml tambahkan (hanya aktif kalau toggle
 *    screen_capture / accessibility dinyalakan saat build):
 *      <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 *      <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
 *      <uses-permission android:name="android.permission.INTERNET" />
 *      <service android:name=".remote.ScreenCaptureService"
 *          android:foregroundServiceType="mediaProjection" android:exported="false" />
 *      <service android:name=".remote.RemoteAccessibilityService"
 *          android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
 *          android:exported="false">
 *        <intent-filter><action android:name="android.accessibilityservice.AccessibilityService" /></intent-filter>
 *        <meta-data android:name="android.accessibilityservice"
 *          android:resource="@xml/accessibility_service_config" />
 *      </service>
 * 3. Di Activity utama (yang punya WebView), daftarkan bridge:
 *      webView.addJavascriptInterface(RemoteControlBridge(this, webView), "RemoteControl")
 * 4. Dari sisi web/JS (index.html kamu) panggil:
 *      RemoteControl.startHost(roomId)   // device yang DIKONTROL
 *      RemoteControl.startViewer(roomId) // device yang MENGONTROL
 *      RemoteControl.stop()
 *
 * CATATAN PENTING:
 * - Dependency WebRTC (org.webrtc:google-webrtc) HARUS ditambahkan
 *   di build.gradle modul app. File ini pakai kelas-kelas dari lib
 *   itu (PeerConnectionFactory, dll).
 * - MediaProjection wajib di-trigger dari izin yang user approve
 *   lewat dialog sistem (tidak bisa otomatis) -> makanya ada
 *   requestScreenCapturePermission() yang harus dipanggil dari
 *   Activity, bukan dari Service langsung.
 * - AccessibilityService wajib diaktifkan manual oleh user di
 *   Settings > Aksesibilitas (tidak bisa auto-enable oleh app lain,
 *   ini pembatasan dari Android sendiri untuk alasan keamanan).
 * - Bagian signaling (tukar SDP offer/answer antar device) di sini
 *   pakai WebSocket sederhana ke endpoint Cloudflare Worker kamu;
 *   sesuaikan SIGNAL_URL ke endpoint Durable Object signaling kamu.
 * ------------------------------------------------------------------
 */

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.webrtc.*
import org.json.JSONObject
import okhttp3.*
import okio.ByteString

// =====================================================================
// 1) BRIDGE — dipanggil dari JavaScript lewat window.RemoteControl.*
// =====================================================================
class RemoteControlBridge(private val activity: Activity, private val webView: WebView) {

    companion object {
        const val REQ_SCREEN_CAPTURE = 9001
    }

    private var pendingRoomId: String? = null
    private var isHost = false

    /** Dipanggil dari JS di device yang MAU DIKONTROL (host). */
    @JavascriptInterface
    fun startHost(roomId: String) {
        pendingRoomId = roomId
        isHost = true
        activity.runOnUiThread {
            val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            activity.startActivityForResult(mpm.createScreenCaptureIntent(), REQ_SCREEN_CAPTURE)
        }
    }

    /** Dipanggil dari JS di device yang MAU MENGONTROL (viewer). */
    @JavascriptInterface
    fun startViewer(roomId: String) {
        val intent = Intent(activity, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START_VIEWER
            putExtra(ScreenCaptureService.EXTRA_ROOM_ID, roomId)
        }
        ContextCompatStartForegroundService(activity, intent)
    }

    @JavascriptInterface
    fun stop() {
        activity.stopService(Intent(activity, ScreenCaptureService::class.java))
    }

    @JavascriptInterface
    fun isAccessibilityEnabled(): Boolean = RemoteAccessibilityService.instance != null

    @JavascriptInterface
    fun openAccessibilitySettings() {
        activity.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** Panggil ini dari onActivityResult() Activity utama. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_SCREEN_CAPTURE && data != null) {
            val intent = Intent(activity, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START_HOST
                putExtra(ScreenCaptureService.EXTRA_ROOM_ID, pendingRoomId)
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            ContextCompatStartForegroundService(activity, intent)
        }
    }

    private fun ContextCompatStartForegroundService(ctx: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent) else ctx.startService(intent)
    }
}

// =====================================================================
// 2) SERVICE — screen capture (host) + koneksi WebRTC + signaling
// =====================================================================
class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START_HOST = "start_host"
        const val ACTION_START_VIEWER = "start_viewer"
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        // Ganti ke endpoint signaling Cloudflare Worker (Durable Object) kamu
        const val SIGNAL_URL = "wss://apk-maker.byhasstudio.workers.dev/signal"
        const val CHANNEL_ID = "remote_control_channel"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var ws: WebSocket? = null
    private var roomId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        when (intent?.action) {
            ACTION_START_HOST -> {
                roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultData != null) startHostCapture(resultCode, resultData)
            }
            ACTION_START_VIEWER -> {
                roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""
                startViewerConnection()
            }
        }
        return START_NOT_STICKY
    }

    // ---------- HOST: capture layar + kirim lewat WebRTC ----------
    private fun startHostCapture(resultCode: Int, resultData: Intent) {
        initPeerConnectionFactory()
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, resultData)

        val metrics = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)

        val eglBase = EglBase.create()
        videoCapturer = ScreenCapturerAndroid(resultData, object : MediaProjection.Callback() {})
        val surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val videoSource = peerConnectionFactory!!.createVideoSource(false)
        videoCapturer!!.initialize(surfaceHelper, applicationContext, videoSource.capturerObserver)
        videoCapturer!!.startCapture(metrics.widthPixels, metrics.heightPixels, 30)

        val videoTrack = peerConnectionFactory!!.createVideoTrack("screen0", videoSource)

        openSignaling { pc ->
            pc.addTrack(videoTrack, listOf("stream0"))
            createOfferAndSend(pc)
        }
    }

    // ---------- VIEWER: terima stream + kirim event sentuh ----------
    private fun startViewerConnection() {
        initPeerConnectionFactory()
        openSignaling { pc ->
            // Data channel buat kirim koordinat tap/swipe ke host
            val dc = pc.createDataChannel("input", DataChannel.Init())
            dc.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(p0: Long) {}
                override fun onStateChange() {}
                override fun onMessage(buffer: DataChannel.Buffer) {}
            })
            // dc.send(...) dipanggil dari bridge saat user viewer nge-tap layar video
        }
    }

    private fun initPeerConnectionFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext).createInitializationOptions()
        )
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    // ---------- Signaling sederhana lewat WebSocket ke Worker ----------
    private fun openSignaling(onReady: (PeerConnection) -> Unit) {
        val client = OkHttpClient()
        val req = Request.Builder().url("$SIGNAL_URL?room=$roomId").build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val rtcConfig = PeerConnection.RTCConfiguration(
                    listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
                )
                peerConnection = peerConnectionFactory!!.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                    override fun onIceCandidate(c: IceCandidate) {
                        val msg = JSONObject().put("type", "candidate").put("candidate", c.sdp)
                            .put("sdpMid", c.sdpMid).put("sdpMLineIndex", c.sdpMLineIndex)
                        webSocket.send(msg.toString())
                    }
                    override fun onDataChannel(dc: DataChannel) {}
                    override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {}
                    override fun onSignalingChange(s: PeerConnection.SignalingState) {}
                    override fun onIceConnectionReceivingChange(b: Boolean) {}
                    override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
                    override fun onAddStream(stream: MediaStream) {}
                    override fun onRemoveStream(stream: MediaStream) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onTrack(transceiver: RtpTransceiver) {}
                }) ?: return
                onReady(peerConnection!!)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JSONObject(text)
                when (json.optString("type")) {
                    "answer" -> peerConnection?.setRemoteDescription(SimpleSdpObserver(),
                        SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp")))
                    "offer" -> peerConnection?.setRemoteDescription(SimpleSdpObserver(),
                        SessionDescription(SessionDescription.Type.OFFER, json.getString("sdp")))
                    "candidate" -> peerConnection?.addIceCandidate(
                        IceCandidate(json.getString("sdpMid"), json.getInt("sdpMLineIndex"), json.getString("candidate")))
                }
            }
        })
    }

    private fun createOfferAndSend(pc: PeerConnection) {
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc.setLocalDescription(SimpleSdpObserver(), desc)
                ws?.send(JSONObject().put("type", "offer").put("sdp", desc.description).toString())
            }
        }, MediaConstraints())
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Remote Control", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote control aktif")
            .setContentText("Layar sedang dibagikan / dikontrol")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    override fun onDestroy() {
        videoCapturer?.stopCapture()
        mediaProjection?.stop()
        virtualDisplay?.release()
        peerConnection?.close()
        ws?.close(1000, "stop")
        super.onDestroy()
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}

// =====================================================================
// 3) ACCESSIBILITY SERVICE — inject tap/swipe di device HOST
//    (dipicu dari data channel saat terima koordinat dari viewer)
// =====================================================================
class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        var instance: RemoteAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** Dipanggil saat terima pesan "tap x y" dari data channel WebRTC. */
    fun injectTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    /** Dipanggil saat terima pesan "swipe x1 y1 x2 y2" dari data channel. */
    fun injectSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 200) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
