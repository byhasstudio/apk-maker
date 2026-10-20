package com.byhasstudio.remotectl

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.security.MessageDigest

/**
 * Menyimpan & membaca daftar "perangkat tersimpan" untuk fitur
 * hubungkan-tanpa-kode. Disimpan di satu node bersama /sharedDevices/{deviceId}.
 * Setiap perangkat (akun apa saja) boleh menulis/membaca node MILIKNYA
 * sendiri, tapi DAFTAR seluruh perangkat hanya bisa dibaca akun admin
 * ([ADMIN_EMAIL]) -- dijaga di dua lapis: aturan keamanan database
 * (database.rules.json) dan pemeriksaan di aplikasi ([isAdmin]).
 * Keamanan konek tetap ditambah kata sandi per perangkat (opsional) dan
 * kode sesi 6 digit yang dibuat ulang setiap permintaan konek.
 */
object DeviceStore {

    // Akun khusus ini bisa melihat & menghubungkan ke SEMUA perangkat yang
    // sedang login -- walau perangkat itu belum mengaktifkan "hubungkan
    // tanpa kode" sama sekali dan walau dipasangi kata sandi. Ini sengaja
    // dibuat sebagai akun admin pribadi (bukan fitur multi-pengguna).
    //
    // Nilainya adalah "email" internal hasil ubahan otomatis dari username
    // admin (lihat LoginActivity.usernameToFakeEmail) -- HARUS SAMA PERSIS
    // dengan hasil ubahan username admin di LoginActivity.kt (Android) dan
    // LoginForm.cs (Windows), serta dengan email di database.rules.json,
    // supaya akun admin yang login lewat username biasa tetap dikenali
    // sebagai admin di semua tempat. Username admin saat ini: "haerka949".
    const val ADMIN_EMAIL = "haerka949@remotectl.local"

    data class DeviceEntry(
        val id: String,
        val name: String,
        val online: Boolean,
        val requiresPassword: Boolean,
        val quickConnectEnabled: Boolean
    )

    private val db get() = SessionRelay.sessionRef("").database.reference

    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    fun isAdmin(): Boolean =
        FirebaseAuth.getInstance().currentUser?.email.equals(ADMIN_EMAIL, ignoreCase = true)

    fun hash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun devicesRef() =
        if (FirebaseAuth.getInstance().currentUser != null) db.child("sharedDevices") else null

    /**
     * onResult diberi tahu hasil SUNGGUHAN dari server (bukan diasumsikan
     * berhasil) -- supaya kalau tulisan ini ditolak server (mis. aturan
     * keamanan database belum ter-publish dengan benar), pengguna
     * langsung lihat pesan errornya alih-alih aplikasi bilang "Aktif"
     * padahal diam-diam gagal dan perangkat tidak pernah benar-benar
     * masuk daftar.
     */
    fun enableQuickConnect(
        context: Context,
        requirePassword: Boolean,
        password: String?,
        onResult: (success: Boolean, error: String?) -> Unit = { _, _ -> }
    ) {
        val ref = devicesRef()?.child(deviceId(context))
        if (ref == null) {
            onResult(false, "Belum login. Masuk dulu dengan akun sebelum mengaktifkan fitur ini.")
            return
        }
        ref.setValue(
            mapOf(
                "name" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "passwordHash" to if (requirePassword && password != null) hash(password) else "",
                "requiresPassword" to requirePassword,
                "enabled" to true,
                "online" to true,
                "lastSeen" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            onResult(true, null)
        }.addOnFailureListener { e ->
            Log.e("DeviceStore", "Gagal enableQuickConnect", e)
            onResult(false, e.message)
        }
    }

    fun disableQuickConnect(context: Context) {
        // Cuma matikan akses "siapa saja boleh konek" -- TIDAK menandai
        // offline, karena presensi (registerPresence) sekarang berjalan
        // terus selama login, lepas dari toggle ini.
        devicesRef()?.child(deviceId(context))?.child("enabled")?.setValue(false)
    }

    /**
     * Daftarkan perangkat ini supaya TERLIHAT di daftar (nama + status
     * online), lepas dari apakah "hubungkan tanpa kode" sudah diaktifkan
     * atau belum -- supaya akun admin ([ADMIN_EMAIL]) selalu bisa melihat
     * dan menghubungkan ke perangkat ini kapan pun sedang login, walau
     * pemilik perangkat belum sempat/mau mengaktifkan fitur itu untuk
     * akun lain. Dipakai dari MainActivity setiap kali dibuka saat sudah
     * login, bukan cuma dari menu Pengaturan. Pakai updateChildren (bukan
     * setValue) supaya tidak menimpa requiresPassword/passwordHash/enabled
     * yang mungkin sudah diset dari menu Pengaturan.
     */
    fun registerPresence(context: Context) {
        val ref = devicesRef()?.child(deviceId(context)) ?: return
        ref.updateChildren(
            mapOf(
                "name" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "online" to true,
                "lastSeen" to System.currentTimeMillis()
            )
        )
    }

    /**
     * Daftarkan ke Firebase: kalau koneksi perangkat ini ke server putus
     * tiba-tiba (app di-force-kill, sinyal hilang, dsb -- bukan dimatikan
     * rapi lewat tombol Nonaktifkan), server sendiri yang menandai
     * "online" jadi false begitu socket-nya putus. Ini jauh lebih bisa
     * diandalkan daripada menunggu heartbeat berikutnya (bisa sampai 30
     * detik) atau PresenceService.onDestroy yang belum tentu sempat jalan.
     */
    fun registerAutoOffline(context: Context) {
        val ref = devicesRef()?.child(deviceId(context)) ?: return
        ref.child("online").onDisconnect().setValue(false)
    }

    fun heartbeat(context: Context) {
        devicesRef()?.child(deviceId(context))?.updateChildren(
            mapOf("online" to true, "lastSeen" to System.currentTimeMillis())
        )
    }

    fun setOffline(context: Context) {
        devicesRef()?.child(deviceId(context))?.child("online")?.setValue(false)
    }

    /** onRequest memberi tahu code yang diminta, passwordHash (kosong "" kalau mode tanpa kata sandi), dan email akun yang minta konek (buat cek bypass admin). */
    fun listenIncomingRequest(context: Context, onRequest: (code: String, passwordHash: String, requesterEmail: String) -> Unit): ValueEventListener? {
        val ref = devicesRef()?.child(deviceId(context))?.child("incomingRequest") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val code = snapshot.child("code").getValue(String::class.java) ?: return
                val hash = snapshot.child("passwordHash").getValue(String::class.java) ?: ""
                val requesterEmail = snapshot.child("requesterEmail").getValue(String::class.java) ?: ""
                onRequest(code, hash, requesterEmail)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun clearIncomingRequest(context: Context) {
        devicesRef()?.child(deviceId(context))?.child("incomingRequest")?.removeValue()
    }

    /** Daftar semua perangkat -- HANYA untuk akun admin. Akun lain langsung dapat daftar kosong tanpa menyentuh server. */
    fun listMyDevices(onResult: (List<DeviceEntry>) -> Unit) {
        if (!isAdmin()) return onResult(emptyList())
        val ref = devicesRef() ?: return onResult(emptyList())
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onResult(parseDevices(snapshot))
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("DeviceStore", "Gagal listMyDevices: ${error.message}")
                onResult(emptyList())
            }
        })
    }

    /**
     * Sama seperti listMyDevices, tapi terus mendengarkan perubahan (status
     * online, dst). HANYA untuk akun admin; akun lain dapat daftar kosong
     * dan tidak ada listener yang dipasang. onError dipanggil kalau server
     * menolak permintaan ini (mis. aturan keamanan database belum
     * ter-publish dengan benar) -- daftar kosong TANPA pesan error
     * biasanya berarti ini penyebabnya.
     */
    fun listenMyDevices(
        onResult: (List<DeviceEntry>) -> Unit,
        onError: (String) -> Unit = {}
    ): ValueEventListener? {
        if (!isAdmin()) return null.also { onResult(emptyList()) }
        val ref = devicesRef() ?: return null.also { onResult(emptyList()) }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onResult(parseDevices(snapshot))
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("DeviceStore", "Gagal listenMyDevices: ${error.message}")
                onResult(emptyList())
                onError(error.message)
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    /**
     * Daftar SEMUA perangkat yang sedang/pernah login (lepas dari apakah
     * "hubungkan tanpa kode" diaktifkan) -- dipakai akun admin untuk
     * melihat dan konek ke perangkat mana pun.
     */
    private fun parseDevices(snapshot: DataSnapshot): List<DeviceEntry> {
        val list = mutableListOf<DeviceEntry>()
        for (child in snapshot.children) {
            val name = child.child("name").getValue(String::class.java) ?: "Perangkat"
            val online = child.child("online").getValue(Boolean::class.java) ?: false
            val requiresPassword = child.child("requiresPassword").getValue(Boolean::class.java) ?: true
            val quickConnectEnabled = child.child("enabled").getValue(Boolean::class.java) ?: false
            list.add(DeviceEntry(child.key ?: "", name, online, requiresPassword, quickConnectEnabled))
        }
        return list
    }

    fun requestConnect(deviceId: String, code: String, password: String?) {
        val ref = devicesRef()?.child(deviceId)?.child("incomingRequest") ?: return
        ref.setValue(
            mapOf(
                "code" to code,
                "passwordHash" to if (password != null) hash(password) else "",
                "requesterEmail" to (FirebaseAuth.getInstance().currentUser?.email ?: ""),
                "ts" to System.currentTimeMillis()
            )
        )
    }
}
