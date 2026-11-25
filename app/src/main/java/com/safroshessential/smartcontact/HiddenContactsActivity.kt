package com.safroshessential.smartcontact

import android.app.Activity
import android.os.Bundle
import android.os.CancellationSignal
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.hardware.fingerprint.FingerprintManager
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class HiddenContactsActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("HiddenContactSecurity", Context.MODE_PRIVATE)
    }

    private lateinit var pinInput: EditText
    private lateinit var storage: PrivateContactStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storage = PrivateContactStorage(this)

        showLoginScreen()
    }

    private fun showLoginScreen() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "Private Contacts"
            textSize = 28f
            setTextColor(Color.rgb(8, 127, 69))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = if (prefs.contains("pin_hash")) {
                "Enter your private PIN to continue"
            } else {
                "Create a private PIN of at least 6 digits"
            }
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        pinInput = EditText(this).apply {
            hint = "Enter PIN"
            inputType = 2 or 16
            gravity = Gravity.CENTER
        }

        val pinButton = Button(this).apply {
            text = if (prefs.contains("pin_hash")) {
                "Unlock with PIN"
            } else {
                "Create PIN"
            }

            setOnClickListener {
                handlePin()
            }
        }

        val fingerprintButton = Button(this).apply {
            text = "Unlock with Fingerprint"

            setOnClickListener {
                authenticateFingerprint()
            }
        }

        // Recovery Email Button
        val recoveryButton = Button(this).apply {
            text = "Recovery Email"

            setOnClickListener {
                val intent = Intent(
                    this@HiddenContactsActivity,
                    EmailRecoveryActivity::class.java
                )

                startActivity(intent)
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(pinInput)
        layout.addView(pinButton)
        layout.addView(fingerprintButton)
        layout.addView(recoveryButton)

        setContentView(layout)
    }

    private fun handlePin() {

        val pin = pinInput.text.toString()

        if (pin.length < 6 || !pin.all { it.isDigit() }) {
            Toast.makeText(
                this,
                "PIN must contain at least 6 digits",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!prefs.contains("pin_hash")) {

            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)

            prefs.edit()
                .putString(
                    "pin_salt",
                    salt.joinToString("") { "%02x".format(it) }
                )
                .putString("pin_hash", hashPin(pin, salt))
                .apply()

            Toast.makeText(
                this,
                "Private PIN created",
                Toast.LENGTH_SHORT
            ).show()

            showVault()
            return
        }

        val salt = hexToBytes(
            prefs.getString("pin_salt", "") ?: ""
        )

        val savedHash = prefs.getString("pin_hash", "") ?: ""

        if (MessageDigest.isEqual(
                hashPin(pin, salt).toByteArray(),
                savedHash.toByteArray()
            )
        ) {

            pinInput.text.clear()
            showVault()

        } else {

            Toast.makeText(
                this,
                "Incorrect PIN",
                Toast.LENGTH_SHORT
            ).show()

            pinInput.text.clear()
        }
    }

    private fun hashPin(pin: String, salt: ByteArray): String {

        val spec = PBEKeySpec(
            pin.toCharArray(),
            salt,
            120000,
            256
        )

        val result = SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded

        spec.clearPassword()

        return result.joinToString("") {
            "%02x".format(it)
        }
    }

    private fun hexToBytes(value: String): ByteArray {
        return value.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun authenticateFingerprint() {

        if (!prefs.contains("pin_hash")) {
            Toast.makeText(
                this,
                "Create your private PIN first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val fingerprintManager =
            getSystemService(Context.FINGERPRINT_SERVICE)
                as FingerprintManager

        if (!fingerprintManager.isHardwareDetected ||
            !fingerprintManager.hasEnrolledFingerprints()
        ) {
            Toast.makeText(
                this,
                "Fingerprint unavailable. Please use your PIN.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val cancellationSignal = CancellationSignal()

        val callback =
            object : FingerprintManager.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result: FingerprintManager.AuthenticationResult?
                ) {
                    super.onAuthenticationSucceeded(result)

                    runOnUiThread {
                        showVault()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()

                    runOnUiThread {
                        Toast.makeText(
                            this@HiddenContactsActivity,
                            "Fingerprint not recognised.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)

                    runOnUiThread {
                        Toast.makeText(
                            this@HiddenContactsActivity,
                            errString,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

        fingerprintManager.authenticate(
            null,
            cancellationSignal,
            0,
            callback,
            null
        )
    }

    private fun showVault() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "My Private Contacts"
            textSize = 25f
            setTextColor(Color.rgb(8, 127, 69))
            gravity = Gravity.CENTER
        }

        val nameInput = EditText(this).apply {
            hint = "Contact name"
        }

        val phoneInput = EditText(this).apply {
            hint = "Phone number"
            inputType = 3
        }

        val saveButton = Button(this).apply {
            text = "Save Private Contact"

            setOnClickListener {

                val name = nameInput.text.toString()
                val phone = phoneInput.text.toString()

                if (storage.saveContact(name, phone)) {

                    nameInput.text.clear()
                    phoneInput.text.clear()

                    Toast.makeText(
                        this@HiddenContactsActivity,
                        "Private contact saved",
                        Toast.LENGTH_SHORT
                    ).show()

                    refreshContacts(layout)

                } else {

                    Toast.makeText(
                        this@HiddenContactsActivity,
                        "Enter a name and phone number",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val contactsTitle = TextView(this).apply {
            text = "Saved Contacts"
            textSize = 20f
            setPadding(0, 24, 0, 12)
        }

        layout.addView(title)
        layout.addView(nameInput)
        layout.addView(phoneInput)
        layout.addView(saveButton)
        layout.addView(contactsTitle)

        setContentView(layout)

        refreshContacts(layout)
    }

    private fun refreshContacts(layout: LinearLayout) {

        val oldList =
            layout.findViewWithTag<LinearLayout>("private_list")

        if (oldList != null) {
            layout.removeView(oldList)
        }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "private_list"
        }

        val contacts = storage.getContacts()

        if (contacts.isEmpty()) {

            list.addView(TextView(this).apply {
                text = "No private contacts saved yet."
                textSize = 16f
            })

        } else {

            contacts.forEach { contact ->

                list.addView(TextView(this).apply {
                    text = "${contact.name}\n${contact.phone}"
                    textSize = 17f
                    setPadding(8, 12, 8, 12)
                })
            }
        }

        layout.addView(list)
    }
}