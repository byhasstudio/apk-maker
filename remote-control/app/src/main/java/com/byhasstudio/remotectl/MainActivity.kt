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
        setContentView(R.layout.activity_main)

        // Masuk anonim ke Firebase supaya aturan keamanan database
        // bisa mensyaratkan pengguna yang masuk (bukan asal publik).
        if (FirebaseAuth.getInstance().currentUser == null) {
            FirebaseAuth.getInstance().signInAnonymously()
        }

        findViewById<android.widget.Button>(R.id.btnHost).setOnClickListener {
            startActivity(Intent(this, HostActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnControl).setOnClickListener {
            startActivity(Intent(this, ControlActivity::class.java))
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
