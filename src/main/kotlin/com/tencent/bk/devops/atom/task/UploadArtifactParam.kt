package com.tencent.bk.devops.atom.task

import com.tencent.bk.devops.atom.pojo.AtomBaseParam
import lombok.Data
import lombok.EqualsAndHashCode

@Data
@EqualsAndHashCode(callSuper = true)
class UploadArtifactParam : AtomBaseParam() {
    val filePath: String = ""
    val isCustomize: String = ""
    val destPath: String = ""
}