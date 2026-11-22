package com.motohana.assistant

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

object SecurityStore {
    private const val PREF = "moto_hana_secure"
    private const val SALT = "pin_salt"
    private const val HASH = "pin_hash"

    fun hasPin(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).contains(HASH)

    fun savePin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(SALT, salt.joinToString("") { "%02x".format(it) })
            .putString(HASH, hash(pin, salt))
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val saltHex = prefs.getString(SALT, null) ?: return false
        val expected = prefs.getString(HASH, null) ?: return false
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return MessageDigest.isEqual(
            expected.toByteArray(),
            hash(pin, salt).toByteArray()
        )
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var value = salt + pin.toByteArray()
        repeat(120_000) { value = digest.digest(value) }
        return value.joinToString("") { "%02x".format(it) }
    }
}
