package com.byhasstudio.remotectl

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class QuickConnectSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_connect)

        val switchEnable = findViewById<Switch>(R.id.switchEnable)
        val radioMode = findViewById<RadioGroup>(R.id.radioMode)
        val editPassword = findViewById<EditText>(R.id.editPassword)
        val status = findViewById<TextView>(R.id.txtStatus)

        fun updatePasswordFieldVisibility() {
            val needsPassword = radioMode.checkedRadioButtonId == R.id.radioWithPassword
            editPassword.visibility = if (needsPassword) android.view.View.VISIBLE else android.view.View.GONE
        }
        radioMode.setOnCheckedChangeListener { _, _ -> updatePasswordFieldVisibility() }
        updatePasswordFieldVisibility()

        findViewById<MaterialButton>(R.id.btnBattery).setOnClickListener {
            val powerManager = getSystemService(android.os.PowerManager::class.java)
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                status.text = "Sudah dikecualikan dari optimasi baterai."
                status.setTextColor(getColor(R.color.success))
            }
        }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            if (switchEnable.isChecked) {
                val requiresPassword = radioMode.checkedRadioButtonId == R.id.radioWithPassword
                if (requiresPassword) {
                    val pass = editPassword.text.toString()
                    if (pass.length < 6) {
                        status.text = "Kata sandi minimal 6 karakter"
                        status.setTextColor(getColor(R.color.danger))
                        return@setOnClickListener
                    }
                    DeviceStore.enableQuickConnect(this, true, pass)
                } else {
                    DeviceStore.enableQuickConnect(this, false, null)
                }
                QuickConnectPrefs.setEnabled(this, true)
                androidx.core.content.ContextCompat.startForegroundService(this, Intent(this, PresenceService::class.java))
                status.text = if (requiresPassword)
                    "Aktif dengan kata sandi. Perangkat ini sekarang muncul di daftar perangkatmu."
                else
                    "Aktif tanpa kata sandi. Tinggal pilih perangkat ini dari daftar lalu Hubungkan, langsung konek."
                status.setTextColor(getColor(R.color.success))
            } else {
                QuickConnectPrefs.setEnabled(this, false)
                DeviceStore.disableQuickConnect(this)
                stopService(Intent(this, PresenceService::class.java))
                status.text = "Dinonaktifkan."
                status.setTextColor(getColor(R.color.text_secondary))
            }
        }
    }
}
