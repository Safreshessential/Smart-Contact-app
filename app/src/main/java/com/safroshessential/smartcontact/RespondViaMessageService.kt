package com.safroshessential.smartcontact

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log

class RespondViaMessageService : Service() {

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val recipient = intent?.data?.schemeSpecificPart
        val message = intent?.getCharSequenceExtra(
            Intent.EXTRA_TEXT
        )?.toString()

        if (
            !recipient.isNullOrBlank() &&
            !message.isNullOrBlank()
        ) {
            try {
                val smsManager = getSystemService(SmsManager::class.java)

                smsManager.sendTextMessage(
                    recipient,
                    null,
                    message,
                    null,
                    null
                )

                Log.d(
                    "SmartContactSMS",
                    "Response message submitted"
                )

            } catch (e: Exception) {
                Log.e(
                    "SmartContactSMS",
                    "Unable to send response message",
                    e
                )
            }
        }

        stopSelf(startId)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}