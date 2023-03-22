package com.tencent.bk.devops.atom.task.api

import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.atom.api.SdkEnv
import com.tencent.bk.devops.atom.exception.AtomException
import com.tencent.bk.devops.atom.task.UploadArtifactParam
import com.tencent.bk.devops.atom.task.constant.REPO_PIPELINE
import com.tencent.bk.devops.atom.task.pojo.MetadataModel
import com.tencent.bk.devops.atom.task.pojo.UserMetadataSaveRequest
import com.tencent.bk.devops.atom.task.util.IosUtils
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.hash.md5
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.generic.pojo.TemporaryAccessToken
import com.tencent.bkrepo.repository.pojo.project.UserProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.repository.pojo.token.TokenType
import net.dongliu.apk.parser.ApkFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

class ArchiveApi : BaseApi() {
    private val atomHttpClient = AtomHttpClient()
    private val fileGateway: String? by lazy { SdkEnv.getFileGateway() }
    private val tokenRequest: Boolean by lazy { !fileGateway.isNullOrBlank() }
    private var createFlag: Boolean = false
    private var token: String? = null

    fun uploadFile(file: File, fullPath: String, atomParam: UploadArtifactParam) {
        if (tokenRequest) {
            token = createToken(atomParam)
            createProjectOrRepoIfNotExist(atomParam.pipelineStartUserId, atomParam.projectName, atomParam.repoName)
            uploadFileByToken(file, fullPath, atomParam)
        } else {
            val request = buildPut(
                "/bkrepo/api/build/generic/${atomParam.projectName}/${atomParam.repoName}$/${urlEncode(fullPath)}",
                file.asRequestBody("application/octet-stream".toMediaType()),
                getUploadHeader(file, atomParam)
            )
            doRequest(request)
        }
    }

    private fun uploadFileByToken(file: File, fullPath: String, atomParam: UploadArtifactParam) {
        val request = buildPut(
            "$fileGateway/generic/${atomParam.projectName}/${atomParam.repoName}/${urlEncode(fullPath)}?token=$token",
            file.asRequestBody("application/octet-stream".toMediaType()),
            getUploadHeader(file, atomParam)
        )
        doRequest(request)
    }

    private fun createToken(atomParam: UploadArtifactParam): String {
        if (!token.isNullOrBlank()) {
            return token!!
        }
        val tokenCreateRequest = TemporaryTokenCreateRequest(
            projectId = atomParam.projectName,
            repoName = atomParam.repoName,
            fullPathSet = setOf(StringPool.ROOT),
            authorizedUserSet = setOf(atomParam.pipelineStartUserId),
            authorizedIpSet = emptySet(),
            expireSeconds = TimeUnit.DAYS.toSeconds(1),
            permits = null,
            type = TokenType.ALL
        )
        val request = buildPost(
            "/bkrepo/api/build/generic/temporary/token/create",
            tokenCreateRequest.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType()),
            buildBaseHeaders(atomParam.pipelineStartUserId)
        )
        val (status, response) = doRequest(request)
        if (status == 200) {
            return response.readJsonString<Response<List<TemporaryAccessToken>>>().data!!.first().token
        } else {
            throw AtomException(response)
        }
    }

    fun setPipelineMetadata(
        userId: String,
        projectId: String,
        pipelineId: String,
        pipelineName: String,
        buildId: String,
        buildNo: String
    ) {
        try {
            saveMetadata(
                userId = userId,
                projectId = projectId,
                repoName = REPO_PIPELINE,
                fullPath = "/$pipelineId",
                metadata = mapOf(METADATA_DISPLAY_NAME to pipelineName)
            )
            saveMetadata(
                userId = userId,
                projectId = projectId,
                repoName = REPO_PIPELINE,
                fullPath = "/$pipelineId/$buildId",
                metadata = mapOf(METADATA_DISPLAY_NAME to buildNo)
            )
        } catch (e: Exception) {
            logger.warn("set pipeline metadata failed: ", e)
        }
    }

    private fun saveMetadata(
        userId: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        metadata: Map<String, String>
    ) {
        val url = "/bkrepo/api/build/repository/api/metadata/$projectId/$repoName/$fullPath"
        val metadataSaveRequest = UserMetadataSaveRequest(metadata.map { MetadataModel(it.key, it.value) })
        val header = mutableMapOf(BKREPO_UID to userId)
        val request = buildPost(
            url,
            metadataSaveRequest.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType()),
            header
        )
        request(request, "save node[$fullPath] metadata failed")
    }


    private fun createProjectOrRepoIfNotExist(userId: String, projectId: String, repoName: String) {
        if (createFlag) {
            return
        }
        createProject(userId, projectId)
        createRepo(userId, projectId, repoName)
        createFlag = true
    }

    private fun createProject(userId: String, projectId: String) {
        val projectCreateRequest = UserProjectCreateRequest(projectId, projectId, "")
        val request = buildPost(
            "$fileGateway/repository/api/project/create",
            projectCreateRequest.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType()),
            buildBaseHeaders(userId)
        )
        val (status, response) = doRequest(request)
        if (status == 200) {
            return
        }
        val code = response.readJsonString<Response<Void>>().code
        if (code == ERROR_PROJECT_EXISTED) {
            return
        } else {
            throw AtomException(response)
        }
    }

    private fun createRepo(userId: String, projectId: String, repoName: String) {
        val repoCreateRequest = RepoCreateRequest(
            projectId = projectId,
            name = repoName,
            type = RepositoryType.GENERIC,
            category = RepositoryCategory.LOCAL,
            public = false,
            operator = userId
        )
        val request = buildPost(
            "$fileGateway/repository/api/repo/create",
            repoCreateRequest.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType()),
            buildBaseHeaders(userId)
        )
        val (status, response) = doRequest(request)
        if (status == 200) {
            return
        }
        val code = response.readJsonString<Response<Void>>().code
        if (code == ERROR_REPO_EXISTED) {
            return
        } else {
            throw AtomException(response)
        }
    }

    private fun doRequest(request: Request, retry: Int = 3): Pair<Int,String> {
        try {
            logger.info("request url: ${request.url}, header: ${request.headers}")
            val response = atomHttpClient.doRequest(request)
            val responseContent = response.body!!.string()
            if (response.isSuccessful) {
                return Pair(response.code, responseContent)
            }
            logger.debug("request url: ${request.url}, code: ${response.code}, response: $responseContent")
            if (response.code > 499 && retry > 0) {
                return doRequest(request, retry - 1)
            }
            return Pair(response.code, responseContent)
        } catch (e: IOException) {
            logger.error("request[${request.url}] error, ", e)
            if (retry > 0) {
                logger.info("retry after 3 seconds")
                Thread.sleep(3 * 1000L)
                return doRequest(request, retry - 1)
            }
            throw e
        }
    }

    private fun buildBaseHeaders(userId: String): MutableMap<String, String> {
        val header = mutableMapOf<String, String>()
        header[BKREPO_UID] = userId
        if (!token.isNullOrBlank()) {
            header[HttpHeaders.AUTHORIZATION] = "Temporary $token"
        }
        logger.info(header.toString())
        return header
    }

    private fun getUploadHeader(
        file: File,
        atomParam: UploadArtifactParam
    ): MutableMap<String, String> {
        val header = buildBaseHeaders(atomParam.pipelineStartUserId)
        header[BKREPO_OVERRIDE] = "true"
        if (atomParam.enableMD5Checksum) {
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
        val map = if (strValue.isBlank()) {
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

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveApi::class.java)

        private const val ERROR_PROJECT_EXISTED = 251005
        private const val ERROR_REPO_EXISTED = 251007

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

        private const val METADATA_DISPLAY_NAME = "displayName"
    }
}