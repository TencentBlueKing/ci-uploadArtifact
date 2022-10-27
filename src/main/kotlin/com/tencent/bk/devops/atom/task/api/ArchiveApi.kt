package com.tencent.bk.devops.atom.task.api

import com.google.common.collect.Maps
import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.atom.exception.AtomException
import com.tencent.bk.devops.atom.task.UploadArtifactParam
import com.tencent.bk.devops.atom.task.util.IosUtils
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.hash.md5
import net.dongliu.apk.parser.ApkFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLEncoder
import java.util.Base64
import java.util.Locale

class ArchiveApi : BaseApi() {
    private val atomHttpClient = AtomHttpClient()

    fun uploadBkRepoCustomFile(file: File, destPath: String, atomBaseParam: UploadArtifactParam) {
        val bkrepoPath = (destPath.removeSuffix("/") + "/" + file.name).removePrefix("/").removePrefix("./")
        val request = atomHttpClient.buildAtomPut(
            "/bkrepo/api/build/generic/${atomBaseParam.projectName}/custom/$bkrepoPath",
            file.asRequestBody("application/octet-stream".toMediaType()),
            getUploadHeader(file, atomBaseParam)
        )
        uploadFile(request)
    }

    fun uploadBkRepoPipelineFile(file: File, atomBaseParam: UploadArtifactParam) {
        val request = buildPut(
            "/bkrepo/api/build/generic/${atomBaseParam.projectName}/pipeline/${atomBaseParam.pipelineId}/${atomBaseParam.pipelineBuildId}/${file.name}",
            file.asRequestBody("application/octet-stream".toMediaType()),
            getUploadHeader(file, atomBaseParam)
        )
        uploadFile(request)
    }

    private fun uploadFile(request: Request, retry: Boolean = false) {
        try {
            val response = atomHttpClient.doRequest(request)
            if (!response.isSuccessful) {
                logger.error("upload file failed, code: ${response.code}, responseContent: ${response.body!!.string()}")
                throw AtomException("upload file failed")
            }
        } catch (e: Exception) {
            logger.error("upload file error, cause: ${e.message}")
            if (!retry) {
                logger.info("retry after 3 seconds")
                Thread.sleep(3 * 1000L)
                uploadFile(request, true)
                return
            }
            throw e
        }
    }

    private fun getUploadHeader(
        file: File,
        atomParam: UploadArtifactParam
    ): HashMap<String, String> {
        val md5Check = atomParam.enableMD5Checksum
        val header = Maps.newHashMap<String, String>()
        header[BKREPO_UID] = atomParam.pipelineStartUserId
        header[BKREPO_OVERRIDE] = "true"
        if (md5Check) {
            val md5 = file.md5()
            logger.info("file ${file.absolutePath} md5: $md5")
            header[BKREPO_MD5] = md5
        }
        val metadata = getMetadata(atomParam.metadata)
        metadata[ARCHIVE_PROPS_PROJECT_ID] = atomParam.projectName
        metadata[ARCHIVE_PROPS_PIPELINE_ID] = atomParam.pipelineId
        metadata[ARCHIVE_PROPS_BUILD_ID] = atomParam.pipelineBuildId
        metadata[ARCHIVE_PROPS_USER_ID] = atomParam.pipelineStartUserId
        metadata[ARCHIVE_PROPS_BUILD_NO] = atomParam.pipelineBuildNum
        metadata[ARCHIVE_PROPS_TASK_ID] = atomParam.pipelineTaskId
        metadata[ARCHIVE_PROPS_SOURCE] = "pipeline"
        metadata.putAll(getAppMetadata(file))
        header[BKREPO_METADATA] = Base64.getEncoder().encodeToString(buildMetadataHeader(metadata).toByteArray())
        return header
    }

    private fun getMetadata(strValue: String): MutableMap<String, String> {
        val map = if (strValue.isNullOrBlank()) {
            mutableMapOf()
        } else {
            try {
                val map = mutableMapOf<String, String>()
                strValue.readJsonString<List<Map<String, String>>>()
                    .filterNot { it["key"].isNullOrBlank() }
                    .filterNot { it["value"].isNullOrBlank() }.map {
                        map[it["key"]!!] = it["value"]!!
                    }
                map
            } catch (e: Exception) {
                logger.error("fail to deserialize input: $strValue")
                mutableMapOf()
            }
        }
        logger.debug("get param metadata: $map")
        return map
    }

    private fun getAppMetadata(file: File): Map<String, String> {
        try {
            return when {
                file.name.endsWith(".ipa") -> {
                    val map = IosUtils.getIpaInfoMap(file)
                    val result = mutableMapOf(
                        ARCHIVE_PROPS_APP_VERSION to (map["bundleVersion"] ?: ""),
                        ARCHIVE_PROPS_APP_BUNDLE_IDENTIFIER to (map["bundleIdentifier"] ?: ""),
                        ARCHIVE_PROPS_APP_APP_TITLE to (map["appTitle"] ?: ""),
                        ARCHIVE_PROPS_APP_IMAGE to (map["image"] ?: ""),
                        ARCHIVE_PROPS_APP_FULL_IMAGE to (map["fullImage"] ?: ""),
                        ARCHIVE_PROPS_APP_SCHEME to (map["scheme"] ?: ""),
                        ARCHIVA_PROPS_APP_NAME to (map["appName"] ?: "")
                    )
                    result
                }
                file.name.endsWith(".apk") -> {
                    val apkFile = ApkFile(file)
                    apkFile.preferredLocale = Locale.SIMPLIFIED_CHINESE
                    val meta = apkFile.apkMeta
                    val result = mutableMapOf(
                        ARCHIVE_PROPS_APP_VERSION to meta.versionName,
                        ARCHIVE_PROPS_APP_APP_TITLE to meta.name,
                        ARCHIVE_PROPS_APP_BUNDLE_IDENTIFIER to meta.packageName,
                        ARCHIVA_PROPS_APP_NAME to (meta.label ?: "")
                    )
                    result
                }
                else -> {
                    mapOf()
                }
            }
        } catch (e: Exception) {
            logger.warn("get metadata from file(${file.absolutePath}) failed", e)
            return mapOf()
        }
    }

    private fun buildMetadataHeader(metadata: Map<String, String>): String {
        return StringUtils.join(
            metadata.map {
                "${urlEncode(it.key)}=${urlEncode(it.value)}"
            },
            "&"
        )
    }

    private fun urlEncode(str: String?): String {
        return if (str.isNullOrBlank()) {
            ""
        } else {
            URLEncoder.encode(str, Charsets.UTF_8.toString()).replace("+", "%20")
        }
    }

    private fun urLEncode(str: String?): String {
        return if (str.isNullOrBlank()) {
            ""
        } else {
            URLEncoder.encode(str, "UTF-8")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveApi::class.java)

        private const val ARCHIVE_PROPS_PROJECT_ID = "projectId"
        private const val ARCHIVE_PROPS_PIPELINE_ID = "pipelineId"
        private const val ARCHIVE_PROPS_BUILD_ID = "buildId"
        private const val ARCHIVE_PROPS_BUILD_NO = "buildNo"
        private const val ARCHIVE_PROPS_TASK_ID = "taskId"
        private const val ARCHIVE_PROPS_USER_ID = "userId"
        private const val ARCHIVE_PROPS_APP_VERSION = "appVersion"
        private const val ARCHIVE_PROPS_APP_BUNDLE_IDENTIFIER = "bundleIdentifier"
        private const val ARCHIVE_PROPS_APP_APP_TITLE = "appTitle"
        private const val ARCHIVE_PROPS_APP_IMAGE = "image"
        private const val ARCHIVE_PROPS_APP_FULL_IMAGE = "fullImage"
        private const val ARCHIVE_PROPS_APP_SCHEME = "appScheme"
        private const val ARCHIVA_PROPS_APP_NAME = "appName"
        private const val ARCHIVE_PROPS_SOURCE = "source"

        private const val BKREPO_METADATA = "X-BKREPO-META"
        private const val BKREPO_UID = "X-BKREPO-UID"
        private const val BKREPO_OVERRIDE = "X-BKREPO-OVERWRITE"
        private const val BKREPO_MD5 = "X-BKREPO-MD5"
    }
}