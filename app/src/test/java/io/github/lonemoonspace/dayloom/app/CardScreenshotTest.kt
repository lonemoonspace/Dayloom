package io.github.lonemoonspace.dayloom.app

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.app.home.TodaySummary
import io.github.lonemoonspace.dayloom.app.settings.ModuleStatus
import io.github.lonemoonspace.dayloom.app.settings.ModuleToggle
import io.github.lonemoonspace.dayloom.app.settings.SettingsPage
import io.github.lonemoonspace.dayloom.app.settings.SettingsPageScreen
import io.github.lonemoonspace.dayloom.app.settings.SettingsScreen
import io.github.lonemoonspace.dayloom.app.settings.SettingsState
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.i18n.uiText
import io.github.lonemoonspace.dayloom.core.location.Place
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.time.FixedClock
import io.github.lonemoonspace.dayloom.core.ui.LocalAppClock
import io.github.lonemoonspace.dayloom.core.ui.theme.DayloomTheme
import io.github.lonemoonspace.dayloom.core.ui.theme.ThemePreset
import io.github.lonemoonspace.dayloom.feature.calendar.CalendarModule
import io.github.lonemoonspace.dayloom.feature.football.FootballModule
import io.github.lonemoonspace.dayloom.feature.news.NewsModule
import io.github.lonemoonspace.dayloom.feature.news.domain.Headline
import io.github.lonemoonspace.dayloom.feature.reminders.RemindersModule
import io.github.lonemoonspace.dayloom.feature.traffic.TrafficModule
import io.github.lonemoonspace.dayloom.feature.transit.BusModule
import io.github.lonemoonspace.dayloom.feature.transit.DeparturesModule
import io.github.lonemoonspace.dayloom.feature.transit.TrainModule
import io.github.lonemoonspace.dayloom.feature.weather.WeatherModule
import io.github.lonemoonspace.dayloom.feature.news.domain.SyncSummary
import io.github.lonemoonspace.dayloom.feature.news.ui.NewsCard
import io.github.lonemoonspace.dayloom.feature.reminders.RemindersSettings
import io.github.lonemoonspace.dayloom.feature.reminders.domain.ReminderItem
import io.github.lonemoonspace.dayloom.feature.reminders.ui.RemindersCard
import io.github.lonemoonspace.dayloom.feature.todo.TodoModule
import io.github.lonemoonspace.dayloom.feature.todo.TodoSettings
import io.github.lonemoonspace.dayloom.feature.todo.domain.TodoItem
import io.github.lonemoonspace.dayloom.feature.todo.ui.TodoCard
import io.github.lonemoonspace.dayloom.feature.traffic.domain.Direction
import io.github.lonemoonspace.dayloom.feature.traffic.domain.TrafficLevel
import io.github.lonemoonspace.dayloom.feature.traffic.domain.TrafficStatus
import io.github.lonemoonspace.dayloom.feature.traffic.ui.TrafficCard
import io.github.lonemoonspace.dayloom.feature.transit.domain.BoardDeparture
import io.github.lonemoonspace.dayloom.feature.transit.domain.BoardResult
import io.github.lonemoonspace.dayloom.feature.transit.domain.Boards
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteKind
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteMode
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteRoute
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteTrips
import io.github.lonemoonspace.dayloom.feature.transit.domain.FavouriteBoard
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitLeg
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitStop
import io.github.lonemoonspace.dayloom.feature.transit.domain.TripOption
import io.github.lonemoonspace.dayloom.feature.transit.ui.BoardsCard
import io.github.lonemoonspace.dayloom.feature.transit.ui.CommuteCard
import io.github.lonemoonspace.dayloom.feature.weather.domain.Forecast
import io.github.lonemoonspace.dayloom.feature.weather.domain.ForecastPoint
import io.github.lonemoonspace.dayloom.feature.weather.ui.WeatherCard
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the home cards with made-up data, at the 384 dp width of a common phone, in both languages and in the dark; the
 * pictures in `src/test/screenshots/` double as mock-ups of the current look. Nothing is drawn in a plain test run:
 * `-Pscreenshots=record` rewrites the pictures, and `-Pscreenshots=compare` leaves them alone and writes
 * `build/outputs/roborazzi/<name>_compare.png` for each card that no longer looks like its picture, which CI uploads.
 * Record on Linux, where CI runs: another OS rasterises text slightly differently.
 * 用虚构数据画出首页卡片：宽度取常见手机的 384 dp，中英文各一份，外加深色；`src/test/screenshots/` 里的图片同时也是当前样子的
 * 效果图。普通的测试运行不画任何东西：`-Pscreenshots=record` 重写这些图片；`-Pscreenshots=compare` 不动它们，只给每张与图片
 * 不再一致的卡片写一张 `build/outputs/roborazzi/<名字>_compare.png`，由 CI 上传。请在 Linux（CI 所在的系统）上录制：其他系统的
 * 文字渲染略有差别。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w384dp-h800dp-xhdpi")
class CardScreenshotTest {
    private val now = ZonedDateTime.of(2026, 10, 20, 8, 20, 0, 0, ZoneId.of("Europe/Oslo"))
    private val fetchedAt = now.minusMinutes(2).toInstant().toEpochMilli()

    private fun at(hour: Int, minute: Int) = now.withHour(hour).withMinute(minute).toInstant().toEpochMilli()

    private fun <T> snapshot(value: T) = Snapshot(value, fetchedAt = fetchedAt, paramsKey = "", schema = 1)

    private val lindvik = TransitStop("stop:1", "Lindvik")
    private val nordby = TransitStop("stop:2", "Nordby")
    private val route = CommuteRoute(origin = lindvik, destination = nordby, opposingHint = true)

    private fun ride(line: String, departure: Long, late: Int, arrival: Long, from: String, to: String, platform: String, realtime: Boolean = true) = TransitLeg(
        mode = "rail", line = line, fromName = from, toName = to,
        aimedDeparture = departure, expectedDeparture = departure + late * 60_000L,
        aimedArrival = arrival, expectedArrival = arrival + late * 60_000L,
        realtime = realtime, platform = platform,
    )

    @Test
    fun `train card`() {
        // A late first train held for a late opposing one, with a faster change folded over it; an on-time change; a direct
        // train without live data.
        // 首车晚点、在等晚点的对向车，更快的换乘方案叠在它上面；一班准点换乘；一班没有实时数据的直达。
        val outbound = listOf(
            TripOption(listOf(ride("L1", at(8, 24), 6, at(8, 52), "Lindvik stasjon", "Nordby stasjon", "1"))),
            TripOption(
                listOf(
                    ride("L1", at(8, 24), 6, at(8, 36), "Lindvik stasjon", "Sentrum stasjon", "1"),
                    ride("R12", at(8, 44), 0, at(8, 54), "Sentrum stasjon", "Nordby stasjon", "3"),
                ),
                alternative = true,
            ),
            TripOption(
                listOf(
                    ride("L1", at(8, 55), 0, at(9, 7), "Lindvik stasjon", "Sentrum stasjon", "1"),
                    ride("R12", at(9, 20), 0, at(9, 30), "Sentrum stasjon", "Nordby stasjon", "3"),
                ),
            ),
            TripOption(listOf(ride("L1", at(9, 25), 0, at(9, 58), "Lindvik stasjon", "Nordby stasjon", "1", realtime = false))),
        )
        val opposing = listOf(
            BoardDeparture(line = "L1", mode = "rail", frontText = "Vestby", platform = "2", aimed = at(8, 25), expected = at(8, 30), realtime = true),
        )
        val trips = CommuteTrips(CommuteMode.OUTBOUND, outbound = outbound, opposing = opposing)
        shots("train") { CommuteCard(CommuteKind.TRAIN, snapshot(trips), null, { _, _ -> false }, route, 0) {} }
    }

    @Test
    fun `train card turned round by hand`() {
        val inbound = listOf(
            TripOption(listOf(ride("L1", at(8, 31), 0, at(8, 59), "Nordby stasjon", "Lindvik stasjon", "2"))),
            TripOption(listOf(ride("L1", at(9, 1), 0, at(9, 29), "Nordby stasjon", "Lindvik stasjon", "2"))),
        )
        val trips = CommuteTrips(CommuteMode.INBOUND, inbound = inbound)
        val flippedAt = now.minusMinutes(1).toInstant().toEpochMilli()
        shots("train-flipped", variants = listOf("zh")) { CommuteCard(CommuteKind.TRAIN, snapshot(trips), null, { _, _ -> false }, route, flippedAt) {} }
    }

    @Test
    fun `departure board card`() {
        fun bus(line: String, front: String, platform: String, aimed: Long, late: Int = 0, cancelled: Boolean = false) = BoardDeparture(
            line = line, mode = "bus", frontText = front, platform = platform,
            aimed = aimed, expected = aimed + late * 60_000L, realtime = true, cancelled = cancelled,
        )
        val boards = Boards(
            listOf(
                BoardResult(
                    "board1",
                    "Sentrum",
                    listOf(
                        bus("250", "Nordby terminal", "B", at(8, 23)),
                        bus("31", "Vestby", "A", at(8, 27), late = 4),
                        bus("250", "Nordby terminal", "B", at(8, 38), cancelled = true),
                        bus("31E", "Lindvik via Sentrum", "C", at(8, 41)),
                    ),
                ),
            ),
        )
        val favourites = listOf(FavouriteBoard("board1", TransitStop("stop:3", "Sentrum")))
        shots("boards", variants = listOf("en", "zh")) { BoardsCard(snapshot(boards), null, { _, _ -> false }, favourites) }
    }

    @Test
    fun `weather card`() {
        // Hourly for two and a half days, then every six hours as MET does: a cool morning with rain around noon.
        // 先逐小时两天半，之后像 MET 那样每 6 小时一个点：凉爽的早晨，中午前后有雨。
        val start = now.withMinute(0)
        val hourly = (0 until 60).map { i ->
            val time = start.plusHours(i.toLong())
            val temperature = 7.0 - 4.0 * cos((time.hour - 3) * PI / 12)
            val rain = time.toLocalDate() == now.toLocalDate() && time.hour in 11..14
            val day = time.hour in 7..17
            ForecastPoint(
                time = time.toInstant().toEpochMilli(),
                temperature = temperature,
                windSpeed = 4.0,
                windGust = 9.0,
                symbol1h = if (rain) "rain" else if (day) "partlycloudy_day" else "partlycloudy_night",
                precipitation1h = if (rain) 1.4 else 0.0,
                apparentTemperature = temperature - 2.5,
                uvIndex = 1.0,
                precipProbability1h = if (rain) 85.0 else 5.0,
            )
        }
        val later = (0 until 28).map { i ->
            val time = start.withHour(2).plusDays(3).plusHours(i * 6L)
            ForecastPoint(
                time = time.toInstant().toEpochMilli(),
                temperature = 5.0 + (i % 4),
                windSpeed = 3.0,
                symbol6h = if (i % 8 < 4) "cloudy" else "lightsnow",
                precipitation6h = if (i % 8 < 4) 0.0 else 1.2,
                precipProbability6h = if (i % 8 < 4) 10.0 else 70.0,
                temperatureMin6h = 3.0 + (i % 4),
                temperatureMax6h = 8.0 + (i % 4),
            )
        }
        val forecast = Forecast(updatedAt = fetchedAt, observedAt = start.toInstant().toEpochMilli(), points = hourly + later)
        shots("weather", variants = listOf("en", "zh")) { WeatherCard(snapshot(forecast), null, { _, _ -> false }) }
        // Any place but Home is named in front of the condition. / 不是家的地点，名字写在天气状况前面。
        shots("weather-place", variants = listOf("zh-night")) { WeatherCard(snapshot(forecast), null, { _, _ -> false }, placeLabel = "Nordby") }
    }

    @Test
    fun `home summary`() {
        // The morning brief's lines as the weather, train, traffic and reminders modules word them.
        // 天气、火车、路况和到期提醒模块各自给出的早间简报那一行。
        val lines = listOf(
            UiText.Res(R.string.weather_brief_umbrella, listOf(UiText.Res(R.string.weather_partly_cloudy), 6, UiText.Res(R.string.weather_clothing_cold))),
            UiText.Res(R.string.transit_brief, listOf("L1", UiText.Time(LocalTime.of(8, 24)), UiText.Plural(R.plurals.transit_late, 6))),
            UiText.Res(R.string.traffic_brief, listOf("Nordby", UiText.Plural(R.plurals.traffic_minutes, 26), UiText.Res(R.string.traffic_slight))),
            UiText.Res(R.string.reminders_notify_tomorrow, listOf("Monthly ticket")),
        )
        shots("home-summary", variants = listOf("en", "zh")) { TodaySummary(lines) }
    }

    @Test
    fun `traffic card`() {
        val status = TrafficStatus(durationSec = 1_560, staticDurationSec = 1_260, delaySec = 300, distanceMeters = 23_400, level = TrafficLevel.SLIGHT, direction = Direction.TO_WORK)
        val home = Place(id = Place.HOME, name = "Lindvik", lat = 60.0, lon = 10.0)
        val work = Place(id = Place.WORK, name = "Nordby", lat = 60.1, lon = 10.2)
        shots("traffic", variants = listOf("en", "zh")) { TrafficCard(snapshot(status), null, { _, _ -> false }, home, work, 0) {} }
    }

    @Test
    fun `reminders card`() {
        val settings = RemindersSettings(
            items = listOf(
                ReminderItem("item1", "Monthly ticket", "2026-10-21T23:59", warnDays = 3),
                ReminderItem("item2", "Parking permit", "2026-11-30T23:59", warnDays = 7),
                ReminderItem("item3", "Passport", "2031-04-12T23:59", warnDays = 90),
            ),
        )
        shots("reminders", variants = listOf("en", "zh")) { RemindersCard(flowOf(settings)) }
    }

    @Test
    fun `todo card`() {
        val settings = TodoSettings(
            items = listOf(
                TodoItem("todo1", "Change to winter tyres", due = "2026-10-18", createdAt = 1),
                TodoItem("todo2", "Pay the rent", due = "2026-10-20", createdAt = 2),
                TodoItem("todo3", "Book the dentist", due = "2026-10-27", createdAt = 3),
                TodoItem("todo4", "Buy coffee beans", createdAt = 4),
                TodoItem("todo5", "Answer the landlord about the heating check and when it suits", createdAt = 5),
                TodoItem("todo6", "Water the plants", createdAt = 6),
                TodoItem("todo7", "Return the library books", createdAt = 7, doneAt = fetchedAt),
            ),
        )
        shots("todo") { TodoCard(flowOf(settings)) {} }
    }

    @Test
    fun `news card`() {
        val summary = SyncSummary(
            unread = 12,
            starred = 3,
            total = 40,
            latest = listOf(
                Headline(1, "A long headline that has to be cut short on a phone-sized card, however wide it is", "Example Daily", at(8, 5)),
                Headline(2, "编织的历史：从平纹到提花", "示例周刊", at(7, 40)),
                Headline(3, "Short one", "Example Blog", at(6, 15)),
            ),
        )
        shots("news", variants = listOf("en", "zh")) { NewsCard(summary, configured = true, feeds = true, error = null, stale = false, pushRejected = false) }
    }

    /**
     * One picture per variant: Robolectric resource qualifiers joined by `-` (`zh`, `en-night`), which is also the file suffix.
     * English is drawn with a 12-hour clock and Chinese with a 24-hour one, set explicitly: left unset, Android asks the JDK
     * what the language prefers, and JDK versions disagree about Chinese.
     * 每个变体一张图：变体是用 `-` 连起来的 Robolectric 资源限定符（`zh`、`en-night`），同时也是文件名后缀。英文用 12 小时制、
     * 中文用 24 小时制，并且显式设定：不设的话 Android 会去问 JDK 这种语言习惯用哪种，而不同版本的 JDK 对中文的回答不一样。
     */
    /**
     * The settings list, which brings its own margins and is taller than one card, so the picture is the whole list.
     * 设置列表：它自带边距、也比一张卡片高，所以图片是整个列表。
     */
    @Test
    @Config(qualifiers = "w384dp-h1100dp-xhdpi")
    fun `settings list`() {
        fun line(text: UiText?) = ModuleStatus.Line(text)
        val modules = listOf(
            ModuleToggle(CalendarModule, true, status = line(null)),
            ModuleToggle(WeatherModule, true, status = line(UiText.Res(R.string.place_home))),
            ModuleToggle(TrainModule, true, status = line(uiText(R.string.transit_settings_status, UiText.Raw("Lindvik"), UiText.Raw("Nordby")))),
            ModuleToggle(BusModule, false),
            ModuleToggle(DeparturesModule, true, status = line(UiText.Plural(R.plurals.transit_boards_status, 2))),
            ModuleToggle(TrafficModule, true, status = line(uiText(R.string.traffic_card_title, UiText.Res(R.string.place_home), UiText.Res(R.string.place_work)))),
            ModuleToggle(TodoModule, true, status = line(UiText.Plural(R.plurals.todo_open_count, 4))),
            ModuleToggle(RemindersModule, true, status = line(UiText.Plural(R.plurals.reminders_settings_status, 3))),
            ModuleToggle(FootballModule, true, status = ModuleStatus.NeedsSetup(uiText(R.string.football_setup_key))),
            ModuleToggle(NewsModule, true, status = line(UiText.Plural(R.plurals.news_settings_status_feeds, 12))),
        )
        val state = SettingsState(
            effectiveZone = "Europe/Oslo",
            themePreset = ThemePreset.NORDIC_DAWN.id,
            modules = modules,
            morningBrief = true,
        )
        shots("settings-list", margin = false) {
            SettingsScreen(state, listOf(Place(Place.HOME), Place(Place.WORK)), onOpenPage = {}, onOpenModule = {}, onModuleEnabled = { _, _ -> })
        }
    }

    /**
     * The appearance page: the theme list with the woven-background switch, then the home card with the summary switch.
     * 外观页：主题列表和背景织纹开关，下面是带摘要开关的「首页」卡片。
     */
    @Test
    @Config(qualifiers = "w384dp-h560dp-xhdpi")
    fun `settings appearance page`() {
        val state = SettingsState(themePreset = ThemePreset.NORDIC_DAWN.id)
        shots("settings-appearance", variants = listOf("zh"), margin = false) {
            SettingsPageScreen(
                page = SettingsPage.APPEARANCE,
                state = state,
                onLanguage = {},
                onTimeZone = { true },
                onMorningBrief = {},
                onMorningBriefTime = {},
                onThemePreset = {},
                onWeaveBackground = {},
                onHomeSummary = {},
            )
        }
    }

    private fun shots(
        name: String,
        variants: List<String> = listOf("en", "zh", "en-night"),
        margin: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        for (variant in variants) {
            RuntimeEnvironment.setQualifiers("+$variant")
            val clock = if (variant.startsWith("en")) "12" else "24"
            Settings.System.putString(RuntimeEnvironment.getApplication().contentResolver, Settings.System.TIME_12_24, clock)
            captureRoboImage("src/test/screenshots/$name-$variant.png") {
                CompositionLocalProvider(LocalAppClock provides FixedClock(now)) {
                    // A fixed preset: the default follows the wallpaper, which a test has none of. / 固定用一个预设：默认配色跟随壁纸，而测试里没有壁纸。
                    DayloomTheme(preset = ThemePreset.NORDIC_DAWN.id) {
                        Surface(color = MaterialTheme.colorScheme.background) { Column(Modifier.padding(if (margin) 12.dp else 0.dp)) { content() } }
                    }
                }
            }
        }
    }
}
