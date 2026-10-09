package io.github.lonemoonspace.dayloom.core.storage

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer

/**
 * Creates the JSON-document stores (`app_settings`, `shared_data`). Each file must be opened only once per process.
 * 创建 JSON 文档类存储（`app_settings`、`shared_data`）。每个文件在进程内只能打开一次。
 */
object AppStores {
    fun <T> jsonValueStore(
        serializer: KSerializer<T>,
        default: T,
        scope: CoroutineScope,
        onDecodeError: (Throwable) -> Unit,
        produceFile: () -> File,
    ): ValueStore<T> = DataStoreValueStore(jsonFileDataStore(serializer, default, scope, onDecodeError, produceFile))

    const val APP_SETTINGS_FILE = "app_settings"
    const val SHARED_DATA_FILE = "shared_data"
}
