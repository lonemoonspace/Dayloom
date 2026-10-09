package io.github.lonemoonspace.dayloom.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Removes credential headers when a redirect leaves the original origin (scheme + host + port).
 * OkHttp only drops `Authorization` on a cross-host redirect; custom key headers would otherwise follow a misconfigured
 * reverse proxy to a third party. A module that sends a credential in a new header must add it to [CREDENTIAL_HEADERS].
 * 重定向离开原始源（scheme + 主机 + 端口）时去掉凭据头。
 * OkHttp 跨主机重定向时只去掉 `Authorization`；自定义的 Key 头会跟着配错的反代跑到第三方。
 * 模块若用新的请求头发送凭据，必须把它加进 [CREDENTIAL_HEADERS]。
 */
object CredentialRedirectGuard {

    val CREDENTIAL_HEADERS = listOf(
        "Authorization",
        "Proxy-Authorization",
        "X-Auth-Token",
        "X-Goog-Api-Key",
        "X-Api-Key",
        "Api-Key",
    )

    private class Origin(val value: String)

    private fun origin(url: HttpUrl) = "${url.scheme}://${url.host}:${url.port}"

    /**
     * The application interceptor tags the original request with its origin (redirect follow-ups inherit tags);
     * the network interceptor compares every hop against it.
     * 应用层拦截器给原始请求记下来源（重定向生成的请求会继承 tag），网络层拦截器逐跳比对。
     */
    fun OkHttpClient.Builder.guardCredentialRedirects(): OkHttpClient.Builder = this
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            chain.proceed(request.newBuilder().tag(Origin::class.java, Origin(origin(request.url))).build())
        })
        .addNetworkInterceptor(Interceptor { chain ->
            val request = chain.request()
            val first = request.tag(Origin::class.java)?.value
            if (first == null || first == origin(request.url)) {
                chain.proceed(request)
            } else {
                val stripped = request.newBuilder()
                CREDENTIAL_HEADERS.forEach { stripped.removeHeader(it) }
                chain.proceed(stripped.build())
            }
        })
}
