package com.byhasstudio.remotectl

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Login email/kata sandi ATAU Google. Dipakai supaya daftar "perangkat
 * tersimpan" (fitur hubungkan tanpa kode) hanya kelihatan dan bisa
 * diakses oleh akun yang sama -- bukan sekadar disaring di aplikasi,
 * tapi memang dikunci di aturan keamanan database (lihat database.rules.json).
 *
 * Login Google butuh satu hal yang harus diisi manual sebelum build:
 * [GOOGLE_WEB_CLIENT_ID] di bawah -- lihat catatan di dekatnya.
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        // Diisi dari Firebase Console -> Authentication -> Sign-in method
        // -> Google -> "Web SDK configuration" -> Web client ID. BUKAN
        // Client ID Android, harus yang tipe "Web application". Dapat
        // nilai aslinya begitu provider Google diaktifkan di Firebase.
        private const val GOOGLE_WEB_CLIENT_ID = "ISI_WEB_CLIENT_ID_DARI_FIREBASE_CONSOLE"
    }

    private val auth = FirebaseAuth.getInstance()
    private lateinit var googleClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener { goToMain() }
                .addOnFailureListener { e ->
                    showError(findViewById(R.id.txtError), e.localizedMessage ?: "Gagal masuk dengan Google.")
                }
        } catch (e: ApiException) {
            // Kode 10 (DEVELOPER_ERROR) biasanya berarti SHA-1 aplikasi ini
            // belum didaftarkan di Firebase Console, atau GOOGLE_WEB_CLIENT_ID
            // di atas belum diisi/salah -- lihat README bagian "Login Google".
            showError(
                findViewById(R.id.txtError),
                if (e.statusCode == 10) "Setup Google Sign-In belum lengkap (lihat README)."
                else "Gagal masuk dengan Google (${e.statusCode})."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val email = findViewById<EditText>(R.id.editEmail)
        val password = findViewById<EditText>(R.id.editPassword)
        val error = findViewById<TextView>(R.id.txtError)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        googleClient = GoogleSignIn.getClient(this, gso)

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
        findViewById<MaterialButton>(R.id.btnGoogle).setOnClickListener {
            error.visibility = android.view.View.GONE
            // Selalu minta pilih akun lagi (bukan auto pakai akun terakhir)
            // supaya pengguna bisa ganti akun Google dengan mudah kalau perlu.
            googleClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleClient.signInIntent)
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
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
            .addOnSuccessListener { goToMain() }
            .addOnFailureListener { e ->
                showError(errorView, e.localizedMessage ?: "Gagal masuk. Coba lagi.")
            }
    }

    private fun showError(view: TextView, message: String) {
        view.text = message
        view.visibility = android.view.View.VISIBLE
    }
}
