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
 * hubungkan-tanpa-kode. Disimpan di satu node bersama /sharedDevices/{deviceId}
 * yang bisa dibaca & ditulis oleh SIAPA PUN yang sudah login (akun apa
 * saja) -- supaya semua perangkat yang mengaktifkan fitur ini tetap
 * muncul di daftar, walaupun perangkat-perangkat itu login pakai akun
 * Firebase yang berbeda-beda (mis. satu pakai "Masuk cepat", satu pakai
 * akun Google). Keamanannya tetap terjaga lewat kata sandi per
 * perangkat (opsional) dan lewat kode sesi 6 digit sekali pakai yang
 * dibuat ulang setiap permintaan konek -- bukan lewat pemisahan akun.
 */
object DeviceStore {

    data class DeviceEntry(val id: String, val name: String, val online: Boolean, val requiresPassword: Boolean)

    private val db get() = SessionRelay.sessionRef("").database.reference

    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

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
        devicesRef()?.child(deviceId(context))?.child("enabled")?.setValue(false)
        devicesRef()?.child(deviceId(context))?.child("online")?.setValue(false)
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

    /** onRequest memberi tahu code yang diminta dan passwordHash (kosong "" kalau mode tanpa kata sandi). */
    fun listenIncomingRequest(context: Context, onRequest: (code: String, passwordHash: String) -> Unit): ValueEventListener? {
        val ref = devicesRef()?.child(deviceId(context))?.child("incomingRequest") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val code = snapshot.child("code").getValue(String::class.java) ?: return
                val hash = snapshot.child("passwordHash").getValue(String::class.java) ?: ""
                onRequest(code, hash)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun clearIncomingRequest(context: Context) {
        devicesRef()?.child(deviceId(context))?.child("incomingRequest")?.removeValue()
    }

    fun listMyDevices(onResult: (List<DeviceEntry>) -> Unit) {
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
     * online, dst). onError dipanggil kalau server menolak permintaan ini
     * (mis. aturan keamanan database belum ter-publish dengan benar) --
     * daftar kosong TANPA pesan error biasanya berarti ini penyebabnya.
     */
    fun listenMyDevices(
        onResult: (List<DeviceEntry>) -> Unit,
        onError: (String) -> Unit = {}
    ): ValueEventListener? {
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

    private fun parseDevices(snapshot: DataSnapshot): List<DeviceEntry> {
        val list = mutableListOf<DeviceEntry>()
        for (child in snapshot.children) {
            val enabled = child.child("enabled").getValue(Boolean::class.java) ?: false
            if (!enabled) continue
            val name = child.child("name").getValue(String::class.java) ?: "Perangkat"
            val online = child.child("online").getValue(Boolean::class.java) ?: false
            val requiresPassword = child.child("requiresPassword").getValue(Boolean::class.java) ?: true
            list.add(DeviceEntry(child.key ?: "", name, online, requiresPassword))
        }
        return list
    }

    fun requestConnect(deviceId: String, code: String, password: String?) {
        val ref = devicesRef()?.child(deviceId)?.child("incomingRequest") ?: return
        ref.setValue(
            mapOf(
                "code" to code,
                "passwordHash" to if (password != null) hash(password) else "",
                "ts" to System.currentTimeMillis()
            )
        )
    }
}
