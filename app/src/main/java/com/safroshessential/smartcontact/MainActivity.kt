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
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.Gravity
import android.widget.*
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var contactsList: LinearLayout
    private lateinit var searchInput: EditText

    private val contacts = mutableListOf<Contact>()
    private val favoriteIds = mutableSetOf<String>()

    private var selectedContact: Contact? = null
    private var currentPage = "home"
    private var showFavoritesOnly = false

    private val favoritePrefs by lazy {
        getSharedPreferences("SmartContactFavorites", MODE_PRIVATE)
    }

    private data class Contact(
        val id: Long,
        val name: String,
        val phone: String,
        val photoUri: String?
    )

    private data class RecentCall(
        val name: String,
        val number: String,
        val type: Int,
        val date: Long,
        val duration: Long
    )

    companion object {
        private const val PICK_IMAGE_REQUEST = 200
        private const val IMPORT_CONTACTS_REQUEST = 201
        private const val EXPORT_CONTACTS_REQUEST = 202
        private const val CONTACT_PERMISSION_REQUEST = 100
        private const val CALL_LOG_PERMISSION_REQUEST = 101

        private val GREEN = Color.rgb(8, 127, 69)
        private val LIGHT_GREEN = Color.rgb(240, 248, 242)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        favoriteIds.addAll(
            favoritePrefs.getStringSet("favorite_ids", emptySet())
                ?: emptySet()
        )

        showHomeScreen()
        checkContactPermission()
    }

    override fun onResume() {
        super.onResume()

        if (::contactsList.isInitialized &&
            hasContactPermission()
        ) {
            loadContacts()
        }

        if (currentPage == "recent" || currentPage == "home") {
            if (hasCallLogPermission()) {
                if (currentPage == "recent") showRecentCalls()
                else showHomeScreen()
            }
        }
    }

    // HOME SCREEN

    private fun showHomeScreen() {
        currentPage = "home"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 10)
            setBackgroundColor(Color.WHITE)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        titleArea.addView(TextView(this).apply {
            text = "Smart Contact"
            textSize = 27f
            setTextColor(GREEN)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        titleArea.addView(TextView(this).apply {
            text = "Your contacts, all in one place"
            textSize = 13f
            setTextColor(Color.DKGRAY)
        })

        header.addView(
            titleArea,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val settingsButton = Button(this).apply {
            text = "⚙"
            textSize = 24f
            setTextColor(GREEN)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { showMoreMenu() }
        }

        header.addView(settingsButton)
        root.addView(header)

        searchInput = EditText(this).apply {
            hint = "Search contacts"
            setSingleLine(true)
            textSize = 16f
            setPadding(16, 10, 16, 10)
            setBackgroundColor(LIGHT_GREEN)
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

                override fun afterTextChanged(s: android.text.Editable?) {}
            }
        )

        val searchParams = LinearLayout.LayoutParams(-1, -2)
        searchParams.topMargin = 18
        root.addView(searchInput, searchParams)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val addButton = Button(this).apply {
            text = "+ Add New Contact"
            setOnClickListener { addContact() }
        }

        val favouritesButton = Button(this).apply {
            text = "★ Favourites"
            setOnClickListener { showFavourites() }
        }

        actionRow.addView(
            addButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        actionRow.addView(
            favouritesButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(actionRow)

        root.addView(TextView(this).apply {
            text = "Recent Calls"
            textSize = 21f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 22, 0, 8)
        })

        contactsList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(contactsList)
        }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        root.addView(createBottomNavigation())

        setContentView(root)

        displayRecentCalls(contactsList, 8)
    }

    // BOTTOM NAVIGATION

    private fun createBottomNavigation(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(LIGHT_GREEN)
        }

        val allButton = Button(this).apply {
            text = "All Contacts"
            setTextColor(GREEN)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { showAllContacts() }
        }

        val recentButton = Button(this).apply {
            text = "Recent"
            setTextColor(GREEN)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { showRecentCalls() }
        }

        val moreButton = Button(this).apply {
            text = "More"
            setTextColor(GREEN)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { showMoreMenu() }
        }

        nav.addView(allButton, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(recentButton, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(moreButton, LinearLayout.LayoutParams(0, -2, 1f))

        return nav
    }

    // ALL CONTACTS

    private fun showAllContacts() {
        currentPage = "all"
        showFavoritesOnly = false

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 10)
            setBackgroundColor(Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = "All Contacts"
            textSize = 26f
            setTextColor(GREEN)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

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

                override fun afterTextChanged(s: android.text.Editable?) {}
            }
        )

        root.addView(searchInput)

        val addButton = Button(this).apply {
            text = "+ Add New Contact"
            setOnClickListener { addContact() }
        }
        root.addView(addButton)

        contactsList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(contactsList)
        }

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(createBottomNavigation())

        setContentView(root)
        displayContacts()
    }

    // RECENT CALLS PAGE

    private fun showRecentCalls() {
        currentPage = "recent"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 10)
            setBackgroundColor(Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = "Recent Calls"
            textSize = 26f
            setTextColor(GREEN)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        contactsList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(contactsList)
        }

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(createBottomNavigation())

        setContentView(root)

        if (!hasCallLogPermission()) {
            contactsList.addView(TextView(this).apply {
                text = "Allow call history permission to see your recent incoming, outgoing and missed calls."
                textSize = 16f
                setPadding(8, 20, 8, 20)
            })

            val permissionButton = Button(this).apply {
                text = "Allow Call History"
                setOnClickListener { requestCallLogPermission() }
            }
            contactsList.addView(permissionButton)
        } else {
            displayRecentCalls(contactsList, null)
        }
    }

    private fun displayRecentCalls(
        target: LinearLayout,
        limit: Int?
    ) {
        target.removeAllViews()

        if (!hasCallLogPermission()) {
            target.addView(TextView(this).apply {
                text = "Allow call history permission to display recent calls."
                textSize = 15f
                setPadding(8, 18, 8, 18)
            })

            target.addView(Button(this).apply {
                text = "Allow Call History"
                setOnClickListener { requestCallLogPermission() }
            })
            return
        }

        val calls = mutableListOf<RecentCall>()

        val projection = arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

                var count = 0
                while (it.moveToNext() && (limit == null || count < limit)) {
                    calls.add(
                        RecentCall(
                            name = it.getString(nameIndex) ?: "",
                            number = it.getString(numberIndex) ?: "",
                            type = it.getInt(typeIndex),
                            date = it.getLong(dateIndex),
                            duration = it.getLong(durationIndex)
                        )
                    )
                    count++
                }
            }
        } catch (e: SecurityException) {
            showMessage("Call history permission is required.")
            return
        } catch (e: Exception) {
            showMessage("Unable to read call history.")
            return
        }

        if (calls.isEmpty()) {
            target.addView(TextView(this).apply {
                text = "No recent calls found."
                textSize = 16f
                setPadding(8, 20, 8, 20)
            })
            return
        }

        calls.forEach { call ->
            val callType = when (call.type) {
                CallLog.Calls.INCOMING_TYPE -> "Incoming call"
                CallLog.Calls.OUTGOING_TYPE -> "Outgoing call"
                CallLog.Calls.MISSED_TYPE -> "Missed call"
                CallLog.Calls.REJECTED_TYPE -> "Rejected call"
                CallLog.Calls.BLOCKED_TYPE -> "Blocked call"
                else -> "Call"
            }

            val displayName = call.name.ifBlank { call.number }
            val dateText = SimpleDateFormat(
                "dd MMM, hh:mm a",
                Locale.getDefault()
            ).format(Date(call.date))

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(LIGHT_GREEN)
            }

            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            details.addView(TextView(this).apply {
                text = displayName
                textSize = 17f
                setTextColor(Color.BLACK)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            details.addView(TextView(this).apply {
                text = call.number
                textSize = 14f
                setTextColor(Color.DKGRAY)
            })

            details.addView(TextView(this).apply {
                text = "$callType • $dateText"
                textSize = 12f
                setTextColor(GREEN)
            })

            card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))

            val callButton = Button(this).apply {
                text = "Call"
                setOnClickListener {
                    if (call.number.isNotBlank()) {
                        try {
                            startActivity(
                                Intent(
                                    Intent.ACTION_DIAL,
                                    Uri.parse("tel:${Uri.encode(call.number)}")
                                )
                            )
                        } catch (e: Exception) {
                            showMessage("Unable to open the dialler.")
                        }
                    }
                }
            }

            card.addView(callButton)

            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = 10
            target.addView(card, params)
        }
    }

    // CALL LOG PERMISSION

    private fun hasCallLogPermission(): Boolean {
        return checkSelfPermission(
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCallLogPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.READ_CALL_LOG),
            CALL_LOG_PERMISSION_REQUEST
        )
    }

    
    // MORE / SETTINGS MENU

    private fun showMoreMenu() {
        val options = arrayOf(
            "Private Contacts",
            "Import Contacts",
            "Export Contacts",
            "Find Duplicate Contacts",
            "Spam Protection",
            "App Settings",
            "About Smart Contact"
        )

        AlertDialog.Builder(this)
            .setTitle("Smart Contact Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openPrivateContacts()
                    1 -> importContacts()
                    2 -> exportContacts()
                    3 -> showDuplicateContacts()
                    4 -> openSpamProtection()
                    5 -> showAppSettings()
                    6 -> showAbout()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openSpamProtection() {
        try {
            startActivity(
                Intent(this, SpamProtectionActivity::class.java)
            )
        } catch (e: Exception) {
            showMessage("Unable to open Spam Protection.")
        }
    }

    private fun showAppSettings() {
        AlertDialog.Builder(this)
            .setTitle("App Settings")
            .setMessage(
                "Smart Contact settings will be expanded as more features are added."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("About Smart Contact")
            .setMessage(
                "Smart Contact helps you organise contacts, manage favourites, review call history and protect private contacts."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openPrivateContacts() {
        try {
            startActivity(
                Intent(this, HiddenContactsActivity::class.java)
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
        val readGranted = hasContactPermission()
        val writeGranted = checkSelfPermission(
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!readGranted || !writeGranted) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS
                ),
                CONTACT_PERMISSION_REQUEST
            )
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

        when (requestCode) {
            CONTACT_PERMISSION_REQUEST -> {
                if (hasContactPermission()) {
                    loadContacts()
                } else {
                    showMessage("Contact permission is needed.")
                }
            }

            CALL_LOG_PERMISSION_REQUEST -> {
                if (hasCallLogPermission()) {
                    if (currentPage == "recent") {
                        showRecentCalls()
                    } else {
                        showHomeScreen()
                    }
                } else {
                    showMessage("Call history permission was not granted.")
                }
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
                        } else null
                    )
                )
            }
        }

        if (::contactsList.isInitialized) {
            if (currentPage == "all") displayContacts()
        }
    }

    // DISPLAY ALL CONTACTS

    private fun displayContacts() {
        if (!::contactsList.isInitialized) return

        contactsList.removeAllViews()

        val query = if (::searchInput.isInitialized) {
            searchInput.text.toString().trim()
        } else ""

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
            contactsList.addView(TextView(this).apply {
                text = if (showFavoritesOnly) {
                    "No favourite contacts yet."
                } else {
                    "No contacts found."
                }
                textSize = 16f
                setPadding(8, 20, 8, 20)
            })
            return
        }

        filtered.forEach { contact ->
            contactsList.addView(createContactCard(contact))
        }
    }

    private fun createContactCard(contact: Contact): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(LIGHT_GREEN)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { showContactDetails(contact) }
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
                setBackgroundColor(GREEN)
            }
        }

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 4, 4, 4)
        }

        details.addView(TextView(this).apply {
            text = contact.name
            textSize = 18f
            setTextColor(Color.BLACK)
        })

        details.addView(TextView(this).apply {
            text = contact.phone
            textSize = 15f
            setTextColor(Color.DKGRAY)
        })

        details.addView(TextView(this).apply {
            text = "View details"
            textSize = 13f
            setTextColor(GREEN)
        })

        topRow.addView(picture)
        topRow.addView(details, LinearLayout.LayoutParams(0, -2, 1f))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val favoriteButton = Button(this).apply {
            text = if (favoriteIds.contains(contact.id.toString())) {
                "★ Favourite"
            } else {
                "☆ Favourite"
            }
            setOnClickListener { toggleFavorite(contact) }
        }

        val pictureButton = Button(this).apply {
            text = "Picture"
            setOnClickListener { chooseContactPicture(contact) }
        }

        val editButton = Button(this).apply {
            text = "Edit"
            setOnClickListener { editContact(contact) }
        }

        buttons.addView(favoriteButton)
        buttons.addView(pictureButton)
        buttons.addView(editButton)

        card.addView(topRow)
        card.addView(buttons)

        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = 12
        card.layoutParams = params

        return card
    }

    // FAVOURITES PAGE

    private fun showFavourites() {
        currentPage = "favourites"
        showFavoritesOnly = true

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 10)
            setBackgroundColor(Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = "Favourites"
            textSize = 26f
            setTextColor(GREEN)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        contactsList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(contactsList)
        }

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(createBottomNavigation())

        setContentView(root)
        displayContacts()
    }

    // IMPORT CONTACTS

    private fun importContacts() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        try {
            startActivityForResult(intent, IMPORT_CONTACTS_REQUEST)
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
            showMessage("Choose the contacts import option to continue.")

        } catch (e: android.content.ActivityNotFoundException) {
            showMessage("No compatible VCF importer was found on this phone.")
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
            putExtra(Intent.EXTRA_TITLE, "SmartContact-Contacts.vcf")
        }

        try {
            startActivityForResult(intent, EXPORT_CONTACTS_REQUEST)
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

                        writer.write("BEGIN:VCARD\r\n")
                        writer.write("VERSION:3.0\r\n")
                        writer.write("FN:${escapeVCard(name)}\r\n")
                        writer.write("N:${escapeVCard(name)};;;;\r\n")

                        entry.second.forEach { number ->
                            writer.write("TEL:${escapeVCard(number)}\r\n")
                        }

                        writer.write("END:VCARD\r\n")
                    }
                }
            }

            showMessage("${contactNumbers.size} contacts exported successfully.")

        } catch (e: Exception) {
            showMessage("Unable to export contacts. Please try again.")
        }
    }

    // DUPLICATE CONTACTS

    private fun normalizePhoneNumber(number: String): String {
        return number.filter { it.isDigit() }
    }

    private fun findDuplicateGroups(): List<Pair<String, List<Contact>>> {
        return contacts
            .filter { normalizePhoneNumber(it.phone).isNotBlank() }
            .groupBy { normalizePhoneNumber(it.phone) }
            .mapNotNull { (number, group) ->
                val uniqueContacts = group.distinctBy { it.id }
                if (uniqueContacts.size > 1) {
                    Pair(number, uniqueContacts)
                } else null
            }
            .sortedBy { it.first }
    }

    private fun showDuplicateContacts() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 24)
            setBackgroundColor(Color.WHITE)
        }

        layout.addView(Button(this).apply {
            text = "← Back to Contacts"
            setOnClickListener { showHomeScreen() }
        })

        layout.addView(TextView(this).apply {
            text = "Duplicate Contacts"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(GREEN)
            setPadding(0, 20, 0, 10)
        })

        layout.addView(TextView(this).apply {
            text = "Contacts with the same phone number are listed together. Review them before deleting anything."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 20)
        })

        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply { addView(results) }

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(layout)

        val groups = findDuplicateGroups()

        if (groups.isEmpty()) {
            results.addView(TextView(this).apply {
                text = "No duplicate phone numbers found."
                textSize = 17f
                setTextColor(GREEN)
                setPadding(8, 20, 8, 20)
            })
            return
        }

        results.addView(TextView(this).apply {
            text = "Found ${groups.size} duplicate number group(s)."
            textSize = 17f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 16)
        })

        groups.forEachIndexed { index, group ->
            val number = group.first
            val duplicateContacts = group.second

            val groupCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(LIGHT_GREEN)
            }

            groupCard.addView(TextView(this).apply {
                text = "Duplicate Group ${index + 1}"
                textSize = 18f
                setTextColor(GREEN)
            })

            groupCard.addView(TextView(this).apply {
                text = "Phone number: $number"
                textSize = 15f
                setTextColor(Color.BLACK)
                setPadding(0, 6, 0, 10)
            })

            duplicateContacts.forEach { contact ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 10, 8, 10)
                }

                row.addView(TextView(this).apply {
                    text = contact.name
                    textSize = 17f
                    setTextColor(Color.BLACK)
                })

                row.addView(TextView(this).apply {
                    text = contact.phone
                    textSize = 14f
                    setTextColor(Color.DKGRAY)
                })

                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                actions.addView(Button(this).apply {
                    text = "View Details"
                    setOnClickListener { showContactDetails(contact) }
                }, LinearLayout.LayoutParams(0, -2, 1f))

                actions.addView(Button(this).apply {
                    text = "Delete"
                    setOnClickListener { confirmDeleteDuplicate(contact) }
                }, LinearLayout.LayoutParams(0, -2, 1f))

                row.addView(actions)
                groupCard.addView(row)
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

    // CONTACT DETAILS

    private fun showContactDetails(contact: Contact) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 30, 24, 24)
            setBackgroundColor(Color.WHITE)
        }

        layout.addView(Button(this).apply {
            text = "← Back to Contacts"
            setOnClickListener { showAllContacts() }
        })

        layout.addView(TextView(this).apply {
            text = "Contact Details"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(GREEN)
            setPadding(0, 20, 0, 25)
        })

        layout.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(150, 150)
            scaleType = ImageView.ScaleType.CENTER_CROP

            if (!contact.photoUri.isNullOrBlank()) {
                try {
                    setImageURI(Uri.parse(contact.photoUri))
                } catch (e: Exception) {
                    setImageDrawable(null)
                }
            }

            if (drawable == null) setBackgroundColor(GREEN)
        })

        layout.addView(TextView(this).apply {
            text = contact.name
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(0, 20, 0, 10)
        })

        layout.addView(TextView(this).apply {
            text = contact.phone
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 25)
        })

        layout.addView(Button(this).apply {
            text = "Call"
            setOnClickListener {
                if (contact.phone.isBlank()) {
                    showMessage("This contact has no phone number.")
                } else {
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_DIAL,
                                Uri.parse("tel:${Uri.encode(contact.phone)}")
                            )
                        )
                    } catch (e: Exception) {
                        showMessage("Unable to open the dialler.")
                    }
                }
            }
        })

        layout.addView(Button(this).apply {
            text = "Message"
            setOnClickListener {
                if (contact.phone.isBlank()) {
                    showMessage("This contact has no phone number.")
                } else {
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_SENDTO,
                                Uri.fromParts("smsto", contact.phone, null)
                            )
                        )
                    } catch (e: Exception) {
                        showMessage("Unable to open a messaging app.")
                    }
                }
            }
        })

        layout.addView(Button(this).apply {
            text = "Edit Contact"
            setOnClickListener { editContact(contact) }
        })

        layout.addView(Button(this).apply {
            text = "Change Profile Picture"
            setOnClickListener { chooseContactPicture(contact) }
        })

        layout.addView(Button(this).apply {
            text = if (favoriteIds.contains(contact.id.toString())) {
                "★ Remove from Favourites"
            } else {
                "☆ Add to Favourites"
            }

            setOnClickListener {
                toggleFavorite(contact)
                showContactDetails(contact)
            }
        })

        layout.addView(Button(this).apply {
            text = "Delete Contact"
            setOnClickListener { confirmDelete(contact) }
        })

        setContentView(layout)
    }

    // PROFILE PICTURES

    private fun chooseContactPicture(contact: Contact) {
        selectedContact = contact

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

        startActivityForResult(intent, PICK_IMAGE_REQUEST)
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
                if (uri != null) saveContactPicture(uri)
            }

            IMPORT_CONTACTS_REQUEST -> {
                val uri = data?.data
                if (uri != null) openVcfImporter(uri)
            }

            EXPORT_CONTACTS_REQUEST -> {
                val uri = data?.data
                if (uri != null) writeVCardFile(uri)
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
            showAllContacts()

        } catch (e: Exception) {
            showMessage("Unable to save picture. Try another image.")
        }
    }

    private fun findRawContactId(contactId: Long): Long? {
        val projection = arrayOf(ContactsContract.RawContacts._ID)

        val cursor = contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            projection,
            "${ContactsContract.RawContacts.CONTACT_ID}=? AND " +
                "${ContactsContract.RawContacts.DELETED}=0",
            arrayOf(contactId.toString()),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) return it.getLong(0)
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

        if (currentPage == "favourites") {
            displayContacts()
        } else if (currentPage == "all") {
            displayContacts()
        }
    }

    // ADD, EDIT AND DELETE

    private fun addContact() {
        startActivity(
            Intent(
                Intent.ACTION_INSERT,
                ContactsContract.Contacts.CONTENT_URI
            )
        )
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

            loadContacts()
            showAllContacts()

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