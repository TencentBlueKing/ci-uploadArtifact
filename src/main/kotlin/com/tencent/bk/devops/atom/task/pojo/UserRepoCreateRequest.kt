package com.tencent.bk.devops.atom.task.pojo

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 创建仓库请求
 */
@ApiModel("创建仓库请求")
data class UserRepoCreateRequest(
    @ApiModelProperty("所属项目id", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val name: String,
    @ApiModelProperty("仓库类型", required = true)
    val type: RepositoryType,
    @ApiModelProperty("仓库类别", required = true)
    val category: RepositoryCategory = RepositoryCategory.COMPOSITE,
    @ApiModelProperty("是否公开", required = true)
    val public: Boolean = false,
    @ApiModelProperty("简要描述", required = false)
    val description: String? = null,
    @ApiModelProperty("仓库配置", required = true)
    val configuration: RepositoryConfiguration? = null,
    @ApiModelProperty("存储凭证key", required = false)
    val storageCredentialsKey: String? = null,
    @ApiModelProperty("仓库配额", required = false)
    val quota: Long? = null,
    @ApiModelProperty("来自插件的请求", required = false)
    val pluginRequest: Boolean = false,
    @ApiModelProperty("是否展示", required = true)
    val display: Boolean = true
)
