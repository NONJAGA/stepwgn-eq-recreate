package com.jagastech.stepwgndsq

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Automatically starts the 13-Band NR-MZ25 Equalizer Service whenever the
 * 2007 Honda Stepwgn ignition key turns on (both cold boot and head unit ACC wake).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, DsqAudioEngineService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
