package io.github.lonemoonspace.dayloom.core.notify

import io.github.lonemoonspace.dayloom.core.json.AppJson
import java.time.LocalDate
import kotlinx.serialization.KSerializer

/** Nullable string; empty or missing = null. / 可空字符串；空串或缺失 = null。 */
object StringStateCodec : StateCodec<String?> {
    override fun decode(raw: String?): String? = raw?.takeIf { it.isNotEmpty() }
    override fun encode(state: String?): String = state.orEmpty()
}

/** `LocalDate.toString()`; empty or corrupt = null. / `LocalDate.toString()`；空串或损坏 = null。 */
object LocalDateStateCodec : StateCodec<LocalDate?> {
    override fun decode(raw: String?): LocalDate? = raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    override fun encode(state: LocalDate?): String = state?.toString().orEmpty()
}

/** JSON via [AppJson.standard]; corrupt = [initial]. / 经 [AppJson.standard] 的 JSON；损坏 = [initial]。 */
class JsonStateCodec<S>(private val serializer: KSerializer<S>, private val initial: S) : StateCodec<S> {
    override fun decode(raw: String?): S =
        raw?.let { runCatching { AppJson.standard.decodeFromString(serializer, it) }.getOrNull() } ?: initial

    override fun encode(state: S): String = AppJson.standard.encodeToString(serializer, state)
}
