package com.safroshessential.smartcontact

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            return
        }

        val messages =
            Telephony.Sms.Intents.getMessagesFromIntent(intent)

        if (messages.isEmpty()) return

        val sender =
            messages[0].displayOriginatingAddress ?: "Unknown"

        val body = messages.joinToString("") {
            it.messageBody ?: ""
        }

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }

        try {
            context.contentResolver.insert(
                Telephony.Sms.Inbox.CONTENT_URI,
                values
            )

            Log.d(
                "SmartContactSMS",
                "SMS saved from $sender"
            )

        } catch (e: Exception) {

            Log.e(
                "SmartContactSMS",
                "Unable to save SMS",
                e
            )
        }
    }
}