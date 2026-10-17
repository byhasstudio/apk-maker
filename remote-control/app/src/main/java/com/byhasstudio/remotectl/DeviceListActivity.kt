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
 * "hubungkan tanpa kode". Pilih satu, masukkan kata sandinya, dan
 * aplikasi otomatis menyambung tanpa perlu menyalin kode 6 digit
 * secara manual.
 */
class DeviceListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        val container = findViewById<LinearLayout>(R.id.deviceContainer)
        val empty = findViewById<TextView>(R.id.txtEmpty)

        DeviceStore.listMyDevices { devices ->
            runOnUiThread {
                container.removeAllViews()
                empty.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
                for ((deviceId, name, online) in devices) {
                    container.addView(buildDeviceRow(deviceId, name, online))
                }
            }
        }
    }

    private fun buildDeviceRow(deviceId: String, name: String, online: Boolean): View {
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
            text = name
            setTextColor(getColor(R.color.text_primary))
            textSize = 15f
        })
        info.addView(TextView(this).apply {
            text = if (online) "● Online" else "○ Offline"
            setTextColor(getColor(if (online) R.color.success else R.color.text_secondary))
            textSize = 12f
        })

        val btn = MaterialButton(this).apply {
            text = "Hubungkan"
            isEnabled = online
            setOnClickListener { promptPasswordAndConnect(deviceId, name) }
        }

        row.addView(info)
        row.addView(btn)
        return row
    }

    private fun promptPasswordAndConnect(deviceId: String, name: String) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Kata sandi $name"
        }
        AlertDialog.Builder(this)
            .setTitle("Hubungkan ke $name")
            .setView(input)
            .setPositiveButton("Sambung") { _, _ ->
                val password = input.text.toString()
                if (password.length < 6) return@setPositiveButton
                val code = SessionRelay.generateCode()
                DeviceStore.requestConnect(deviceId, code, password)

                val intent = Intent(this, ControlActivity::class.java)
                intent.putExtra(ControlActivity.EXTRA_PRECONNECT_CODE, code)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
