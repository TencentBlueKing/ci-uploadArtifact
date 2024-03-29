## 插件功能和使用场景
将构建机上的文件归档到制品库，支持归档到流水线仓库或自定义仓库

## 插件参数描述
- 待归档的文件(filePath): 归档当前工作空间相对路径或绝对路径目录下的构建产物，可以用通配符匹配，支持多个路径(以英文 , 隔开)，不支持归档目录
- 归档仓库(repoName): 流水线仓库：以流水线名称+构建号自动生成的目录结构;自定义仓库：可自定义仓库目录结构，不存在将自动创建
- 归档至父流水线(isParentPipeline): 是否归档至父流水线的流水线仓库，需要配合子流水线调用插件使用
- 自定义仓库的归档目录(destPath): 归档至自定义仓库时的目标目录路径
- 需要输出下载链接的文件(downloadFiles): 选填。可以填写多组，每组一个文件路径对应一个输出变量名称
- 自定义元数据(metadata): 选填。可以填写多组，每组为一个元数据的键值对
- 启用MD5校验(enableMD5Checksum): 上传文件时校验文件MD5
