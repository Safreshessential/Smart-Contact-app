package com.safroshessential.smartcontact

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.widget.Toast

class SmsStatusReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_SENT =
            "com.safroshessential.smartcontact.SMS_SENT"

        const val ACTION_SMS_DELIVERED =
            "com.safroshessential.smartcontact.SMS_DELIVERED"
    }

    override fun onReceive(context: Context, intent: Intent) {

        when (intent.action) {

            ACTION_SMS_SENT -> {
                when (resultCode) {
                    Activity.RESULT_OK ->
                        showStatus(context, "SMS sent successfully.")

                    SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
                        showStatus(context, "SMS sending failed.")

                    SmsManager.RESULT_ERROR_NO_SERVICE ->
                        showStatus(context, "No mobile network service.")

                    SmsManager.RESULT_ERROR_NULL_PDU ->
                        showStatus(context, "Invalid SMS message.")

                    SmsManager.RESULT_ERROR_RADIO_OFF ->
                        showStatus(context, "Mobile network is switched off.")

                    else ->
                        showStatus(context, "Unable to send SMS.")
                }
            }

            ACTION_SMS_DELIVERED -> {
                if (resultCode == Activity.RESULT_OK) {
                    showStatus(context, "SMS delivered to recipient.")
                } else {
                    showStatus(context, "Delivery confirmation unavailable.")
                }
            }
        }
    }

    private fun showStatus(context: Context, message: String) {
        Toast.makeText(
            context,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}