package io.github.lonemoonspace.dayloom.core.secret

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class SecretState(
    /** For input fields: the plaintext, or "" when unset or unreadable (ciphertext is never shown). / 输入框回填：明文；未设置或解不开时为 ""（永不显示密文）。 */
    val display: String,
    /** Something is stored, including unreadable ciphertext. / 存储里有值（含解不开的密文）。 */
    val isSet: Boolean,
    /** Stored but cannot be decrypted; the user must re-enter it. / 有值但解不开，需要用户重新输入。 */
    val unreadable: Boolean,
) {
    /** Never put the plaintext into logs or exception messages. / 不把明文带进日志或异常信息。 */
    override fun toString(): String = "SecretState(isSet=$isSet, unreadable=$unreadable)"

    companion object {
        val EMPTY = SecretState(display = "", isSet = false, unreadable = false)
    }
}

/**
 * The only way to read and write credentials. Ids are namespaced `<moduleId>.<name>` (see `ModuleContext.secret`).
 * Requests always use [usable]: an unreadable credential counts as unset, so ciphertext is never sent as a credential.
 * 读写凭据的唯一入口。id 带命名空间 `<模块id>.<名称>`（见 `ModuleContext.secret`）。
 * 发请求一律用 [usable]：解不开的凭据视同未设置，密文永远不会被当作凭据发出去。
 */
interface SecretStore {
    fun observe(id: String): Flow<SecretState>

    /** For requests: "" when unset or unreadable. / 发请求用：未设置或解不开都返回 ""。 */
    suspend fun usable(id: String): String

    /** Encrypts and stores; blank deletes. / 加密后保存；空白 = 删除。 */
    suspend fun put(id: String, plain: String)
}

suspend fun SecretStore.current(id: String): SecretState = observe(id).first()
