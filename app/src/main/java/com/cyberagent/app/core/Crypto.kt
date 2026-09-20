package com.cyberagent.app.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado de campos sensibles con AES-256-GCM y clave custodiada en el
 * Android Keystore (no exportable). Sustituye al almacenamiento en claro
 * de la v2.0.
 */
object Crypto {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "cyberagent_master_key"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_LEN = 12

    private val lock = Any()

    /**
     * Clave cacheada. Antes se abría el Keystore en CADA cifrado/descifrado
     * (KeyStore.getInstance + load son operaciones caras): con un historial de
     * cientos de eventos eso eran varios segundos y la app parecía colgada.
     */
    @Volatile
    private var cachedKey: SecretKey? = null

    private fun secretKey(): SecretKey {
        cachedKey?.let { return it }
        synchronized(lock) {
            cachedKey?.let { return it }
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val existing = ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) {
                cachedKey = existing.secretKey
                return existing.secretKey
            }

            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            gen.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            val fresh = gen.generateKey()
            cachedKey = fresh
            return fresh
        }
    }

    /** Cifra texto plano. Devuelve "" si la entrada está vacía. */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
        } catch (t: Throwable) {
            ""
        }
    }

    /** Descifra un blob generado por [encrypt]. Devuelve "" si falla. */
    fun decrypt(blob: String): String {
        if (blob.isEmpty()) return ""
        return try {
            val raw = Base64.decode(blob, Base64.NO_WRAP)
            if (raw.size <= IV_LEN) return ""
            val iv = raw.copyOfRange(0, IV_LEN)
            val ct = raw.copyOfRange(IV_LEN, raw.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (t: Throwable) {
            ""
        }
    }

    /** SHA-256 de una cadena, en hexadecimal. */
    fun sha256Hex(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
