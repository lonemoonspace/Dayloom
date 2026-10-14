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
 * Modules never see each other's keys, except a split-out module reading its [Former] one until it saves its own; a broken
 * entry falls back to the module's defaults.
 * `module_settings` 文件：每个模块一个键（模块 id），值是该模块设置的 JSON。
 * 模块之间看不到彼此的键，只有拆分出来的模块在保存自己的设置之前会读原模块（[Former]）的键；某一项损坏时回退为该模块的默认值。
 */
class ModuleSettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val onDecodeError: (moduleId: String, Throwable) -> Unit = { _, _ -> },
) {
    /**
     * Where a split-out module's settings come from until it saves its own: the entry of [moduleId], read by [read].
     * 拆分出来的模块在保存自己的设置之前，设置从哪里来：[moduleId] 的条目，由 [read] 读出。
     */
    class Former<T> private constructor(val moduleId: String, internal val read: (String) -> T) {
        companion object {
            fun <T, F> of(moduleId: String, serializer: KSerializer<F>, convert: (F) -> T): Former<T> =
                Former(moduleId) { raw -> convert(AppJson.standard.decodeFromString(serializer, raw)) }
        }
    }

    fun <T> forModule(moduleId: String, serializer: KSerializer<T>, default: T, former: Former<T>? = null): ValueStore<T> =
        ModuleValueStore(moduleId, serializer, default, former)

    private inner class ModuleValueStore<T>(
        private val moduleId: String,
        private val serializer: KSerializer<T>,
        private val default: T,
        private val former: Former<T>?,
    ) : ValueStore<T> {
        private val key = stringPreferencesKey(moduleId)
        private val formerKey = former?.let { stringPreferencesKey(it.moduleId) }

        override val flow: Flow<T> = dataStore.data
            .map { it[key] to formerKey?.let(it::get) }
            // Decode only when this module's entry changed, not on every write to another module.
            // 只在本模块的条目变化时解码，其他模块写入时不重复解码。
            .distinctUntilChanged()
            .map { (raw, formerRaw) -> decode(raw, formerRaw) }

        override suspend fun update(transform: (T) -> T) {
            dataStore.edit { prefs ->
                prefs[key] = AppJson.standard.encodeToString(serializer, transform(decode(prefs[key], formerKey?.let(prefs::get))))
            }
        }

        private fun decode(raw: String?, formerRaw: String?): T {
            if (raw.isNullOrBlank()) return formerRaw?.takeIf { it.isNotBlank() }?.let(::decodeFormer) ?: default
            return try {
                AppJson.standard.decodeFromString(serializer, raw)
            } catch (e: IllegalArgumentException) {
                onDecodeError(moduleId, e)
                default
            }
        }

        private fun decodeFormer(raw: String): T? = try {
            former?.read?.invoke(raw)
        } catch (e: IllegalArgumentException) {
            onDecodeError(moduleId, e)
            null
        }
    }
}
