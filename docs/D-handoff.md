# D：收藏与提醒交付

日期：2026-11-19。实现范围为 `feature/engagement`、D 自有 Java/XML 资源与 D 的 Android 测试。公共契约签名未改动；`app/bootstrap/AppGraph` 仅把 D 的真实工厂、UI 入口和重同步保存操作接入现有装配位置。

## 题目对应

| 要求 | 实现与入口 |
| --- | --- |
| 收藏、取消收藏 | 活动详情 `ENGAGEMENT` 面板的目标状态按钮；`FavoriteApi.setFavorite(eventId, desiredState)` |
| 我的收藏 | 顶部“我的收藏”；`MY_FAVORITES` 屏幕和 `FavoriteApi.listMine()` |
| 设置、关闭提醒 | 详情面板的 5/15/30/60 分钟快捷设置、关闭按钮；`ReminderApi.set/get/disable` |
| 真实系统通知 | `AlarmManager` + `ReminderAlarmReceiver` + `NotificationManager`；通知点击使用宿主提供的 `NotificationRouteFactory` |
| 调度和恢复 | 持久化 `REMINDER_RECONCILE` 副作用、启动/重启/权限变化/身份切换重同步、稳定 `scheduleId` 覆盖旧调度 |
| 活动变更端口 | `onEventTimeChanged` 重算待提醒并递增 revision；`disablePendingForEvent` 关闭活动待提醒 |

## 业务规则

- 收藏和提醒是学生个人操作。未登录返回 `NOT_LOGGED_IN`，管理员返回 `FORBIDDEN`。
- 收藏使用目标状态而不是切换：重复收藏保留原收藏时间，重复取消幂等；取消活动仍保留收藏并显示活动最新状态。
- 提醒提前分钟数必须为正整数。计算使用 `long`；`triggerAt <= now` 返回 `REMINDER_TIME_PASSED`，失败请求不覆盖原提醒。
- 活动不存在、已取消、已开始时拒绝设置提醒。关闭不存在的提醒返回未配置的成功结果。
- `reminders.status` 表示业务意图，`deliveryState` 表示系统同步结果。调度成功不递增 revision，意图或触发规则变化才递增。
- 活动改期只处理 `PENDING` 提醒：重算 `triggerAt`；新触发时间已过则转为 `EXPIRED`，其余保持 `PENDING` 并等待重新调度。
- 活动取消将待提醒改为 `DISABLED`，保留历史记录和收藏。

## 系统接入

- Manifest 声明 `POST_NOTIFICATIONS`、`SCHEDULE_EXACT_ALARM` 和 `RECEIVE_BOOT_COMPLETED`。
- `AndroidEngagementSystem` 使用 `setExactAndAllowWhileIdle`。通知权限、通知渠道或精确闹钟权限不可用时，提醒标记为 `BLOCKED/REMINDER_UNAVAILABLE`，不伪装成已生效。
- 系统调度抛出异常时标记为 `FAILED/REMINDER_SCHEDULE_FAILED`，保留用户意图并允许显式重试。
- `ReminderAlarmReceiver` 在触发时重新校验用户、session epoch、reminderId、revision、triggerAt、提醒状态和最新活动状态；校验通过后提交通知并把提醒记为 `SENT`。
- `ReminderRestoreReceiver` 只接受 `BOOT_COMPLETED`、`MY_PACKAGE_REPLACED` 和精确闹钟权限变化广播，再唤醒重同步。
- `EngagementRuntime` 仅作为 Manifest Receiver 到 D 模块的窄桥接，不向其他功能暴露应用装配对象。
- 详情面板会在通知权限或精确闹钟权限从未授权变为已授权时重试同步，也提供显式“重试同步”按钮。
- 同一时刻只为当前演示学生安排系统提醒；切换身份会取消旧身份调度，并恢复新身份的有效提醒。

## 模块出口

```java
EngagementModule module = new EngagementModule(
        context, dataAccess, eventReadPort, notificationRouteFactory);

module.favoriteApi();
module.reminderApi();
module.readPort();
module.eventPort();
module.effectHandler();
module.requestReconcile();
module.isReady(); // true
```

UI 入口：

```java
new EngagementUiEntry(
        engagement.favoriteApi(),
        engagement.reminderApi(),
        engagement::requestReconcile,
        platform.invalidation(),
        navigator);
```

`EngagementUiEntry` 注册 `MY_FAVORITES` 和 `DetailSlot.ENGAGEMENT`，并登记 `EngagementFragment` 的重建工厂。详情面板和“我的收藏”共用同一 Fragment，以 `eventId=0` 区分。

## 数据

继续使用现有 `favorites`、`reminders` 表，数据库总版本保持 2，没有公共字段或总版本变更。

- `favorites` 使用 `(userId,eventId)` 唯一约束。
- `reminders` 使用 `(userId,eventId)` 唯一约束和稳定唯一 `scheduleId`。
- `scheduleId` 在创建时分配，更新提醒沿用；耗尽时返回可说明的调度失败，不截断 long 主键。
- v1→v2 迁移继续显式接受 D 表无变化。

## 已运行验证

环境：Windows、JDK 25、Gradle 9.6.0、AGP 9.5.1、Android API 37 模拟器 `Medium_Phone_API_37.0`。

| 检查 | 结果 |
| --- | --- |
| `python tools/check_architecture.py` | 通过，七模块边界检查无异常 |
| `:feature:engagement:testDebugUnitTest` | 4 项纯规则测试通过 |
| `:feature:engagement:connectedDebugAndroidTest` | 7 项通过：6 项真实 SQLite/恢复场景 + 1 项真实 AlarmManager/NotificationManager 通知 |
| `:app:connectedDebugAndroidTest` | 5 项通过，宿主与详情重建未被 D 接入破坏 |
| `:app:assembleDebug` | 通过，生成可安装 Debug APK |
| `:feature:engagement:lintDebug` | 0 error、0 warning |
| `:app:verifyFeatureIntegration` | 仍按预期失败，阻塞模块仅剩 `:feature:browse`，不再包含 D |

真实系统测试验证了：提醒设置后指定时刻触发、系统通知实际出现在通知栏、提醒随后变为 `SENT`。SQLite 测试验证了收藏幂等和用户隔离、提醒设置等待调度结果、权限阻止与显式重试、活动改期、活动取消保留收藏、旧 revision 闹钟失效、进程重启后重同步。

## 尚待设备验收

- 本次使用 API 37 模拟器，未在实体手机的厂商后台限制、省电策略和真实重启流程下复测。
- 已测试进程级数据库关闭/重开恢复；系统 `BOOT_COMPLETED` 真正重启后的通知补排仍需最终设备演练。
- 通知点击后由 A 的宿主校验当前身份；身份不符时要求重新选择身份的场景需在完整 APK 手动验收中录屏。
- 正式 APK 仍被 B 的 `integrationReady=false` 阻止，这是 B 的既有发布门槛，不是 D 未接入。
