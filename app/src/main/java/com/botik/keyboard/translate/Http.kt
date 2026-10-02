package com.botik.keyboard.translate

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** One shared client so every HTTP translator reuses warm connections. */
internal object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    val JSON = "application/json; charset=utf-8".toMediaType()
}

class HttpStatusException(val code: Int) : Exception("HTTP $code")

/** Cancellation handle for one HTTP translation. */
class HttpCall {
    @Volatile var cancelled = false
        private set
    @Volatile private var http: okhttp3.Call? = null

    fun cancel() {
        cancelled = true
        http?.cancel()
    }

    /** Runs [request] and returns the body; throws [HttpStatusException] on a non-2xx status. */
    fun execute(request: Request): String {
        if (cancelled) return ""
        val call = Http.client.newCall(request)
        http = call
        call.execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            return body
        }
    }
}
