package com.safroshessential.smartcontact

import android.os.Bundle
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class HiddenContactsActivity : FragmentActivity() {

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
            Toast.makeText(this, "PIN must contain at least 6 digits", Toast.LENGTH_SHORT).show()
            return
        }

        if (!prefs.contains("pin_hash")) {
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)

            prefs.edit()
                .putString("pin_salt", salt.joinToString("") { "%02x".format(it) })
                .putString("pin_hash", hashPin(pin, salt))
                .apply()

            Toast.makeText(this, "Private PIN created", Toast.LENGTH_SHORT).show()
            return
        }

        val salt = hexToBytes(prefs.getString("pin_salt", "") ?: "")
        val savedHash = prefs.getString("pin_hash", "") ?: ""

        if (MessageDigest.isEqual(
                hashPin(pin, salt).toByteArray(),
                savedHash.toByteArray()
            )) {
            Toast.makeText(this, "PIN accepted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
        }

        pinInput.text.clear()
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120000, 256)
        val result = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        spec.clearPassword()
        return result.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(value: String): ByteArray {
        return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun authenticateFingerprint() {
        val manager = BiometricManager.from(this)

        if (manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            ) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            Toast.makeText(
                this,
                "Fingerprint is unavailable. Please use your PIN.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(
                        this@HiddenContactsActivity,
                        "Fingerprint accepted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Private Contacts")
            .setSubtitle("Verify your fingerprint")
            .setNegativeButtonText("Use PIN instead")
            .build()

        prompt.authenticate(info)
    }
}