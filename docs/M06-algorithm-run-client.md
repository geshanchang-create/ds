# M06 算法运行客户端实现说明

## 1. 文档目的

本文说明 DolphinScheduler（以下简称 DS）在 M06 阶段与算法平台进行训练、预测交互的当前实现、接口映射、验证结果和后续边界，供后端联调、前端开发和代码审核使用。

当前提交完成的是 **DS Java 后端访问算法平台的运行客户端**。它是后续 `/industrial/runs` 业务接口的底层能力，但不等同于完整的运行管理模块。

## 2. 当前完成范围

本阶段已在 `dolphinscheduler-api` 模块中完成以下能力：

1. 提交训练任务。
2. 查询训练任务状态。
3. 查询训练任务日志。
4. 停止训练任务。
5. 提交预测任务。
6. 查询预测任务状态。
7. 查询预测任务日志。
8. 停止预测任务。
9. 查询预测结果视图。
10. 将算法平台响应转换为稳定的 Java DTO。
11. 将网络、鉴权、资源不存在、业务冲突和响应解析异常映射为 DS 状态码。

调用算法平台时统一使用服务端配置的 `X-API-Key`。密钥不会返回浏览器，也不会写入日志。

## 3. 代码结构

| 文件 | 作用 |
| --- | --- |
| `AlgorithmRun.java` | 训练和预测请求、训练引用、日志条目的 DTO 定义 |
| `AlgorithmExecutionReference.java` | 预测任务引用及状态信息 |
| `AlgorithmPlatformClient.java` | DS 调用算法平台的统一 Java 接口 |
| `AlgorithmPlatformClientImpl.java` | 基于 OkHttp 的接口实现、响应解析和错误处理 |
| `Status.java` | M06 对外稳定错误码 |
| `AlgorithmPlatformClientTest.java` | MockWebServer 单元测试 |
| `AlgorithmPlatformClientLiveTest.java` | 可选的真实算法平台联调测试 |

## 4. 算法平台接口映射

`AlgorithmPlatformClient` 封装算法平台已有接口。DS 其他业务服务不需要直接拼接 URL、Header 或 JSON。

| DS 客户端能力 | 算法平台操作 | 用途 |
| --- | --- | --- |
| `submitTraining` | 创建训练任务 | 按算法版本、输入文件和参数发起训练 |
| `getTraining` | 查询训练任务 | 获取训练状态和模型产出信息 |
| `getTrainingLogs` | 查询训练日志 | 获取训练过程日志 |
| `stopTraining` | 停止训练任务 | 请求终止尚未结束的训练 |
| `submitExecution` | 创建预测任务 | 按模型、输入文件和参数发起预测 |
| `getExecution` | 查询预测任务 | 获取预测状态 |
| `getExecutionLogs` | 查询预测日志 | 获取预测过程日志 |
| `stopExecution` | 停止预测任务 | 请求终止尚未结束的预测 |
| `getExecutionResult` | 查询结果视图 | 获取已规范化的预测结果 JSON |

请求体使用算法平台约定的 snake_case 字段。响应体有大小上限，避免异常服务返回过大内容占满 DS JVM 内存。

## 5. 错误处理

新增的 M06 状态码为：

| 状态码 | 含义 |
| --- | --- |
| `1500016` | 算法平台运行请求失败 |
| `1500017` | 算法平台运行资源不存在 |
| `1500018` | 算法平台运行请求冲突 |
| `1500019` | 算法平台运行响应无效 |
| `1500020` | 算法平台运行服务不可用 |

客户端会区分 HTTP 401/403、404、409、其他 4xx/5xx、网络异常和 JSON 解析异常，避免把所有失败都表现为同一个模糊错误。

## 6. 安全与配置约束

1. 算法平台地址和 API Key 只从 DS 后端配置读取。
2. 前端不接触永久 API Key，也不直接调用算法平台。
3. 日志不得打印 API Key、完整鉴权 Header 或敏感请求内容。
4. 客户端只接受 DS 业务层提供的受控参数。
5. 后续 `/industrial/runs` 接口必须根据登录用户和业务记录校验权限，不能直接暴露任意 `fileId` 或参数透传能力。

## 7. 测试与实际结果

### 7.1 编译和单元测试

已完成生产代码编译、测试代码编译和相关模块回归。实际结果如下：

- M06 客户端单元测试：10 个通过。
- 相关 API、目录、结果服务回归：37 个通过，0 个失败。
- 普通测试模式下真实联调测试默认跳过，不影响离线构建。
- M06 文件的定向 Spotless 校验通过。

完整 API 模块 Spotless 仍会扫描到仓库内 21 个历史格式问题；这些问题不属于本提交，因此没有在 M06 PR 中一并修改。

### 7.2 真实算法平台联调

已使用本地真实 FastAPI 算法平台进行一次受控联调，验证对象为：

- 训练任务 ID：405，状态 `SUCCESS`。
- 预测任务 ID：910，状态 `SUCCESS`。

验证内容包括算法目录读取、训练状态、训练日志、预测状态、预测日志和预测结果视图。算法平台对应请求均返回 HTTP 200，真实联调测试 1 个通过。

真实联调测试默认关闭，可通过以下环境变量启用：

```powershell
$env:ALGORITHM_PLATFORM_LIVE_TEST = "true"
$env:ALGORITHM_PLATFORM_BASE_URL = "http://127.0.0.1:8000"
$env:ALGORITHM_PLATFORM_API_KEY = "<local-secret>"
$env:ALGORITHM_PLATFORM_TRAINING_ID = "405"
$env:ALGORITHM_PLATFORM_EXECUTION_ID = "910"
```

API Key 只应在本地环境变量或服务器密钥配置中设置，不得写入代码、测试报告或 Git。

## 8. 本次未实现的内容

本提交没有直接新增以下正式前端接口：

- `POST /industrial/runs`
- `GET /industrial/runs`
- `GET /industrial/runs/{runId}`
- `GET /industrial/runs/{runId}/events`
- `POST /industrial/runs/{runId}/retry`
- `POST /industrial/runs/{runId}/cancel`

原因是这些接口属于 DS 的业务运行编排层，需要同时依赖：

1. M05 固化后的方案版本和步骤快照。
2. 数据准备记录及可用状态。
3. `business_run` 运行主表及步骤/事件持久化结构。
4. `client_request_id` 幂等规则。
5. DS 实例 ID、算法平台任务 ID 与业务运行 ID 的映射。
6. 重试、取消、状态同步和权限规则。

如果当前直接增加简单透传 Controller，会绕过以上约束，并允许调用者任意提交文件 ID 和运行参数，不符合已经冻结的 M06 协议。

## 9. 对前端开发的影响

前端可以依据协议提前完成页面结构、类型定义和 Mock 数据，但暂时不能把正式 `/industrial/runs` 请求接到本提交上。

本提交为后续业务接口提供了可靠的底层调用能力。待 M05 快照和 `business_run` 持久化方案确定后，DS 后端可在此客户端之上实现正式 Controller 和 Service，前端接口路径不需要直接感知算法平台地址。

## 10. 下一步建议

1. 确认 M05 方案快照、数据准备记录和运行步骤的数据结构。
2. 建立 `business_run` 及运行步骤/事件表，明确状态机和幂等键。
3. 实现 M06 Run Service，将业务运行记录与算法平台任务 ID 绑定。
4. 按协议实现 `/industrial/runs` Controller。
5. 增加运行创建、查询、取消、重试和状态同步的集成测试。
6. 与前端使用同一份接口协议完成联调。

## 11. 当前结论

M06 的算法平台运行客户端已经完成并通过单元、回归和真实联调验证；正式运行管理 API 尚未完成，其实现依赖 M05 和业务运行持久化设计。本提交可以独立审核客户端能力，但不能被描述为 M06 全业务闭环已经完成。
