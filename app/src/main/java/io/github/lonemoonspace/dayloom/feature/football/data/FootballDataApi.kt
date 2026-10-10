package io.github.lonemoonspace.dayloom.feature.football.data

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.core.network.decodeOrBadData
import io.github.lonemoonspace.dayloom.core.network.executeOrAppError
import io.github.lonemoonspace.dayloom.feature.football.domain.FootballPolicy
import io.github.lonemoonspace.dayloom.feature.football.domain.Match
import io.github.lonemoonspace.dayloom.feature.football.domain.MatchStatus
import io.github.lonemoonspace.dayloom.feature.football.domain.Standings
import io.github.lonemoonspace.dayloom.feature.football.domain.TableGroup
import io.github.lonemoonspace.dayloom.feature.football.domain.TableRow
import io.github.lonemoonspace.dayloom.feature.football.domain.TeamRef
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * football-data.org v4 with the user's own key, sent as `X-Auth-Token`. The free tier covers twelve competitions and ten
 * requests a minute; a 429 is reported like any HTTP error and the next round tries again.
 * 使用用户自己 Key 的 football-data.org v4，Key 放在 `X-Auth-Token` 头里。免费档覆盖十二项赛事、每分钟十次请求；429 与其他
 * HTTP 错误一样报告，下一轮再试。
 */
class FootballDataApi(
    private val http: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.football-data.org/v4/".toHttpUrl(),
    private val io: CoroutineContext = Dispatchers.IO,
) {

    /** Teams of a competition's current season, for the team picker. / 某项赛事当前赛季的球队，供选择球队用。 */
    suspend fun teams(key: String, competition: String): List<TeamRef> = withContext(io) {
        val url = baseUrl.newBuilder().addPathSegments("competitions").addPathSegment(competition).addPathSegment("teams").build()
        get(key, url, TeamsDto.serializer(), "teams response").teams.map { it.toRef() }.sortedBy { it.label }
    }

    suspend fun teamMatches(key: String, teamId: Int, from: LocalDate, to: LocalDate): List<Match> = withContext(io) {
        val url = baseUrl.newBuilder()
            .addPathSegment("teams").addPathSegment(teamId.toString()).addPathSegment("matches")
            .addQueryParameter("dateFrom", from.toString())
            .addQueryParameter("dateTo", to.toString())
            .build()
        get(key, url, MatchesDto.serializer(), "matches response").matches.mapNotNull { it.toMatch() }
    }

    suspend fun standings(key: String, competition: String): Standings = withContext(io) {
        val url = baseUrl.newBuilder().addPathSegment("competitions").addPathSegment(competition).addPathSegment("standings").build()
        val dto = get(key, url, StandingsDto.serializer(), "standings response")
        // Only the overall tables; home and away tables repeat the same teams. / 只取总表；主场表、客场表是同一批球队的重复。
        val groups = dto.standings.filter { it.type == "TOTAL" }.map { s ->
            TableGroup(
                group = s.group.orEmpty(),
                rows = s.table.map { r ->
                    TableRow(r.position, r.team.toRef(), r.playedGames, r.won, r.draw, r.lost, r.goalDifference, r.points)
                },
            )
        }
        Standings(competitionCode = dto.competition.code.ifBlank { competition }, competition = dto.competition.name, groups = groups)
    }

    private fun <T> get(key: String, url: HttpUrl, serializer: KSerializer<T>, what: String): T {
        val request = Request.Builder().url(url).header("X-Auth-Token", key).build()
        http.newCall(request).executeOrAppError().use { response ->
            val body = response.body.string()
            // The message says what is wrong (restricted competition, bad key), so a short excerpt is kept.
            // 错误信息会说明原因（赛事不在套餐内、Key 不对），所以保留一小段。
            if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code, body.take(200))
            return decodeOrBadData(SERVICE, what) { AppJson.standard.decodeFromString(serializer, body) }
        }
    }

    companion object {
        const val SERVICE = "football-data.org"

        internal fun parseMillis(iso: String?): Long? = try {
            iso?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
        } catch (_: DateTimeParseException) {
            null
        }

        /** 52, "52" and "45+2" all give the leading number; anything else gives null. / 52、"52" 与 "45+2" 都取开头的数字；其他情况为 null。 */
        internal fun minuteOf(raw: JsonElement?): Int? =
            (raw as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()

        internal fun status(raw: String?): MatchStatus = when (raw) {
            // "LIVE" is the umbrella value some responses use for in-play matches. / 有些响应用「LIVE」笼统表示进行中。
            "LIVE" -> MatchStatus.IN_PLAY
            else -> MatchStatus.entries.firstOrNull { it.name == raw } ?: MatchStatus.UNKNOWN
        }
    }
}

@Serializable
internal data class TeamDto(
    val id: Int = 0,
    val name: String? = null,
    val shortName: String? = null,
    val tla: String? = null,
    val crest: String? = null,
) {
    fun toRef() = TeamRef(id, name.orEmpty(), shortName.orEmpty(), tla.orEmpty(), crest.orEmpty())
}

@Serializable
internal data class TeamsDto(val teams: List<TeamDto> = emptyList())

@Serializable
internal data class GoalsDto(val home: Int? = null, val away: Int? = null) {
    fun pair() = home to away
}

@Serializable
internal data class ScoreDto(
    val duration: String? = null,
    val fullTime: GoalsDto = GoalsDto(),
    val regularTime: GoalsDto? = null,
    val extraTime: GoalsDto? = null,
    val penalties: GoalsDto? = null,
)

@Serializable
internal data class CompetitionDto(val name: String = "", val code: String = "")

@Serializable
internal data class MatchDto(
    val id: Long = 0,
    val utcDate: String? = null,
    val status: String? = null,
    val matchday: Int? = null,
    /** Documented as a number; read leniently so an odd value never fails the whole response. / 文档写的是数字；宽松读取，异常的值不会让整个响应解码失败。 */
    val minute: JsonElement? = null,
    val injuryTime: JsonElement? = null,
    val competition: CompetitionDto = CompetitionDto(),
    val homeTeam: TeamDto = TeamDto(),
    val awayTeam: TeamDto = TeamDto(),
    val score: ScoreDto = ScoreDto(),
) {
    /** A match without a kick-off time cannot be placed anywhere, so it is dropped. / 没有开球时间的比赛无处安放，丢掉。 */
    fun toMatch(): Match? {
        val kickoff = FootballDataApi.parseMillis(utcDate) ?: return null
        val (home, away) = FootballPolicy.scoreOfPlay(
            duration = score.duration,
            fullTime = score.fullTime.pair(),
            regularTime = score.regularTime?.pair(),
            extraTime = score.extraTime?.pair(),
            penalties = score.penalties?.pair(),
        )
        val shootout = score.duration == "PENALTY_SHOOTOUT"
        return Match(
            id = id,
            kickoff = kickoff,
            status = FootballDataApi.status(status),
            competition = competition.name,
            competitionCode = competition.code,
            matchday = matchday,
            home = homeTeam.toRef(),
            away = awayTeam.toRef(),
            homeGoals = home,
            awayGoals = away,
            homePens = if (shootout) score.penalties?.home else null,
            awayPens = if (shootout) score.penalties?.away else null,
            minute = FootballDataApi.minuteOf(minute),
            injuryTime = FootballDataApi.minuteOf(injuryTime)?.takeIf { it > 0 },
        )
    }
}

@Serializable
internal data class MatchesDto(val matches: List<MatchDto> = emptyList())

@Serializable
internal data class TableRowDto(
    val position: Int = 0,
    val team: TeamDto = TeamDto(),
    val playedGames: Int = 0,
    val won: Int = 0,
    val draw: Int = 0,
    val lost: Int = 0,
    val points: Int = 0,
    val goalDifference: Int = 0,
)

@Serializable
internal data class StandingDto(val type: String? = null, val group: String? = null, val table: List<TableRowDto> = emptyList())

@Serializable
internal data class StandingsDto(val competition: CompetitionDto = CompetitionDto(), val standings: List<StandingDto> = emptyList())
