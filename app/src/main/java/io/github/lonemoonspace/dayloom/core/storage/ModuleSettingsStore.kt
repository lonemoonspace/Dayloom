package io.github.lonemoonspace.dayloom.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.lonemoonspace.dayloom.core.json.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer

/**
 * The `module_settings` file: one key per module (the module id), holding that module's settings as JSON.
 * Modules never see each other's keys; a broken entry falls back to the module's defaults.
 * `module_settings` 文件：每个模块一个键（模块 id），值是该模块设置的 JSON。
 * 模块之间看不到彼此的键；某一项损坏时回退为该模块的默认值。
 */
class ModuleSettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val onDecodeError: (moduleId: String, Throwable) -> Unit = { _, _ -> },
) {
    fun <T> forModule(moduleId: String, serializer: KSerializer<T>, default: T): ValueStore<T> =
        ModuleValueStore(moduleId, serializer, default)

    private inner class ModuleValueStore<T>(
        private val moduleId: String,
        private val serializer: KSerializer<T>,
        private val default: T,
    ) : ValueStore<T> {
        private val key = stringPreferencesKey(moduleId)

        override val flow: Flow<T> = dataStore.data
            .map { it[key] }
            // Decode only when this module's entry changed, not on every write to another module.
            // 只在本模块的条目变化时解码，其他模块写入时不重复解码。
            .distinctUntilChanged()
            .map(::decode)

        override suspend fun update(transform: (T) -> T) {
            dataStore.edit { prefs ->
                prefs[key] = AppJson.standard.encodeToString(serializer, transform(decode(prefs[key])))
            }
        }

        private fun decode(raw: String?): T {
            if (raw.isNullOrBlank()) return default
            return try {
                AppJson.standard.decodeFromString(serializer, raw)
            } catch (e: IllegalArgumentException) {
                onDecodeError(moduleId, e)
                default
            }
        }
    }
}
