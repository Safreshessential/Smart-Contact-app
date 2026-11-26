package com.safroshessential.smartcontact

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SmsInboxActivity : Activity() {

    private val green = Color.rgb(20, 130, 75)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 24)
            setBackgroundColor(Color.WHITE)
        }

        val heading = TextView(this).apply {
            text = "Messages"
            textSize = 26f
            setTextColor(green)
            gravity = Gravity.CENTER_VERTICAL
        }

        val description = TextView(this).apply {
            text = "Your SMS conversations will appear here."
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }

        val newMessageButton = Button(this).apply {
            text = "New Message"
            setOnClickListener {
                openNewMessage()
            }
        }

        root.addView(
            heading,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                65
            )
        )

        root.addView(
            description,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(
            newMessageButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                55
            )
        )

        setContentView(root)
    }

    private fun openNewMessage() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:")
        }

        startActivity(intent)
    }
}