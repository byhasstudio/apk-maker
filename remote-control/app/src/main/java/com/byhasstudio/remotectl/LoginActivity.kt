package com.byhasstudio.remotectl

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

/**
 * Login email/kata sandi. Dipakai supaya daftar "perangkat tersimpan"
 * (fitur hubungkan tanpa kode) hanya kelihatan dan bisa diakses oleh
 * akun yang sama -- bukan sekadar disaring di aplikasi, tapi memang
 * dikunci di aturan keamanan database (lihat database.rules.json).
 */
class LoginActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val email = findViewById<EditText>(R.id.editEmail)
        val password = findViewById<EditText>(R.id.editPassword)
        val error = findViewById<TextView>(R.id.txtError)

        findViewById<MaterialButton>(R.id.btnLogin).setOnClickListener {
            doAuth(email.text.toString().trim(), password.text.toString(), error) { e, p ->
                auth.signInWithEmailAndPassword(e, p)
            }
        }
        findViewById<MaterialButton>(R.id.btnRegister).setOnClickListener {
            doAuth(email.text.toString().trim(), password.text.toString(), error) { e, p ->
                auth.createUserWithEmailAndPassword(e, p)
            }
        }
    }

    private fun doAuth(
        email: String,
        password: String,
        errorView: TextView,
        action: (String, String) -> com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult>
    ) {
        errorView.visibility = android.view.View.GONE
        if (email.isBlank() || !email.contains("@")) {
            showError(errorView, "Masukkan email yang valid")
            return
        }
        if (password.length < 6) {
            showError(errorView, "Kata sandi minimal 6 karakter")
            return
        }

        action(email, password)
            .addOnSuccessListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                showError(errorView, e.localizedMessage ?: "Gagal masuk. Coba lagi.")
            }
    }

    private fun showError(view: TextView, message: String) {
        view.text = message
        view.visibility = android.view.View.VISIBLE
    }
}
