package com.yourname.pillpantry.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short haptic buzz confirming a barcode was detected — useful feedback since
 *  you're often not looking closely at the screen while scanning. */
object ScanFeedback {

    fun vibrate(context: Context) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return

        // 200ms at a strong explicit amplitude — the previous 50ms/DEFAULT_AMPLITUDE
        // buzz was too subtle to reliably notice while scanning.
        vibrator.vibrate(VibrationEffect.createOneShot(200, 220))
    }
}
