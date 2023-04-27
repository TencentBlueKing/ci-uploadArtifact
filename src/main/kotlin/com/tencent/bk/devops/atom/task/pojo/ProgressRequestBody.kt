/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bk.devops.atom.task.pojo

import com.tencent.bkrepo.common.api.util.HumanReadable
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

internal class ProgressRequestBody(
    private val delegate: RequestBody,
    private val fileInfo: FileInfo
) : RequestBody() {

    private var uploaded: Long = 0L
    private var lastLogTime: LocalDateTime = LocalDateTime.now()
    private var startTimestamp = System.currentTimeMillis()

    init {
        printProgress()
    }

    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val countingSink = CountingSink(sink)
        val bufferedSink: BufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }

    inner class CountingSink(delegate: Sink) : ForwardingSink(delegate) {
        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            uploaded += byteCount
            if (LocalDateTime.now().isAfter(lastLogTime.plusSeconds(5))) {
                lastLogTime = LocalDateTime.now()
                printProgress()
            }
            if (uploaded == fileInfo.size) {
                printProgress()
                logger.info("file [${fileInfo.name}] transfer time: " +
                    HumanReadable.time(System.currentTimeMillis()-startTimestamp, TimeUnit.MILLISECONDS)
                )
            }
        }
    }

    private fun printProgress() {
        logger.info("${fileInfo.name} >>> ${HumanReadable.size(uploaded)}/${HumanReadable.size(fileInfo.size)}")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProgressRequestBody::class.java)
    }
}
