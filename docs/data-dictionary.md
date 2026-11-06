# 北航活动通数据库字典

版本 v1.0，日期 2026-11-06。数据库 campus_events.db，当前总版本 1，外键启用。字段名保留 camelCase，与 SQLite 建表代码一致。主键 long，时间为 Unix 毫秒。SQLite DDL 是落库的直接依据，字段或状态变化同时更新本文并安排迁移。

## 核心表

| 表 | 字段 | 约束与归属 |
| --- | --- | --- |
| users | id、displayName、role | id 正数主键，role ADMIN/STUDENT，A/platform |
| app_session | singletonId、currentUserId、epoch | 单行 singletonId=1，currentUserId 可空外键 users.id，epoch>=0，A/platform |
| system_effects | effectKey、requestedVersion、appliedVersion、attempts、lastError、updatedAt | effectKey 主键，requestedVersion>=appliedVersion，默认版本/attempts为0、lastError为空，A/platform |

用户固定 1 管理员、2 学生甲、3 学生乙。初始会话为 null/epoch=0。每次实际切换递增 epoch。system_effects 目前固定键 REMINDER_RECONCILE，记录长期保留，任务成功更新 appliedVersion。

## events 归 A

| 字段 | 类型与约束 |
| --- | --- |
| id | INTEGER PRIMARY KEY AUTOINCREMENT |
| title | TEXT NOT NULL，去空白后非空，业务层最多100字符 |
| description | TEXT NOT NULL，业务层非空，最多10000字符 |
| location | TEXT NOT NULL，去空白后非空，最多200字符 |
| startAt、endAt、registrationDeadline | INTEGER NOT NULL，registrationDeadline<=startAt<endAt |
| capacity | INTEGER NOT NULL >0 |
| category | TEXT NOT NULL，业务层非空，最多50字符，初始TECH/SPORT/ACADEMIC |
| sourceType | TEXT NOT NULL，OFFICIAL/MANUAL |
| sourceName | TEXT NOT NULL，业务层非空，最多200字符 |
| sourceUrl | TEXT NOT NULL，空串表示未提供，最多2000字符 |
| status | TEXT NOT NULL，ACTIVE/CANCELLED |
| createdBy | INTEGER NOT NULL，外键 users.id |
| createdAt、updatedAt | INTEGER NOT NULL |
| version | INTEGER NOT NULL >0，创建为1，成功编辑/首次取消加1 |

索引 event_start(startAt,id)。查询包含已取消活动，默认开始时间和 ID 升序。三条初始活动在首次建库时生成，MANUAL 来源名称为课程作业演示数据。后续启动不重新插入。

## registrations 归 C

| 字段 | 类型与约束 |
| --- | --- |
| id | INTEGER PRIMARY KEY AUTOINCREMENT |
| userId、eventId | INTEGER NOT NULL，外键 users.id/events.id |
| status | TEXT NOT NULL，REGISTERED/CANCELLED |
| cancelReason | TEXT NOT NULL，默认NONE，NONE/USER_CANCELLED/EVENT_CANCELLED |
| registeredAt | INTEGER NOT NULL |
| cancelledAt | INTEGER，可空 |

UNIQUE(userId,eventId)，索引 reg_event_status(eventId,status)。REGISTERED 要求 cancelReason=NONE 且 cancelledAt=null，CANCELLED 要求非 NONE 原因且 cancelledAt 非空。报名人数仅 COUNT(status='REGISTERED')。查询无记录用 RegistrationStatus.NONE，不插入该状态。取消后重报更新原记录，registeredAt 更新为本次报名时刻。

## favorites 归 D

字段 id INTEGER PRIMARY KEY AUTOINCREMENT、userId INTEGER NOT NULL REFERENCES users(id)、eventId INTEGER NOT NULL REFERENCES events(id)、createdAt INTEGER NOT NULL。UNIQUE(userId,eventId)。setFavorite(false) 删除收藏，true 时已存在则保留原收藏时间。FavoriteItem.favoritedAt 映射 createdAt。

## reminders 归 D

| 字段 | 类型与约束 |
| --- | --- |
| id | INTEGER PRIMARY KEY AUTOINCREMENT |
| userId、eventId | INTEGER NOT NULL，外键 users.id/events.id |
| leadMinutes | INTEGER NOT NULL >0 |
| triggerAt | INTEGER NOT NULL，业务上 startAt-leadMinutes*60000，计算使用long并检查范围 |
| status | TEXT NOT NULL，PENDING/DISABLED/SENT/EXPIRED |
| revision | INTEGER NOT NULL >0，默认1 |
| deliveryState | TEXT NOT NULL，默认PENDING，PENDING/SCHEDULED/BLOCKED/FAILED/NOT_REQUIRED |
| deliveryError | TEXT NOT NULL，默认空字符串，错误时保存稳定结果码 |
| scheduleId | INTEGER NOT NULL UNIQUE，D分配稳定正int供系统调度复用 |
| createdAt、updatedAt | INTEGER NOT NULL |

UNIQUE(userId,eventId)，索引 eng_reminder_status(status,triggerAt)。同用户同活动复用提醒记录，关闭保留历史。无记录返回 ReminderView.unconfigured，不创建假提醒。DB deliveryError 空串在DTO中映射 ResultCode.OK，configured=false时deliveryError=null。

revision 在设置、关闭、时间重算、发送、过期等意图或业务状态变化时递增，单独更新调度结果不递增。SCHEDULED 表示系统接口接受调度，仍需结合挂起同步任务和权限判断。

## Schema 与迁移规则

建表顺序由 A 在 AppGraph 统一提供，平台核心表先建立，再 events、registrations、favorites/reminders。各模块只维护自己的 SQL，跨模块查询和写入调用端口。平台提供的SQLiteDatabase句柄不构成表级访问控制，代码审查和静态检查共同约束边界。

首次创建、种子初始化和升级由 SQLiteOpenHelper 事务管理。SchemaContributor.seed 只在 onCreate 执行。当前工程原始模板没有旧业务数据库，版本1为业务初始结构。未来升级提升总版本并提供逐版本迁移，默认迁移会显式报错，避免静默遗漏字段。

签到反馈与兴趣表尚未建表，C/B 增加选做功能时提出 Schema 与迁移，不修改其他模块表。联动业务不使用触发器跨越所有权，统一通过事务端口执行。
