package com.byhasstudio.remotectl

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

/**
 * Login username/kata sandi biasa (bebas ketik, bukan email Gmail).
 * Satu layar yang tampilannya berubah tergantung mode -- "Masuk" dan
 * "Daftar" punya judul, teks tombol, dan link toggle yang beda, supaya
 * kelihatan jelas lagi di mode mana, tanpa perlu dua layout terpisah.
 *
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

    // Mode saat ini -- false = Masuk (login), true = Daftar (buat akun baru).
    private var isRegisterMode = false

    // Status intip kata sandi -- false = disembunyikan (default).
    private var isPasswordVisible = false

    private lateinit var txtTitle: TextView
    private lateinit var txtSubtitle: TextView
    private lateinit var editUsername: EditText
    private lateinit var editPassword: EditText
    private lateinit var btnTogglePassword: TextView
    private lateinit var txtError: TextView
    private lateinit var btnPrimary: MaterialButton
    private lateinit var txtToggleMode: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        txtTitle = findViewById(R.id.txtTitle)
        txtSubtitle = findViewById(R.id.txtSubtitle)
        editUsername = findViewById(R.id.editUsername)
        editPassword = findViewById(R.id.editPassword)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        txtError = findViewById(R.id.txtError)
        btnPrimary = findViewById(R.id.btnPrimary)
        txtToggleMode = findViewById(R.id.txtToggleMode)

        applyModeToViews()

        btnPrimary.setOnClickListener {
            doAuth(editUsername.text.toString(), editPassword.text.toString())
        }
        txtToggleMode.setOnClickListener {
            isRegisterMode = !isRegisterMode
            txtError.visibility = android.view.View.GONE
            applyModeToViews()
        }
        btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            applyPasswordVisibility()
        }
    }

    /** Ganti judul, subjudul, teks tombol, dan link toggle sesuai mode saat ini. */
    private fun applyModeToViews() {
        if (isRegisterMode) {
            txtTitle.text = "Buat akun baru"
            txtSubtitle.text = "Satu akun ini dipakai di semua perangkatmu supaya fitur \"hubungkan tanpa kode\" bisa jalan"
            btnPrimary.text = "Daftar"
            txtToggleMode.text = "Sudah punya akun? Masuk"
        } else {
            txtTitle.text = "Masuk ke akun"
            txtSubtitle.text = "Dipakai supaya daftar perangkatmu hanya terlihat olehmu sendiri"
            btnPrimary.text = "Masuk"
            txtToggleMode.text = "Belum punya akun? Daftar"
        }
    }

    /** Tampilkan/sembunyikan karakter kata sandi, kursor tetap di akhir teks. */
    private fun applyPasswordVisibility() {
        val selection = editPassword.text?.length ?: 0
        editPassword.inputType = if (isPasswordVisible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        editPassword.setSelection(selection)
        btnTogglePassword.text = if (isPasswordVisible) "🙈" else "👁"
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun doAuth(username: String, password: String) {
        txtError.visibility = android.view.View.GONE
        if (username.isBlank()) {
            showError("Masukkan username")
            return
        }
        if (password.length < 6) {
            showError("Kata sandi minimal 6 karakter")
            return
        }

        val fakeEmail = usernameToFakeEmail(username)
        val task = if (isRegisterMode) {
            auth.createUserWithEmailAndPassword(fakeEmail, password)
        } else {
            auth.signInWithEmailAndPassword(fakeEmail, password)
        }
        task.addOnSuccessListener { goToMain() }
            .addOnFailureListener { e -> showError(friendlyAuthError(e)) }
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

    private fun showError(message: String) {
        txtError.text = message
        txtError.visibility = android.view.View.VISIBLE
    }
}
