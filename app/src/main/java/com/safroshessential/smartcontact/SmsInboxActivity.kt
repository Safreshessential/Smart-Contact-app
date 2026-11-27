package com.safroshessential.smartcontact

import android.app.Activity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsInboxActivity : Activity() {

    private val green = Color.rgb(20, 130, 75)
    private lateinit var messageList: LinearLayout

    companion object {
        private const val SMS_PERMISSION_REQUEST = 301
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 20)
            setBackgroundColor(Color.WHITE)
        }

        val heading = TextView(this).apply {
            text = "Messages"
            textSize = 26f
            setTextColor(green)
            gravity = Gravity.CENTER_VERTICAL
        }

        val refreshButton = Button(this).apply {
            text = "Refresh Inbox"
            setOnClickListener {
                checkPermissionAndLoad()
            }
        }

        messageList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(messageList)
        }

        root.addView(
            heading,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                60
            )
        )

        root.addView(refreshButton)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        checkPermissionAndLoad()
    }

    private fun checkPermissionAndLoad() {

        if (
            checkSelfPermission(Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.READ_SMS),
                SMS_PERMISSION_REQUEST
            )
        } else {
            loadMessages()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == SMS_PERMISSION_REQUEST) {
            if (
                grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                loadMessages()
            } else {
                showMessage("SMS permission is required to read your inbox.")
            }
        }
    }

    private fun loadMessages() {

        messageList.removeAllViews()

        try {
            val cursor: Cursor? = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            var count = 0

            cursor?.use {
                while (it.moveToNext()) {

                    val sender = it.getString(0) ?: "Unknown"
                    val body = it.getString(1) ?: ""
                    val date = it.getLong(2)

                    val dateText =
                        SimpleDateFormat(
                            "dd MMM, hh:mm a",
                            Locale.getDefault()
                        ).format(Date(date))

                    addMessage(sender, body, dateText)
                    count++
                }
            }

            if (count == 0) {
                showMessage("No SMS messages found.")
            }

        } catch (e: Exception) {
            showMessage("Unable to load messages: ${e.message}")
        }
    }

    private fun addMessage(
        sender: String,
        body: String,
        date: String
    ) {

        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 14, 12, 14)
        }

        val senderText = TextView(this).apply {
            text = sender
            textSize = 17f
            setTextColor(green)
        }

        val bodyText = TextView(this).apply {
            text = body
            textSize = 15f
            setTextColor(Color.DKGRAY)
        }

        val dateText = TextView(this).apply {
            text = date
            textSize = 12f
            setTextColor(Color.GRAY)
        }

        item.addView(senderText)
        item.addView(bodyText)
        item.addView(dateText)

        messageList.addView(item)

        val divider = android.view.View(this).apply {
            setBackgroundColor(Color.LTGRAY)
        }

        messageList.addView(
            divider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            )
        )
    }

    private fun showMessage(message: String) {

        val text = TextView(this).apply {
            this.text = message
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(12, 30, 12, 30)
        }

        messageList.addView(text)
    }
}