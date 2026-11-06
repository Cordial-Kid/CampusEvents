# 北航活动通

安卓开发作业第四题，Java + XML + Fragment + SQLite，一个 APK，七个 Gradle 模块。当前交付 A 的公共底座、应用壳与活动维护实现，BCD 通过固定出口继续独立开发。

## 协作入口

1. [统一约定](docs/协作开发统一约定.md) 明确分工、不可跨越的边界、统一数据口径和验收规则。
2. [接口与接入说明](docs/interface-design.md) 给出实际包名、方法签名、线程和调用示例。
3. [数据库字典](docs/data-dictionary.md) 给出七张表及字段、所有者和迁移要求。
4. [原始架构设计](docs/architecture-design.md) 保留完整设计依据与联动流程，实施差异以当前统一约定和代码为准。

## 从 GitHub clone 后首次运行

协作者使用 Android Studio 按以下步骤完成首次配置。Gradle、AGP 和应用依赖会按仓库中的版本配置下载，本机准备好 Android SDK 和 JDK 25 后即可同步、运行。

### 1 打开整个项目

在 Android Studio 中选择 **Open**，打开 clone 得到的 `CampusEvents` 根目录。该目录包含 `settings.gradle.kts`、`gradlew.bat`、`app`、`core` 和 `feature`。首次打开时，允许 Android Studio 信任并导入该项目。

### 2 安装 Android SDK

打开 **Tools → SDK Manager**，确认已安装以下组件。

- **SDK Platforms** 中的 Android API 37 平台，用于编译项目。
- **SDK Tools** 中的 Android SDK Build-Tools 36.0.0，可勾选 Show Package Details 查找具体版本。
- **SDK Tools** 中的 Android SDK Platform-Tools，用于连接和安装到设备。
- 使用模拟器的协作者还需安装 Android Emulator，系统镜像在创建模拟器时下载。

Android Studio 通常会根据本机 SDK 位置生成根目录的 `local.properties`。这个文件保存每个人自己的 SDK 路径，已加入 Git 忽略规则。若出现 SDK location not found，使用 SDK Manager 中显示的 Android SDK Location 配置本机 SDK，随后重新同步。

### 3 确认构建使用 JDK 25

打开 **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**。macOS 从 Android Studio 的 Settings 进入同一页面。

仓库的 `gradle/gradle-daemon-jvm.properties` 已将 Gradle 构建进程固定为 Java 25。界面若显示 **Gradle Daemon JVM Criteria**，确认版本为 **25**，并按提示下载或选择匹配的 JDK。界面若显示 **Gradle JDK**，选择本机的 JDK 25，或使用 **Download JDK** 下载 25。自带 JBR 的版本为 25 时，也可选择该 JBR。

JDK 的安装目录由每个人在本机选择。项目中的 Java 11 表示业务代码采用的语言级别，运行构建工具使用的 JDK 为 25。Android Studio 的界面选项及两者的关系可参考官方说明。[Android 构建 JDK 配置](https://developer.android.com/build/jdks)

### 4 同步项目

点击 **Sync Project with Gradle Files**，等待同步完成。首次同步会下载仓库指定的 Gradle 9.6.0、AGP 9.5.1 和 AndroidX 等依赖，缺少匹配 JDK 时还可能下载 JDK。

首次同步需要能够访问 Gradle、Google Maven、Maven Central 及 JDK 下载服务。下载失败时先检查本机网络和 Android Studio 的 HTTP Proxy 设置，再重新同步。若提示当前 Android Studio 不支持 AGP 9.5，升级 Android Studio 后重新打开项目。

### 5 选择设备并运行

- 使用模拟器时，在 **Tools → Device Manager** 中创建并启动设备。可选 API 37 系统镜像，该版本已完成本项目测试。
- 使用手机时，打开开发者选项和 USB 调试，连接电脑并确认调试授权。应用支持 API 24 及以上的设备。
- 在顶部运行配置中选择 **app**，选择设备，然后点击绿色 **Run ▶**。

成功后会进入“北航活动通”，可以浏览三条演示活动，并通过下拉框选择管理员或学生身份。管理员可进入活动管理。当前 C、D 功能面板显示待接入，具体范围见下文“当前功能与演示”。

### 首次配置完成的检查清单

- [ ] Android Studio 的项目视图中能看到七个模块。
- [ ] Gradle 同步成功。
- [ ] Run 后设备上能打开北航活动通。
- [ ] 能选择管理员身份并进入活动管理。

## 命令行构建与开发检查

以下命令供使用终端构建、检查模块的协作者使用。前文的 Android Studio 操作可以直接完成应用构建与运行。

Windows PowerShell 在仓库根目录执行以下命令。先将 `JAVA_HOME` 设置为本机实际的 JDK 25 安装目录，再运行仓库自带的 Wrapper。

```powershell
$env:JAVA_HOME = '<替换为本机 JDK 25 安装目录>'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```

上面的路径占位符需要替换，目录下应存在 `bin/java.exe`。`JAVA_HOME` 为命令行启动提供 JDK，最终构建进程仍按仓库中的 Java 25 条件选择 JDK。[Gradle 构建进程 JDK 选择](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:daemon_jvm_criteria)

macOS/Linux 在设置本机 JDK 25 的 `JAVA_HOME` 后，使用 `./gradlew :app:assembleDebug`。若出现脚本执行权限错误，先执行 `chmod +x gradlew`。

调试 APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。Android Studio 的 Run 会自动完成安装，使用终端安装时执行 `adb install -r app/build/outputs/apk/debug/app-debug.apk`，其中 adb 来自 Android SDK Platform-Tools。

各功能可分别检查编译结果。

```powershell
.\gradlew.bat :feature:browse:assembleDebug
.\gradlew.bat :feature:registration:assembleDebug
.\gradlew.bat :feature:engagement:assembleDebug
```

架构边界检查单独使用 Python 3，供开发自检时执行。

```powershell
python tools/check_architecture.py
```

### 项目固定版本的含义

| 项目 | 当前值 | 协作者需要做的事 |
| --- | --- | --- |
| Gradle | 9.6.0 | 通过仓库中的 Wrapper 或 Android Studio 同步获取 |
| Android Gradle Plugin | 9.5.1 | 根据 `gradle/libs.versions.toml` 自动下载 |
| 构建 JDK | 25 | 在本机选择或下载匹配版本 |
| compileSdk / targetSdk | 37 | 安装 API 37 的编译平台 |
| minSdk | 24 | 运行设备的 API 级别至少为 24 |
| Java 源代码级别 | 11 | 编写业务代码时沿用该语言级别 |

AGP 9.5 的默认 Build-Tools 版本为 36.0.0，SDK 平台与 Build-Tools 分别安装。[AGP 9.5 兼容性说明](https://developer.android.com/build/releases/agp-9-5-0-release-notes)

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
