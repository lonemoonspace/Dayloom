package io.github.lonemoonspace.dayloom.feature.transit.data

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.json.AppJson
import io.github.lonemoonspace.dayloom.core.network.decodeOrBadData
import io.github.lonemoonspace.dayloom.core.network.executeOrAppError
import io.github.lonemoonspace.dayloom.feature.transit.domain.BoardDeparture
import io.github.lonemoonspace.dayloom.feature.transit.domain.CommuteKind
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitLeg
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitProvider
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitStop
import io.github.lonemoonspace.dayloom.feature.transit.domain.TripOption
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Entur Journey Planner v3 (GraphQL) and Geocoder v3, Norway only, no key. Entur asks every client to identify itself with
 * `ET-Client-Name`. Query texts are constants and every caller value travels as a GraphQL variable, so a stop id can never
 * change the shape of a query.
 * Entur 行程规划 v3（GraphQL）与地理编码 v3，仅限挪威，无需 Key。Entur 要求每个客户端用 `ET-Client-Name` 标明身份。
 * 查询文本是常量，调用方的值一律作为 GraphQL 变量传入，站点 id 因此永远改变不了查询的结构。
 */
class EnturProvider(
    private val http: OkHttpClient,
    private val journeyPlanner: HttpUrl = "https://api.entur.io/journey-planner/v3/graphql".toHttpUrl(),
    private val geocoder: HttpUrl = "https://api.entur.io/geocoder/v3/autocomplete".toHttpUrl(),
    /** Where the blocking calls run; tests may keep it. / 阻塞调用在哪里运行；测试可以保留默认。 */
    private val io: CoroutineContext = Dispatchers.IO,
) : TransitProvider {

    override suspend fun searchStops(query: String): List<TransitStop> = withContext(io) {
        val text = query.trim()
        if (text.length < 2) return@withContext emptyList()
        val url = geocoder.newBuilder()
            .addQueryParameter("q", text)
            .addQueryParameter("limit", "10")
            .addQueryParameter("layers", "stopPlace")
            .build()
        val body = execute(Request.Builder().url(url).header(CLIENT_HEADER, CLIENT_NAME).build())
        decode(GeocoderResponse.serializer(), body, "geocoder response").features.mapNotNull { feature ->
            val id = feature.properties.id.takeIf { it.startsWith("NSR:StopPlace:") } ?: return@mapNotNull null
            TransitStop(
                id = id,
                name = feature.properties.names.default,
                locality = feature.properties.address.locality.orEmpty(),
            )
        }
    }

    override suspend fun planTrips(from: TransitStop, to: TransitStop, at: ZonedDateTime, count: Int, kind: CommuteKind): List<TripOption> =
        withContext(io) {
            val variables = buildJsonObject {
                put("from", from.id)
                put("to", to.id)
                put("at", at.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                put("count", count)
                put("modes", buildJsonArray { modesOf(kind).forEach { add(buildJsonObject { put("transportMode", it) }) } })
            }
            val response = decode(TripResponse.serializer(), post(TRIP_QUERY, variables), "trip response")
            failOnErrors(response.errors)
            response.data?.trip?.tripPatterns.orEmpty().mapNotNull { pattern ->
                // Walking legs only connect rides; a pattern with no ride at all is just a walk. / 步行段只是连接各段；一段车都不坐的方案只是步行。
                val legs = pattern.legs.filter { it.mode != "foot" }.mapNotNull(::toLeg)
                legs.takeIf { it.isNotEmpty() }?.let(::TripOption)
            }
        }

    override suspend fun departures(stopIds: List<String>, at: ZonedDateTime): Map<String, List<BoardDeparture>> =
        withContext(io) {
            if (stopIds.isEmpty()) return@withContext emptyMap()
            // Aliases come from indices, never from the ids themselves. / 别名由序号生成，从不取自 id 本身。
            val query = buildString {
                append("query Boards(")
                append(stopIds.indices.joinToString(", ") { "\$stop$it: String!" })
                append(", \$start: DateTime) {\n")
                stopIds.indices.forEach { append(boardBlock(it)).append('\n') }
                append("}")
            }
            val variables = buildJsonObject {
                put("start", at.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                stopIds.forEachIndexed { index, id -> put("stop$index", id) }
            }
            val response = decode(BoardsResponse.serializer(), post(query, variables), "departures response")
            failOnErrors(response.errors)
            val data = response.data ?: return@withContext emptyMap()
            stopIds.mapIndexedNotNull { index, id ->
                // A retired stop id comes back as null instead of an error. / 已退役的站点 id 返回 null 而不是报错。
                data["stop$index"]?.let { stop -> id to stop.estimatedCalls.mapNotNull(::toDeparture) }
            }.toMap()
        }

    private fun toLeg(leg: Leg): TransitLeg? {
        val aimedDep = millis(leg.aimedStartTime) ?: return null
        val aimedArr = millis(leg.aimedEndTime) ?: return null
        return TransitLeg(
            mode = leg.mode.orEmpty(),
            line = leg.line?.publicCode.orEmpty(),
            fromName = leg.fromPlace?.name.orEmpty(),
            toName = leg.toPlace?.name.orEmpty(),
            frontText = leg.fromEstimatedCall?.destinationDisplay?.frontText.orEmpty(),
            aimedDeparture = aimedDep,
            expectedDeparture = millis(leg.expectedStartTime) ?: aimedDep,
            aimedArrival = aimedArr,
            expectedArrival = millis(leg.expectedEndTime) ?: aimedArr,
            realtime = leg.realtime,
            cancelled = leg.fromEstimatedCall?.cancellation == true || leg.toEstimatedCall?.cancellation == true,
        )
    }

    private fun toDeparture(call: EstimatedCall): BoardDeparture? {
        val aimed = millis(call.aimedDepartureTime) ?: return null
        return BoardDeparture(
            line = call.serviceJourney?.line?.publicCode.orEmpty(),
            mode = call.serviceJourney?.line?.transportMode.orEmpty(),
            frontText = call.destinationDisplay?.frontText.orEmpty(),
            platform = call.quay?.publicCode.orEmpty(),
            aimed = aimed,
            expected = millis(call.expectedDepartureTime) ?: aimed,
            realtime = call.realtime,
            cancelled = call.cancellation,
        )
    }

    private fun post(query: String, variables: JsonObject): String {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val request = Request.Builder()
            .url(journeyPlanner)
            .header(CLIENT_HEADER, CLIENT_NAME)
            .post(payload.toString().toRequestBody(JSON))
            .build()
        return execute(request)
    }

    private fun execute(request: Request): String = http.newCall(request).executeOrAppError().use { response ->
        if (!response.isSuccessful) throw AppError.Http(SERVICE, response.code)
        response.body.string()
    }

    private fun <T> decode(serializer: KSerializer<T>, body: String, what: String): T =
        decodeOrBadData(SERVICE, what) { AppJson.standard.decodeFromString(serializer, body) }

    private fun failOnErrors(errors: List<GraphQlError>) {
        if (errors.isNotEmpty()) throw AppError.BadData(SERVICE, "GraphQL: " + errors.joinToString("; ") { it.message })
    }

    private fun millis(iso: String?): Long? = try {
        iso?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
    } catch (_: DateTimeParseException) {
        null
    }

    companion object {
        const val SERVICE = "Entur"

        /**
         * Entur transport modes per commute kind; coaches run bus routes too. Walking to and between stops stays allowed.
         * 每种通勤对应的 Entur 交通方式；长途大巴也跑公交线路。到站与换乘之间的步行照样允许。
         */
        internal fun modesOf(kind: CommuteKind): List<String> = when (kind) {
            CommuteKind.TRAIN -> listOf("rail")
            CommuteKind.BUS -> listOf("bus", "coach")
        }
        const val CLIENT_HEADER = "ET-Client-Name"
        const val CLIENT_NAME = "lonemoonspace-dayloom"
        private val JSON = "application/json".toMediaType()

        /**
         * Any line of the commute's modes, with transfers. Cancelled options are included and flagged: hiding them would
         * leave the user waiting for a train that will not come.
         * 该通勤交通方式下的任意线路，可换乘。被取消的方案也返回并标出：把它们藏起来，用户会在站台等一班不会来的车。
         */
        internal val TRIP_QUERY = """
            query Trip(${'$'}from: String!, ${'$'}to: String!, ${'$'}at: DateTime, ${'$'}count: Int, ${'$'}modes: [TransportModes]) {
              trip(from: { place: ${'$'}from }, to: { place: ${'$'}to }, dateTime: ${'$'}at, numTripPatterns: ${'$'}count,
                   includeRealtimeCancellations: true,
                   modes: { accessMode: foot, egressMode: foot, transportModes: ${'$'}modes }) {
                tripPatterns {
                  legs {
                    mode realtime aimedStartTime expectedStartTime aimedEndTime expectedEndTime
                    line { publicCode }
                    fromPlace { name }
                    toPlace { name }
                    fromEstimatedCall { cancellation destinationDisplay { frontText } }
                    toEstimatedCall { cancellation }
                  }
                }
              }
            }
        """.trimIndent()

        /**
         * Three hours and 200 departures: lines are filtered on the client, so the departure count must not be a tighter
         * limit than the time range, or a rare line would vanish behind busy ones.
         * 三小时、200 班：线路在客户端过滤，所以班次上限不能比时间范围更紧，否则少见的线路会被繁忙线路挤掉。
         */
        private fun boardBlock(index: Int) = """
            stop$index: stopPlace(id: ${'$'}stop$index) {
              estimatedCalls(startTime: ${'$'}start, timeRange: 10800, numberOfDepartures: 200, includeCancelledTrips: true) {
                realtime cancellation aimedDepartureTime expectedDepartureTime
                destinationDisplay { frontText }
                quay { publicCode }
                serviceJourney { line { publicCode transportMode } }
              }
            }
        """.trimIndent()
    }
}

@Serializable
internal data class GraphQlError(val message: String = "")

@Serializable
internal data class GeocoderResponse(val features: List<Feature> = emptyList()) {
    @Serializable
    data class Feature(val properties: Properties = Properties())

    @Serializable
    data class Properties(val id: String = "", val names: Names = Names(), val address: Address = Address())

    @Serializable
    data class Names(val default: String = "")

    @Serializable
    data class Address(val locality: String? = null)
}

@Serializable
internal data class TripResponse(val data: Data? = null, val errors: List<GraphQlError> = emptyList()) {
    @Serializable
    data class Data(val trip: Trip? = null)

    @Serializable
    data class Trip(val tripPatterns: List<Pattern> = emptyList())

    @Serializable
    data class Pattern(val legs: List<Leg> = emptyList())
}

@Serializable
internal data class Leg(
    val mode: String? = null,
    val realtime: Boolean = false,
    val aimedStartTime: String? = null,
    val expectedStartTime: String? = null,
    val aimedEndTime: String? = null,
    val expectedEndTime: String? = null,
    val line: Line? = null,
    val fromPlace: Place? = null,
    val toPlace: Place? = null,
    val fromEstimatedCall: CallState? = null,
    val toEstimatedCall: CallState? = null,
) {
    @Serializable
    data class Place(val name: String? = null)

    @Serializable
    data class CallState(val cancellation: Boolean = false, val destinationDisplay: DestinationDisplay? = null)
}

@Serializable
internal data class Line(val publicCode: String? = null, val transportMode: String? = null)

@Serializable
internal data class DestinationDisplay(val frontText: String? = null)

/** Values are nullable: Entur answers `null` for a stop id it no longer knows. / 值可空：Entur 对不再认识的站点 id 返回 `null`。 */
@Serializable
internal data class BoardsResponse(val data: Map<String, Stop?>? = null, val errors: List<GraphQlError> = emptyList()) {
    @Serializable
    data class Stop(val estimatedCalls: List<EstimatedCall> = emptyList())
}

@Serializable
internal data class EstimatedCall(
    val realtime: Boolean = false,
    val cancellation: Boolean = false,
    val aimedDepartureTime: String? = null,
    val expectedDepartureTime: String? = null,
    val destinationDisplay: DestinationDisplay? = null,
    val quay: Quay? = null,
    val serviceJourney: ServiceJourney? = null,
) {
    @Serializable
    data class Quay(val publicCode: String? = null)

    @Serializable
    data class ServiceJourney(val line: Line? = null)
}
