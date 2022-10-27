package com.tencent.bk.devops.atom.task

import com.tencent.bk.devops.atom.pojo.AtomBaseParam
import lombok.Data
import lombok.EqualsAndHashCode

@Data
@EqualsAndHashCode(callSuper = true)
class UploadArtifactParam : AtomBaseParam() {
    val filePath: String = ""
    val repoName: String = "pipeline"
    val destPath: String = ""
    val isParentPipeline: Boolean = false
    val downloadFiles: String = ""
    val metadata: String = ""
    val enableMD5Checksum: Boolean = false
}