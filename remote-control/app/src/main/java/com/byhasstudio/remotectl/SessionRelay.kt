package com.byhasstudio.remotectl

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.random.Random

/**
 * Jembatan sinyal antara perangkat "host" (yang dikontrol) dan
 * perangkat "controller" (yang mengontrol), lewat Firebase Realtime
 * Database. Kode sesi 6 digit berfungsi sebagai kunci bersama yang
 * sederhana -- siapa pun yang tahu kodenya bisa menyambung, jadi kode
 * harus dibagikan hanya ke orang yang dipercaya.
 *
 * Struktur data:
 *  /sessions/{code}/meta   -> { width, height, createdAt, active }
 *  /sessions/{code}/frame  -> { jpegBase64, ts }
 *  /sessions/{code}/cmd    -> { type, x, y, x2, y2, durationMs, ts }
 */
object SessionRelay {

    private val db get() = FirebaseDatabase.getInstance().reference

    fun generateCode(): String =
        (100000 + Random.nextInt(900000)).toString()

    fun sessionRef(code: String) = db.child("sessions").child(code)

    fun publishMeta(code: String, width: Int, height: Int) {
        sessionRef(code).child("meta").setValue(
            mapOf(
                "width" to width,
                "height" to height,
                "createdAt" to System.currentTimeMillis(),
                "active" to true
            )
        )
    }

    fun publishFrame(code: String, jpegBase64: String) {
        sessionRef(code).child("frame").setValue(
            mapOf("jpegBase64" to jpegBase64, "ts" to System.currentTimeMillis())
        )
    }

    fun sendCommand(code: String, cmd: Map<String, Any?>) {
        sessionRef(code).child("cmd").setValue(
            cmd + ("ts" to System.currentTimeMillis())
        )
    }

    fun endSession(code: String) {
        sessionRef(code).child("meta").child("active").setValue(false)
    }

    fun listenFrames(code: String, onFrame: (String) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val b64 = snapshot.child("jpegBase64").getValue(String::class.java)
                if (b64 != null) onFrame(b64)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        sessionRef(code).child("frame").addValueEventListener(listener)
        return listener
    }

    fun listenCommands(code: String, onCommand: (DataSnapshot) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) onCommand(snapshot)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        sessionRef(code).child("cmd").addValueEventListener(listener)
        return listener
    }

    fun listenMeta(code: String, onMeta: (Int, Int) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val w = snapshot.child("width").getValue(Int::class.java) ?: return
                val h = snapshot.child("height").getValue(Int::class.java) ?: return
                onMeta(w, h)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        sessionRef(code).child("meta").addValueEventListener(listener)
        return listener
    }

    fun removeListener(code: String, child: String, listener: ValueEventListener) {
        sessionRef(code).child(child).removeEventListener(listener)
    }
}
