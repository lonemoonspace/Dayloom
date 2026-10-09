package io.github.lonemoonspace.dayloom.core.secret

/**
 * Maps the stored raw value to a [SecretState]. Display value and request value both come from [SecretState.display],
 * which is "" when decryption fails, so ciphertext can never end up in an input field or a request.
 * 把落盘原文映射为 [SecretState]。展示值与请求值都来自 [SecretState.display]，解密失败时为 ""，
 * 密文因此不会进入输入框，也不会被当作凭据发出。
 */
object SecretField {

    fun read(raw: String, decrypt: (String) -> String?): SecretState {
        if (raw.isBlank()) return SecretState.EMPTY
        if (!isCiphertext(raw)) return SecretState(display = raw, isSet = true, unreadable = false)
        val plain = decrypt(raw) ?: return SecretState(display = "", isSet = true, unreadable = true)
        return SecretState(display = plain, isSet = true, unreadable = false)
    }

    /**
     * A user pasting `v1:…` as a credential would store undecryptable ciphertext ([SecretBox.encrypt] passes it through);
     * check this before saving.
     * 用户把 `v1:…` 当凭据输入时，保存的是解不开的密文（[SecretBox.encrypt] 会原样透传）；保存前用它拦截。
     */
    fun isCiphertext(value: String): Boolean = value.startsWith(SecretBox.PREFIX)
}
