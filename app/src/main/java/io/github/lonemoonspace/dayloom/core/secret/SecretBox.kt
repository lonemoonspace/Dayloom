package io.github.lonemoonspace.dayloom.core.secret

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Credential encryption. Production uses [SecretBox] (Android Keystore); JVM tests inject a fake, since the Keystore
 * is unavailable there.
 * 凭据加解密。生产实现是 [SecretBox]（Android Keystore）；JVM 单测注入假实现，因为那里没有 Keystore。
 */
interface SecretCipher {
    /** Blank → ""; already `v1:` ciphertext → unchanged; otherwise encrypt. Throws on failure. / 空白 → ""；已是 `v1:` 密文 → 原样；否则加密。失败抛异常。 */
    fun encrypt(value: String): String

    /** Non-`v1:` values pass through; `v1:` ciphertext that fails to decrypt returns null. / 非 `v1:` 原样返回；`v1:` 密文解密失败返回 null。 */
    fun decrypt(value: String): String?
}

/**
 * AES-GCM with a key in the Android Keystore. The `v1:` prefix and the key alias are frozen from v1.0.0:
 * changing either makes every stored credential unreadable.
 * 密钥存于 Android Keystore 的 AES-GCM。`v1:` 前缀与密钥 alias 从 v1.0.0 起冻结：改了等于所有已存凭据失效。
 */
object SecretBox : SecretCipher {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "dayloom_secrets_v1"
    const val PREFIX = "v1:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    private val lock = Any()

    @Volatile
    private var cachedKey: SecretKey? = null

    override fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        // Already ciphertext (kept after a failed decrypt): write it back unchanged instead of encrypting twice.
        // 已是密文（解密失败后保留的原值）：原样写回，避免二次加密。
        if (value.startsWith(PREFIX)) return value
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val ciphertext = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            "$PREFIX$iv.$ciphertext"
        }.getOrElse { error ->
            throw IllegalStateException("cannot store credential securely", error)
        }
    }

    /**
     * Null means "looks like ciphertext but cannot be decrypted"; the store keeps the original and asks the user to
     * re-enter, rather than treating it as empty and overwriting it on an unrelated save.
     * null 表示「看起来是密文但解不开」；存储层保留原文并提示用户重新输入，而不是当成空串、在一次无关的保存里被覆盖。
     */
    override fun decrypt(value: String): String? {
        if (value.isBlank() || !value.startsWith(PREFIX)) return value
        return runCatching {
            val encoded = value.removePrefix(PREFIX).split('.', limit = 2)
            require(encoded.size == 2)
            val iv = Base64.decode(encoded[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(encoded[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun key(): SecretKey = synchronized(lock) {
        cachedKey?.let { return@synchronized it }
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
            cachedKey = it
            return@synchronized it
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        generator.generateKey().also { cachedKey = it }
    }
}
