package com.safroshessential.smartcontact

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class EmailRecoveryActivity : Activity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText

    private val securityPrefs by lazy {
        getSharedPreferences(
            "HiddenContactSecurity",
            MODE_PRIVATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        showEmailScreen()
    }

    private fun showEmailScreen() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 30, 30, 30)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "Smart Contact"
            textSize = 28f
            setTextColor(Color.rgb(8, 127, 69))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Private Contacts Recovery"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        emailInput = EditText(this).apply {
            hint = "Your recovery email"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        passwordInput = EditText(this).apply {
            hint = "Account password"
            inputType =
                InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val registerButton = Button(this).apply {
            text = "Register Recovery Email"
            setOnClickListener {
                registerEmail()
            }
        }

        val loginButton = Button(this).apply {
            text = "Sign In to Recovery Account"
            setOnClickListener {
                signIn()
            }
        }

        val verifyButton = Button(this).apply {
            text = "I Have Verified My Email"
            setOnClickListener {
                verifyAndLinkEmail()
            }
        }

        val resetButton = Button(this).apply {
            text = "Reset Private Contacts PIN"
            setOnClickListener {
                resetPrivatePin()
            }
        }

        val passwordResetButton = Button(this).apply {
            text = "Forgot Account Password?"
            setOnClickListener {
                sendPasswordReset()
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(emailInput)
        layout.addView(passwordInput)
        layout.addView(registerButton)
        layout.addView(loginButton)
        layout.addView(verifyButton)
        layout.addView(resetButton)
        layout.addView(passwordResetButton)

        setContentView(layout)
    }

    private fun registerEmail() {

        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (email.isBlank() || password.length < 6) {
            showMessage(
                "Enter a valid email and a password of at least 6 characters."
            )
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->

                if (task.isSuccessful) {

                    auth.currentUser?.sendEmailVerification()
                        ?.addOnCompleteListener { verificationTask ->

                            if (verificationTask.isSuccessful) {
                                showMessage(
                                    "Verification email sent. Open your inbox and verify your email. Then return here."
                                )
                            } else {
                                showMessage(
                                    "Account created, but verification email could not be sent. Try again."
                                )
                            }
                        }

                } else {

                    showMessage(
                        task.exception?.localizedMessage
                            ?: "Registration failed."
                    )
                }
            }
    }

    private fun signIn() {

        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (email.isBlank() || password.isBlank()) {
            showMessage("Enter your email and password.")
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->

                if (task.isSuccessful) {

                    showMessage(
                        "Signed in. Now check your email verification."
                    )

                } else {

                    showMessage(
                        task.exception?.localizedMessage
                            ?: "Sign in failed."
                    )
                }
            }
    }

    private fun verifyAndLinkEmail() {

        val user = auth.currentUser

        if (user == null) {
            showMessage(
                "Please register or sign in first."
            )
            return
        }

        user.reload().addOnCompleteListener(this) { task ->

            if (!task.isSuccessful) {
                showMessage(
                    "Could not check email verification. Try again."
                )
                return@addOnCompleteListener
            }

            val verifiedUser = auth.currentUser

            if (verifiedUser?.isEmailVerified != true) {
                showMessage(
                    "Your email is not verified yet. Check your inbox first."
                )
                return@addOnCompleteListener
            }

            val savedUid = securityPrefs.getString(
                "recovery_firebase_uid",
                null
            )

            if (savedUid != null &&
                savedUid != verifiedUser.uid
            ) {
                showMessage(
                    "This recovery vault is already linked to another email account."
                )
                return@addOnCompleteListener
            }

            securityPrefs.edit()
                .putString(
                    "recovery_firebase_uid",
                    verifiedUser.uid
                )
                .putString(
                    "recovery_email",
                    verifiedUser.email
                )
                .apply()

            showMessage(
                "Your verified email is now linked to Private Contacts. You can reset your PIN."
            )
        }
    }

    private fun resetPrivatePin() {

        val user = auth.currentUser

        if (user == null) {
            showMessage(
                "Sign in to your recovery email account first."
            )
            return
        }

        user.reload().addOnCompleteListener(this) { task ->

            if (!task.isSuccessful) {
                showMessage(
                    "Unable to verify your account. Try again."
                )
                return@addOnCompleteListener
            }

            val verifiedUser = auth.currentUser

            val savedUid = securityPrefs.getString(
                "recovery_firebase_uid",
                null
            )

            if (verifiedUser?.isEmailVerified != true ||
                savedUid == null ||
                verifiedUser.uid != savedUid
            ) {
                showMessage(
                    "Recovery denied. Use the verified email originally linked to this vault."
                )
                return@addOnCompleteListener
            }

            showNewPinDialog()
        }
    }

    private fun showNewPinDialog() {

        val newPinInput = EditText(this).apply {
            hint = "Enter new PIN (at least 6 digits)"
            inputType =
                InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Create New Private PIN")
            .setMessage(
                "Your verified recovery account has been accepted. Enter your new PIN."
            )
            .setView(newPinInput)
            .setPositiveButton("Save New PIN") { _, _ ->

                val newPin = newPinInput.text.toString()

                if (newPin.length < 6 ||
                    !newPin.all { it.isDigit() }
                ) {
                    showMessage(
                        "PIN must contain at least 6 digits."
                    )
                    return@setPositiveButton
                }

                saveNewPin(newPin)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveNewPin(pin: String) {

        try {

            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)

            val spec = PBEKeySpec(
                pin.toCharArray(),
                salt,
                120000,
                256
            )

            val hash = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded

            spec.clearPassword()

            val saltHex = salt.joinToString("") {
                "%02x".format(it)
            }

            val hashHex = hash.joinToString("") {
                "%02x".format(it)
            }

            securityPrefs.edit()
                .putString("pin_salt", saltHex)
                .putString("pin_hash", hashHex)
                .apply()

            showMessage(
                "Your Private Contacts PIN has been changed successfully."
            )

        } catch (e: Exception) {

            showMessage(
                "Unable to change PIN. Please try again."
            )
        }
    }

    private fun sendPasswordReset() {

        val email = emailInput.text.toString().trim()

        if (email.isBlank()) {
            showMessage("Enter your registered email address.")
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener(this) { task ->

                showMessage(
                    if (task.isSuccessful) {
                        "If the account exists, a password reset email has been sent."
                    } else {
                        "Unable to send password reset email. Try again."
                    }
                )
            }
    }

    private fun showMessage(message: String) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}