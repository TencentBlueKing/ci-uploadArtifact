package com.tencent.bk.devops.atom.task

import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.exception.AtomException
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems

@AtomService(paramClass = UploadArtifactParam::class)
class UploadArtifactAtom : TaskAtom<UploadArtifactParam> {
    override fun execute(atomContext: AtomContext<UploadArtifactParam>) {
        val atomParam = atomContext.param

        val filePath = atomParam.filePath
        val isCustomize = atomParam.customize == "true"
        val workspace = File(atomParam.bkWorkspace)
        val destPath: String = atomParam.destPath ?: ""
        val filesToUpload = mutableSetOf<String>()
        filePath.split(",").forEach {
            filesToUpload.addAll(
                matchFiles(workspace, it.trim().removePrefix("./")).map { file ->
                    file.absolutePath
                }
            )
        }

        if (filesToUpload.isEmpty()) {
            logger.error(" 0 file to upload, please check your input")
            throw AtomException("no file match")
        }
        if (filesToUpload.size > maxFileCount) {
            logger.error("file to upload count(${filesToUpload.size}) exceed $maxFileCount, please check your input")
            throw AtomException("too many match files")
        }

        logger.info("${filesToUpload.size} file matched to upload")
        filesToUpload.forEach {
            if (isCustomize) {
                archiveApi.uploadBkRepoCustomFile(File(it), destPath, atomParam)

            } else {
                archiveApi.uploadBkRepoPipelineFile(File(it), atomParam)
            }
            logger.info("$it uploaded")
        }
        logger.info("upload ${filesToUpload.size} files done")
    }

    private fun matchFiles(workspace: File, filePath: String): List<File> {
        val file = if (filePath.startsWith("./")) filePath.substring(2)
        else filePath
        val absPath = file.startsWith("/") || (file[0].isLetter() && file[1] == ':')
        val fullFile = if (!absPath) File(workspace.absolutePath + File.separator + file)
        else File(file)
        if (fullFile.isDirectory) throw RuntimeException("this path is directory......")
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

    companion object {
        private val logger = LoggerFactory.getLogger(UploadArtifactAtom::class.java)
        private const val maxFileCount = 500
        var archiveApi = ArchiveApi()
    }
}