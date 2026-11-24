package com.safroshessential.smartcontact

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class PrivateContact(
    val name: String,
    val phone: String
)

class PrivateContactStorage(context: Context) {

    private val prefs = context.getSharedPreferences(
        "SmartContactPrivateStorage",
        Context.MODE_PRIVATE
    )

    private val keyAlias = "SmartContactPrivateKey"

    private fun getOrCreateKey(): SecretKey {
        val androidKeyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
        androidKeyStore.load(null)

        val existing = androidKeyStore.getKey(keyAlias, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            keyAlias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .setKeySize(256)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    fun saveContact(name: String, phone: String): Boolean {
        if (name.isBlank() || phone.isBlank()) return false

        return try {
            val contacts = getContacts().toMutableList()
            contacts.add(PrivateContact(name.trim(), phone.trim()))
            saveAll(contacts)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getContacts(): List<PrivateContact> {
        return try {
            val encrypted = prefs.getString("contacts", null) ?: return emptyList()
            val iv = Base64.decode(
                prefs.getString("iv", null),
                Base64.DEFAULT
            )

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, iv)
            )

            val decrypted = cipher.doFinal(
                Base64.decode(encrypted, Base64.DEFAULT)
            )

            val array = JSONArray(String(decrypted, Charsets.UTF_8))
            val contacts = mutableListOf<PrivateContact>()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                contacts.add(
                    PrivateContact(
                        item.getString("name"),
                        item.getString("phone")
                    )
                )
            }

            contacts
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAll(contacts: List<PrivateContact>) {
        val array = JSONArray()

        contacts.forEach { contact ->
            array.put(
                JSONObject().apply {
                    put("name", contact.name)
                    put("phone", contact.phone)
                }
            )
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val encrypted = cipher.doFinal(
            array.toString().toByteArray(Charsets.UTF_8)
        )

        prefs.edit()
            .putString(
                "contacts",
                Base64.encodeToString(encrypted, Base64.DEFAULT)
            )
            .putString(
                "iv",
                Base64.encodeToString(cipher.iv, Base64.DEFAULT)
            )
            .apply()
    }
}