package com.byhasstudio.remotectl

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Perlu akun sungguhan (bukan login anonim) supaya fitur
        // "hubungkan tanpa kode" bisa dikunci khusus ke akun ini lewat
        // aturan keamanan database.
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        findViewById<android.widget.Button>(R.id.btnHost).setOnClickListener {
            startActivity(Intent(this, HostActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnControl).setOnClickListener {
            startActivity(Intent(this, ControlActivity::class.java))
        }
        findViewById<android.widget.TextView>(R.id.txtSettings).setOnClickListener {
            startActivity(Intent(this, QuickConnectSettingsActivity::class.java))
        }

        checkForUpdate()
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val info = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME) ?: return@launch
            if (isFinishing || isDestroyed) return@launch
            UpdateDialog.show(this@MainActivity, info) {
                UpdateChecker.downloadAndInstall(this@MainActivity, info)
            }
        }
    }
}
