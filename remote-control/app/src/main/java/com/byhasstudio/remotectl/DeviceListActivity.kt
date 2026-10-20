package com.byhasstudio.remotectl

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * Daftar perangkat (milik akun yang sama) yang sudah mengaktifkan
 * "hubungkan tanpa kode". Pilih satu -- kalau perangkat itu diset
 * tanpa kata sandi, langsung tersambung; kalau pakai kata sandi,
 * diminta masukkan dulu. Keduanya tanpa perlu menyalin kode 6 digit
 * secara manual.
 */
class DeviceListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        val container = findViewById<LinearLayout>(R.id.deviceContainer)
        val empty = findViewById<TextView>(R.id.txtEmpty)
        val error = findViewById<TextView>(R.id.txtError)

        // Listener langsung (bukan baca sekali) supaya daftar ini ikut
        // update kalau status online berubah selagi layar ini terbuka --
        // dan status "online" cuma sinyal mentah berdasar heartbeat tiap
        // 30 detik, bukan jaminan pasti. Tombol Hubungkan TIDAK dikunci
        // berdasar status ini (lihat buildDeviceRow) karena kalau status
        // itu sedikit telat, pengguna jadi terkunci balik ke kode manual
        // padahal perangkatnya sebenarnya siap.
        DeviceStore.listenMyDevices(
            onResult = { devices ->
                runOnUiThread {
                    container.removeAllViews()
                    empty.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
                    for (device in devices) {
                        container.addView(buildDeviceRow(device))
                    }
                }
            },
            onError = { message ->
                // Daftar kosong TANPA pesan ini biasanya cuma berarti belum
                // ada perangkat yang aktifkan fitur ini. Daftar kosong
                // DENGAN pesan ini berarti server menolak permintaannya --
                // hampir selalu karena database.rules.json belum ter-publish
                // dengan benar di Firebase Console.
                runOnUiThread {
                    error.text = "Gagal memuat daftar perangkat dari server: $message\n\n" +
                        "Kemungkinan besar aturan keamanan database (database.rules.json) " +
                        "belum ter-publish dengan benar di Firebase Console -- lihat README."
                    error.visibility = View.VISIBLE
                }
            }
        )
    }

    private fun buildDeviceRow(device: DeviceStore.DeviceEntry): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(28, 28, 28, 28)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(this).apply {
            text = device.name
            setTextColor(getColor(R.color.text_primary))
            textSize = 15f
        })
        val isAdmin = DeviceStore.isAdmin()
        info.addView(TextView(this).apply {
            // "Offline" di sini cuma perkiraan dari heartbeat terakhir,
            // BUKAN status real-time pasti -- jadi ini sekadar info, tombol
            // Hubungkan di bawah tetap aktif walau statusnya kelihatan offline.
            val statusText = if (device.online) "● Online" else "○ Mungkin offline"
            val modeText = when {
                isAdmin && !device.quickConnectEnabled -> " · belum aktifkan fitur ini (akses admin)"
                device.requiresPassword -> ""
                else -> " · tanpa kata sandi"
            }
            text = statusText + modeText
            setTextColor(getColor(if (device.online) R.color.success else R.color.text_secondary))
            textSize = 12f
        })

        val btn = MaterialButton(this).apply {
            text = "Hubungkan"
            setOnClickListener {
                // Akun admin selalu langsung konek tanpa diminta kata sandi
                // sama sekali -- perangkat tujuan yang memutuskan izinkan
                // atau tidak berdasarkan email akun ini (lihat
                // PresenceService.handleRequest di perangkat tujuan).
                if (device.requiresPassword && !isAdmin) {
                    promptPasswordAndConnect(device)
                } else {
                    connectNow(device.id, null)
                }
            }
        }

        row.addView(info)
        row.addView(btn)
        return row
    }

    private fun promptPasswordAndConnect(device: DeviceStore.DeviceEntry) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Kata sandi ${device.name}"
        }
        AlertDialog.Builder(this)
            .setTitle("Hubungkan ke ${device.name}")
            .setView(input)
            .setPositiveButton("Sambung") { _, _ ->
                val password = input.text.toString()
                if (password.length < 6) return@setPositiveButton
                connectNow(device.id, password)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun connectNow(deviceId: String, password: String?) {
        val code = SessionRelay.generateCode()
        DeviceStore.requestConnect(deviceId, code, password)

        val intent = Intent(this, ControlActivity::class.java)
        intent.putExtra(ControlActivity.EXTRA_PRECONNECT_CODE, code)
        startActivity(intent)
        finish()
    }
}
