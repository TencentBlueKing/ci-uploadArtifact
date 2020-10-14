package com.tencent.bk.devops.atom.task

import com.google.common.collect.Maps
import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.atom.exception.AtomException
import com.tencent.bk.devops.atom.pojo.AtomBaseParam
import net.dongliu.apk.parser.ApkFile
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLEncoder

class ArchiveApi : BaseApi() {
    private val atomHttpClient = AtomHttpClient()

    fun uploadBkRepoCustomFile(file: File, destPath: String, atomBaseParam: AtomBaseParam) {
        val bkrepoPath = (destPath.removeSuffix("/") + "/" + file.name).removePrefix("/").removePrefix("./")
        val request = atomHttpClient.buildAtomPut(
            "/bkrepo/api/build/generic/${atomBaseParam.projectName}/custom/$bkrepoPath",
            RequestBody.create(MediaType.parse("application/octet-stream"), file),
            getBkRepoUploadHeader(file, atomBaseParam)
        )
        uploadFile(request)
    }

    fun uploadBkRepoPipelineFile(file: File, atomBaseParam: AtomBaseParam) {
        val request = buildPut(
            "/bkrepo/api/build/generic/${atomBaseParam.projectName}/pipeline/${atomBaseParam.pipelineId}/${atomBaseParam.pipelineBuildId}/${file.name}",
            RequestBody.create(MediaType.parse("application/octet-stream"), file),
            getBkRepoUploadHeader(file, atomBaseParam)
        )
        uploadFile(request)
    }

    private fun uploadFile(request: Request, retry: Boolean = false) {
        try {
            val response = atomHttpClient.doRequest(request)
            if (!response.isSuccessful) {
                logger.error("upload file failed, code: ${response.code()}, responseContent: ${response.body()!!.string()}")
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

    private fun getBkRepoUploadHeader(file: File, atomParam: AtomBaseParam): HashMap<String, String> {
        val header = Maps.newHashMap<String, String>()
        header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_PROJECT_ID] = atomParam.projectName
        header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_PIPELINE_ID] = atomParam.pipelineId
        header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_BUILD_ID] = atomParam.pipelineBuildId
        header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_USER_ID] = atomParam.pipelineStartUserName
        header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_BUILD_NO] = atomParam.pipelineBuildNum
        header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_SOURCE] = "pipeline"
        header[BKREPO_UID] = atomParam.pipelineStartUserName
        header[BKREPO_OVERRIDE] = "true"
        setBkRepoAppProps(file, header)
        return header
    }

    private fun setBkRepoAppProps(file: File, header: HashMap<String, String>) {
        try {
            if (file.name.endsWith(".ipa")) {
                val map = IosUtils.getIpaInfoMap(file)
                header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_APP_VERSION] = urLEncode(map["bundleVersion"])
                header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_APP_BUNDLE_IDENTIFIER] = urLEncode(map["bundleIdentifier"])
                header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_APP_APP_TITLE] = urLEncode(map["appTitle"])
                header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_APP_IMAGE] = urLEncode(map["image"])
                header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_APP_FULL_IMAGE] = urLEncode(map["fullImage"])
            }
            if (file.name.endsWith(".apk")) {
                val apkFile = ApkFile(file)
                val meta = apkFile.apkMeta
                header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_APP_VERSION] = urLEncode(meta.versionName)
                header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_APP_APP_TITLE] = urLEncode(meta.name)
                header[BKREPO_METADATA_PREFIX + ARCHIVE_PROPS_APP_BUNDLE_IDENTIFIER] = urLEncode(meta.packageName)
            }
        } catch (e: Exception) {
            logger.warn("get metadata from file(${file.absolutePath}) failed", e)
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
        private const val ARCHIVE_PROPS_USER_ID = "userId"
        private const val ARCHIVE_PROPS_APP_VERSION = "appVersion"
        private const val ARCHIVE_PROPS_APP_BUNDLE_IDENTIFIER = "bundleIdentifier"
        private const val ARCHIVE_PROPS_APP_APP_TITLE = "appTitle"
        private const val ARCHIVE_PROPS_APP_IMAGE = "image"
        private const val ARCHIVE_PROPS_APP_FULL_IMAGE = "fullImage"
        private const val ARCHIVE_PROPS_SOURCE = "source"

        private const val BKREPO_METADATA_PREFIX = "X-BKREPO-META-"
        private const val BKREPO_UID = "X-BKREPO-UID"
        private const val BKREPO_OVERRIDE = "X-BKREPO-OVERWRITE"
    }
}