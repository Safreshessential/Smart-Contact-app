package com.safroshessential.smartcontact

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Locale

class ContactBackupActivity : Activity() {

    private val requestPermissionsCode = 100
    private val createBackupCode = 101
    private val restoreBackupCode = 102

    private var pendingAction = ""

    private val green = 0xFF168447.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xFFF5FFF7.toInt())
        }

        val title = TextView(this).apply {
            text = "Contact Backup & Restore"
            textSize = 24f
            setTextColor(green)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 30)
        }

        val description = TextView(this).apply {
            text = "Protect your contacts by creating a backup file. You can restore your contacts whenever you need them."
            textSize = 16f
            setTextColor(0xFF333333.toInt())
            setPadding(0, 0, 0, 30)
        }

        val backupButton = Button(this).apply {
            text = "Back Up Contacts"
            setOnClickListener {
                checkPermissions("backup")
            }
        }

        val restoreButton = Button(this).apply {
            text = "Restore Contacts"
            setOnClickListener {
                checkPermissions("restore")
            }
        }

        val info = TextView(this).apply {
            text = "Backup format: VCF (vCard)\n\nKeep your backup file somewhere safe. Restoring may add contacts that are already on your phone."
            textSize = 14f
            setTextColor(0xFF555555.toInt())
            setPadding(0, 30, 0, 0)
        }

        layout.addView(title)
        layout.addView(description)
        layout.addView(backupButton)
        layout.addView(restoreButton)
        layout.addView(info)

        setContentView(layout)
    }

    private fun checkPermissions(action: String) {
        pendingAction = action

        val permissions = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )

        val missing = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            continueAction()
        } else {
            requestPermissions(
                missing.toTypedArray(),
                requestPermissionsCode
            )
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

        if (requestCode == requestPermissionsCode) {
            if (grantResults.isNotEmpty() &&
                grantResults.all {
                    it == PackageManager.PERMISSION_GRANTED
                }
            ) {
                continueAction()
            } else {
                showMessage("Contact permissions are required.")
            }
        }
    }

    private fun continueAction() {
        if (pendingAction == "backup") {
            createBackupFile()
        } else if (pendingAction == "restore") {
            openBackupFile()
        }
    }

    private fun createBackupFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/vcard"
            putExtra(
                Intent.EXTRA_TITLE,
                "SmartContact_Backup.vcf"
            )
        }

        startActivityForResult(intent, createBackupCode)
    }

    private fun openBackupFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        startActivityForResult(intent, restoreBackupCode)
    }

    @Deprecated("Using activity result callback for file selection")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) return

        val uri = data?.data ?: return

        when (requestCode) {
            createBackupCode -> exportContacts(uri)
            restoreBackupCode -> importContacts(uri)
        }
    }

    private fun exportContacts(uri: Uri) {
        try {
            val writer = OutputStreamWriter(
                contentResolver.openOutputStream(uri) ?: return,
                Charsets.UTF_8
            )

            var count = 0

            writer.use { output ->

                val cursor = contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.DISPLAY_NAME
                    ),
                    null,
                    null,
                    ContactsContract.Contacts.DISPLAY_NAME + " ASC"
                )

                cursor?.use { contacts ->

                    while (contacts.moveToNext()) {

                        val contactId = contacts.getString(0)
                        val name = contacts.getString(1) ?: ""

                        val phones = mutableListOf<String>()
                        val emails = mutableListOf<String>()

                        contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                            ),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                            arrayOf(contactId),
                            null
                        )?.use { phoneCursor ->
                            while (phoneCursor.moveToNext()) {
                                phones.add(phoneCursor.getString(0) ?: "")
                            }
                        }

                        contentResolver.query(
                            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                            arrayOf(
                                ContactsContract.CommonDataKinds.Email.ADDRESS
                            ),
                            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID}=?",
                            arrayOf(contactId),
                            null
                        )?.use { emailCursor ->
                            while (emailCursor.moveToNext()) {
                                emails.add(emailCursor.getString(0) ?: "")
                            }
                        }

                        output.write("BEGIN:VCARD\r\n")
                        output.write("VERSION:3.0\r\n")
                        output.write("FN:${escape(name)}\r\n")

                        for (phone in phones) {
                            output.write("TEL:${escape(phone)}\r\n")
                        }

                        for (email in emails) {
                            output.write("EMAIL:${escape(email)}\r\n")
                        }

                        output.write("END:VCARD\r\n")

                        count++
                    }
                }
            }

            showMessage("$count contacts backed up successfully.")

        } catch (e: Exception) {
            showMessage("Backup failed: ${e.message}")
        }
    }

    private fun importContacts(uri: Uri) {
        try {
            val reader = BufferedReader(
                InputStreamReader(
                    contentResolver.openInputStream(uri)
                        ?: throw Exception("Unable to open backup file")
                )
            )

            val cards = mutableListOf<List<String>>()
            val currentCard = mutableListOf<String>()
            var insideCard = false

            reader.useLines { lines ->
                lines.forEach { line ->
                    val cleanLine = line.trim()

                    when {
                        cleanLine.equals(
                            "BEGIN:VCARD",
                            ignoreCase = true
                        ) -> {
                            currentCard.clear()
                            insideCard = true
                        }

                        cleanLine.equals(
                            "END:VCARD",
                            ignoreCase = true
                        ) -> {
                            if (insideCard) {
                                cards.add(currentCard.toList())
                            }
                            insideCard = false
                        }

                        insideCard -> currentCard.add(cleanLine)
                    }
                }
            }

            var restored = 0

            for (card in cards) {
                if (saveContactFromCard(card)) {
                    restored++
                }
            }

            AlertDialog.Builder(this)
                .setTitle("Restore Complete")
                .setMessage(
                    "$restored contacts restored.\n\nExisting contacts may be duplicated."
                )
                .setPositiveButton("OK", null)
                .show()

        } catch (e: Exception) {
            showMessage("Restore failed: ${e.message}")
        }
    }

    private fun saveContactFromCard(
        lines: List<String>
    ): Boolean {

        try {
            var name = ""
            val phones = mutableListOf<String>()
            val emails = mutableListOf<String>()

            for (line in lines) {
                val separator = line.indexOf(':')
                if (separator < 0) continue

                val property = line.substring(0, separator)
                    .substringBefore(';')
                    .uppercase(Locale.ROOT)

                val value = unescape(
                    line.substring(separator + 1)
                )

                when (property) {
                    "FN" -> name = value
                    "TEL" -> if (value.isNotBlank()) phones.add(value)
                    "EMAIL" -> if (value.isNotBlank()) emails.add(value)
                }
            }

            if (name.isBlank() &&
                phones.isEmpty() &&
                emails.isEmpty()
            ) {
                return false
            }

            val operations =
                ArrayList<android.content.ContentProviderOperation>()

            operations.add(
                android.content.ContentProviderOperation
                    .newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(
                        ContactsContract.RawContacts.ACCOUNT_TYPE,
                        null
                    )
                    .withValue(
                        ContactsContract.RawContacts.ACCOUNT_NAME,
                        null
                    )
                    .build()
            )

            operations.add(
                android.content.ContentProviderOperation
                    .newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(
                        ContactsContract.Data.RAW_CONTACT_ID,
                        0
                    )
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                        name
                    )
                    .build()
            )

            for (phone in phones) {
                operations.add(
                    android.content.ContentProviderOperation
                        .newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(
                            ContactsContract.Data.RAW_CONTACT_ID,
                            0
                        )
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            phone
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        )
                        .build()
                )
            }

            for (email in emails) {
                operations.add(
                    android.content.ContentProviderOperation
                        .newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(
                            ContactsContract.Data.RAW_CONTACT_ID,
                            0
                        )
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.Email.ADDRESS,
                            email
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.Email.TYPE,
                            ContactsContract.CommonDataKinds.Email.TYPE_HOME
                        )
                        .build()
                )
            }

            contentResolver.applyBatch(
                ContactsContract.AUTHORITY,
                operations
            )

            return true

        } catch (e: Exception) {
            return false
        }
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace(",", "\\,")
            .replace(";", "\\;")
    }

    private fun unescape(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}