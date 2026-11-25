package com.safroshessential.smartcontact

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private lateinit var searchInput: EditText
    private lateinit var contactsList: LinearLayout

    private var showFavoritesOnly = false

    private data class Contact(
        val id: Long,
        val name: String,
        val phone: String
    )

    private val contacts = mutableListOf<Contact>()

    private val favoritePrefs by lazy {
        getSharedPreferences(
            "SmartContactFavorites",
            MODE_PRIVATE
        )
    }

    private val favoriteIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        favoriteIds.addAll(
            favoritePrefs.getStringSet(
                "favorite_ids",
                emptySet()
            ) ?: emptySet()
        )

        showHomeScreen()
    }

    override fun onResume() {
        super.onResume()

        if (::contactsList.isInitialized &&
            hasContactPermission()
        ) {
            loadContacts()
        }
    }

    private fun showHomeScreen() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 24)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "Smart Contact"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(8, 127, 69))
        }

        val subtitle = TextView(this).apply {
            text = "All your connections in one place"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 20)
        }

        searchInput = EditText(this).apply {
            hint = "Search name or phone number"
            setSingleLine(true)
        }

        searchInput.addTextChangedListener(
            object : android.text.TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    displayContacts()
                }

                override fun afterTextChanged(
                    s: android.text.Editable?
                ) {}
            }
        )

        val addButton = Button(this).apply {
            text = "Add New Contact"

            setOnClickListener {
                addContact()
            }
        }

        val privateButton = Button(this).apply {
            text = "Private Contacts"

            setOnClickListener {
                startActivity(
                    Intent(
                        this@MainActivity,
                        HiddenContactsActivity::class.java
                    )
                )
            }
        }

        val filterButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val allButton = Button(this).apply {
            text = "All Contacts"

            setOnClickListener {
                showFavoritesOnly = false
                displayContacts()
            }
        }

        val favoritesButton = Button(this).apply {
            text = "★ Favourites"

            setOnClickListener {
                showFavoritesOnly = true
                displayContacts()
            }
        }

        filterButtons.addView(
            allButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        filterButtons.addView(
            favoritesButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val heading = TextView(this).apply {
            text = "My Contacts"
            textSize = 21f
            setTextColor(Color.BLACK)
            setPadding(0, 20, 0, 10)
        }

        contactsList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(searchInput)
        layout.addView(addButton)
        layout.addView(privateButton)
        layout.addView(filterButtons)
        layout.addView(heading)
        layout.addView(contactsList)

        setContentView(layout)

        checkContactPermission()
    }

    private fun hasContactPermission(): Boolean {
        return checkSelfPermission(
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkContactPermission() {

        val permissions = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )

        if (!hasContactPermission() ||
            checkSelfPermission(
                Manifest.permission.WRITE_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(permissions, 100)
        } else {
            loadContacts()
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

        if (requestCode == 100) {
            if (hasContactPermission()) {
                loadContacts()
            } else {
                showMessage(
                    "Contact permission is needed to display your contacts."
                )
            }
        }
    }

    private fun loadContacts() {

        contacts.clear()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {

            val idIndex = it.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            )

            val nameIndex = it.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            val phoneIndex = it.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            while (it.moveToNext()) {

                contacts.add(
                    Contact(
                        id = it.getLong(idIndex),
                        name = it.getString(nameIndex) ?: "",
                        phone = it.getString(phoneIndex) ?: ""
                    )
                )
            }
        }

        displayContacts()
    }

    private fun displayContacts() {

        if (!::contactsList.isInitialized) return

        contactsList.removeAllViews()

        val query = searchInput.text.toString().trim()

        val filtered = contacts.filter { contact ->

            val matchesSearch =
                contact.name.contains(query, ignoreCase = true) ||
                contact.phone.contains(query, ignoreCase = true)

            val matchesFavorite =
                !showFavoritesOnly ||
                favoriteIds.contains(contact.id.toString())

            matchesSearch && matchesFavorite
        }

        if (filtered.isEmpty()) {

            contactsList.addView(
                TextView(this).apply {
                    text = if (showFavoritesOnly) {
                        "No favourite contacts yet."
                    } else {
                        "No contacts found."
                    }

                    textSize = 16f
                    setPadding(8, 20, 8, 20)
                }
            )

            return
        }

        filtered.forEach { contact ->

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(Color.rgb(245, 248, 245))
            }

            val name = TextView(this).apply {
                text = contact.name
                textSize = 18f
                setTextColor(Color.BLACK)
            }

            val phone = TextView(this).apply {
                text = contact.phone
                textSize = 15f
                setTextColor(Color.DKGRAY)
            }

            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val isFavorite =
                favoriteIds.contains(contact.id.toString())

            val favoriteButton = Button(this).apply {

                text = if (isFavorite) {
                    "★ Favourite"
                } else {
                    "☆ Add Favourite"
                }

                setOnClickListener {
                    toggleFavorite(contact)
                }
            }

            val editButton = Button(this).apply {
                text = "Edit"

                setOnClickListener {
                    editContact(contact)
                }
            }

            val deleteButton = Button(this).apply {
                text = "Delete"

                setOnClickListener {
                    confirmDelete(contact)
                }
            }

            buttons.addView(favoriteButton)
            buttons.addView(editButton)
            buttons.addView(deleteButton)

            card.addView(name)
            card.addView(phone)
            card.addView(buttons)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            params.bottomMargin = 12

            contactsList.addView(card, params)
        }
    }

    private fun toggleFavorite(contact: Contact) {

        val id = contact.id.toString()

        if (favoriteIds.contains(id)) {
            favoriteIds.remove(id)
            showMessage("Removed from favourites.")
        } else {
            favoriteIds.add(id)
            showMessage("Added to favourites.")
        }

        favoritePrefs.edit()
            .putStringSet(
                "favorite_ids",
                favoriteIds.toSet()
            )
            .apply()

        displayContacts()
    }

    private fun addContact() {

        val intent = Intent(
            Intent.ACTION_INSERT,
            ContactsContract.Contacts.CONTENT_URI
        )

        startActivity(intent)
    }

    private fun editContact(contact: Contact) {

        val uri = ContentUris.withAppendedId(
            ContactsContract.Contacts.CONTENT_URI,
            contact.id
        )

        val intent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(
                uri,
                ContactsContract.Contacts.CONTENT_ITEM_TYPE
            )
        }

        startActivity(intent)
    }

    private fun confirmDelete(contact: Contact) {

        AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setMessage(
                "Are you sure you want to delete ${contact.name}?"
            )
            .setPositiveButton("Delete") { _, _ ->
                deleteContact(contact)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteContact(contact: Contact) {

        if (checkSelfPermission(
                Manifest.permission.WRITE_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            checkContactPermission()
            return
        }

        try {

            val uri = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI,
                contact.id
            )

            contentResolver.delete(uri, null, null)

            favoriteIds.remove(contact.id.toString())

            favoritePrefs.edit()
                .putStringSet(
                    "favorite_ids",
                    favoriteIds.toSet()
                )
                .apply()

            showMessage("Contact deleted.")
            loadContacts()

        } catch (e: Exception) {

            showMessage(
                "Unable to delete this contact."
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