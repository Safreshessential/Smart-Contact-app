package com.safroshessential.smartcontact

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException

class EmailRecoveryActivity : Activity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText

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
            text = "Register your recovery email"
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        emailInput = EditText(this).apply {
            hint = "Your email address"
            inputType = 33
        }

        passwordInput = EditText(this).apply {
            hint = "Create account password"
            inputType = 129
        }

        val registerButton = Button(this).apply {
            text = "Register Recovery Email"
            setOnClickListener {
                registerEmail()
            }
        }

        val resetButton = Button(this).apply {
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
        layout.addView(resetButton)

        setContentView(layout)
    }

    private fun registerEmail() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (email.isBlank() || password.length < 6) {
            Toast.makeText(
                this,
                "Enter a valid email and password of at least 6 characters",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->

                if (task.isSuccessful) {
                    auth.currentUser?.sendEmailVerification()
                        ?.addOnCompleteListener { verificationTask ->
                            if (verificationTask.isSuccessful) {
                                Toast.makeText(
                                    this,
                                    "Verification email sent. Check your inbox.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Account created, but verification email failed.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                } else {
                    Toast.makeText(
                        this,
                        task.exception?.localizedMessage
                            ?: "Registration failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun sendPasswordReset() {
        val email = emailInput.text.toString().trim()

        if (email.isBlank()) {
            Toast.makeText(
                this,
                "Enter your registered email address",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener(this) { task ->
                val message = if (task.isSuccessful) {
                    "If the account exists, a password reset email has been sent."
                } else {
                    "Unable to send reset email. Please try again."
                }

                Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}