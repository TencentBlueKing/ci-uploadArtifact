package com.tencent.bk.devops.atom.task

import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.api.SdkEnv
import com.tencent.bk.devops.atom.exception.AtomException
import com.tencent.bk.devops.atom.pojo.StringData
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import com.tencent.bk.devops.atom.task.api.ArchiveApi
import com.tencent.bk.devops.atom.task.constant.PARENT_BUILD_ID
import com.tencent.bk.devops.atom.task.constant.PARENT_PIPELINE_ID
import com.tencent.bk.devops.atom.task.constant.REPO_CUSTOM
import com.tencent.bk.devops.atom.task.constant.REPO_PIPELINE
import com.tencent.bk.devops.atom.task.constant.UPLOAD_MAX_FILE_COUNT
import com.tencent.bk.devops.atom.task.util.FileGlobMatch
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.path.PathUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems

@AtomService(paramClass = UploadArtifactParam::class)
class UploadArtifactAtom : TaskAtom<UploadArtifactParam> {
    override fun execute(atomContext: AtomContext<UploadArtifactParam>) {
        val atomParam = atomContext.param
        val projectId = atomParam.projectName
        val filePath = atomParam.filePath
        val repoName = atomParam.repoName
        val workspace = File(atomParam.bkWorkspace)
        val destPath = atomParam.destPath
        val isParentPipeline = atomParam.isParentPipeline
        val downloadFiles = getDownloadFiles(atomParam.downloadFiles)

        if (repoName == REPO_PIPELINE && isParentPipeline) {
            val parentPipelineId = System.getenv(PARENT_PIPELINE_ID)
            val parentBuildId = System.getenv(PARENT_BUILD_ID)
            parentPipelineId?.let { atomParam.pipelineId = parentPipelineId }
            parentBuildId?.let { atomParam.pipelineBuildId = parentBuildId }
        }

        val filesToUpload = mutableSetOf<String>()
        filePath.split(",").forEach {
            filesToUpload.addAll(matchFiles(workspace, it.trim().removePrefix("./"))
                .map { file -> file.absolutePath }
            )
        }

        val downloadFileMap = mutableMapOf<String, String>()
        downloadFiles.forEach {
            val key = matchFiles(workspace, it["path"]!!.trim().removePrefix("./"))
                .map { file -> file.absolutePath }
                .firstOrNull()
            val value = it["param"]!!
            if (key == null) {
                logger.warn("path[${it["path"]}] doesn't match any file, please check your input")
            } else {
                downloadFileMap[key] = value
            }
        }
        logger.info("${filesToUpload.size} file to upload")

        if (filesToUpload.isEmpty()) {
            logger.error(" 0 file to upload, please check your input")
            throw AtomException("no file match")
        }
        if (filesToUpload.size > UPLOAD_MAX_FILE_COUNT) {
            logger.error("file to upload count(${filesToUpload.size}) exceed $UPLOAD_MAX_FILE_COUNT")
            throw AtomException("too many match files")
        }

        logger.info("${filesToUpload.size} file matched to upload")
        filesToUpload.forEach {
            val file = File(it)
            val fullPath: String
            when (repoName) {
                REPO_CUSTOM -> {
                    fullPath = PathUtils.normalizeFullPath("/$destPath/${file.name}")
                    archiveApi.uploadFile(file, fullPath, atomParam)
                }

                else -> {
                    fullPath = PathUtils.normalizeFullPath(
                        "/${atomParam.pipelineId}/${atomParam.pipelineBuildId}/${file.name}"
                    )
                    archiveApi.uploadFile(file, fullPath, atomParam)
                }
            }
            logger.info("$it uploaded")
            if (downloadFileMap.containsKey(it)) {
                atomContext.result.data[downloadFileMap[it]] =
                    StringData(generateDownloadUrl(projectId, repoName, fullPath))
                logger.info("add file[${it}] download url to output param[${downloadFileMap[it]}]")
            }
        }

        if (repoName == REPO_PIPELINE) {
            archiveApi.setPipelineMetadata(
                atomParam.pipelineStartUserId,
                atomParam.projectName,
                atomParam.pipelineId,
                atomParam.pipelineName,
                atomParam.pipelineBuildId,
                atomParam.pipelineBuildNum
            )
        }
        logger.info("upload ${filesToUpload.size} files done")
    }

    private fun matchFiles(workspace: File, filePath: String): List<File> {
        val file = if (filePath.startsWith("./")) filePath.substring(2)
        else filePath
        val absPath = file.startsWith("/") || (file[0].isLetter() && file[1] == ':')
        val fullFile = if (!absPath) File(workspace.absolutePath + File.separator + file)
        else File(file)
        if (fullFile.isDirectory) throw AtomException("this path is directory......")
        val fullPath = fullFile.absolutePath
        val filePathGlob = "glob:$fullPath"
        val fileList: List<File>
        fileList = if (fullPath.contains("**")) {
            val startPath = File("${fullPath.substring(0, fullPath.indexOf("**"))}a").parent.toString()
            val var1 = filePathGlob.replace("\\", "/")
            val var2 = startPath.replace("\\", "/")
            FileGlobMatch.match(var1, var2)
        } else {
            fileMatch(fullPath)
        }
        // 文件夹返回所有文件
        val resultList = mutableListOf<File>()
        fileList.forEach { f ->
            // 文件名称不允许带有空格
            if (!f.name.contains(" ")) {
                resultList.add(f)
            } else {
                logger.info("The file name has a space and will not be uploaded! >>> ${f.name}")
            }
        }
        return resultList
    }

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    fun fileMatch(full: String): List<File> {
        var list = ArrayList<File>()
        val fullPath = full.replace("\\", "/")
        val dirPath = fullPath.substring(0, fullPath.lastIndexOf("/") + 1)
        val glob = "glob:$fullPath"
        if (!dirPath.contains("*")) {
            val pathMatcher = FileSystems.getDefault().getPathMatcher(glob)
            val regex = Regex(pattern = "\\]|\\[|\\}|\\{|\\?")
            if (!fullPath.contains(regex) && File(fullPath).exists()) {
                list.add(File(fullPath))
                logger.info("match file is $fullPath")
                return list
            }
            val parentFile = File(fullPath).parentFile
            println("parentFile: ${parentFile.canonicalPath}")
            parentFile.listFiles()?.forEach { f ->
                if (pathMatcher.matches(f.toPath())) {
                    list.add(f)
                }
            }
        } else {
            val var3 = File("${fullPath.substring(0, fullPath.indexOf("*"))}a").parent.toString()
            if (dirPath.indexOf("*") != dirPath.lastIndexOf("*")) {
                val var4 = glob
                val var5 = var3.replace("\\", "/")
                list = FileGlobMatch.match(var4, var5) as ArrayList<File>
            } else {
                val secondIndex = dirPath.indexOf("/", dirPath.indexOf("*"))
                val secondPath = dirPath.substring(0, secondIndex)
                val thirdPath = dirPath.substring(secondIndex)
                val globSecond = "glob:$secondPath"
                var pathMatcher = FileSystems.getDefault().getPathMatcher(globSecond)
                val DirMatchFile = ArrayList<File>()
                File(secondPath.substring(0, secondPath.lastIndexOf("/") + 1)).listFiles()?.forEach { f ->
                    if (pathMatcher.matches(f.toPath())) {
                        var matchDirPath = File(f, thirdPath)
                        DirMatchFile.add(matchDirPath)
                    }
                }
                DirMatchFile.forEach { f ->
                    pathMatcher = FileSystems.getDefault().getPathMatcher(glob)
                    f.listFiles()?.forEach { file ->
                        if (pathMatcher.matches(file.toPath())) {
                            list.add(file)
                        }
                    }
                }
            }
        }
        return list
    }

    private fun getDownloadFiles(strValue: String): List<Map<String, String>> {
        val list: List<Map<String, String>> = if (strValue.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                strValue.readJsonString<List<Map<String, String>>>().filterNot { it["path"].isNullOrBlank() }
                    .filterNot { it["path"]!!.contains("*") }.filterNot { it["param"].isNullOrBlank() }
            } catch (e: Exception) {
                logger.error("fail to deserialize input: $strValue")
                emptyList()
            }
        }
        logger.debug("get param download files: ${strValue?.replace("\n", "")}")
        return list
    }

    private fun generateDownloadUrl(
        projectId: String, repoName: String, fullDestPath: String
    ): String {
        val fileGateway = SdkEnv.getFileGateway()
        return if (fileGateway.contains("bkrepo")) {
            "$fileGateway/web/generic/$projectId/$repoName/$fullDestPath?download=true"
        } else if (fileGateway.contains("devops")) {
            "$fileGateway/bkrepo/api/user/generic/$projectId/$repoName/$fullDestPath?download=true"
        } else {
            "/bkrepo/api/user/generic/$projectId/$repoName/$fullDestPath?download=true"
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UploadArtifactAtom::class.java)
        var archiveApi = ArchiveApi()
    }
}
