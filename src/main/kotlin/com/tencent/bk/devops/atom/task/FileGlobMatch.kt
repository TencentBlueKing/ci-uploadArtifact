package com.tencent.bk.devops.atom.task

import com.tencent.bk.devops.atom.pojo.AtomBaseParam
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

object FileGlobMatch : AtomBaseParam() {
    private val logger = LoggerFactory.getLogger(FileGlobMatch::class.java)

    fun match(glob: String, location: String): List<File> {
        val filelist: MutableList<File> = mutableListOf()
        val pathMatcher = FileSystems.getDefault().getPathMatcher(glob)
        Files.walkFileTree(
            Paths.get(location),
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                    logger.info(">>>is matching $path")
                    if (pathMatcher.matches(path)) {
                        filelist.add(File(path.toString()))
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }
            })
        return filelist
    }
}