package io.github.lonemoonspace.dayloom.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import io.github.lonemoonspace.dayloom.core.json.AppJson
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * A DataStore holding one JSON document in its own file. Only one instance may exist per file in a process,
 * so these are created in `AppGraph` (or with a temp file in tests).
 * 一个文件存一份 JSON 的 DataStore。同一文件在进程内只能有一个实例，所以只在 `AppGraph` 里创建（测试用临时文件）。
 *
 * OkioStorage instead of the File-based serializer: the latter cannot replace an existing file on Windows JVMs,
 * which breaks local unit tests.
 * 用 OkioStorage 而不是 File 版序列化器：后者在 Windows JVM 上无法覆盖已有文件，本地单测会失败。
 */
internal fun <T> jsonFileDataStore(
    serializer: KSerializer<T>,
    default: T,
    scope: CoroutineScope,
    onDecodeError: (Throwable) -> Unit,
    produceFile: () -> File,
): DataStore<T> = DataStoreFactory.create(
    storage = OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = JsonOkioSerializer(serializer, default, onDecodeError),
        producePath = { produceFile().toOkioPath() },
    ),
    scope = scope,
)

private class JsonOkioSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
    private val onDecodeError: (Throwable) -> Unit,
) : OkioSerializer<T> {

    // A corrupt or incompatible document falls back to the default instead of throwing CorruptionException:
    // a broken settings file must not lock the user out of the app. SerializationException is an IllegalArgumentException.
    // 损坏或不兼容的文档回退为默认值，而不是抛 CorruptionException：设置文件坏了不能让用户无法使用 App。
    // SerializationException 是 IllegalArgumentException 的子类。
    override suspend fun readFrom(source: BufferedSource): T {
        val text = source.readUtf8()
        if (text.isBlank()) return defaultValue
        return try {
            AppJson.standard.decodeFromString(serializer, text)
        } catch (e: IllegalArgumentException) {
            onDecodeError(e)
            defaultValue
        }
    }

    override suspend fun writeTo(t: T, sink: BufferedSink) {
        sink.writeUtf8(AppJson.standard.encodeToString(serializer, t))
    }
}
