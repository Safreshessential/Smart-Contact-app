package com.safroshessential.smartcontact

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "smart_contact_sms"
        private const val CHANNEL_NAME = "New SMS Messages"
    }

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
            put(
                Telephony.Sms.TYPE,
                Telephony.Sms.MESSAGE_TYPE_INBOX
            )
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

        showSmsNotification(context, sender, body)
    }

    private fun getContactName(
        context: Context,
        phoneNumber: String
    ): String {

        try {
            val lookupUri =
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI
                    .buildUpon()
                    .appendPath(phoneNumber)
                    .build()

            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)

                    if (!name.isNullOrBlank()) {
                        return name
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(
                "SmartContactSMS",
                "Unable to find contact name",
                e
            )
        }

        return phoneNumber
    }

    private fun showSmsNotification(
        context: Context,
        sender: String,
        message: String
    ) {

        try {
            val notificationManager =
                context.getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for new SMS messages"
                }

                notificationManager.createNotificationChannel(channel)
            }

            val contactName = getContactName(context, sender)

            val openInboxIntent =
                Intent(context, SmsInboxActivity::class.java).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

            val pendingIntent = PendingIntent.getActivity(
                context,
                sender.hashCode(),
                openInboxIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

            val builder =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Notification.Builder(context, CHANNEL_ID)
                } else {
                    Notification.Builder(context)
                        .setPriority(Notification.PRIORITY_HIGH)
                }

            val notification = builder
                .setSmallIcon(android.R.drawable.sym_action_email)
                .setContentTitle(contactName)
                .setContentText(message)
                .setStyle(
                    Notification.BigTextStyle()
                        .bigText(message)
                )
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(
                sender.hashCode(),
                notification
            )

            Log.d(
                "SmartContactSMS",
                "SMS notification displayed"
            )

        } catch (e: Exception) {

            Log.e(
                "SmartContactSMS",
                "Unable to display SMS notification",
                e
            )
        }
    }
}