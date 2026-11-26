package com.safroshessential.smartcontact

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class SmartCallActivity : Activity() {

    private lateinit var nameText: TextView
    private lateinit var numberText: TextView
    private lateinit var durationText: TextView
    private lateinit var muteButton: Button
    private lateinit var speakerButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private var muted = false
    private var speakerOn = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            seconds++

            val minutes = seconds / 60
            val remainingSeconds = seconds % 60

            durationText.text = String.format(
                Locale.getDefault(),
                "%02d:%02d",
                minutes,
                remainingSeconds
            )

            handler.postDelayed(this, 1000)
        }
    }

    private val green = Color.rgb(22, 132, 71)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contactName =
            intent.getStringExtra("contact_name") ?: "Unknown Contact"

        val phoneNumber =
            intent.getStringExtra("phone_number") ?: ""

        createCallingScreen(contactName, phoneNumber)
        handler.postDelayed(timerRunnable, 1000)
    }

    private fun createCallingScreen(
        contactName: String,
        phoneNumber: String
    ) {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 40, 30, 40)
            setBackgroundColor(green)
        }

        nameText = TextView(this).apply {
            text = contactName
            textSize = 28f
            setTextColor(white)
            gravity = Gravity.CENTER
        }

        numberText = TextView(this).apply {
            text = phoneNumber
            textSize = 18f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 20)
        }

        durationText = TextView(this).apply {
            text = "00:00"
            textSize = 24f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 60)
        }

        muteButton = Button(this).apply {
            text = "Mute"
            setOnClickListener {
                muted = !muted
                text = if (muted) "Unmute" else "Mute"
                Toast.makeText(
                    this@SmartCallActivity,
                    if (muted) "Mute selected" else "Mute disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        speakerButton = Button(this).apply {
            text = "Speaker"
            setOnClickListener {
                speakerOn = !speakerOn
                text = if (speakerOn) "Speaker Off" else "Speaker"
                Toast.makeText(
                    this@SmartCallActivity,
                    if (speakerOn) "Speaker selected"
                    else "Speaker disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        controls.addView(muteButton)
        controls.addView(speakerButton)

        val endCallButton = Button(this).apply {
            text = "End Call"
            textSize = 18f
            setBackgroundColor(Color.RED)
            setTextColor(white)
            setOnClickListener {
                handler.removeCallbacks(timerRunnable)
                Toast.makeText(
                    this@SmartCallActivity,
                    "Calling screen closed",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }

        layout.addView(nameText)
        layout.addView(numberText)
        layout.addView(durationText)
        layout.addView(controls)
        layout.addView(endCallButton)

        setContentView(layout)
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRunnable)
        super.onDestroy()
    }
}