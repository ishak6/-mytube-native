package com.mytube.android

import android.net.Uri
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.mlm.adblock.AdblockEngine
import org.mlm.adblock.RequestTypeMapper
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Brave adblock-rust adapter for mytube-owned HTTP traffic.
 * It does not intercept YouTube's internal player pipeline.
 */
object NativeAdblock {
    private const val EASYLIST = "https://easylist.to/easylist/easylist.txt"
    private const val EASYPRIVACY = "https://easylist.to/easylist/easyprivacy.txt"

    private val executor = Executors.newSingleThreadExecutor()
    private val enabled = AtomicBoolean(true)
    private val blockedCount = AtomicInteger(0)
    @Volatile private var engine: AdblockEngine? = null
    @Volatile private var initialized = false
    @Volatile private var currentStatus = "Not initialized"

    fun setEnabled(value: Boolean) {
        enabled.set(value)
        currentStatus = when {
            !value -> "Ready • protection paused"
            initialized -> "Ready • EasyList + EasyPrivacy loaded • ${blockedCount.get()} blocked"
            else -> "Protection enabled • loading filter lists"
        }
    }

    fun status(): String = currentStatus

    fun initialize(onStatus: (String) -> Unit = {}) {
        if (initialized) {
            onStatus(currentStatus)
            return
        }
        executor.execute {
            try {
                val rules = downloadRules()
                val instance = AdblockEngine.create()
                if (!instance.loadFilterList(rules)) {
                    instance.close()
                    throw IOException("Brave engine rejected the downloaded filter rules")
                }
                engine?.close()
                engine = instance
                initialized = true
                currentStatus = if (enabled.get()) {
                    "Ready • EasyList + EasyPrivacy loaded • ${blockedCount.get()} blocked"
                } else "Ready • protection paused"
            } catch (error: Throwable) {
                currentStatus = "Not ready • ${error.message ?: "filter download failed"}"
            }
            onStatus(currentStatus)
        }
    }

    fun refresh(onStatus: (String) -> Unit = {}) {
        currentStatus = "Updating EasyList and EasyPrivacy…"
        onStatus(currentStatus)
        executor.execute {
            try {
                val rules = downloadRules()
                val instance = AdblockEngine.create()
                if (!instance.loadFilterList(rules)) {
                    instance.close()
                    throw IOException("Could not load filter rules")
                }
                engine?.close()
                engine = instance
                initialized = true
                currentStatus = "Updated • EasyList + EasyPrivacy loaded"
            } catch (error: Throwable) {
                currentStatus = "Update failed • ${error.message ?: "network error"}"
            }
            onStatus(currentStatus)
        }
    }

    /** Use this client for mytube-owned API/feed/image requests that should be filtered. */
    fun newHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BraveFilterInterceptor())
        .build()

    fun blockedRequests(): Int = blockedCount.get()

    private fun downloadRules(): String {
        val client = OkHttpClient.Builder().build()
        return fetchText(client, EASYLIST) + "\n" + fetchText(client, EASYPRIVACY)
    }

    private fun fetchText(client: OkHttpClient, url: String): String {
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "mytube Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Filter list HTTP ${response.code}")
            return response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw IOException("Empty filter list")
        }
    }

    private class BraveFilterInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (!enabled.get()) return chain.proceed(request)

            val activeEngine = engine ?: return chain.proceed(request)
            val requestUrl = request.url.toString()
            val sourceUrl = request.header("Referer")
                ?: request.header("Origin")
                ?: request.url.newBuilder().encodedPath("/").query(null).build().toString()
            val requestType = RequestTypeMapper.from(
                Uri.parse(requestUrl),
                request.header("Accept")
            )
            val decision = activeEngine.checkNetworkRequest(requestUrl, sourceUrl, requestType)

            if (decision.matched && !decision.exception) {
                blockedCount.incrementAndGet()
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(204)
                    .message("Blocked by Brave adblock-rust")
                    .body(ByteArray(0).toResponseBody(null))
                    .build()
            }

            val rewritten = decision.rewrittenUrl
            if (!rewritten.isNullOrBlank() && rewritten != requestUrl) {
                return chain.proceed(request.newBuilder().url(rewritten).build())
            }
            return chain.proceed(request)
        }
    }
}
