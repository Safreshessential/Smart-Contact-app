
package com.safroshessential.smartcontact

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.view.ViewGroup

class SpamProtectionActivity : Activity() {

    private val green = Color.rgb(20, 130, 75)
    private val darkGreen = Color.rgb(10, 95, 55)
    private val preferencesName = "SmartContactSpam"
    private val numbersKey = "known_spam_numbers"

    private lateinit var content: LinearLayout
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 249, 246))
            setPadding(20, 20, 20, 20)
        }

        val header = TextView(this).apply {
            text = "Spam Protection"
            textSize = 25f
            setTextColor(darkGreen)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10, 0, 20)
        }
        root.addView(header)

        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(14, 14, 14, 14)
            setBackgroundColor(Color.WHITE)
        }
        root.addView(statusText)

        val roleButton = makeButton("Enable Call Screening")
        roleButton.setOnClickListener {
            requestCallScreeningRole()
        }
        root.addView(roleButton)

        val addButton = makeButton("Add Spam Number")
        addButton.setOnClickListener {
            showAddNumberDialog()
        }
        root.addView(addButton)

        val heading = TextView(this).apply {
            text = "Blocked Numbers"
            textSize = 20f
            setTextColor(darkGreen)
            setPadding(0, 24, 0, 12)
        }
        root.addView(heading)

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val backButton = makeButton("Back")
        backButton.setOnClickListener { finish() }
        root.addView(backButton)

        setContentView(root)
        refreshScreen()
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(green)
            isAllCaps = false
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 8, 0, 8)
            layoutParams = params
        }
    }

    private fun getSpamNumbers(): MutableSet<String> {
        val saved = getSharedPreferences(
            preferencesName,
            Context.MODE_PRIVATE
        ).getStringSet(numbersKey, emptySet()) ?: emptySet()

        return saved.toMutableSet()
    }

    private fun saveSpamNumbers(numbers: Set<String>) {
        getSharedPreferences(
            preferencesName,
            Context.MODE_PRIVATE
        ).edit().putStringSet(
            numbersKey,
            numbers.toSet()
        ).apply()
    }

    private fun showAddNumberDialog() {
        val input = EditText(this).apply {
            hint = "Enter phone number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }

        AlertDialog.Builder(this)
            .setTitle("Add Spam Number")
            .setMessage("Enter the number you want Smart Contact to block.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val number = input.text.toString().trim()

                if (number.length < 5) {
                    showMessage("Please enter a valid phone number.")
                    return@setPositiveButton
                }

                val numbers = getSpamNumbers()
                val alreadySaved = numbers.any {
                    PhoneNumberUtils.compare(it, number)
                }

                if (alreadySaved) {
                    showMessage("This number is already blocked.")
                } else {
                    numbers.add(number)
                    saveSpamNumbers(numbers)
                    refreshScreen()
                    showMessage("Spam number saved.")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeNumber(number: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove Blocked Number")
            .setMessage("Remove $number from your spam list?")
            .setPositiveButton("Remove") { _, _ ->
                val numbers = getSpamNumbers()
                numbers.remove(number)
                saveSpamNumbers(numbers)
                refreshScreen()
                showMessage("Number removed.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshScreen() {
        statusText.text = if (isCallScreeningEnabled()) {
            "Call screening is enabled. Smart Contact can block numbers in your list."
        } else {
            "Call screening is not enabled. Enable it to allow Smart Contact to block known spam calls."
        }

        content.removeAllViews()

        val numbers = getSpamNumbers().sorted()

        if (numbers.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No blocked numbers yet."
                textSize = 16f
                setTextColor(Color.GRAY)
                setPadding(12, 20, 12, 20)
            }
            content.addView(emptyText)
            return
        }

        numbers.forEach { number ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 10, 12, 10)
                setBackgroundColor(Color.WHITE)
            }

            val numberText = TextView(this).apply {
                text = number
                textSize = 17f
                setTextColor(Color.BLACK)
            }

            row.addView(
                numberText,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )

            val removeButton = Button(this).apply {
                text = "Remove"
                isAllCaps = false
                setOnClickListener { removeNumber(number) }
            }

            row.addView(removeButton)

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 5, 0, 5)
            content.addView(row, params)
        }
    }

    private fun isCallScreeningEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        val roleManager = getSystemService(RoleManager::class.java)
        return roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)

            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
            ) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    showMessage("Smart Contact already has call screening access.")
                } else {
                    val intent = roleManager.createRequestRoleIntent(
                        RoleManager.ROLE_CALL_SCREENING
                    )
                    startActivityForResult(intent, 200)
                }
            } else {
                showMessage("Call screening is not available on this device.")
            }
        } else {
            startActivity(
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            )
        }
    }

    @Deprecated("Handled for Android role selection")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 200) {
            refreshScreen()
            showMessage(
                if (resultCode == Activity.RESULT_OK) {
                    "Call screening enabled."
                } else {
                    "Call screening was not enabled."
                }
            )
        }
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(
            this,
            message,
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            refreshScreen()
        }
    }
}
