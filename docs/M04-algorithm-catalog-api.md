# M04：DS 算法目录查询接口（前端接入说明）

本次实现让 DS 前端通过 DS Java 后端，查询算法平台登记的算法、算法版本及兼容模型。算法平台负责保存目录及检查文件是否存在；DS 负责登录、项目访问检查、代理调用、字段转换及错误处理。DS 不另存一份算法目录，也不按算法上传人筛选。

## 1. 调用方式和范围

前端使用现有 DS 请求客户端和登录会话。以下路径均相对于 DS API 的 context-path；沿用现有 API baseURL，不能把页面的 /ui 路径当成 API 前缀。代理部署下以实际 Nginx 配置为准。

| 用途 | 方法及路径 |
| --- | --- |
| 算法列表 | GET /projects/{projectCode}/industrial/algorithms |
| 算法版本列表 | GET /projects/{projectCode}/industrial/algorithms/{algorithmId}/versions |
| 兼容模型列表 | GET /projects/{projectCode}/industrial/algorithm-versions/{versionId}/models |

projectCode、algorithmId、versionId 必须为正整数。目录响应中的所有 ID 使用字符串，前端应按字符串保存，避免 JavaScript 大整数精度丢失。不要将 ID 转成 Number。

前端不需要算法平台的 X-API-Key，也不得要求用户填写算法平台地址、密钥或文件物理路径。

## 2. 算法列表与分页

请求：

~~~http
GET /projects/123/industrial/algorithms?pageNo=1&pageSize=20
~~~

pageNo 默认 1；pageSize 默认 20，允许 1–100。成功响应示例：

~~~json
{
  "code": 0,
  "msg": "success",
  "data": {
    "items": [
      {"id": "10", "name": "异常检测算法", "description": "设备状态检测"}
    ],
    "pageNo": 1,
    "pageSize": 20,
    "total": null,
    "hasNext": false
  }
}
~~~

msg 会随 DS 语言设置变化，前端只根据 code 判断成功。算法平台暂未提供总条数，因此 total 为 null，不能解释为 0。使用“上一页 / 下一页”或加载更多，不显示虚构的总页数。

Java 将页码换算为 offset，每次多请求一条来确定 hasNext。数据为空时返回空 items。目录分页没有快照保证，翻页期间发生增删时建议重新加载第一页。

## 3. 选择算法版本

~~~http
GET /projects/123/industrial/algorithms/10/versions
~~~

~~~json
{
  "code": 0,
  "msg": "success",
  "data": {
    "items": [
      {
        "id": "20",
        "algorithmId": "10",
        "version": "0.3.0",
        "available": true,
        "unavailableReasons": []
      },
      {
        "id": "21",
        "algorithmId": "10",
        "version": "0.2.0",
        "available": false,
        "unavailableReasons": ["ALGORITHM_FILE_MISSING"]
      }
    ]
  }
}
~~~

版本列表保留不可用版本，前端可以显示原因并禁用选择。切换算法时，必须清空原来的算法版本和模型选择。列表无分页；过大的上游响应会按配置限制拒绝，不能静默截断。

## 4. 选择同版本模型

~~~http
GET /projects/123/industrial/algorithm-versions/20/models
~~~

~~~json
{
  "code": 0,
  "msg": "success",
  "data": {
    "items": [
      {
        "id": "30",
        "algorithmVersionId": "20",
        "version": "model-1",
        "status": "AVAILABLE",
        "available": true,
        "unavailableReasons": []
      }
    ]
  }
}
~~~

默认仅提供同一个算法版本、状态 AVAILABLE、模型文件存在且关联算法可用的模型。模型文件是否存在由算法平台检查，DS 不读取算法平台磁盘。

可选参数 includeUnavailable=true 会返回不可用模型及原因，适合需要展示禁用选项的界面。不可用行不能作为提交运行的选项。Java 会拒绝关联 ID 与请求不一致的上游数据。

| 原因代码 | 建议显示文案 |
| --- | --- |
| ALGORITHM_FILE_MISSING | 算法文件不存在 |
| ALGORITHM_DELETED | 关联算法已删除 |
| MODEL_NOT_AVAILABLE | 模型状态不可用 |
| MODEL_FILE_MISSING | 模型文件不存在 |

对尚未识别的新原因代码，显示“资源暂不可用”并保留诊断代码，不应把未知原因视为可用。切换算法版本时，清空原模型；快速切换选择时，忽略过期请求的响应。

## 5. 错误处理

沿用 DS 的 Result 包装和全局异常处理。业务错误通常仍为 HTTP 200，但 code 非 0；不能仅用 HTTP 200 判断成功。未登录由现有 DS 登录拦截器处理，项目无权访问沿用 DS 原有错误。参数绑定错误同样沿用全局处理。

| code | 含义 | 前端处理 |
| --- | --- | --- |
| 10001 | 页码、页大小或 ID 不合法 | 修正参数 |
| 1500002 | 算法平台集成未启用 | 提示管理员配置集成 |
| 1500009 | DS 调用算法平台的服务认证失败 | 提示管理员检查服务密钥 |
| 1500012 | 未预期的目录查询异常 | 显示错误并保留诊断信息 |
| 1500013 | 算法或版本不存在，上游返回 404 | 清空失效选项，刷新目录 |
| 1500014 | 上游目录字段、关联关系或响应大小不合法 | 禁止选择，提示管理员检查 |
| 1500015 | 上游不可访问、超时、返回服务错误或重定向 | 允许用户重试，检查服务地址 |

上游 401/403 均映射为 1500009；404 映射为 1500013；503 等服务错误、连接错误和超时映射为 1500015。接口不返回上游原始错误正文、密钥或内部文件路径。

## 6. DS 与算法平台的后端调用

| DS 用途 | 算法平台已有接口 |
| --- | --- |
| 算法列表 | GET /api/service/catalog/algorithms?offset=0&limit=21 |
| 版本列表 | GET /api/service/catalog/algorithms/10/versions |
| 模型列表 | GET /api/service/catalog/versions/20/models?include_unavailable=false |

DS 使用既有 AlgorithmPlatformConfiguration：

| 环境变量 | 默认值 / 说明 |
| --- | --- |
| ALGORITHM_PLATFORM_ENABLED | false；接入时设为 true |
| ALGORITHM_PLATFORM_BASE_URL | 算法平台后端根地址，不包含 /api |
| ALGORITHM_PLATFORM_API_KEY | 与算法平台配置的 DS_API_KEY 一致，服务端保管 |
| ALGORITHM_PLATFORM_CONNECT_TIMEOUT_MILLIS | 3000 |
| ALGORITHM_PLATFORM_READ_TIMEOUT_MILLIS | 10000 |
| ALGORITHM_PLATFORM_MAX_RESPONSE_BYTES | 10485760，即 10 MiB |

新目录 HTTP 请求的整体超时为连接超时与读取超时之和；读取过程中限制实际响应字节数，包括没有 Content-Length 的分块响应。重定向不跟随，须配置最终可访问地址。现有结果查询接口继续使用原实现。

本功能不增加数据库表、不需要迁移、不修改算法上传记录和 DS 工作流。普通项目成员经 DS 既有访问检查后可看到全局共享目录。

## 7. 本阶段边界与前端工作

前端现在可以实现“算法 → 版本 → 模型”的联动选择、空状态、不可用提示和错误重试。

available 表示本次查询时的资源可用性，不代表所有 Python 依赖、GPU 环境或输入数据格式已经验证。查询到提交之间资源也可能发生变化；M06 提交训练 / 预测时仍须在后端重新校验，不能信任浏览器传回的 available。

本次不提供参数契约 /contract、paramsSchema 或训练预测签名，也不实现任务提交。相应元数据应在算法平台有真实来源后补齐。不可根据版本名称自行猜测算法参数。

## 8. 开发检查

新增测试涵盖：HTTP 路由与默认参数、分页和大 offset、项目访问拒绝、跨算法 / 跨版本拒绝、不可用原因、服务密钥传递、响应字段白名单、大整数序列化、认证 / 404 / 服务错误、重定向、超时、分块超限和空目录。

标准 Maven 命令（已具备项目构建环境及依赖时）：

~~~powershell
.\mvnw.cmd -pl dolphinscheduler-api verify '-Dspotless.skip=true' '-Djacoco.skip=true' '-Danalyze.skip=true' '-Dmaven.test.skip=false' '-DskipUT=false' '-Dtest=AlgorithmCatalogClientTest,AlgorithmCatalogServiceTest,AlgorithmCatalogControllerTest,AlgorithmPlatformClientTest,AlgorithmResultServiceTest,AlgorithmResultControllerTest,AlgorithmPlatformConfigurationTest'
~~~

实际执行情况和环境差异将在本次验证完成后记录。单元测试使用模拟上游与项目服务，不修改开发数据库；真实部署联调需要更新运行中的 DS Java 服务后另行进行。

