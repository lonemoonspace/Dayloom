package io.github.lonemoonspace.dayloom.feature.football

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.feature.football.data.FootballDataApi
import io.github.lonemoonspace.dayloom.feature.football.domain.MatchStatus
import io.github.lonemoonspace.dayloom.feature.football.ui.CrestLoader
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FootballDataApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api() = FootballDataApi(OkHttpClient(), server.url("/v4/"))

    private val matchesBody = """
        {"matches":[
          {"id":11,"utcDate":"2026-10-04T19:00:00Z","status":"FINISHED","matchday":7,
           "competition":{"name":"League A","code":"PL"},
           "homeTeam":{"id":1,"name":"Team A FC","shortName":"Team A","tla":"TMA","crest":"https://crests.example/1.svg"},
           "awayTeam":{"id":2,"name":"Team B FC","shortName":"Team B","tla":"TMB","crest":null},
           "score":{"winner":"AWAY_TEAM","duration":"PENALTY_SHOOTOUT","fullTime":{"home":5,"away":6},
             "regularTime":{"home":1,"away":1},"extraTime":{"home":0,"away":0},"penalties":{"home":4,"away":5}}},
          {"id":12,"utcDate":"2026-10-12T17:30:00Z","status":"TIMED","matchday":8,
           "competition":{"name":"League A","code":"PL"},
           "homeTeam":{"id":3,"name":"Team C FC"},"awayTeam":{"id":1,"name":"Team A FC"},
           "score":{"fullTime":{"home":null,"away":null}}},
          {"id":13,"utcDate":"2026-10-14T19:00:00Z","status":"LIVE","minute":90,"injuryTime":4,"homeTeam":{"id":1},"awayTeam":{"id":4},"score":{"fullTime":{"home":0,"away":0}}},
          {"id":14,"utcDate":null,"status":"SCHEDULED"},
          {"id":15,"utcDate":"2026-10-20T19:00:00Z","status":"SOMETHING_NEW","minute":"45+2","injuryTime":null}
        ]}
    """.trimIndent()

    @Test
    fun `team matches send the key in a header and parse score, shoot-out and status`() = runTest {
        server.enqueue(MockResponse().setBody(matchesBody))

        val matches = api().teamMatches("test-key", 1, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 11, 14))

        val request = server.takeRequest()
        assertEquals("test-key", request.getHeader("X-Auth-Token"))
        assertEquals("/v4/teams/1/matches?dateFrom=2026-09-12&dateTo=2026-11-14", request.path)
        assertEquals(listOf(11L, 12L, 13L, 15L), matches.map { it.id })
        val final = matches.first()
        assertEquals(MatchStatus.FINISHED, final.status)
        assertEquals(1 to 1, final.homeGoals to final.awayGoals)
        assertEquals(4 to 5, final.homePens to final.awayPens)
        assertEquals("Team A", final.home.label)
        assertEquals("", final.away.crest)
        assertNull("no shoot-out, no pens", matches[1].homePens)
        assertEquals(MatchStatus.IN_PLAY, matches[2].status)
        assertEquals(MatchStatus.UNKNOWN, matches[3].status)
        assertEquals(90 to 4, matches[2].minute to matches[2].injuryTime)
        // An odd minute is read, not fatal. / 异常的分钟值照样读出，不会让解码失败。
        assertEquals(45, matches[3].minute)
        assertNull(matches[3].injuryTime)
        assertNull(matches[0].minute)
    }

    @Test
    fun `standings keep only the overall tables`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"competition":{"name":"League A","code":"PL"},"standings":[
                  {"type":"TOTAL","group":null,"table":[{"position":1,"team":{"id":1,"shortName":"Team A"},"playedGames":7,"won":6,"draw":1,"lost":0,"points":19,"goalDifference":12}]},
                  {"type":"HOME","group":null,"table":[{"position":1,"team":{"id":1}}]},
                  {"type":"TOTAL","group":"GROUP_B","table":[]}
                ]}""",
            ),
        )
        val standings = api().standings("k", "PL")
        assertEquals(listOf("", "GROUP_B"), standings.groups.map { it.group })
        assertEquals(19, standings.groups.first().rows.single().points)
        assertEquals("League A", standings.competition)
        assertEquals("/v4/competitions/PL/standings", server.takeRequest().path)
    }

    @Test
    fun `errors keep the service's message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"The resource you are looking for is restricted"}"""))
        val error = runCatching { api().teams("k", "XYZ") }.exceptionOrNull() as AppError.Http
        assertEquals(403, error.code)
        assertTrue(error.detail.contains("restricted"))
        server.enqueue(MockResponse().setBody("<html>"))
        assertTrue(runCatching { api().teams("k", "PL") }.exceptionOrNull() is AppError.BadData)
    }

    @Test
    fun `svg crests are fetched as png, other urls are left alone`() {
        assertEquals("https://crests.example/86.png", CrestLoader.pngOf("https://crests.example/86.svg"))
        assertEquals("https://crests.example/86.png", CrestLoader.pngOf("https://crests.example/86.png"))
        assertNull(CrestLoader.pngOf(""))
        assertNull(CrestLoader.pngOf("file:///etc/passwd"))
    }
}
