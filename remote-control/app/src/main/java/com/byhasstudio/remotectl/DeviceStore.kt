package com.byhasstudio.remotectl

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.security.MessageDigest

/**
 * Menyimpan & membaca daftar "perangkat tersimpan" milik akun yang
 * sedang login, untuk fitur hubungkan-tanpa-kode. Disimpan di bawah
 * /users/{uid}/devices/{deviceId} -- dikunci oleh aturan database
 * supaya hanya pemilik akun itu sendiri yang bisa membaca/menulisnya.
 */
object DeviceStore {

    private val db get() = SessionRelay.sessionRef("").database.reference
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    fun hash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun devicesRef() = uid?.let { db.child("users").child(it).child("devices") }

    fun enableQuickConnect(context: Context, password: String) {
        val ref = devicesRef()?.child(deviceId(context)) ?: return
        ref.setValue(
            mapOf(
                "name" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "passwordHash" to hash(password),
                "enabled" to true,
                "online" to true,
                "lastSeen" to System.currentTimeMillis()
            )
        )
    }

    fun disableQuickConnect(context: Context) {
        devicesRef()?.child(deviceId(context))?.child("enabled")?.setValue(false)
        devicesRef()?.child(deviceId(context))?.child("online")?.setValue(false)
    }

    fun heartbeat(context: Context) {
        devicesRef()?.child(deviceId(context))?.updateChildren(
            mapOf("online" to true, "lastSeen" to System.currentTimeMillis())
        )
    }

    fun setOffline(context: Context) {
        devicesRef()?.child(deviceId(context))?.child("online")?.setValue(false)
    }

    fun listenIncomingRequest(context: Context, onRequest: (code: String, passwordHash: String) -> Unit): ValueEventListener? {
        val ref = devicesRef()?.child(deviceId(context))?.child("incomingRequest") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val code = snapshot.child("code").getValue(String::class.java) ?: return
                val hash = snapshot.child("passwordHash").getValue(String::class.java) ?: return
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

    fun listMyDevices(onResult: (List<Triple<String, String, Boolean>>) -> Unit) {
        val ref = devicesRef() ?: return onResult(emptyList())
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Triple<String, String, Boolean>>() // id, name, online
                for (child in snapshot.children) {
                    val enabled = child.child("enabled").getValue(Boolean::class.java) ?: false
                    if (!enabled) continue
                    val name = child.child("name").getValue(String::class.java) ?: "Perangkat"
                    val online = child.child("online").getValue(Boolean::class.java) ?: false
                    list.add(Triple(child.key ?: "", name, online))
                }
                onResult(list)
            }
            override fun onCancelled(error: DatabaseError) {
                onResult(emptyList())
            }
        })
    }

    fun requestConnect(deviceId: String, code: String, password: String) {
        val ref = devicesRef()?.child(deviceId)?.child("incomingRequest") ?: return
        ref.setValue(mapOf("code" to code, "passwordHash" to hash(password), "ts" to System.currentTimeMillis()))
    }
}
