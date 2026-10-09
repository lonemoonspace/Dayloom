package io.github.lonemoonspace.dayloom.core.secret

import java.util.Base64

/**
 * Follows the [SecretBox] contract without a Keystore: ciphertext looks like `v1:fake.<base64>`; any other `v1:` value is
 * treated as "this device's key cannot decrypt it".
 * 按 [SecretBox] 的契约行事、不需要 Keystore：密文形如 `v1:fake.<base64>`；其他 `v1:` 开头的值一律视为「本机密钥解不开」。
 */
class FakeCipher : SecretCipher {
    var failEncrypt = false

    override fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        if (value.startsWith(SecretBox.PREFIX)) return value
        if (failEncrypt) throw IllegalStateException("cannot store credential securely")
        return ciphertextOf(value)
    }

    override fun decrypt(value: String): String? {
        if (value.isBlank() || !value.startsWith(SecretBox.PREFIX)) return value
        if (!value.startsWith(MARK)) return null
        return String(Base64.getDecoder().decode(value.removePrefix(MARK)), Charsets.UTF_8)
    }

    companion object {
        private const val MARK = "${SecretBox.PREFIX}fake."

        fun ciphertextOf(plain: String): String = MARK + Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8))

        /** Encrypted on another device; cannot be decrypted here. / 在另一台设备上加密、本机解不开。 */
        fun foreignCiphertext(tag: String): String = "${SecretBox.PREFIX}iv-$tag.undecryptable"
    }
}
