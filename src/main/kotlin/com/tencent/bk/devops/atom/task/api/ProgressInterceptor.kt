package com.tencent.bk.devops.atom.task.api

import com.tencent.bk.devops.atom.task.pojo.FileInfo
import com.tencent.bk.devops.atom.task.pojo.ProgressRequestBody
import com.tencent.bkrepo.common.api.util.HumanReadable
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class ProgressInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fileInfo = request.tag(FileInfo::class.java)
        if (fileInfo != null) {
            val startTime = System.currentTimeMillis()
            val response = chain.proceed(wrapRequest(request, fileInfo))
            if (response.isSuccessful) {
                logger.info("file [${fileInfo.name}] upload time: " +
                    HumanReadable.time(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
                )
            }
            return response
        }

        return chain.proceed(request)
    }

    private fun wrapRequest(request: Request, fileInfo: FileInfo): Request {
        return request.newBuilder()
            .method(request.method, ProgressRequestBody(request.body!!, fileInfo))
            .build()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProgressInterceptor::class.java)
    }
}