package com.safroshessential.smartcontact

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneNumberUtils
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class KnownSpamCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {

        val incomingNumber =
            callDetails.handle?.schemeSpecificPart ?: ""

        val preferences = getSharedPreferences(
            "SmartContactSpam",
            Context.MODE_PRIVATE
        )

        val knownSpamNumbers = preferences.getStringSet(
            "known_spam_numbers",
            emptySet()
        ) ?: emptySet()

        val isKnownSpam = knownSpamNumbers.any {
            PhoneNumberUtils.compare(it, incomingNumber)
        }

        if (isKnownSpam) {

            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()

            respondToCall(callDetails, response)

            showSpamNotification(incomingNumber)

        } else {

            respondToCall(
                callDetails,
                CallResponse.Builder().build()
            )
        }
    }

    private fun showSpamNotification(number: String) {

        val channelId = "smart_contact_spam"

        val manager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Spam Call Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )

            manager.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Spam call blocked")
            .setContentText("Blocked known spam number: $number")
            .setAutoCancel(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setChannelId(channelId)
                }
            }
            .build()

        manager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
}