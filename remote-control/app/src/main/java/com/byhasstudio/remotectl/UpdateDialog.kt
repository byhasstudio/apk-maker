package com.byhasstudio.remotectl

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * Pop-up pembaruan dengan animasi muncul (fade + scale dengan sedikit
 * "overshoot" supaya terasa hidup, bukan dialog AlertDialog polos
 * bawaan Android).
 */
object UpdateDialog {

    fun show(activity: Activity, info: UpdateChecker.UpdateInfo, onUpdateNow: () -> Unit) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)

        view.findViewById<TextView>(R.id.txtVersion).text = "Versi baru: ${info.version}"
        view.findViewById<TextView>(R.id.txtChangelog).text = info.changelog

        view.findViewById<MaterialButton>(R.id.btnLater).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<MaterialButton>(R.id.btnUpdateNow).setOnClickListener {
            onUpdateNow()
            dialog.dismiss()
        }

        dialog.show()

        val card = view.findViewById<android.view.View>(R.id.updateCard)
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(320)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
    }
}
