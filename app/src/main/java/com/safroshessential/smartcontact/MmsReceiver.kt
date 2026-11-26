package com.safroshessential.smartcontact

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (
            intent.action !=
            "android.provider.Telephony.WAP_PUSH_DELIVER"
        ) {
            return
        }

        val mimeType = intent.type

        Log.d(
            "SmartContactMMS",
            "Incoming MMS broadcast received. MIME type: $mimeType"
        )
    }
}