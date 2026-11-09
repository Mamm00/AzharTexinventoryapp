package com.alazhartex.stock.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.Base64

/**
 * PBKDF2-SHA256 (spec §4.7): salt >= 16 bytes, iterations >= 100,000 (seed uses 210,000),
 * constant-time compare, stored as iterations$salt_b64$hash_b64.
 */
object PasswordHasher {
    const val DEFAULT_ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun hash(password: String, salt: ByteArray = randomSalt(), iterations: Int = DEFAULT_ITERATIONS): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val dk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return "$iterations\$${Base64.getEncoder().encodeToString(salt)}\$${Base64.getEncoder().encodeToString(dk)}"
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        if (iterations < 100_000) return false
        val salt = Base64.getDecoder().decode(parts[1])
        val expected = Base64.getDecoder().decode(parts[2])
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, expected.size * 8)
        val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return MessageDigest.isEqual(actual, expected)
    }

    private fun randomSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
}
