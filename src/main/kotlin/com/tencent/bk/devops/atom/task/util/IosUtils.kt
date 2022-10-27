package com.tencent.bk.devops.atom.task.util

import com.dd.plist.NSArray
import com.dd.plist.NSDictionary
import com.dd.plist.NSString
import com.dd.plist.PropertyListParser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object IosUtils {
    fun getIpaInfoMap(ipa: File): Map<String, String> {

        val map = mutableMapOf<String, String>()
        val file = getPlistFile(ipa) ?: throw RuntimeException("not Info.plist found")
        // 第三方jar包提供
        val rootDict = PropertyListParser.parse(file) as NSDictionary
        // 应用包名
        if (!rootDict.containsKey("CFBundleIdentifier")) throw RuntimeException("no CFBundleIdentifier find in plist")
        var parameters = rootDict.objectForKey("CFBundleIdentifier") as NSString
        map.put("bundleIdentifier", parameters.toString())
        // 应用名称
        if (!rootDict.containsKey("CFBundleName")) throw RuntimeException("no CFBundleName find in plist")
        parameters = rootDict.objectForKey("CFBundleName") as NSString
        map.put("appTitle", parameters.toString())
        // 应用版本
        if (!rootDict.containsKey("CFBundleShortVersionString")) throw RuntimeException("no CFBundleShortVersionString find in plist")
        parameters = rootDict.objectForKey("CFBundleShortVersionString") as NSString
        map.put("bundleVersion", parameters.toString())
        // 应用版本
        if (!rootDict.containsKey("CFBundleVersion")) throw RuntimeException("no CFBundleVersion find in plist")
        parameters = rootDict.objectForKey("CFBundleVersion") as NSString
        map.put("bundleVersionFull", parameters.toString())

        // 如果没有图标，捕获异常，不影响接下步骤
        try {
            val cfBundleIcons = rootDict.objectForKey("CFBundleIcons") as NSDictionary
            val cfBundlePrimaryIcon = cfBundleIcons.objectForKey("CFBundlePrimaryIcon") as NSDictionary
            val cfBundleIconFiles = cfBundlePrimaryIcon.objectForKey("CFBundleIconFiles") as NSArray
            val size = cfBundleIconFiles.array.size
            map.put("image", (cfBundleIconFiles.array[0] as NSString).toString())
            map.put("fullImage", (cfBundleIconFiles.array[size - 1] as NSString).toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return map
    }

    private fun getPlistFile(srcZipFile: File): File? {
        val pattern = Pattern.compile("Payload/[\\w.-]+\\.app/Info.plist")
        ZipInputStream(BufferedInputStream(FileInputStream(srcZipFile))).use { zis ->
            var entry: ZipEntry?
            while (true) {
                entry = zis.nextEntry
                if (entry == null) break
                if (pattern.matcher(entry.name).matches()) {
                    val file = File.createTempFile("Info", ".plist")
                    BufferedOutputStream(FileOutputStream(file.canonicalFile)).use { bos ->
                        var b: Int
                        val buf = ByteArray(4096)
                        while (true) {
                            b = zis.read(buf)
                            if (b == -1) break
                            bos.write(buf, 0, b)
                        }
                    }
                    return file
                }
            }
        }
        return null
    }
}
