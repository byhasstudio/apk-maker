package com.byhasstudio.remotectl

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Activity yang berjalan di perangkat yang AKAN DIKONTROL.
 * Menampilkan kode sesi, meminta izin rekam layar, lalu menjalankan
 * ScreenCaptureService di latar depan. Perintah sentuh/geser dari
 * controller dieksekusi oleh RemoteAccessibilityService secara terpisah,
 * selama layanan Accessibility sudah diaktifkan pengguna di Pengaturan.
 */
class HostActivity : AppCompatActivity() {

    private lateinit var sessionCode: String
    private lateinit var projectionManager: MediaProjectionManager

    private val captureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenCaptureService.EXTRA_SESSION_CODE, sessionCode)
            }
            ContextCompat.startForegroundService(this, intent)
            findViewById<TextView>(R.id.txtStatus).text =
                "Aktif. Bagikan kode ini ke perangkat pengontrol."
        } else {
            findViewById<TextView>(R.id.txtStatus).text =
                "Izin rekam layar ditolak. Tidak bisa berbagi layar."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)

        sessionCode = SessionRelay.generateCode()
        findViewById<TextView>(R.id.txtCode).text = sessionCode

        projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        findViewById<Button>(R.id.btnGrantAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<TextView>(R.id.txtStatus).text = "Meminta izin rekam layar..."
        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    override fun onDestroy() {
        super.onDestroy()
        SessionRelay.endSession(sessionCode)
        stopService(Intent(this, ScreenCaptureService::class.java))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "${packageName}/${RemoteAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
            .asSequence().contains(expected)
    }
}

private fun TextUtils.SimpleStringSplitter.asSequence(): Sequence<String> = sequence {
    while (hasNext()) yield(next())
}
