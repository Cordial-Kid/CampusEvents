# B 模块交付与验收

更新于 2026-11-26，基于主分支 930a427f 的 A/B/C/D 真实实现完成联调。

B 提供北京时间活动时间轴、关键词与分类筛选、详情内容和来源链接、报名状态展示、刷新与兴趣推荐。兴趣偏好按学生 ID 保存在本机，推荐从当前筛选结果取前三条匹配兴趣且 C 确认可报名的活动。

## 本次完成

- 补充 app/src/androidTest/java/com/example/campusevents/BrowseIntegrationTest.java，使用真实 AppGraph、SQLite 和 A/C/D 端口验证 B 的展示。
- 将 BrowseModule.isReady() 与 integrationReady 设置为 true，正式集成检查通过。
- 修复 Release 冷启动崩溃。日志报告 BrowseViewModel.clear() 与压缩后的 AndroidX 父类 final 方法冲突，现将内部清屏方法重命名为 resetState()，并更新调用处。修复后重新构建、安装并验证非调试模式启动。
- 为搜索框声明不参与个人信息自动填充，B 的 Lint 检查无问题。

## 联调验收

环境为 Windows 11、WHPX、Android Emulator 37.3.9、Android 15/API 35 x86_64。构建使用 JDK 25.0.2、Gradle 9.6.0、AGP 9.5.1 和 Android SDK 37。

| 场景 | 验证结果 |
| --- | --- |
| 报名与人数刷新 | 学生甲报名后显示本人已报名、余量从 1 变为 0，活动从推荐中移除 |
| 身份隔离与满额 | 学生乙显示本人未报名，真实报名请求返回 EVENT_FULL |
| 取消与推荐恢复 | 甲取消后余量恢复为 1，显示本人取消状态，推荐恢复 |
| 截止、取消与推荐 | 已截止活动不推荐，管理员取消后列表显示已取消并移出推荐 |
| 管理员编辑 | 修改标题、地点和时间后列表重查显示新内容，详情显示新介绍和来源 |
| 页面恢复 | 报名后的列表和编辑后的详情经 Activity 重建后仍正确，后台返回后重新读取 |
| 进程恢复与正式启动 | 强制停止应用后重新启动，身份和兴趣可恢复。临时用本机测试证书签名的 Release 包可启动，确认无 DEBUGGABLE 标记 |
| 原有展示功能 | 时间轴、筛选、空结果、来源链接和兴趣隔离已在上一轮设备验收通过 |

## 检查结果与复现

| 检查 | 结果 |
| --- | --- |
| B 单元测试 | 3 项通过 |
| 应用设备回归 | 7 项通过，包含新增 B 联调 2 项、原有宿主 4 项及模板测试 1 项 |
| B lintDebug | No issues found |
| 架构边界 | 七模块检查通过 |
| app:verifyFeatureIntegration | 通过 |
| Debug 与 Release 构建 | 通过 |
| Release 设备启动 | 修复后通过 |

在已配置 Android SDK、JDK 25 和运行中设备的环境执行。Windows 建议使用全英文项目与 Gradle 缓存路径。

~~~powershell
.\gradlew.bat :feature:browse:testDebugUnitTest :feature:browse:lintDebug
.\gradlew.bat :app:verifyFeatureIntegration :app:assembleDebug :app:assembleRelease
.\gradlew.bat :app:connectedDebugAndroidTest
python tools/check_architecture.py
~~~

新增设备测试只删除自己创建的唯一活动及其关联记录，并恢复测试前身份和兴趣。录屏保存在本地 B-final-fixed.mp4。Release 默认输出 app-release-unsigned.apk，最终交付签名由团队使用正式签名配置完成。

## 演示活动录入单

已有的三条预置活动可用于新安装。原预置记录过期时，请 A 从管理页录入下表。D 表示演示当天。时间均为北京时间，来源类型为手工录入，来源名称填「课程作业演示数据」，来源链接留空。

| 标题 | 分类 | 地点 | 开始与结束 | 报名截止 | 名额 | 介绍 |
| --- | --- | --- | --- | --- | --- | --- |
| Android 开发交流 | TECH | 教学楼 101 | D+2 14:00–16:00 | D+2 13:00 | 2 | 交流 Android 页面与本地数据设计 |
| 校园运动体验 | SPORT | 校园体育场 | D+2 17:00–18:00 | D+2 16:00 | 10 | 体验校园团体运动 |
| 学术阅读分享 | ACADEMIC | 教学楼 301 | D+3 19:00–20:30 | D+3 18:00 | 10 | 分享阅读笔记并讨论 |

测试来源链接时，可另录入一条有真实公开公告的活动，选择官方来源，填写公告名称与 HTTP(S) 地址。

