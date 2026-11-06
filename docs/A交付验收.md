# A 的交付与验收记录

日期 2026-11-06。范围为作业第四题中 A 的工程底座、统一数据、身份、导航、管理员维护和协作接入。七模块结构沿用已有架构设计，实际分工见 [统一约定](协作开发统一约定.md)。

## 已交付实现

| 内容 | 代码入口 | 实现范围 |
| --- | --- | --- |
| 公共契约 | core/contracts | 纯 Java DTO、异步接口、结果码与 A/B/C/D 业务声明 |
| 基础设施 | core/platform | 单 SQLite、Schema、串行队列、持久会话、epoch、事务、主线程回调、失效通知、持久副作用队列 |
| 活动管理 | feature/event/EventModule | 真实活动读取、新增、编辑、取消、版本冲突、权限和表单校验 |
| 应用装配 | app/bootstrap/AppGraph | 明确依赖注入、各模块出口、通知路由、开发/正式接入检查 |
| 主宿主 | app/MainActivity、ui/DetailFragment | 身份选择、主导航、三个独立面板容器、恢复工厂、后台延迟导航和通知身份检查 |
| 并行起点 | feature/browse、registration、engagement | 基础浏览聚合、模块Schema和UI出口、明确未接入适配器 |
| 协作资料 | docs | 统一约定、接口调用、字段与迁移、构建和验收说明 |

三份早期架构中引用的分工/数据清单在实际目录中缺失。本次依据原始评分 DOCX 和两份现有设计 MD 补齐了实际 Schema、报名/提醒边界、分类和错误口径，集中记录在统一约定与数据字典，协作者可以据此核对。

## 验证环境

Windows，Android Studio JBR 25.0.4，Gradle 9.6.0，AGP 9.5.1，Android API37 模拟器 Medium_Phone_API_37.0，应用最低 API24。当前设备验证只覆盖该模拟器，其他 Android 版本和实体设备尚待团队最终回归。

命令行 Wrapper 首次下载遇到本机证书链问题，本次使用缓存中相同版本 Gradle 安装并设置 JAVA_HOME/GRADLE_USER_HOME 完成构建。随后联网从官方依赖仓库补齐 AAPT2。TLS 校验保持开启。

## 验证结果

| 验证项 | 结果 |
| --- | --- |
| app assembleDebug | 构建通过，生成可安装调试 APK |
| B/C/D 各模块 assembleDebug | 分别通过，默认配置缓存可写入 |
| core/contracts test | 任务正常，当前 NO-SOURCE |
| core/platform 设备测试 | 3 项通过，0 失败 |
| feature/event 设备测试 | 7 项通过，0 失败 |
| app 设备测试 | 5 项通过，0 失败，其中新宿主集成 4 项，模板原有 1 项 |
| Android lintDebug | 0 错误，8 条非阻断警告 |
| tools/check_architecture.py | 七模块边界检查通过 |
| app verifyFeatureIntegration | 按预期拒绝 B/C/D 未接入的正式打包，配置缓存正常保存 |
| 调试 APK 安装与冷启动 | 模拟器安装成功，冷启动正常，主页面视觉核对完成 |

Lint 的 8 条提示为旧 lifecycle 库自带检查器与当前 lint 的兼容提示 1 条、Fragment 可升级提示 1 条、模板已有颜色资源未使用 2 条、按钮栏样式建议 4 条。旧 lifecycle 自带检查器的部分检查被跳过，平台与应用的实际生命周期行为另有设备回归覆盖。版本沿用已构建验证的固定值。

## 测试范围

PlatformRegressionTest 的三项真实 SQLite 测试覆盖业务数据、队列和失效通知共同回滚，旧身份排队写入拒绝及主线程回调，缺失处理器时持久保留任务并在重新打开后恢复。

EventModuleRegressionTest 的七项测试覆盖学生越权、普通文案编辑与毫秒时间保留、旧版本、取消流程中 C/D 分别失败回滚、时间变更时 D 失败回滚、容量边界和重复取消。跨模块测试替身只存在 androidTest，生产端口保持未接入语义。

ShellIntegrationTest 覆盖真实身份选择、API 新增与文案编辑后的管理列表、三面板的 eventId 一致性与 Activity 重建、后台身份回调后恢复导航。后台恢复项通过真实按钮 performClick 执行应用监听器，避免 API37 辅助 Activity 过渡期间系统丢触控，其余交互项保留 Espresso 触控。

独立库测试使用 UUID 测试数据库。宿主测试仅清理自己创建的活动并恢复原身份。源码静态检查覆盖功能模块间编译依赖与实现导入、跨所有者 SQL、UI 数据层导入及纯 Java contracts 的 Android 类型边界。

## 后续集成依赖

C 的真实报名端口和 D 的提醒端口仍需各自实现。当前新增和普通内容编辑可直接操作，容量/时间修改与取消流程会在缺少所需端口时返回 MODULE_NOT_READY，整个事务回滚。其原子调用、版本和错误路径已经在真实 SQLite 中用测试替身验证。

B 继续完善正式日历/时间轴和状态展示。C、D 完成报名、收藏与真实通知后，由 A 接入并验收完整闭环、真机权限/重启/后台提醒、最终签名包及关键流程录屏。当前调试 APK 用于 A 底座交接。
