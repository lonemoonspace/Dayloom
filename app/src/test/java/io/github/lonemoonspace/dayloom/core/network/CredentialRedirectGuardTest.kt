package io.github.lonemoonspace.dayloom.core.network

import io.github.lonemoonspace.dayloom.core.network.CredentialRedirectGuard.guardCredentialRedirects
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CredentialRedirectGuardTest {

    private val origin = MockWebServer()
    private val other = MockWebServer()
    private val client = OkHttpClient.Builder().guardCredentialRedirects().build()

    @Before
    fun setUp() {
        origin.start()
        other.start()
    }

    @After
    fun tearDown() {
        origin.shutdown()
        other.shutdown()
    }

    private fun call(path: String) = client.newCall(
        Request.Builder()
            .url(origin.url(path))
            .header("X-Auth-Token", "secret")
            .header("X-Goog-Api-Key", "key")
            .header("X-Api-Key", "other-key")
            .header("Accept", "application/json")
            .build(),
    ).execute().use { it.code }

    @Test
    fun `credential headers are dropped when redirected to another origin`() {
        origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/landing")))
        other.enqueue(MockResponse().setResponseCode(200))

        assertEquals(200, call("/v1/entries"))

        assertEquals("secret", origin.takeRequest().getHeader("X-Auth-Token"))
        val redirected = other.takeRequest()
        CredentialRedirectGuard.CREDENTIAL_HEADERS.forEach { assertNull(it, redirected.getHeader(it)) }
        assertEquals("non-credential headers are kept", "application/json", redirected.getHeader("Accept"))
    }

    @Test
    fun `credential headers stay on a same-origin redirect`() {
        origin.enqueue(MockResponse().setResponseCode(301).setHeader("Location", origin.url("/v1/entries/")))
        origin.enqueue(MockResponse().setResponseCode(200))

        assertEquals(200, call("/v1/entries"))

        origin.takeRequest()
        assertEquals("secret", origin.takeRequest().getHeader("X-Auth-Token"))
    }

    @Test
    fun `the shared client sends an identifying User-Agent`() {
        origin.enqueue(MockResponse().setResponseCode(200))
        // Android's Log is not available on the JVM, so logging stays off here. / JVM 上没有 Android 的 Log，这里关闭日志。
        val shared = SharedHttpClient.build(versionName = "1.2.3", debugLogging = false)
        shared.newCall(Request.Builder().url(origin.url("/")).build()).execute().close()

        assertEquals("Dayloom/1.2.3 (+${SharedHttpClient.REPO_URL})", origin.takeRequest().getHeader("User-Agent"))
    }
}
