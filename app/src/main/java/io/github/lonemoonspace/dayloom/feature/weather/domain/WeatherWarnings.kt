package io.github.lonemoonspace.dayloom.feature.weather.domain

import kotlinx.serialization.Serializable

/**
 * The snapshot of `weather.warnings`: MET's land warnings in force at the weather place. Event and level are MET's codes
 * and are translated only when shown; the description and advice are MET's own English text, shown as a quote when the
 * user opens a warning.
 * `weather.warnings` 的快照：天气地点当前有效的 MET 陆地预警。事件与级别存 MET 的代码，显示时才翻译；说明与建议是 MET 自己的
 * 英文原文，用户点开预警时作为引文显示。
 */
@Serializable
data class WeatherWarnings(val warnings: List<WeatherWarning> = emptyList())

@Serializable
data class WeatherWarning(
    /** MET's CAP identifier; a new version of a warning gets a new one. / MET 的 CAP 标识；预警更新后会换一个新的。 */
    val id: String = "",
    /** MET event code, e.g. `wind`, `rain`, `ice`. / MET 的事件代码，如 `wind`、`rain`、`ice`。 */
    val event: String = "",
    /** 2 yellow, 3 orange, 4 red, as in MET's `awareness_level`. / 2 黄、3 橙、4 红，与 MET 的 `awareness_level` 一致。 */
    val level: Int = 0,
    /** Epoch millis. / epoch 毫秒。 */
    val start: Long = 0,
    val end: Long = 0,
    val description: String = "",
    val instruction: String = "",
) {
    val severity: WarningLevel? get() = WarningLevel.fromMet(level)
    val kind: WarningEvent get() = WarningEvent.fromMet(event)
}

enum class WarningLevel(val met: Int) {
    YELLOW(2), ORANGE(3), RED(4);

    companion object {
        fun fromMet(level: Int): WarningLevel? = entries.firstOrNull { it.met == level }
    }
}

/**
 * MET's land warning events. Codes the app does not know yet still show, as a general weather warning.
 * MET 的陆地预警事件。App 还不认识的代码照样显示，归为一般天气预警。
 */
enum class WarningEvent(val met: String) {
    WIND("wind"),
    RAIN("rain"),
    RAIN_FLOOD("rainFlood"),
    FLOOD("flood"),
    SNOW("snow"),
    BLOWING_SNOW("blowingSnow"),
    ICE("ice"),
    LIGHTNING("lightning"),
    FOREST_FIRE("forestFire"),
    AVALANCHES("avalanches"),
    DRIVING_CONDITIONS("drivingConditions"),
    STORM_SURGE("stormSurge"),
    GALE("gale"),
    POLAR_LOW("polarLow"),
    ICING("icing"),
    OTHER("");

    companion object {
        fun fromMet(code: String): WarningEvent = entries.firstOrNull { it.met.isNotEmpty() && it.met.equals(code, ignoreCase = true) } ?: OTHER
    }
}
