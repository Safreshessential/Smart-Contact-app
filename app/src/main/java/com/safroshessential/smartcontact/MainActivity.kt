package com.safroshessential.smartcontact

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.widget.*
import java.io.OutputStreamWriter

class MainActivity : Activity() {

    private lateinit var searchInput: EditText
    private lateinit var contactsList: LinearLayout

    private var showFavoritesOnly = false
    private var selectedContact: Contact? = null

    private data class Contact(
        val id: Long,
        val name: String,
        val phone: String,
        val photoUri: String?
    )

    private val contacts = mutableListOf<Contact>()

    private val favoritePrefs by lazy {
        getSharedPreferences("SmartContactFavorites", MODE_PRIVATE)
    }

    private val favoriteIds = mutableSetOf<String>()

    companion object {
        private const val PICK_IMAGE_REQUEST = 200
        private const val IMPORT_CONTACTS_REQUEST = 201
        private const val EXPORT_CONTACTS_REQUEST = 202
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        favoriteIds.addAll(
            favoritePrefs.getStringSet("favorite_ids", emptySet())
                ?: emptySet()
        )

        showHomeScreen()
    }

    override fun onResume() {
        super.onResume()

        if (::contactsList.isInitialized && hasContactPermission()) {
            loadContacts()
        }
    }

    // HOME SCREEN

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
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?, start: Int, before: Int, count: Int
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

        val moreButton = Button(this).apply {
            text = "☰ More / Tools"
            setOnClickListener {
                showMoreMenu()
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
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        filterButtons.addView(
            favoritesButton,
            LinearLayout.LayoutParams(0, -2, 1f)
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

        val scrollView = ScrollView(this).apply {
            addView(contactsList)
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(searchInput)
        layout.addView(addButton)
        layout.addView(moreButton)
        layout.addView(filterButtons)
        layout.addView(heading)

        layout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(layout)
        checkContactPermission()
    }

    // MORE / TOOLS MENU

    private fun showMoreMenu() {
        val options = arrayOf(
            "Private Contacts",
            "Import Contacts",
            "Export Contacts",
            "Find Duplicate Contacts"
        )

        AlertDialog.Builder(this)
            .setTitle("More / Tools")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openPrivateContacts()
                    1 -> importContacts()
                    2 -> exportContacts()
                    3 -> showDuplicateContacts()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openPrivateContacts() {
        try {
            startActivity(
                Intent(
                    this,
                    HiddenContactsActivity::class.java
                )
            )
        } catch (e: Exception) {
            showMessage("Unable to open Private Contacts.")
        }
    }

    // CONTACT PERMISSIONS

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

    // LOAD CONTACTS

    private fun loadContacts() {
        contacts.clear()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
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

            val photoIndex = it.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
            )

            while (it.moveToNext()) {
                contacts.add(
                    Contact(
                        id = it.getLong(idIndex),
                        name = it.getString(nameIndex) ?: "",
                        phone = it.getString(phoneIndex) ?: "",
                        photoUri = if (photoIndex >= 0) {
                            it.getString(photoIndex)
                        } else {
                            null
                        }
                    )
                )
            }
        }

        displayContacts()
    }

    // DISPLAY CONTACTS

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

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    showContactDetails(contact)
                }
            }

            val picture = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(64, 64)
                scaleType = ImageView.ScaleType.CENTER_CROP

                if (!contact.photoUri.isNullOrBlank()) {
                    try {
                        setImageURI(Uri.parse(contact.photoUri))
                    } catch (e: Exception) {
                        setImageDrawable(null)
                    }
                }

                if (drawable == null) {
                    setBackgroundColor(Color.rgb(8, 127, 69))
                }
            }

            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 4, 4, 4)
                setOnClickListener {
                    showContactDetails(contact)
                }
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

            val openDetails = TextView(this).apply {
                text = "View details"
                textSize = 13f
                setTextColor(Color.rgb(8, 127, 69))
            }

            details.addView(name)
            details.addView(phone)
            details.addView(openDetails)

            topRow.addView(picture)

            topRow.addView(
                details,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val isFavorite =
                favoriteIds.contains(contact.id.toString())

            val favoriteButton = Button(this).apply {
                text = if (isFavorite) {
                    "★ Favourite"
                } else {
                    "☆ Favourite"
                }

                setOnClickListener {
                    toggleFavorite(contact)
                }
            }

            val pictureButton = Button(this).apply {
                text = "Picture"
                setOnClickListener {
                    chooseContactPicture(contact)
                }
            }

            val editButton = Button(this).apply {
                text = "Edit"
                setOnClickListener {
                    editContact(contact)
                }
            }

            buttons.addView(favoriteButton)
            buttons.addView(pictureButton)
            buttons.addView(editButton)

            card.addView(topRow)
            card.addView(buttons)

            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = 12

            contactsList.addView(card, params)
        }
    }

    // IMPORT CONTACTS

    private fun importContacts() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        try {
            startActivityForResult(
                intent,
                IMPORT_CONTACTS_REQUEST
            )
        } catch (e: Exception) {
            showMessage("Unable to open your phone files.")
        }
    }

    private fun openVcfImporter(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/x-vcard")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(intent)

            showMessage(
                "Choose the contacts import option to continue."
            )

        } catch (e: android.content.ActivityNotFoundException) {
            showMessage(
                "No compatible VCF importer was found on this phone."
            )
        } catch (e: Exception) {
            showMessage("Unable to open this contact backup.")
        }
    }

    // EXPORT CONTACTS

    private fun exportContacts() {
        if (!hasContactPermission()) {
            checkContactPermission()
            showMessage("Allow contact permission, then try exporting again.")
            return
        }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/vcard"
            putExtra(
                Intent.EXTRA_TITLE,
                "SmartContact-Contacts.vcf"
            )
        }

        try {
            startActivityForResult(
                intent,
                EXPORT_CONTACTS_REQUEST
            )
        } catch (e: Exception) {
            showMessage("Unable to open the save location.")
        }
    }

    private fun escapeVCard(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace(";", "\\;")
            .replace(",", "\\,")
    }

    private fun writeVCardFile(uri: Uri) {
        try {
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

            val contactNumbers =
                linkedMapOf<Long, Pair<String, MutableList<String>>>()

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
                    val id = it.getLong(idIndex)
                    val name = it.getString(nameIndex) ?: ""
                    val phone = it.getString(phoneIndex) ?: ""

                    if (phone.isNotBlank()) {
                        val entry = contactNumbers.getOrPut(id) {
                            Pair(name, mutableListOf())
                        }

                        if (!entry.second.contains(phone)) {
                            entry.second.add(phone)
                        }
                    }
                }
            }

            if (contactNumbers.isEmpty()) {
                showMessage("There are no phone contacts to export.")
                return
            }

            val output = contentResolver.openOutputStream(uri)

            if (output == null) {
                showMessage("Unable to create the backup file.")
                return
            }

            output.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->

                    contactNumbers.values.forEach { entry ->
                        val name = entry.first
                        val numbers = entry.second

                        writer.write("BEGIN:VCARD\r\n")
                        writer.write("VERSION:3.0\r\n")
                        writer.write("FN:${escapeVCard(name)}\r\n")
                        writer.write("N:${escapeVCard(name)};;;;\r\n")

                        numbers.forEach { number ->
                            writer.write(
                                "TEL:${escapeVCard(number)}\r\n"
                            )
                        }

                        writer.write("END:VCARD\r\n")
                    }
                }
            }

            showMessage(
                "${contactNumbers.size} contacts exported successfully."
            )

        } catch (e: Exception) {
            showMessage(
                "Unable to export contacts. Please try again."
            )
        }
    }

    // DUPLICATE CONTACT DETECTION

    private fun normalizePhoneNumber(number: String): String {
        return number.filter { it.isDigit() }
    }

    private fun findDuplicateGroups():
        List<Pair<String, List<Contact>>> {

        return contacts
            .filter {
                normalizePhoneNumber(it.phone).isNotBlank()
            }
            .groupBy {
                normalizePhoneNumber(it.phone)
            }
            .mapNotNull { (number, group) ->
                val uniqueContacts = group.distinctBy { it.id }

                if (uniqueContacts.size > 1) {
                    Pair(number, uniqueContacts)
                } else {
                    null
                }
            }
            .sortedBy { it.first }
    }

    private fun showDuplicateContacts() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 24)
            setBackgroundColor(Color.WHITE)
        }

        val backButton = Button(this).apply {
            text = "← Back to Contacts"
            setOnClickListener {
                showHomeScreen()
            }
        }

        val heading = TextView(this).apply {
            text = "Duplicate Contacts"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(8, 127, 69))
            setPadding(0, 20, 0, 10)
        }

        val explanation = TextView(this).apply {
            text = "Contacts with the same phone number are listed together. Review them before deleting anything."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 20)
        }

        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(results)
        }

        layout.addView(backButton)
        layout.addView(heading)
        layout.addView(explanation)

        layout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(layout)

        val duplicateGroups = findDuplicateGroups()

        if (duplicateGroups.isEmpty()) {
            results.addView(
                TextView(this).apply {
                    text = "No duplicate phone numbers found."
                    textSize = 17f
                    setTextColor(Color.rgb(8, 127, 69))
                    setPadding(8, 20, 8, 20)
                }
            )
            return
        }

        results.addView(
            TextView(this).apply {
                text = "Found ${duplicateGroups.size} duplicate number group(s)."
                textSize = 17f
                setTextColor(Color.BLACK)
                setPadding(0, 0, 0, 16)
            }
        )

        duplicateGroups.forEachIndexed { index, group ->

            val number = group.first
            val duplicateContacts = group.second

            val groupCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(Color.rgb(245, 248, 245))
            }

            groupCard.addView(
                TextView(this).apply {
                    text = "Duplicate Group ${index + 1}"
                    textSize = 18f
                    setTextColor(Color.rgb(8, 127, 69))
                }
            )

            groupCard.addView(
                TextView(this).apply {
                    text = "Phone number: $number"
                    textSize = 15f
                    setTextColor(Color.BLACK)
                    setPadding(0, 6, 0, 10)
                }
            )

            duplicateContacts.forEach { contact ->

                val contactRow = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 10, 8, 10)
                }

                contactRow.addView(
                    TextView(this).apply {
                        text = contact.name
                        textSize = 17f
                        setTextColor(Color.BLACK)
                    }
                )

                contactRow.addView(
                    TextView(this).apply {
                        text = contact.phone
                        textSize = 14f
                        setTextColor(Color.DKGRAY)
                    }
                )

                val actionRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                val viewButton = Button(this).apply {
                    text = "View Details"
                    setOnClickListener {
                        showContactDetails(contact)
                    }
                }

                val deleteButton = Button(this).apply {
                    text = "Delete"
                    setOnClickListener {
                        confirmDeleteDuplicate(contact)
                    }
                }

                actionRow.addView(
                    viewButton,
                    LinearLayout.LayoutParams(0, -2, 1f)
                )

                actionRow.addView(
                    deleteButton,
                    LinearLayout.LayoutParams(0, -2, 1f)
                )

                contactRow.addView(actionRow)
                groupCard.addView(contactRow)
            }

            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = 16

            results.addView(groupCard, params)
        }
    }

    private fun confirmDeleteDuplicate(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle("Delete Duplicate Contact?")
            .setMessage(
                "Are you sure you want to delete ${contact.name} (${contact.phone})? This will remove the contact from your phone."
            )
            .setPositiveButton("Delete") { _, _ ->
                deleteDuplicateContact(contact)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteDuplicateContact(contact: Contact) {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            checkContactPermission()
            return
        }

        try {
            val uri = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI,
                contact.id
            )

            val deleted = contentResolver.delete(uri, null, null)

            if (deleted > 0) {
                favoriteIds.remove(contact.id.toString())

                favoritePrefs.edit()
                    .putStringSet("favorite_ids", favoriteIds.toSet())
                    .apply()

                showMessage("Contact deleted.")
            } else {
                showMessage("Contact could not be deleted.")
            }

            loadContacts()
            showDuplicateContacts()

        } catch (e: Exception) {
            showMessage("Unable to delete this contact.")
        }
    }

    // INDIVIDUAL CONTACT DETAILS

    private fun showContactDetails(contact: Contact) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 30, 24, 24)
            setBackgroundColor(Color.WHITE)
        }

        val backButton = Button(this).apply {
            text = "← Back to Contacts"
            setOnClickListener {
                showHomeScreen()
            }
        }

        val heading = TextView(this).apply {
            text = "Contact Details"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(8, 127, 69))
            setPadding(0, 20, 0, 25)
        }

        val picture = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(150, 150)
            scaleType = ImageView.ScaleType.CENTER_CROP

            if (!contact.photoUri.isNullOrBlank()) {
                try {
                    setImageURI(Uri.parse(contact.photoUri))
                } catch (e: Exception) {
                    setImageDrawable(null)
                }
            }

            if (drawable == null) {
                setBackgroundColor(Color.rgb(8, 127, 69))
            }
        }

        val name = TextView(this).apply {
            text = contact.name
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(0, 20, 0, 10)
        }

        val phone = TextView(this).apply {
            text = contact.phone
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 25)
        }

        val callButton = Button(this).apply {
            text = "Call"
            setOnClickListener {
                if (contact.phone.isBlank()) {
                    showMessage("This contact has no phone number.")
                } else {
                    try {
                        val intent = Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:${Uri.encode(contact.phone)}")
                        )
                        startActivity(intent)
                    } catch (e: Exception) {
                        showMessage("Unable to open the dialler.")
                    }
                }
            }
        }

        val messageButton = Button(this).apply {
            text = "Message"
            setOnClickListener {
                if (contact.phone.isBlank()) {
                    showMessage("This contact has no phone number.")
                } else {
                    try {
                        val intent = Intent(
                            Intent.ACTION_SENDTO,
                            Uri.fromParts("smsto", contact.phone, null)
                        )
                        startActivity(intent)
                    } catch (e: Exception) {
                        showMessage("Unable to open a messaging app.")
                    }
                }
            }
        }

        val editButton = Button(this).apply {
            text = "Edit Contact"
            setOnClickListener {
                editContact(contact)
            }
        }

        val pictureButton = Button(this).apply {
            text = "Change Profile Picture"
            setOnClickListener {
                chooseContactPicture(contact)
            }
        }

        val favoriteButton = Button(this).apply {
            text = if (favoriteIds.contains(contact.id.toString())) {
                "★ Remove from Favourites"
            } else {
                "☆ Add to Favourites"
            }

            setOnClickListener {
                toggleFavorite(contact)
                showContactDetails(contact)
            }
        }

        val deleteButton = Button(this).apply {
            text = "Delete Contact"
            setOnClickListener {
                confirmDelete(contact)
            }
        }

        layout.addView(backButton)
        layout.addView(heading)
        layout.addView(picture)
        layout.addView(name)
        layout.addView(phone)
        layout.addView(callButton)
        layout.addView(messageButton)
        layout.addView(editButton)
        layout.addView(pictureButton)
        layout.addView(favoriteButton)
        layout.addView(deleteButton)

        setContentView(layout)
    }

    // PROFILE PICTURES

    private fun chooseContactPicture(contact: Contact) {
        selectedContact = contact

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

        startActivityForResult(
            intent,
            PICK_IMAGE_REQUEST
        )
    }

    @Deprecated("Legacy activity result callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) return

        when (requestCode) {

            PICK_IMAGE_REQUEST -> {
                val uri = data?.data

                if (uri != null) {
                    saveContactPicture(uri)
                }
            }

            IMPORT_CONTACTS_REQUEST -> {
                val uri = data?.data

                if (uri != null) {
                    openVcfImporter(uri)
                }
            }

            EXPORT_CONTACTS_REQUEST -> {
                val uri = data?.data

                if (uri != null) {
                    writeVCardFile(uri)
                }
            }
        }
    }

    private fun saveContactPicture(uri: Uri) {
        val contact = selectedContact

        if (contact == null) {
            showMessage("No contact selected.")
            return
        }

        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            showMessage("Contact permission is required.")
            return
        }

        try {
            val photoBytes = contentResolver
                .openInputStream(uri)
                ?.use { it.readBytes() }

            if (photoBytes == null || photoBytes.isEmpty()) {
                showMessage("Unable to read this picture.")
                return
            }

            val rawContactId = findRawContactId(contact.id)

            if (rawContactId == null) {
                showMessage("Unable to find this contact.")
                return
            }

            val selection =
                "${ContactsContract.Data.RAW_CONTACT_ID}=? AND " +
                "${ContactsContract.Data.MIMETYPE}=?"

            val selectionArgs = arrayOf(
                rawContactId.toString(),
                ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
            )

            val values = ContentValues().apply {
                put(
                    ContactsContract.CommonDataKinds.Photo.PHOTO,
                    photoBytes
                )
            }

            val updated = contentResolver.update(
                ContactsContract.Data.CONTENT_URI,
                values,
                selection,
                selectionArgs
            )

            if (updated == 0) {
                values.put(
                    ContactsContract.Data.RAW_CONTACT_ID,
                    rawContactId
                )

                values.put(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                )

                contentResolver.insert(
                    ContactsContract.Data.CONTENT_URI,
                    values
                )
            }

            showMessage("Contact picture saved.")
            selectedContact = null
            loadContacts()

        } catch (e: Exception) {
            showMessage("Unable to save picture. Try another image.")
        }
    }

    private fun findRawContactId(contactId: Long): Long? {
        val projection = arrayOf(
            ContactsContract.RawContacts._ID
        )

        val cursor = contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            projection,
            "${ContactsContract.RawContacts.CONTACT_ID}=? AND " +
                "${ContactsContract.RawContacts.DELETED}=0",
            arrayOf(contactId.toString()),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }

        return null
    }

    // FAVOURITES

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
            .putStringSet("favorite_ids", favoriteIds.toSet())
            .apply()

        displayContacts()
    }

    // ADD, EDIT AND DELETE CONTACTS

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
                "Are you sure you want to delete ${contact.name}? This will remove the contact from your phone."
            )
            .setPositiveButton("Delete") { _, _ ->
                deleteContact(contact)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteContact(contact: Contact) {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            checkContactPermission()
            return
        }

        try {
            val uri = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI,
                contact.id
            )

            val deleted = contentResolver.delete(uri, null, null)

            favoriteIds.remove(contact.id.toString())

            favoritePrefs.edit()
                .putStringSet("favorite_ids", favoriteIds.toSet())
                .apply()

            if (deleted > 0) {
                showMessage("Contact deleted.")
            } else {
                showMessage("Contact could not be deleted.")
            }

            showHomeScreen()

        } catch (e: Exception) {
            showMessage("Unable to delete this contact.")
        }
    }

    // GENERAL MESSAGE

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}