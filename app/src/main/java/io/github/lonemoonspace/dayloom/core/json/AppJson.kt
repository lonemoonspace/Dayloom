package io.github.lonemoonspace.dayloom.core.json

import kotlinx.serialization.json.Json

object AppJson {
    /**
     * Shared by caches, settings and external APIs. `coerceInputValues` falls back to the default when an API
     * sends null or an unknown enum value, instead of failing the whole payload; it only affects decoding.
     * 缓存、设置与外部接口共用。`coerceInputValues`：接口给 null 或未知枚举值时取默认值，而不是整包解码失败；只影响解码。
     */
    val standard: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
}
