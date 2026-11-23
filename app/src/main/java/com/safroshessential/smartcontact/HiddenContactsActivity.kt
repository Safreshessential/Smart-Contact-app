package com.safroshessential.smartcontact

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.os.CancellationSignal
import android.content.Context
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(pinInput)
        layout.addView(pinButton)
        layout.addView(fingerprintButton)

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

            pinInput.text.clear()
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
            Toast.makeText(
                this,
                "PIN accepted",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                this,
                "Incorrect PIN",
                Toast.LENGTH_SHORT
            ).show()
        }

        pinInput.text.clear()
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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(
                this,
                "Fingerprint authentication is not supported. Use your PIN.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val fingerprintManager =
            getSystemService(Context.FINGERPRINT_SERVICE) as FingerprintManager

        if (!fingerprintManager.isHardwareDetected) {
            Toast.makeText(
                this,
                "Fingerprint sensor not found. Use your PIN.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!fingerprintManager.hasEnrolledFingerprints()) {
            Toast.makeText(
                this,
                "No fingerprint is registered on this phone.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val cancellationSignal = CancellationSignal()

        val callback = object : FingerprintManager.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(
                result: FingerprintManager.AuthenticationResult?
            ) {
                super.onAuthenticationSucceeded(result)

                runOnUiThread {
                    Toast.makeText(
                        this@HiddenContactsActivity,
                        "Fingerprint accepted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()

                runOnUiThread {
                    Toast.makeText(
                        this@HiddenContactsActivity,
                        "Fingerprint not recognised. Try again.",
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
}