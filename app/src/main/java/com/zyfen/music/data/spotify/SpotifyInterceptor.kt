package com.zyfen.music.data.spotify

import okhttp3.Interceptor
import okhttp3.Response

/** Adds the anonymous session headers to api.spotify.com requests. */
class SpotifyInterceptor(private val session: SpotifySession) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var req = chain.request()
        if (req.url.host == "api.spotify.com") {
            val b = req.newBuilder()
            session.authHeader()?.let { b.header("Authorization", it) }
            session.clientTokenHeader()?.let { b.header("client-token", it) }
            b.header("App-Platform", "WebPlayer")
            b.header("Origin", "https://open.spotify.com")
            b.header("Referer", "https://open.spotify.com/")
            b.header("Accept", "application/json")
            b.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            )
            req = b.build()
        }
        return chain.proceed(req)
    }
}
