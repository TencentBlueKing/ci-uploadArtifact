package com.tencent.bk.devops.atom.task

import com.tencent.bk.devops.atom.api.SdkEnv
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class AtomHttpClient {
    private val logger = LoggerFactory.getLogger(AtomHttpClient::class.java)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10L, TimeUnit.SECONDS)
        .readTimeout(60L, TimeUnit.SECONDS)
        .writeTimeout(60L, TimeUnit.SECONDS)
        .build()

    private val longHttpClient = OkHttpClient.Builder()
        .connectTimeout(10L, TimeUnit.SECONDS)
        .readTimeout(300L, TimeUnit.SECONDS)
        .writeTimeout(300L, TimeUnit.SECONDS)
        .build()

    fun doRequest(request: Request): Response {
        return okHttpClient.newCall(request).execute()
    }

    fun doRequestWithContent(request: Request): String {
        val response = okHttpClient.newCall(request).execute()
        val responseContent = response.body()!!.string()
        if (!response.isSuccessful) {
            logger.error("http request failed, code: ${response.code()}, responseContent: $responseContent")
        }
        return responseContent
    }

    fun doRequestWithCodeAndContent(request: Request): Pair<Int, String> {
        val response = okHttpClient.newCall(request).execute()
        return Pair(response.code(), response.body()!!.string())
    }

    fun doLongRequest(request: Request): Response {
        return longHttpClient.newCall(request).execute()
    }

    fun buildAtomGet(path: String): Request {
        return buildAtomGet(path, mutableMapOf())
    }

    fun buildAtomGet(path: String, headers: MutableMap<String, String>): Request {
        val url = buildAtomUrl(path)
        return Request.Builder().url(url).headers(Headers.of(getAllAtomHeaders(headers))).get().build()
    }

    fun buildAtomPost(path: String): Request {
        return buildAtomPost(path, mutableMapOf())
    }

    fun buildAtomPost(path: String, headers: MutableMap<String, String> = mutableMapOf()): Request {
        val requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "")
        return buildAtomPost(path, requestBody, headers)
    }

    fun buildAtomPost(path: String, requestBody: RequestBody): Request? {
        val url = buildAtomUrl(path)
        return Request.Builder().url(url).headers(Headers.of(getAllAtomHeaders(mutableMapOf()))).post(requestBody).build()
    }

    fun buildAtomPost(path: String, requestBody: RequestBody, headers: MutableMap<String, String>): Request {
        val url = buildAtomUrl(path)
        return Request.Builder().url(url).headers(Headers.of(getAllAtomHeaders(headers))).post(requestBody).build()
    }

    fun buildAtomPut(path: String): Request? {
        return buildAtomPut(path, mutableMapOf())
    }

    fun buildAtomPut(path: String, headers: MutableMap<String, String>): Request {
        val requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "")
        return buildAtomPut(path, requestBody, headers)
    }

    fun buildAtomPut(path: String, requestBody: RequestBody): Request {
        val url = buildAtomUrl(path)
        return Request.Builder().url(url).headers(Headers.of(getAllAtomHeaders(mutableMapOf()))).put(requestBody).build()
    }

    fun buildAtomPut(path: String, requestBody: RequestBody, headers: MutableMap<String, String>): Request {
        val url = buildAtomUrl(path)
        return Request.Builder().url(url).headers(Headers.of(getAllAtomHeaders(headers))).put(requestBody).build()
    }

    fun buildAtomDelete(path: String): Request {
        val url = buildAtomUrl(path)
        return Request.Builder().url(url).headers(Headers.of(getAllAtomHeaders(mutableMapOf()))).delete().build()
    }

    fun buildAtomDelete(path: String, headers: MutableMap<String, String>): Request {
        val url = buildAtomUrl(path)
        return Request.Builder().url(url).headers(Headers.of(getAllAtomHeaders(headers))).delete().build()
    }

    fun buildAtomDelete(path: String, requestBody: RequestBody): Request {
        val url = buildAtomUrl(path)
        return Request.Builder().url(url).headers(Headers.of(getAllAtomHeaders(mutableMapOf()))).delete(requestBody).build()
    }

    fun buildAtomDelete(path: String, requestBody: RequestBody, headers: MutableMap<String, String>): Request {
        val url = buildAtomUrl(path)
        return Request.Builder().url(url).headers(Headers.of(getAllAtomHeaders(headers))).delete(requestBody).build()
    }

    private fun buildAtomUrl(path: String): String {
        return SdkEnv.genUrl(path)
    }

    private fun getAllAtomHeaders(headers: MutableMap<String, String>): Map<String, String> {
        // headers.putAll(SdkEnv.getSdkHeader())
        val method = SdkEnv::class.java.getDeclaredMethod("getSdkHeader")
        method.isAccessible = true
        val envObj = method.invoke(SdkEnv::class.java)
        headers.putAll(envObj as Map<String, String>)
        return headers
    }
}