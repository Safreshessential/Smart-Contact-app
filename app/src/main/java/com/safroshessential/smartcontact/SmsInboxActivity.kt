package com.safroshessential.smartcontact

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.ContentValues
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.telephony.SmsManager
import android.provider.Telephony
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.LinkedHashMap

class SmsInboxActivity : Activity() {

    private val green = Color.rgb(20, 130, 75)
    private val paleGreen = Color.rgb(225, 245, 235)

    private lateinit var root: LinearLayout
    private lateinit var messageList: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var scrollView: ScrollView

    private var pendingMessage = ""

    companion object {
        private const val SMS_PERMISSION_REQUEST = 301
        private const val SEND_PERMISSION_REQUEST = 302
        private const val EXTRA_ADDRESS = "conversation_address"
    }

    private data class SmsPreview(
        val address: String,
        val body: String,
        val date: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (conversationAddress() == null) {
            showInbox()
        } else {
            showConversation()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun conversationAddress(): String? {
        return intent.getStringExtra(EXTRA_ADDRESS)
    }

    private fun showInbox() {

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(12))
            setBackgroundColor(Color.WHITE)
        }

        val heading = TextView(this).apply {
            text = "Messages"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val newMessageButton = Button(this).apply {
            text = "New Message"
            setOnClickListener {
                showNewMessageDialog()
            }
        }

        val refreshButton = Button(this).apply {
            text = "Refresh"
            setOnClickListener {
                checkPermissionAndLoad()
            }
        }

        buttons.addView(
            newMessageButton,
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )

        buttons.addView(
            refreshButton,
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )

        messageList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scrollView = ScrollView(this).apply {
            addView(messageList)
        }

        root.addView(
            heading,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(55)
            )
        )

        root.addView(buttons)

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
            loadInbox()
        }
    }

    private fun loadInbox() {

        messageList.removeAllViews()

        try {
            val conversations =
                LinkedHashMap<String, SmsPreview>()

            val cursor = contentResolver.query(
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

            cursor?.use {
                while (it.moveToNext()) {

                    val address =
                        it.getString(0) ?: "Unknown"

                    val body =
                        it.getString(1) ?: ""

                    val date =
                        it.getLong(2)

                    if (!conversations.containsKey(address)) {
                        conversations[address] = SmsPreview(
                            address,
                            body,
                            date
                        )
                    }
                }
            }

            if (conversations.isEmpty()) {
                showEmptyMessage("No SMS conversations yet.")
                return
            }

            conversations.values.forEach { preview ->
                addConversationItem(preview)
            }

        } catch (e: Exception) {
            showEmptyMessage(
                "Unable to load messages: ${e.message}"
            )
        }
    }

    private fun addConversationItem(preview: SmsPreview) {

        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setBackgroundColor(Color.WHITE)

            setOnClickListener {
                openConversation(preview.address)
            }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val sender = TextView(this).apply {
            text = preview.address
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
        }

        val date = TextView(this).apply {
            text = formatDate(preview.date)
            textSize = 11f
            setTextColor(Color.GRAY)
            gravity = Gravity.END
        }

        topRow.addView(
            sender,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        topRow.addView(date)

        val body = TextView(this).apply {
            text = preview.body
            textSize = 14f
            setTextColor(Color.DKGRAY)
            maxLines = 2
            setPadding(0, dp(6), 0, 0)
        }

        item.addView(topRow)
        item.addView(body)

        messageList.addView(item)

        messageList.addView(View(this).apply {
            setBackgroundColor(Color.LTGRAY)
        }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun openConversation(address: String) {

        val newIntent = android.content.Intent(
            this,
            SmsInboxActivity::class.java
        )

        newIntent.putExtra(EXTRA_ADDRESS, address)
        startActivity(newIntent)
    }

    private fun showNewMessageDialog() {

        val numberInput = EditText(this).apply {
            hint = "Enter phone number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        AlertDialog.Builder(this)
            .setTitle("New Message")
            .setMessage("Enter the recipient's phone number.")
            .setView(numberInput)
            .setPositiveButton("Continue") { _, _ ->

                val number = numberInput.text.toString().trim()

                if (number.isEmpty()) {
                    showToast("Enter a phone number.")
                } else {
                    openConversation(number)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showConversation() {

        val address = conversationAddress() ?: return

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.WHITE)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val backButton = Button(this).apply {
            text = "Back"
            setOnClickListener {
                finish()
            }
        }

        val title = TextView(this).apply {
            text = address
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            setPadding(dp(10), 0, 0, 0)
        }

        header.addView(backButton)

        header.addView(
            title,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        messageList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }

        scrollView = ScrollView(this).apply {
            addView(messageList)
        }

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        messageInput = EditText(this).apply {
            hint = "Type a message..."
            textSize = 15f
            minLines = 1
            maxLines = 4
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedBackground(
                Color.rgb(245, 245, 245),
                Color.LTGRAY
            )
        }

        val sendButton = Button(this).apply {
            text = "Send"
            setOnClickListener {
                sendCurrentMessage()
            }
        }

        composer.addView(
            messageInput,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        composer.addView(sendButton)

        root.addView(header)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        root.addView(composer)

        setContentView(root)

        loadConversation(address)
    }

    private fun loadConversation(address: String) {

        messageList.removeAllViews()

        try {
            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                "${Telephony.Sms.ADDRESS} = ?",
                arrayOf(address),
                "${Telephony.Sms.DATE} ASC"
            )

            var count = 0

            cursor?.use {
                while (it.moveToNext()) {

                    val body = it.getString(1) ?: ""
                    val date = it.getLong(2)
                    val type = it.getInt(3)

                    val outgoing =
                        type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                        type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                        type == Telephony.Sms.MESSAGE_TYPE_FAILED ||
                        type == Telephony.Sms.MESSAGE_TYPE_QUEUED

                    addMessageBubble(body, date, outgoing)
                    count++
                }
            }

            if (count == 0) {
                showEmptyMessage(
                    "No messages yet. You can send the first message below."
                )
            }

            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }

        } catch (e: Exception) {
            showEmptyMessage(
                "Unable to open conversation: ${e.message}"
            )
        }
    }

    private fun addMessageBubble(
        body: String,
        date: Long,
        outgoing: Boolean
    ) {

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (outgoing) Gravity.END else Gravity.START
            setPadding(0, dp(5), 0, dp(5))
        }

        val bubble = TextView(this).apply {
            text = body + "\n\n" + formatDate(date)
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(
                if (outgoing) paleGreen else Color.rgb(242, 242, 242),
                if (outgoing) green else Color.LTGRAY
            )
        }

        container.addView(
            bubble,
            LinearLayout.LayoutParams(
                dp(280),
                -2
            )
        )

        messageList.addView(container)
    }

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), strokeColor)
        }
    }

    private fun sendCurrentMessage() {

        val address = conversationAddress() ?: return
        val body = messageInput.text.toString().trim()

        if (body.isEmpty()) {
            showToast("Type a message first.")
            return
        }

        if (
            checkSelfPermission(Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingMessage = body

            requestPermissions(
                arrayOf(Manifest.permission.SEND_SMS),
                SEND_PERMISSION_REQUEST
            )
            return
        }

        sendSms(address, body)
    }

    private fun sendSms(address: String, body: String) {

        try {
            SmsManager.getDefault().sendTextMessage(
                address,
                null,
                body,
                null,
                null
            )

            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(
                    Telephony.Sms.TYPE,
                    Telephony.Sms.MESSAGE_TYPE_SENT
                )
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }

            try {
                contentResolver.insert(
                    Telephony.Sms.Sent.CONTENT_URI,
                    values
                )
            } catch (_: Exception) {
                // The SMS may still have been submitted.
            }

            messageInput.text.clear()
            loadConversation(address)
            showToast("SMS submitted for sending.")

        } catch (e: Exception) {
            showToast("Unable to send SMS: ${e.message}")
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

        val granted =
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED

        when (requestCode) {

            SMS_PERMISSION_REQUEST -> {
                if (granted) {
                    loadInbox()
                } else {
                    showEmptyMessage(
                        "SMS permission is required to read your inbox."
                    )
                }
            }

            SEND_PERMISSION_REQUEST -> {
                if (granted) {
                    val address = conversationAddress()
                    if (address != null && pendingMessage.isNotEmpty()) {
                        val body = pendingMessage
                        pendingMessage = ""
                        sendSms(address, body)
                    }
                } else {
                    pendingMessage = ""
                    showToast("SMS sending permission was denied.")
                }
            }
        }
    }

    private fun formatDate(time: Long): String {
        return SimpleDateFormat(
            "dd MMM, hh:mm a",
            Locale.getDefault()
        ).format(Date(time))
    }

    private fun showEmptyMessage(message: String) {

        val text = TextView(this).apply {
            this.text = message
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(30), dp(12), dp(30))
        }

        messageList.removeAllViews()
        messageList.addView(text)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}