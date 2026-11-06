# 北航活动通接口与接入说明

版本 v1.0，日期 2026-11-06。本文对应已落地 Java 代码，统一业务规则见 [协作约定](协作开发统一约定.md)。包根为 com.example.campusevents。

## 1 接入出口与依赖

| 所有者 | 模块出口 | 创建参数与产物 |
| --- | --- | --- |
| A | event.EventModule | new EventModule(DataAccess)，readApi()、readPort()；createAdmin(RegistrationEventPort,ReminderEventPort)；静态 schema() |
| B | browse.BrowseModule | DataAccess、EventReadPort、RegistrationReadPort、EngagementReadPort；api() |
| C | registration.RegistrationModule | DataAccess、EventReadPort；api()、readPort()、eventPort()；静态 schema() |
| D | engagement.EngagementModule | DataAccess、EventReadPort、NotificationRouteFactory；favoriteApi()、reminderApi()、readPort()、eventPort()、effectHandler()；静态 schema() |
| A 平台 | platform.Platform | Context、List<SchemaContributor>；dataAccess()、sessionApi()、invalidation()、registerEffectHandler()、start()、retryEffects()、close() |

构造阶段只装配依赖。AppGraph 注册 Schema、全部 UI 和 EffectHandler 后再调用 platform.start()。首次真实接入由 A 核对 app/bootstrap/AppGraph，日常功能开发保持在自己的 feature 内。

UI 出口为 event.ui.EventUiEntry(readApi,adminApi,navigator)、browse.BrowseUiEntry(api,invalidation,navigator)、registration.RegistrationUiEntry()、engagement.EngagementUiEntry()。C、D 接管实现时可将自己的 API、Navigator、失效通知通过 UI 构造器注入，首次调整由 A 修改对应装配行。

## 2 公共异步协议

所有以下业务接口位于 core/contracts/src/main/java/com/example/campusevents/contract。API 返回 AsyncCall，调用 enqueue 才执行。接口实例可重复使用，单个 AsyncCall 只能 enqueue 一次。

```java
// contract.common
interface AsyncCall<T> { CallHandle enqueue(ResultCallback<T> callback); }
interface ResultCallback<T> { void onResult(ApiResult<T> result); }
interface CallHandle { void cancelDelivery(); }
```

ApiResult 的 public final 字段为 success、code、message、data。code 使用 ResultCode，仅 OK 表示成功，失败 data=null。工厂为 ApiResult.ok(data)、ApiResult.failure(code,message)。空列表是成功结果。

```java
CallHandle request = eventReadApi.get(eventId).enqueue(result -> {
    if (result.success) {
        render(result.data); // 主线程
    } else {
        showFailure(result.code, result.message);
    }
});
// Fragment.onDestroyView
request.cancelDelivery();
```

上例 render/showFailure 是页面自己的显示方法。SESSION_CHANGED 应触发重新查询，STALE_DATA 应提示重新读取表单，MODULE_NOT_READY 显示待接入。取消交付不撤销已经开始的事务。

## 3 业务 API 的已确定签名

```java
// contract.session
interface SessionApi {
    AsyncCall<List<UserSnapshot>> listDemoUsers();
    AsyncCall<SessionSnapshot> current();
    AsyncCall<SessionSnapshot> switchDemoUser(long userId);
}
// contract.event
interface EventReadApi {
    AsyncCall<List<EventRecord>> list(EventFilter filter);
    AsyncCall<EventRecord> get(long eventId);
}
interface EventAdminApi {
    AsyncCall<EventRecord> create(EventInput input);
    AsyncCall<EventRecord> update(long eventId, long expectedVersion, EventInput input);
    AsyncCall<EventRecord> cancel(long eventId, long expectedVersion);
}
// contract.browse
interface EventBrowseApi {
    AsyncCall<List<EventView>> list(EventFilter filter);
    AsyncCall<EventView> detail(long eventId);
}
// contract.registration
interface RegistrationApi {
    AsyncCall<RegistrationSummary> getSummary(long eventId);
    AsyncCall<RegistrationSummary> register(long eventId);
    AsyncCall<RegistrationSummary> cancelRegistration(long eventId);
    AsyncCall<List<RegistrationItem>> listMine();
}
// contract.engagement
interface FavoriteApi {
    AsyncCall<Boolean> setFavorite(long eventId, boolean desiredState);
    AsyncCall<List<FavoriteItem>> listMine();
}
interface ReminderApi {
    AsyncCall<ReminderView> get(long eventId);
    AsyncCall<ReminderView> set(long eventId, int leadMinutes);
    AsyncCall<ReminderView> disable(long eventId);
}
```

SessionApi 和 EventReadApi 可在未选择身份时调用。EventAdminApi 限管理员。C/D 的个人查询及写操作应检查学生身份，未登录返回 NOT_LOGGED_IN，管理员使用学生专属操作返回 FORBIDDEN。此校验由 C、D 实现。

## 4 DTO 字段与构造器

DTO 使用 public final 字段，构造器参数与下表字段顺序一致。具体 Java 类型以同名源文件为准。

| 类型 | 字段顺序 |
| --- | --- |
| UserSnapshot | userId、displayName、role |
| SessionSnapshot | user（可空）、epoch |
| EventInput | title、description、location、startAt、endAt、registrationDeadline、capacity、category、sourceType、sourceName、sourceUrl |
| EventRecord | id，随后 EventInput 的全部字段，status、createdBy、createdAt、updatedAt、version |
| EventFilter | startAtFrom、startAtUntil（均 Long 可空）、category、keyword、includeCancelled |
| RegistrationSummary | activeCount、remainingCapacity、myStatus、cancelReason、canRegister、rejectionCode、canCancel |
| RegistrationItem | id、event、status、cancelReason、registeredAt、cancelledAt（Long 可空） |
| ReminderView | configured、reminderId、leadMinutes、triggerAt、status、deliveryState、deliveryError |
| EngagementSummary | favorite、reminder |
| FavoriteItem | event、favoritedAt |
| EventView | event、evaluatedAt、registration、engagement |

new EventFilter() 表示全量、含取消。时间按活动 startAt 左闭右开筛选，排序 startAt、id 升序。分类精确匹配，keyword 匹配标题或地点的字面子串，百分号和下划线不作为 SQL 通配符。filter=null 也视为默认筛选。

EventView 的 registration 与 engagement 使用 ModuleValue。READY 必须携带真实非空数据，NOT_READY 携带 null。端口批量 Map 必须覆盖全部传入活动 ID，漏项是 INTERNAL_ERROR。实际无记录的摘要与 NOT_READY 分开处理。

ReminderView.unconfigured() 返回 configured=false 且记录字段为空。RegistrationStatus.NONE 只用于无报名记录的查询状态，不入表。DTO 及集合不持有可变 DAO 状态，生产方返回不可变 List/Map 副本。

## 5 内部同步端口

下列接口位于 platform.port，由所有者实现，app 注入给使用者。

```java
interface EventReadPort {
    EventRecord require(ReadScope scope, long eventId);
    List<EventRecord> list(ReadScope scope, EventFilter filter);
}
interface RegistrationReadPort {
    ModuleValue<Map<Long, RegistrationSummary>> summaries(ReadScope scope, List<EventRecord> events);
}
interface RegistrationEventPort {
    int countActive(TxScope tx, long eventId);
    void cancelActiveForEvent(TxScope tx, long eventId);
}
interface EngagementReadPort {
    ModuleValue<Map<Long, EngagementSummary>> summaries(ReadScope scope, List<EventRecord> events);
}
interface ReminderEventPort {
    void onEventTimeChanged(TxScope tx, EventRecord before, EventRecord after);
    void disablePendingForEvent(TxScope tx, long eventId);
}
```

EventReadPort.require 未找到活动抛 BusinessException(EVENT_NOT_FOUND,message)。业务失败使用 platform.data.BusinessException(ResultCode,String)，其他数据库异常交给最外层转换并回滚。端口不捕获异常后伪装成功。

## 6 数据执行范围和刷新

```java
// platform.data
interface DataAccess {
    <T> AsyncCall<T> read(ReadWork<T> work);
    <T> AsyncCall<T> write(WriteWork<T> work);
}
interface ReadWork<T> { T run(ReadScope scope); }
interface WriteWork<T> { T run(TxScope tx); }
```

ReadScope 提供 db()、session()、actor()、sessionEpoch()、now()、hasPendingEffect(EffectKey)。TxScope 继承 ReadScope，增加 invalidate(ChangeTopic,long eventId)、invalidate(ChangeSet)、requestEffect(EffectKey)。范围不允许创建、保留或跨线程使用，db() 仅交给 data 层。

C 写入的示意如下，RegistrationStore 是 C 自己实现的模块内类。

```java
return data.write(tx -> {
    EventRecord event = eventPort.require(tx, eventId);
    // 在同一范围检查学生身份、活动状态/截止/开始/名额/重复。
    RegistrationSummary after = registrationStore.register(tx, event);
    tx.invalidate(ChangeTopic.REGISTRATION, eventId);
    return after;
});
```

B 的 BrowseModule 已提供一次 read 内批量调用三个只读端口的示例。禁止在 scope 内调用异步 API 并阻塞等待。写事务失败不会发布已登记的失效通知或保留队列更新。

platform.invalidation.DataInvalidation.subscribe(Listener) 返回 Subscription，onDestroyView 调用 unsubscribe()。ChangeSet 有 topic、eventId，0 表示全局。主题为 EVENT_CONTENT、REGISTRATION、ENGAGEMENT、REMINDER_DELIVERY、SESSION，回调在主线程。仅在提交后发送失效信号，监听方重新查询。

## 7 Schema 贡献和迁移

```java
// platform.db
interface SchemaContributor {
    String id();
    void create(SQLiteDatabase db);
    default void seed(SQLiteDatabase db, long now) { }
    default void migrate(SQLiteDatabase db, int oldVersion, int newVersion) { /* 需显式实现 */ }
}
```

注册顺序在 AppGraph，核心 users/app_session/system_effects 由平台先建立，再调用 EventModule、RegistrationModule、EngagementModule 的 schema。seed 仅首次建库调用。默认 migrate 会拒绝未实现的升级。数据库升级时 A 提升 CampusDatabase.VERSION，所有者补充对应迁移，即使该模块无字段变化也需明确确认版本过渡。

## 8 UI 和通知入口

```java
// platform.ui
interface FeatureUiEntry {
    Set<ScreenKey> screens();
    Fragment createScreen(ScreenKey key, RouteArgs args);
    Set<String> fragmentClassNames();
    Fragment instantiate(String fragmentClassName);
}
interface DetailPanelProvider {
    DetailSlot slot();
    Fragment createPanel(long eventId);
}
interface Navigator { void open(Route route); void back(); }
interface NotificationRouteFactory {
    PendingIntent eventDetail(long eventId, long recipientUserId);
}
```

UiRegistry.register(FeatureUiEntry)、register(DetailPanelProvider) 注册后调用 validateComplete() 冻结。重复注册、缺屏幕、缺面板或输出未登记恢复工厂的 Fragment 会报错。

Route.to(ScreenKey) 用于无参数屏幕，Route.event(ScreenKey,eventId) 用于详情及编辑。ScreenKey 包含 EVENT_LIST、EVENT_DETAIL、MY_REGISTRATIONS、MY_FAVORITES、ADMIN_LIST、ADMIN_CREATE、ADMIN_EDIT。DetailSlot 包含 CONTENT、REGISTRATION、ENGAGEMENT。读取参数统一使用 RouteArgs.fromBundle(getArguments()).eventId 或 RouteArgs.EVENT_ID。

```java
navigator.open(Route.event(ScreenKey.EVENT_DETAIL, eventId));
// app 中，在 super.onCreate(savedInstanceState) 之前
getSupportFragmentManager().setFragmentFactory(ui.fragmentFactory());
```

每个 Fragment 类在 fragmentClassNames 中登记，instantiate 注入与首次创建相同的依赖，系统恢复 arguments。详情宿主只在未恢复已有面板时创建三块内容。

D 通过 NotificationRouteFactory 取得显式、不可变 PendingIntent，稳定 URI 包含 eventId 与 userId，FLAG_UPDATE_CURRENT 更新已有入口。D 自己控制通知 permission、channel、notificationId 与调度 receiver。

## 9 副作用协议

```java
// platform.effect
interface EffectHandler {
    EffectKey key();
    EffectOutcome reconcile(EffectContext context);
}
interface EffectContext {
    long requestedVersion();
    <T> T read(ReadWork<T> work);
    <T> T write(WriteWork<T> work);
}
```

目前 EffectKey 只有 REMINDER_RECONCILE。结果为 APPLIED、RETRY_LATER、MODULE_NOT_READY。D 使用 context.read/write 同步执行，避免重新排队死锁。系统操作安排在这些事务之外，再用 context.write 记录 deliveryState/error 并发布 REMINDER_DELIVERY。处理器返回 APPLIED 后平台才确认捕获版本。

D 的 set API 要将意图写入、队列同步和读取本次调度结果组成一个异步用例。DataAccess.write 在回调前尝试处理任务，但返回的业务 DTO 若在同步前构造会过时，D 应在同步后再查询状态并转换成实际成功或错误结果。callback 聚合仍遵守 epoch、取消交付和恰好一次交付规则。

## 10 错误码与当前接入状态

完整 ResultCode 定义位于 contract/common/ResultCode.java。除 OK 外包含 NOT_LOGGED_IN、FORBIDDEN、EVENT_NOT_FOUND、INVALID_INPUT、EVENT_CANCELLED、EVENT_STARTED、REGISTRATION_CLOSED、EVENT_FULL、ALREADY_REGISTERED、CAPACITY_TOO_SMALL、REMINDER_TIME_PASSED、REMINDER_UNAVAILABLE、REMINDER_SCHEDULE_FAILED、DATABASE_ERROR、MODULE_NOT_READY、SESSION_CHANGED、STALE_DATA、INTERNAL_ERROR。

A 的会话、活动读写、UI、事务与队列均为真实实现。B 的基础读路径已经接通，正式时间轴/推荐由 B 完善。C/D 的 API 和联动端口当前是明确未接入的开发适配器。它们保留所需 Schema、UI 和出口，接管后按本文签名替换实现。容量/时间/取消联动的生产验证须等真实端口接入后完成。

## 11 Android 机制依据

Fragment 恢复工厂须在 Activity.super.onCreate 之前安装，工程的宿主按此配置。[Android FragmentManager 官方文档](https://developer.android.com/guide/fragments/fragmentmanager)

本工程沿用 AGP 9.5.1 与 API37，具体工具版本以版本目录和实际构建结果为准。[Android Gradle Plugin 9.5 官方说明](https://developer.android.com/build/releases/agp-9-5-0-release-notes)
