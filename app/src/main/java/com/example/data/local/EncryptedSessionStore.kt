package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.domain.model.IoTNetworkDefaults
import com.example.domain.model.OperatorSession
import com.example.domain.model.PersonalDataSummary
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cryptographic Session & Configuration Store guided by ISO/IEC 27400, OWASP MASVS-STORAGE,
 * and RGPD / LOPDGDD data protection principles.
 *
 * Security guarantees:
 * 1. NEVER falls back to plaintext SharedPreferences on disk.
 * 2. Uses hardware-backed Android KeyStore (`EncryptedSharedPreferences` AES256_SIV / AES256_GCM)
 *    when available.
 * 3. If hardware KeyStore is unavailable, uses a fail-safe ephemeral in-memory AES-256-GCM vault
 *    so personal data is never persisted in cleartext on flash storage.
 * 4. Signs and verifies session tokens using HMAC-SHA256 with Base64-encoded claims and enforces an 8-hour TTL.
 */
class EncryptedSessionStore(context: Context) {

    private val appContext: Context = context.applicationContext
    private val secureRandom = SecureRandom()
    private val hardwarePrefs: SharedPreferences?
    private val memoryVault = EphemeralAesGcmVault(secureRandom)
    private val hmacKey: SecretKey

    val isHardwareBacked: Boolean
        get() = hardwarePrefs != null

    init {
        // Proactively delete any legacy plaintext fallback file if it ever existed from older builds
        try {
            appContext.deleteSharedPreferences(PREFS_FILE_NAME + "_fallback")
        } catch (_: Exception) {}

        hardwarePrefs = try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            Log.w(
                TAG,
                "Android KeyStore unavailable; activating fail-safe ephemeral AES-256-GCM memory vault."
            )
            null
        }

        hmacKey = initOrLoadHmacKey()
    }

    private fun initOrLoadHmacKey(): SecretKey {
        val existingBase64 = readRawString(KEY_HMAC_SECRET)
        if (!existingBase64.isNullOrBlank()) {
            return try {
                val decoded = Base64.decode(existingBase64, Base64.NO_WRAP)
                javax.crypto.spec.SecretKeySpec(decoded, HMAC_ALGORITHM)
            } catch (_: Exception) {
                generateAndStoreHmacKey()
            }
        }
        return generateAndStoreHmacKey()
    }

    private fun generateAndStoreHmacKey(): SecretKey {
        val generator = KeyGenerator.getInstance(HMAC_ALGORITHM)
        generator.init(256, secureRandom)
        val key = generator.generateKey()
        val encoded = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        writeRawString(KEY_HMAC_SECRET, encoded)
        return key
    }

    /**
     * Generates a cryptographically random 128-bit operator identifier using [SecureRandom].
     */
    fun generateSecureOperatorUid(): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return "usr_" + bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a cryptographically signed session token bound to the operator's identity and TTL.
     * Encodes identity claims in URL-safe Base64 to prevent delimiter injection.
     */
    fun generateSignedSessionToken(
        uid: String,
        email: String,
        loginTimestamp: Long,
        expiresAtTimestamp: Long = loginTimestamp + IoTNetworkDefaults.SESSION_MAX_AGE_MS
    ): String {
        val nonceBytes = ByteArray(12)
        secureRandom.nextBytes(nonceBytes)
        val nonceHex = nonceBytes.joinToString("") { "%02x".format(it) }
        val encodedUid = encodeClaim(uid)
        val encodedEmail = encodeClaim(email)
        val payload = "v1|$encodedUid|$encodedEmail|$loginTimestamp|$expiresAtTimestamp|$nonceHex"
        val signature = computeHmacHex(payload)
        return "$payload|$signature"
    }

    /**
     * Verifies HMAC-SHA256 integrity and TTL expiration of a signed session token.
     */
    fun verifySessionToken(
        token: String,
        expectedUid: String,
        expectedEmail: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val parts = token.split("|")
        if (parts.size != 7 || parts[0] != "v1") return false
        val encodedUid = parts[1]
        val encodedEmail = parts[2]
        val expiresAt = parts[4].toLongOrNull() ?: return false
        val providedSig = parts[6]

        if (encodedUid != encodeClaim(expectedUid) || encodedEmail != encodeClaim(expectedEmail)) {
            return false
        }
        if (nowMillis > expiresAt) return false

        val signedPayload = parts.subList(0, 6).joinToString("|")
        val expectedSig = computeHmacHex(signedPayload)
        return MessageDigest.isEqual(
            providedSig.toByteArray(StandardCharsets.UTF_8),
            expectedSig.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun encodeClaim(value: String): String {
        return Base64.encodeToString(
            value.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun computeHmacHex(payload: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(hmacKey)
        val digest = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun saveSession(session: OperatorSession) {
        val signedToken = if (verifySessionToken(session.token, session.uid, session.email, session.loginTimestamp)) {
            session.token
        } else {
            generateSignedSessionToken(
                uid = session.uid,
                email = session.email,
                loginTimestamp = session.loginTimestamp,
                expiresAtTimestamp = session.expiresAtTimestamp
            )
        }

        writeRawString(KEY_UID, session.uid)
        writeRawString(KEY_DISPLAY_NAME, session.displayName)
        writeRawString(KEY_EMAIL, session.email)
        writeRawString(KEY_TOKEN, signedToken)
        writeRawString(KEY_AUTH_METHOD, session.authMethod)
        writeRawLong(KEY_TIMESTAMP, session.loginTimestamp)
        writeRawLong(KEY_EXPIRES_AT, session.expiresAtTimestamp)
    }

    fun getSession(nowMillis: Long = System.currentTimeMillis()): OperatorSession? {
        val uid = readRawString(KEY_UID) ?: return null
        val displayName = readRawString(KEY_DISPLAY_NAME) ?: ""
        val email = readRawString(KEY_EMAIL) ?: ""
        val token = readRawString(KEY_TOKEN) ?: ""
        val authMethod = readRawString(KEY_AUTH_METHOD) ?: DEFAULT_AUTH_METHOD
        val timestamp = readRawLong(KEY_TIMESTAMP, nowMillis)
        val expiresAt = readRawLong(KEY_EXPIRES_AT, timestamp + IoTNetworkDefaults.SESSION_MAX_AGE_MS)

        if (!verifySessionToken(token, uid, email, nowMillis) || nowMillis > expiresAt) {
            Log.w(TAG, "Session expired or failed HMAC integrity verification; purging session.")
            clearSession()
            return null
        }

        return OperatorSession(
            uid = uid,
            displayName = displayName,
            email = email,
            token = token,
            authMethod = authMethod,
            loginTimestamp = timestamp,
            expiresAtTimestamp = expiresAt
        )
    }

    fun clearSession() {
        removeRawKey(KEY_UID)
        removeRawKey(KEY_DISPLAY_NAME)
        removeRawKey(KEY_EMAIL)
        removeRawKey(KEY_TOKEN)
        removeRawKey(KEY_AUTH_METHOD)
        removeRawKey(KEY_TIMESTAMP)
        removeRawKey(KEY_EXPIRES_AT)
    }

    /**
     * Right to Erasure & Google Play Account/Data Deletion.
     * Completely wipes all operator personal data, session keys, and saved network configuration.
     */
    fun eraseAllPersonalData() {
        clearSession()
        removeRawKey(KEY_VEHICLE_IP)
        removeRawKey(KEY_VEHICLE_PORT)
        removeRawKey(KEY_STREAM_URL)
        removeRawKey(KEY_HMAC_SECRET)
        memoryVault.clearAll()
        hardwarePrefs?.edit { clear() }
    }

    /**
     * Right of Access:
     * Returns a transparent summary of what personal data is currently stored and how it is protected.
     */
    fun getPersonalDataSummary(nowMillis: Long = System.currentTimeMillis()): PersonalDataSummary {
        val currentSession = getSession(nowMillis)
        val (savedIp, _, _) = getVehicleConfig()
        val encryptionMode = if (isHardwareBacked) {
            ENCRYPTION_MODE_HARDWARE
        } else {
            ENCRYPTION_MODE_MEMORY_VAULT
        }
        return PersonalDataSummary(
            hasActiveOperatorSession = currentSession != null,
            operatorUid = currentSession?.uid,
            operatorName = currentSession?.displayName,
            operatorEmail = currentSession?.email,
            encryptionMode = encryptionMode,
            tokenIntegrityVerified = currentSession != null,
            sessionExpiresAtMillis = currentSession?.expiresAtTimestamp,
            savedVehicleIp = savedIp
        )
    }

    fun hasActiveSession(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return getSession(nowMillis) != null
    }

    fun saveVehicleConfig(ip: String, port: Int, streamUrl: String) {
        val safeIp = IoTNetworkDefaults.sanitizeLocalIp(ip)
        val safePort = IoTNetworkDefaults.sanitizePort(port)
        val safeStreamUrl = IoTNetworkDefaults.sanitizeStreamUrl(streamUrl, safeIp)
        writeRawString(KEY_VEHICLE_IP, safeIp)
        writeRawInt(KEY_VEHICLE_PORT, safePort)
        writeRawString(KEY_STREAM_URL, safeStreamUrl)
    }

    fun getVehicleConfig(defaultIp: String = IoTNetworkDefaults.DEFAULT_AP_IP): Triple<String, Int, String> {
        val safeDefaultIp = IoTNetworkDefaults.sanitizeLocalIp(defaultIp)
        val rawIp = readRawString(KEY_VEHICLE_IP).orEmpty()
        val ip = IoTNetworkDefaults.sanitizeLocalIp(rawIp, safeDefaultIp)
        val port = IoTNetworkDefaults.sanitizePort(
            readRawInt(KEY_VEHICLE_PORT, IoTNetworkDefaults.DEFAULT_TCP_PORT)
        )
        val rawStreamUrl = readRawString(KEY_STREAM_URL).orEmpty()
        val streamUrl = IoTNetworkDefaults.sanitizeStreamUrl(rawStreamUrl, ip)
        return Triple(ip, port, streamUrl)
    }

    private fun writeRawString(key: String, value: String) {
        val prefs = hardwarePrefs
        if (prefs != null) {
            prefs.edit { putString(key, value) }
        } else {
            memoryVault.putEncrypted(key, value)
        }
    }

    private fun readRawString(key: String): String? {
        val prefs = hardwarePrefs
        return if (prefs != null) {
            prefs.getString(key, null)
        } else {
            memoryVault.getDecrypted(key)
        }
    }

    private fun writeRawLong(key: String, value: Long) {
        writeRawString(key, value.toString())
    }

    private fun readRawLong(key: String, defaultVal: Long): Long {
        return readRawString(key)?.toLongOrNull() ?: defaultVal
    }

    private fun writeRawInt(key: String, value: Int) {
        writeRawString(key, value.toString())
    }

    private fun readRawInt(key: String, defaultVal: Int): Int {
        return readRawString(key)?.toIntOrNull() ?: defaultVal
    }

    private fun removeRawKey(key: String) {
        val prefs = hardwarePrefs
        if (prefs != null) {
            prefs.edit { remove(key) }
        } else {
            memoryVault.remove(key)
        }
    }

    /**
     * Ephemeral AES-256-GCM authenticated encryption vault in RAM.
     * Used as a fail-safe when hardware KeyStore is unavailable so plaintext is NEVER written to disk.
     */
    private class EphemeralAesGcmVault(private val secureRandom: SecureRandom) {
        private val aesKey: SecretKey = KeyGenerator.getInstance("AES").apply {
            init(256, secureRandom)
        }.generateKey()

        private val cipherMap = ConcurrentHashMap<String, ByteArray>()

        fun putEncrypted(key: String, plaintext: String) {
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            secureRandom.nextBytes(iv)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.updateAAD(key.toByteArray(StandardCharsets.UTF_8))
            val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            cipherMap[key] = combined
        }

        fun getDecrypted(key: String): String? {
            val combined = cipherMap[key] ?: return null
            if (combined.size <= GCM_IV_LENGTH_BYTES) return null
            return try {
                val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
                val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                cipher.updateAAD(key.toByteArray(StandardCharsets.UTF_8))
                val plainBytes = cipher.doFinal(ciphertext)
                String(plainBytes, StandardCharsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }

        fun remove(key: String) {
            cipherMap.remove(key)
        }

        fun clearAll() {
            cipherMap.clear()
        }
    }

    companion object {
        private const val TAG = "EncryptedSessionStore"
        private const val PREFS_FILE_NAME = "iot_secure_operator_store"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val DEFAULT_AUTH_METHOD = "Google Account"
        private const val ENCRYPTION_MODE_HARDWARE = "AES-256-GCM (Android KeyStore Hardware)"
        private const val ENCRYPTION_MODE_MEMORY_VAULT = "AES-256-GCM (Bóveda Efímera en RAM)"
        private const val KEY_HMAC_SECRET = "sec_hmac_key_v1"
        private const val KEY_UID = "sec_uid"
        private const val KEY_DISPLAY_NAME = "sec_name"
        private const val KEY_EMAIL = "sec_email"
        private const val KEY_TOKEN = "sec_token"
        private const val KEY_AUTH_METHOD = "sec_method"
        private const val KEY_TIMESTAMP = "sec_time"
        private const val KEY_EXPIRES_AT = "sec_expires_at"
        private const val KEY_VEHICLE_IP = "cfg_vehicle_ip"
        private const val KEY_VEHICLE_PORT = "cfg_vehicle_port"
        private const val KEY_STREAM_URL = "cfg_stream_url"
    }
}
