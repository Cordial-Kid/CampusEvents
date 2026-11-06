# 北航活动通

安卓开发作业第四题，Java + XML + Fragment + SQLite，一个 APK，七个 Gradle 模块。当前交付 A 的公共底座、应用壳与活动维护实现，BCD 通过固定出口继续独立开发。

## 协作入口

1. [统一约定](docs/协作开发统一约定.md) 明确分工、不可跨越的边界、统一数据口径和验收规则。
2. [接口与接入说明](docs/interface-design.md) 给出实际包名、方法签名、线程和调用示例。
3. [数据库字典](docs/data-dictionary.md) 给出七张表及字段、所有者和迁移要求。
4. [原始架构设计](docs/architecture-design.md) 保留完整设计依据与联动流程，实施差异以当前统一约定和代码为准。

## 环境与构建

沿用已有工程版本并锁定。AGP 9.5.1、Gradle 9.6.0、JDK/JBR 25、compileSdk/targetSdk 37、minSdk 24、Java 源代码级别 11。Android Studio 自带 JBR 可作 Gradle JDK。已验证本机 JBR 为 25.0.4。AndroidX 等依赖固定在 gradle/libs.versions.toml。

用 Android Studio 打开根目录，设置 SDK 并同步。local.properties 为本机 SDK 路径，不共享其绝对路径。命令行须确保 JAVA_HOME 指向 JDK 25。

```powershell
$env:JAVA_HOME = '<JDK 25 的安装目录>'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :feature:browse:assembleDebug
.\gradlew.bat :feature:registration:assembleDebug
.\gradlew.bat :feature:engagement:assembleDebug
python tools/check_architecture.py
```

调试 APK 在 app/build/outputs/apk/debug/app-debug.apk。可通过 Android Studio Run 安装到 API24 及以上设备。

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

初次构建需要访问 Gradle 和 Google Maven 官方仓库。本机首次 Wrapper 下载遇到证书链问题时，本次验证使用同版本的已有 Gradle 9.6.0 安装及缓存执行构建，随后联网补全 AAPT2。构建未关闭 TLS 校验。团队通常直接使用 Wrapper 或 Android Studio 即可。

## 当前功能与演示

- 首次进入可以公开浏览三条来源明确的本地样例。身份下拉框提供管理员、学生甲、学生乙，切换后 epoch 与当前用户持久保存。
- 管理员进入活动管理，新增活动，编辑标题、说明、地点、分类和来源。保存后返回并刷新可见。官方来源须填写有效 http(s) URL，手工来源可留空链接。
- 时间按北京时间输入 yyyy-MM-dd HH:mm，名额为正整数，报名截止不晚于开始，开始在未来且早于结束。
- 进入详情，A 宿主装入内容、报名、收藏提醒三个独立面板。B 的基础活动内容可显示，C/D 面板明确显示待接入。
- 修改容量需要 C 的人数端口，修改开始/结束需要 D 的提醒端口，取消需要 C/D 的事务端口。这些联动用例已实现，当前开发适配器返回 MODULE_NOT_READY 并回滚。
- 收藏、提醒、报名、正式时间轴、推荐和签到反馈由 B/C/D 继续实现。真实通知和最终完整流程等待对应模块接入。

预置数据仅在首次建库生成，时间相对首次初始化时刻的未来两至四天。后续重启保持原记录。日期过期后由管理员新增未来活动，初始化不会覆盖用户修改。

## 测试与验收

启动模拟器或连接设备后运行。

```powershell
.\gradlew.bat :core:platform:connectedDebugAndroidTest
.\gradlew.bat :feature:event:connectedDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :core:contracts:test
```

contracts 当前没有单元测试用例，test 任务可执行但为 NO-SOURCE。已有实际 SQLite 设备回归覆盖平台事务/身份/队列恢复及管理员权限、版本、容量、C/D 失败回滚。宿主集成测试覆盖身份选择、真实活动 API 和详情重建。测试结果与手动验证范围记录在 [A 交付验收](docs/A交付验收.md)。

库测试使用独立 UUID 测试数据库。宿主测试创建唯一活动并在结束后清理、恢复初始身份。业务替身仅位于 androidTest。

## BCD 接管

每人修改自己的 feature 和归属 contracts 子包，保持已有模块出口。正式实现就绪后与 A 核对 AppGraph 中的 UI 依赖注入，更新模块 isReady() 和 integrationReady=true，按协作约定提交验收记录。

```powershell
.\gradlew.bat :app:verifyFeatureIntegration
.\gradlew.bat :app:assembleRelease
```

当前 readiness 检查预期失败并列出 B/C/D，避免未接入适配器进入正式包。非调试应用还在启动装配时核对模块 isReady()。标记的真实性需要代码审查及设备验收。最终源码、签名安装包、使用说明和关键流程录屏由团队在全部必做功能完成后整合提交。
