package com.safroshessential.smartcontact

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 30, 30, 30)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "Smart Contact"
            textSize = 30f
            setTextColor(Color.rgb(8, 127, 69))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "All your connections in one place"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 15, 0, 25)
        }

        val privateContactsButton = Button(this).apply {
            text = "Private Contacts"

            setOnClickListener {
                val intent = Intent(
                    this@MainActivity,
                    HiddenContactsActivity::class.java
                )
                startActivity(intent)
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(privateContactsButton)

        setContentView(layout)
    }
}