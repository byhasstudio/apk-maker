package com.byhasstudio.remotectl

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Activity transparan yang dibuka otomatis oleh PresenceService saat
 * ada permintaan konek dengan kata sandi yang cocok -- bukan dibuka
 * manual oleh pengguna. Tujuannya cuma satu: memicu dialog izin rekam
 * layar bawaan Android ("Mulai merekam atau mentransmisikan?").
 *
 * Android TIDAK mengizinkan aplikasi mana pun melewati dialog ini
 * secara diam-diam -- ini pagar keamanan sistem supaya aplikasi tidak
 * bisa diam-diam merekam layar tanpa sepengetahuan pemilik perangkat.
 * Jadi pemilik perangkat tetap akan melihat (dan perlu menyentuh
 * "Mulai sekarang") satu dialog singkat ini, walau tidak membuka
 * aplikasi Remote Ctl secara manual.
 */
class CaptureRequestActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_CODE = "session_code"
    }

    private lateinit var sessionCode: String

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
            Toast.makeText(this, "Kontrol jarak jauh dimulai", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent.getStringExtra(EXTRA_SESSION_CODE)
        if (code.isNullOrBlank()) {
            finish()
            return
        }
        sessionCode = code
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
