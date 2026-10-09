package io.github.lonemoonspace.dayloom.core.secret

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * [SecretStore] on the `secrets` Preferences file; the key is the credential id.
 * 基于 `secrets` Preferences 文件的 [SecretStore]；键即凭据 id。
 */
class DataStoreSecretStore(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : SecretStore {

    // Deduplicate on the raw value before decrypting: writes to other keys do not trigger another (costly) Keystore call.
    // 先按原文去重再解密：其他键的写入不会再触发一次（不便宜的）Keystore 调用。
    override fun observe(id: String): Flow<SecretState> =
        store.data
            .map { it[stringPreferencesKey(id)].orEmpty() }
            .distinctUntilChanged()
            .map { SecretField.read(it, cipher::decrypt) }

    override suspend fun usable(id: String): String =
        SecretField.read(store.data.first()[stringPreferencesKey(id)].orEmpty(), cipher::decrypt).display

    override suspend fun put(id: String, plain: String) {
        // Encrypt outside the edit block: a failure reaches the caller without opening a write transaction.
        // 加密放在 edit 之外：失败直接抛给调用方，不开写事务。
        val stored = cipher.encrypt(plain)
        val key = stringPreferencesKey(id)
        store.edit { prefs -> if (stored.isEmpty()) prefs.remove(key) else prefs[key] = stored }
    }
}
