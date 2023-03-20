package com.tencent.bk.devops.atom.task.pojo

data class UserMetadataSaveRequest(
    val nodeMetadata: List<MetadataModel>? = null
)


data class MetadataModel(
    /**
     * 元数据键
     */
    val key: String,
    /**
     * 元数据值
     */
    var value: Any,
    /**
     * 是否为系统元数据
     */
    val system: Boolean = false,
    /**
     * 元数据描述信息
     */
    val description: String? = null,
    /**
     * 元数据链接地址
     */
    val link: String? = null
)
