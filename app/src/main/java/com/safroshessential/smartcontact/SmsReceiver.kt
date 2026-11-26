package com.safroshessential.smartcontact

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        for (message in messages) {

            val sender = message.displayOriginatingAddress ?: "Unknown"
            val body = message.messageBody ?: ""

            Log.d(
                "SmartContactSMS",
                "New SMS from $sender: $body"
            )
        }
    }
}