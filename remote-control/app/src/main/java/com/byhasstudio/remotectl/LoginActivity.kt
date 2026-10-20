package com.byhasstudio.remotectl

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

/**
 * Login username/kata sandi biasa (bebas ketik, bukan email Gmail).
 * Dipakai supaya daftar "perangkat tersimpan" (fitur hubungkan tanpa
 * kode) hanya kelihatan dan bisa diakses oleh akun yang sama -- bukan
 * sekadar disaring di aplikasi, tapi memang dikunci di aturan keamanan
 * database (lihat database.rules.json).
 *
 * Firebase Auth di baliknya tetap jalan lewat email/password seperti
 * biasa, tapi username yang diketik user diubah otomatis jadi alamat
 * email palsu internal (mis. "hass" -> "hass@remotectl.local") supaya
 * tidak perlu minta email Gmail asli sama sekali. Ini aman karena
 * aturan database hanya mengecek "auth != null", bukan alamat emailnya.
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        // Domain palsu dipakai internal supaya Firebase Auth (yang
        // sebetulnya berbasis email/password) bisa menerima username
        // bebas tanpa minta email Gmail asli.
        private const val FAKE_EMAIL_DOMAIN = "@remotectl.local"

        /** Ubah username bebas jadi "email" internal yang valid buat Firebase. */
        private fun usernameToFakeEmail(username: String): String {
            val sanitized = username.trim().lowercase()
                .replace(Regex("[^a-z0-9._-]"), "_")
            return "$sanitized$FAKE_EMAIL_DOMAIN"
        }
    }

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val username = findViewById<EditText>(R.id.editUsername)
        val password = findViewById<EditText>(R.id.editPassword)
        val error = findViewById<TextView>(R.id.txtError)

        findViewById<MaterialButton>(R.id.btnLogin).setOnClickListener {
            doAuth(username.text.toString(), password.text.toString(), error) { e, p ->
                auth.signInWithEmailAndPassword(e, p)
            }
        }
        findViewById<MaterialButton>(R.id.btnRegister).setOnClickListener {
            doAuth(username.text.toString(), password.text.toString(), error) { e, p ->
                auth.createUserWithEmailAndPassword(e, p)
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun doAuth(
        username: String,
        password: String,
        errorView: TextView,
        action: (String, String) -> com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult>
    ) {
        errorView.visibility = android.view.View.GONE
        if (username.isBlank()) {
            showError(errorView, "Masukkan username")
            return
        }
        if (password.length < 6) {
            showError(errorView, "Kata sandi minimal 6 karakter")
            return
        }

        action(usernameToFakeEmail(username), password)
            .addOnSuccessListener { goToMain() }
            .addOnFailureListener { e -> showError(errorView, friendlyAuthError(e)) }
    }

    /**
     * Kalau SEMUA cara login (Masuk, Daftar) gagal sama-sama,
     * penyebab yang paling sering adalah provider "Email/Password" belum
     * diaktifkan di Firebase Console -- Authentication -> Sign-in method
     * (beda dari provider Anonymous yang diaktifkan waktu setup awal).
     * Pesan error bawaan Firebase untuk kasus ini ("CONFIGURATION_NOT_FOUND"
     * / "an internal error has occurred") membingungkan, jadi di sini
     * diterjemahkan.
     */
    private fun friendlyAuthError(e: Exception): String {
        val raw = e.message ?: ""
        return when {
            raw.contains("CONFIGURATION_NOT_FOUND") || raw.contains("operation-not-allowed", true) ->
                "Provider Email/Password belum aktif di Firebase Console (Authentication -> Sign-in method). Lihat README bagian setup."
            raw.contains("network error", true) ->
                "Tidak ada koneksi internet. Coba lagi."
            raw.contains("badly formatted", true) || raw.contains("invalid-email", true) ->
                "Username mengandung karakter yang tidak didukung. Coba username lain."
            else -> e.localizedMessage ?: "Gagal masuk. Coba lagi."
        }
    }

    private fun showError(view: TextView, message: String) {
        view.text = message
        view.visibility = android.view.View.VISIBLE
    }
}
