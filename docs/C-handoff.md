# C：报名管理、签到反馈交付

本次实现位于 feature/registration、contracts/registration。AppGraph 仅增加 C UI 的构造注入；数据库总版本升级为 2，A/event 与 D/engagement 显式声明 1→2 不修改自己表的迁移。其他功能实现保持原状。

## 题目对应

| 要求 | 实现与入口 |
| --- | --- |
| 报名、取消 | 活动详情中的「报名参加」「取消报名」 |
| 人数统计和名额限制 | 详情显示有效人数与剩余名额；RegistrationReadPort 提供 B 所需摘要；RegistrationEventPort.countActive 提供 A 的容量校验 |
| 防重复、满额拦截 | 同一平台串行写事务内重新读取活动、人数和本人记录；数据库 UNIQUE(userId,eventId) 兜底 |
| 我的报名 | 顶部「我的报名」展示本人有效与取消记录，可进入活动详情 |
| 签到、简短反馈 | 详情「活动签到」、1–5 分及 1–500 字反馈；签到时间和反馈持久保存 |
| 联调与材料 | 本文验收步骤、SQLite 一致性脚本、两个 Android 测试类 |

## 规则

报名与个人操作只允许学生，会话身份由平台提供，调用者不能传 userId。公开摘要允许访客和管理员查看人数，个人状态为 NONE，报名按钮不可用。时间使用平台 scope.now()，以北京时间显示。

活动取消、开始、报名截止、已报名、满额按顺序判断资格；now 等于截止时间即关闭，now 等于开始时间不可报名或取消。报名截止后至活动开始前可以取消。不存在或已经取消的报名，取消操作幂等成功。取消后重报复用原记录，更新报名时间并清空取消原因/时间，重新检查截止与名额。

活动取消端口只更新 C 自己的 registrations，不提交外层事务。有效记录转为 CANCELLED/EVENT_CANCELLED；原本主动取消的历史保留 USER_CANCELLED。每次成功更改登记 REGISTRATION 失效通知，外层失败整笔回滚，B 与 C 收到通知后重查。

签到要求有效报名、活动 ACTIVE 且 startAt <= now < endAt。重复签到保留第一次时间。签到前、未报名、取消报名、已取消活动不能新签到或提交反馈。签到后允许提交/修改评分和反馈，活动结束后仍可反馈。此签到为本地课程演示签到，不验证定位或二维码。

反馈表单保留旋转草稿及评分，草稿带所属用户 ID，切换身份时清除；ViewModel 保留正在执行的写操作，旋转不会重新提交。页面销毁取消读结果交付并解除监听，返回和主动刷新查询真实数据库。进程被杀后重新读取已提交状态，不自动重放操作。

## 接口与装配

原 RegistrationApi / RegistrationSummary / RegistrationItem / RegistrationReadPort / RegistrationEventPort 签名保持兼容。新增纯 Java AttendanceApi.get/checkIn/submitFeedback 和 AttendanceView(userId,checkedInAt,rating,feedback,canCheckIn,canFeedback,message)。签到资格失败和评分/文本校验使用 INVALID_INPUT，附具体中文消息。

```java
RegistrationModule registrations = new RegistrationModule(data, events.readPort());
new RegistrationUiEntry(registrations.api(), registrations.attendanceApi(), invalidation, navigator);
```

RegistrationUiEntry 同时注册 MY_REGISTRATIONS 和详情 REGISTRATION 面板，并登记 RegistrationFragment 的恢复工厂。模块 isReady() 与 integrationReady 均已设为 true，表示生产实现已接入；并不表示设备验收已经通过。

## 数据库升级

版本 1→2 保留所有现有表和数据，仅新增 reg_attendance。其 registrationId 为主键且外键指向 registrations.id；每个报名至多一条签到反馈，checkedInAt 必填；rating 可空或 1–5；feedback 最多 500 字；submittedAt 与评分同时存在。未签到不建记录。未反馈时 rating/submittedAt 为空、feedback 为空。业务层进一步拒绝空白反馈。

全局 CampusDatabase.VERSION=2；RegistrationModule.schema 同时支持新建与迁移；EventSchema 与 EngagementModule.schema 显式接受 1→2 空迁移。没有删库、重建既有表或重复初始化。其他并行分支若也修改总版本，需要由 A 合并迁移链，不能直接覆盖。

## 已运行验证（本机）

- `python tools/check_architecture.py`：通过，七个模块依赖/SQL 所有权边界检查。
- `python tools/check_registration_schema.py`：6 个真实 SQLite 测试全部通过，读取生产 DDL，验证防重、取消状态约束、签到唯一与外键、评分/长度约束、回滚、v1 数据保留。
- 补充 Java 编译：用本机 Android API 35 android.jar、项目对应 AndroidX 依赖和临时资源 R 常量，编译 contracts、platform、C 全部生产与设备测试代码，以及 A/D 两处 schema 迁移，成功。Kotlin 注解依赖缺失产生警告。该检查不是 API 37 的 Gradle/APK 构建。
- `git diff --check`：通过。

完整 Gradle 构建尚未完成：本机只有 API 35 平台，无 API 37 / Build-Tools / adb / JDK 25。Wrapper 首次下载还遇到 Java 证书信任失败；使用进程级 Windows-ROOT 信任后进入下载，但下载未完成。没有关闭 TLS 证书验证，也没有修改项目固定工具版本或全局 Java 设置。尚未生成 APK、运行模拟器/真机或录制演示。

## Android 自动化验收（已编写，待运行）

```powershell
.\gradlew.bat :feature:registration:assembleDebug :app:assembleDebug
.\gradlew.bat :feature:registration:connectedDebugAndroidTest
.\gradlew.bat :core:platform:connectedDebugAndroidTest :feature:event:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

RegistrationRegressionTest：10 个真实 SQLite 场景，用测试 DataAccess 控制精确时刻，调用 C 的生产 API/端口。覆盖防重满额、取消重报、截止/开始/结束边界、个人身份、取消回滚、签到/反馈、v1→v2 和数据库唯一约束。

RegistrationQueueTest：通过真实 SerialDataAccess 提交 8 次快速报名，预期 1 次成功、7 次 ALREADY_REGISTERED；切换第二位学生后 EVENT_FULL；关闭重开 SQLite 后人数及本人历史保留。测试仅操作库测试 APK 的独立数据目录，不应改成在正式用户数据库运行。

## 手动验收用例与一致性检查（待设备验证）

| 编号 | 操作 | 预期 |
| --- | --- | --- |
| C01 | 管理员新建未来活动，名额 1；学生甲进入详情报名 | 人数 1、余量 0、本人已报名 |
| C02 | 连续点击或通过 API 再次报名 | ALREADY_REGISTERED，表中仍为一行 |
| C03 | 学生乙报名同一活动 | EVENT_FULL，本人未报名，人数不增加 |
| C04 | 学生甲取消两次 | 取消成功且幂等，人数 0、余量 1 |
| C05 | 学生甲在截止前重报 | 同一报名 ID，状态恢复，取消字段清空 |
| C06 | 截止后、开始前尝试报名及取消原报名 | 新报名拒绝，已有报名允许取消 |
| C07 | 活动开始后取消报名 | EVENT_STARTED，人数和记录不变 |
| C08 | 两个学生分别查看我的报名；管理员和访客尝试个人操作 | 数据隔离；分别 FORBIDDEN / NOT_LOGGED_IN |
| C09 | 开始前、开始时、结束时签到 | 分别拒绝、成功、关闭；重复签到时间不变 |
| C10 | 未签到提交反馈；签到后提交评分 0/6、空白、501 字 | 均拒绝，无非法数据写入 |
| C11 | 签到后提交和修改有效反馈，退出重进或重启 | 评分、文本、签到时间保留；他人不可见 |
| C12 | 报名后管理员将容量降到低于有效人数 | CAPACITY_TOO_SMALL，活动和人数均保持原状 |
| C13 | A 的活动取消事务成功调用 C/D | 活动 CANCELLED；有效报名原因 EVENT_CANCELLED；人数归零 |
| C14 | 在 C 更新后注入 D 失败 | 活动、报名、提醒和副作用队列全回滚；无提交失效信号 |
| C15 | 报名提交中旋转、反馈草稿中旋转、切换身份、返回页面 | 不重复写入；草稿保留且不跨用户；页面显示数据库最新状态 |
| C16 | 安装 v1 创建报名，再覆盖安装 v2（不清除数据） | 既有活动和报名保留，签到反馈可使用 |

数据一致性检查重点：有效人数等于 `registrations.status='REGISTERED'` 的行数，余量=max(0,capacity-人数)，同学生同活动只有一行；取消记录必须有非 NONE 原因及 cancelledAt；REGISTERED 的原因必须 NONE 且 cancelledAt 为空；签到必须有对应报名，每个报名一条签到，外键检查为空。可用 Android Studio Database Inspector 查看，完整设备用例完成后由 C 填写实测记录并向 A 交接。

## 当前联调限制

D 的 eventPort 仍返回 MODULE_NOT_READY。因此应用中「管理员取消活动」会整体回滚，这是已有平台的正确保护，不能把 C 的报名单独取消后向用户显示活动取消成功。C 的真实取消端口和失败回滚已提供测试，完整管理员取消链路等待 D 接通。更改活动时间同样需要 D；只改容量已可调用 C 的真实人数端口。

设备验收、完整 APK 和录屏仍需上述构建环境。不要把本次静态/主机 SQLite 检查写成设备通过。
