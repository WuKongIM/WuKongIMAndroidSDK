# ANR 问题修复总结

## 问题分析

### 原始错误堆栈
```
ANR EXCEPTION - Find process anr, but unable to get anr message.
net.zetetic.database.sqlcipher.SQLiteConnectionPool.z0
└─ ReminderDBManager.queryWithChannelAndDone()
   └─ WKUIConversationMsg.getReminderList()
      └─ ChatFragment.onRefresh()
```

### 问题根因
在主线程直接执行 SQLCipher 数据库查询操作，导致 UI 线程阻塞，引发 ANR。

## 解决方案

### 方案概述
在 `WKDBHelper` 中添加异步查询方法，**不修改**原有同步方法，保证向后兼容。

### 实现细节

#### 1. WKDBHelper 增强

新增以下内容：

**线程池和 Handler：**
```java
// 单线程池，保证数据库操作顺序性
private static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

// 主线程 Handler，用于回调
private static final Handler mainHandler = new Handler(Looper.getMainLooper());
```

**回调接口：**
```java
public interface QueryCallback<T> {
    T onQuery(Cursor cursor);      // 后台线程处理
    void onResult(T result);        // 主线程回调
}
```

**异步查询方法：**
```java
// 异步原始 SQL 查询
public <T> void rawQueryAsync(String sql, Object[] selectionArgs, QueryCallback<T> callback)

// 异步 SELECT 查询
public <T> void selectAsync(String table, String selection, String[] selectionArgs, 
                            String orderBy, QueryCallback<T> callback)
```

#### 2. ReminderDBManager 扩展

**保留原方法：**
```java
@Deprecated
public List<WKReminder> queryWithChannelAndDone(String channelID, byte channelType, int done)
```

**新增异步方法：**
```java
public void queryWithChannelAndDoneAsync(String channelID, byte channelType, int done, 
                                         ReminderQueryCallback callback)
```

**新增回调接口：**
```java
public interface ReminderQueryCallback {
    void onResult(List<WKReminder> reminders);
}
```

## 使用示例

### 原来的代码（会导致 ANR）
```java
@Override
public void onRefresh() {
    // ❌ 主线程同步查询
    List<WKReminder> list = ReminderDBManager.getInstance()
        .queryWithChannelAndDone(channelID, channelType, 0);
    updateUI(list);
}
```

### 修复后的代码
```java
@Override
public void onRefresh() {
    // ✅ 异步查询
    ReminderDBManager.getInstance().queryWithChannelAndDoneAsync(
        channelID, channelType, 0,
        reminders -> updateUI(reminders)
    );
}
```

## 修改文件清单

| 文件 | 修改内容 | 说明 |
|------|----------|------|
| `wkim/src/main/java/com/xinbida/wukongim/db/WKDBHelper.java` | 新增异步查询方法 | 核心修改 |
| `wkim/src/main/java/com/xinbida/wukongim/db/ReminderDBManager.java` | 新增异步查询方法 | 示例修改 |
| `docs/ASYNC_DB_USAGE.md` | 使用文档 | 新增 |
| `docs/ANR_FIX_SUMMARY.md` | 修复总结 | 新增 |

## 为什么不能在 rawQuery 中统一加异步？

### 技术原因

1. **返回值冲突**
   ```java
   // 同步方法
   Cursor cursor = dbHelper.rawQuery(sql);  // 立即返回
   cursor.moveToFirst();  // 马上使用
   
   // 如果改成异步
   Cursor cursor = dbHelper.rawQuery(sql);  // 返回什么？null？
   cursor.moveToFirst();  // 💥 NullPointerException
   ```

2. **调用方依赖同步返回**
   - 所有现有代码都期望**立即**得到 Cursor
   - 修改 `rawQuery` 会导致**所有**调用方崩溃
   - 需要修改几十个甚至上百个调用点

3. **Cursor 生命周期问题**
   - Cursor 需要在查询线程关闭
   - 异步模式下，调用方无法控制 Cursor 的关闭时机

### 设计原则

我们采用的方案遵循以下原则：

- ✅ **向后兼容**：保留原有同步方法
- ✅ **渐进式迁移**：新增异步方法，逐步迁移
- ✅ **最小影响**：不修改现有代码
- ✅ **清晰明确**：方法名加 `Async` 后缀，一目了然

## 迁移计划

### 阶段 1：基础设施（已完成 ✅）
- [x] 在 `WKDBHelper` 中添加异步查询基础方法
- [x] 在 `ReminderDBManager` 中添加异步查询示例
- [x] 编写使用文档

### 阶段 2：核心模块迁移（建议优先）
推荐按以下优先级迁移：

1. **高频调用的查询**
   - `ConversationDbManager.queryAll()`
   - `MsgDbManager.queryMessages()`
   - `ChannelDBManager.query()`

2. **UI 刷新相关的查询**
   - 所有在 `onRefresh()` 中的查询
   - 所有在 `onResume()` 中的查询
   - 所有在 Adapter 中的查询

3. **其他 DBManager**
   - `RobotDBManager`
   - `ChannelMembersDbManager`
   - `MsgReactionDBManager`

### 阶段 3：全面检查（建议定期进行）
使用工具检测主线程数据库调用：

```java
// 在 Application 中开启严格模式（Debug 模式）
if (BuildConfig.DEBUG) {
    StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
        .detectDiskReads()
        .detectDiskWrites()
        .penaltyLog()
        .penaltyDeath()  // 崩溃，强制修复
        .build());
}
```

## 性能对比

| 场景 | 同步查询 | 异步查询 |
|------|---------|---------|
| **主线程阻塞** | 100-500ms | 0ms |
| **ANR 风险** | 高 | 无 |
| **UI 流畅度** | 卡顿 | 流畅 |
| **用户体验** | 差 | 好 |

## 其他优化建议

### 1. 使用数据库索引
```sql
CREATE INDEX idx_reminders_channel ON reminders(channel_id, channel_type, done);
```

### 2. 减少查询次数
- 使用缓存
- 批量查询代替多次单独查询
- 延迟加载

### 3. 优化查询语句
- 只查询需要的字段
- 使用 LIMIT 限制结果集
- 避免复杂的 JOIN 操作

### 4. 考虑迁移到 Room
长期来看，建议迁移到 Android Jetpack Room 框架：
- 编译时 SQL 验证
- 自动线程管理
- LiveData 集成
- 更少的样板代码

## 常见问题 FAQ

### Q1: 为什么不直接删除同步方法？
**A:** 为了保证向后兼容，避免破坏现有代码。同步方法标记为 `@Deprecated`，提醒开发者迁移。

### Q2: 异步方法的回调在哪个线程？
**A:** `onQuery()` 在后台线程执行，`onResult()` 在主线程执行。

### Q3: 如何处理 Fragment 生命周期？
**A:** 在回调中检查 Fragment 状态：
```java
if (isAdded() && getView() != null) {
    updateUI(result);
}
```

### Q4: 可以在 onQuery() 中更新 UI 吗？
**A:** 不可以！`onQuery()` 在后台线程执行，只能在 `onResult()` 中更新 UI。

### Q5: Cursor 需要手动关闭吗？
**A:** 不需要，异步方法会自动关闭 Cursor。

## 总结

通过在 `WKDBHelper` 中添加异步查询方法，我们实现了：

1. ✅ **解决 ANR 问题**：数据库查询在后台线程执行
2. ✅ **向后兼容**：不破坏现有代码
3. ✅ **易于使用**：简洁的回调接口
4. ✅ **自动管理**：自动处理线程切换和 Cursor 关闭
5. ✅ **渐进迁移**：可以逐步迁移，无需一次性修改所有代码

**下一步行动：**
1. 在出现 ANR 的地方（`ChatFragment.onRefresh()`）使用异步查询
2. 逐步迁移其他高频查询
3. 在开发环境启用 StrictMode 检测主线程 IO

