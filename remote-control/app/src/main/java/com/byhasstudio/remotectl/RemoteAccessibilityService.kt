package com.byhasstudio.remotectl

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.firebase.database.DataSnapshot

/**
 * Berjalan di perangkat yang DIKONTROL. Menerima perintah (tap/swipe)
 * yang diteruskan dari ScreenCaptureService (yang mendengarkan Firebase)
 * dan mengeksekusinya sebagai gestur nyata lewat Accessibility API.
 *
 * Pengguna harus mengaktifkan layanan ini secara manual di
 * Pengaturan > Aksesibilitas, karena Android tidak mengizinkan aplikasi
 * mengaktifkan Accessibility Service untuk dirinya sendiri.
 */
class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        var instance: RemoteAccessibilityService? = null
        private var lastHandledTs: Long = 0L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun handleRemoteCommand(snapshot: DataSnapshot) {
        val ts = snapshot.child("ts").getValue(Long::class.java) ?: return
        if (ts <= lastHandledTs) return
        lastHandledTs = ts

        when (snapshot.child("type").getValue(String::class.java)) {
            "tap" -> {
                val x = snapshot.child("x").getValue(Float::class.java) ?: return
                val y = snapshot.child("y").getValue(Float::class.java) ?: return
                dispatchTap(x, y)
            }
            "swipe" -> {
                val x1 = snapshot.child("x").getValue(Float::class.java) ?: return
                val y1 = snapshot.child("y").getValue(Float::class.java) ?: return
                val x2 = snapshot.child("x2").getValue(Float::class.java) ?: return
                val y2 = snapshot.child("y2").getValue(Float::class.java) ?: return
                val duration = snapshot.child("durationMs").getValue(Long::class.java) ?: 200L
                dispatchSwipe(x1, y1, x2, y2, duration)
            }
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "settext" -> {
                val value = snapshot.child("value").getValue(String::class.java) ?: return
                setFocusedFieldText(value)
            }
            "key" -> {
                when (snapshot.child("key").getValue(String::class.java)) {
                    "ENTER" -> pressEnter()
                    "BACKSPACE" -> backspaceFocusedField()
                }
            }
        }
    }

    /**
     * Timpa isi kolom teks yang sedang fokus di layar ini dengan `value` --
     * dipakai fitur keyboard jarak jauh, supaya ngetik di perangkat
     * pengontrol langsung terlihat sebagai teks di kolom yang aktif di sini.
     */
    private fun setFocusedFieldText(value: String) {
        val node = findFocusedEditableNode() ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
    }

    private fun backspaceFocusedField() {
        val node = findFocusedEditableNode() ?: return
        val current = node.text?.toString().orEmpty()
        if (current.isNotEmpty()) {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    current.substring(0, current.length - 1)
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        node.recycle()
    }

    private fun pressEnter() {
    private fun pressEnter() {
        val node = findFocusedEditableNode() ?: return
        // Memicu aksi Enter/Kirim dengan mengirimkan perintah klik aksi input
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
    }

        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    private fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(16))
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
