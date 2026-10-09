package io.github.lonemoonspace.dayloom.core.network

import android.util.Log
import io.github.lonemoonspace.dayloom.core.network.CredentialRedirectGuard.guardCredentialRedirects
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * One OkHttpClient for the whole app, so modules share a connection pool.
 * 全应用共用一个 OkHttpClient，各模块共享连接池。
 */
object SharedHttpClient {

    const val REPO_URL = "https://github.com/lonemoonspace/dayloom"

    /**
     * MET Norway requires an identifying User-Agent with contact information; every service gets the same one.
     * MET Norway 要求 User-Agent 带标识与联系方式；所有服务统一使用同一个。
     */
    fun userAgent(versionName: String): String = "Dayloom/$versionName (+$REPO_URL)"

    fun build(versionName: String, debugLogging: Boolean): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        // A hung connection must not block a refresh forever. / 僵死连接不能让刷新永远卡住。
        .callTimeout(90, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val withAgent = if (request.header("User-Agent") == null) {
                request.newBuilder().header("User-Agent", userAgent(versionName)).build()
            } else {
                request
            }
            if (debugLogging) {
                // Only method, host and path: queries can contain addresses and headers can contain keys.
                // 只记方法、主机与路径：query 里可能有地址，请求头里可能有 Key。
                Log.d("OkHttp", "--> ${request.method} ${request.url.scheme}://${request.url.host}${request.url.encodedPath}")
            }
            val response = chain.proceed(withAgent)
            if (debugLogging) Log.d("OkHttp", "<-- ${response.code} ${request.url.host}${request.url.encodedPath}")
            response
        }
        .guardCredentialRedirects()
        .build()
}
