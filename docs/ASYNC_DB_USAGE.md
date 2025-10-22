# 异步数据库查询使用指南

## 背景

为了避免在主线程执行数据库查询导致的 ANR（Application Not Responding）问题，我们在 `WKDBHelper` 中添加了异步查询方法。

## 核心改动

### 1. WKDBHelper 新增方法

#### `rawQueryAsync()` - 异步原始 SQL 查询
```java
public <T> void rawQueryAsync(String sql, Object[] selectionArgs, QueryCallback<T> callback)
```

#### `selectAsync()` - 异步 SELECT 查询
```java
public <T> void selectAsync(String table, String selection, String[] selectionArgs, 
                            String orderBy, QueryCallback<T> callback)
```

#### `QueryCallback<T>` - 查询回调接口
```java
public interface QueryCallback<T> {
    // 在后台线程执行，处理 Cursor
    T onQuery(Cursor cursor);
    
    // 在主线程执行，接收查询结果
    void onResult(T result);
}
```

## 使用方式

### ❌ 错误的用法（会导致 ANR）

```java
// 在主线程直接调用同步查询
public void refreshUI() {
    // ⚠️ 这会阻塞主线程，导致 ANR
    List<WKReminder> list = ReminderDBManager.getInstance()
        .queryWithChannelAndDone(channelID, channelType, 0);
    updateUI(list);
}
```

### ✅ 正确的用法（异步查询）

#### 方式 1：使用 ReminderDBManager 的异步方法

```java
// 在主线程调用异步查询
public void refreshUI() {
    ReminderDBManager.getInstance().queryWithChannelAndDoneAsync(
        channelID, 
        channelType, 
        0, 
        new ReminderDBManager.ReminderQueryCallback() {
            @Override
            public void onResult(List<WKReminder> reminders) {
                // ✅ 这里已经在主线程，可以直接更新 UI
                updateUI(reminders);
            }
        }
    );
}
```

#### 方式 2：直接使用 WKDBHelper 的异步方法

```java
String sql = "SELECT * FROM reminders WHERE channel_id=? AND done=?";
Object[] args = new Object[]{channelID, 0};

WKIMApplication.getInstance().getDbHelper().rawQueryAsync(sql, args, 
    new WKDBHelper.QueryCallback<List<WKReminder>>() {
        @Override
        public List<WKReminder> onQuery(Cursor cursor) {
            // ✅ 后台线程：处理 Cursor
            List<WKReminder> list = new ArrayList<>();
            if (cursor != null) {
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    WKReminder reminder = serializeReminder(cursor);
                    list.add(reminder);
                }
            }
            return list;
        }
        
        @Override
        public void onResult(List<WKReminder> result) {
            // ✅ 主线程：更新 UI
            updateUI(result);
        }
    }
);
```

#### 方式 3：使用 Lambda 表达式（Java 8+）

```java
ReminderDBManager.getInstance().queryWithChannelAndDoneAsync(
    channelID, channelType, 0, 
    reminders -> updateUI(reminders)  // 主线程回调
);
```

## 迁移指南

### 第一步：识别主线程数据库调用

查找所有在 UI 相关类中的数据库调用：
- Fragment 的 `onViewCreated()`, `onResume()`
- Activity 的 `onCreate()`, `onStart()`
- Adapter 的 `getView()`, `onBindViewHolder()`
- UI 事件回调（如 `onClick()`, `onRefresh()`）

### 第二步：替换为异步调用

**原代码：**
```java
@Override
public void onRefresh() {
    // ❌ 主线程数据库查询
    List<WKReminder> list = ReminderDBManager.getInstance()
        .queryWithChannelAndDone(channelID, channelType, 0);
    adapter.setData(list);
    swipeRefreshLayout.setRefreshing(false);
}
```

**修改后：**
```java
@Override
public void onRefresh() {
    // ✅ 异步查询
    ReminderDBManager.getInstance().queryWithChannelAndDoneAsync(
        channelID, channelType, 0, 
        reminders -> {
            adapter.setData(reminders);
            swipeRefreshLayout.setRefreshing(false);
        }
    );
}
```

### 第三步：处理 WKUIConversationMsg.getReminderList()

**问题代码：**
```java
// WKUIConversationMsg.java
public List<WKReminder> getReminderList() {
    // ❌ 同步查询，会阻塞调用线程
    return ReminderDBManager.getInstance()
        .queryWithChannelAndDone(channelID, channelType, 0);
}
```

**解决方案 A：改为异步回调**
```java
// WKUIConversationMsg.java
public void getReminderList(ReminderDBManager.ReminderQueryCallback callback) {
    ReminderDBManager.getInstance()
        .queryWithChannelAndDoneAsync(channelID, channelType, 0, callback);
}

// 使用处
conversationMsg.getReminderList(reminders -> {
    // 更新 UI
    updateReminderList(reminders);
});
```

**解决方案 B：延迟加载（推荐）**
```java
// WKUIConversationMsg.java
private List<WKReminder> cachedReminderList;

// 异步加载
public void loadReminderList(ReminderDBManager.ReminderQueryCallback callback) {
    if (cachedReminderList != null) {
        callback.onResult(cachedReminderList);
        return;
    }
    
    ReminderDBManager.getInstance().queryWithChannelAndDoneAsync(
        channelID, channelType, 0, 
        reminders -> {
            cachedReminderList = reminders;
            callback.onResult(reminders);
        }
    );
}

// 获取缓存
public List<WKReminder> getCachedReminderList() {
    return cachedReminderList != null ? cachedReminderList : new ArrayList<>();
}
```

## 性能优化建议

### 1. 使用缓存减少查询次数
```java
private Map<String, List<WKReminder>> reminderCache = new HashMap<>();

public void getReminderListWithCache(String channelID, byte channelType, 
                                     ReminderQueryCallback callback) {
    String key = channelID + "_" + channelType;
    
    // 先检查缓存
    if (reminderCache.containsKey(key)) {
        callback.onResult(reminderCache.get(key));
        return;
    }
    
    // 缓存未命中，查询数据库
    queryWithChannelAndDoneAsync(channelID, channelType, 0, reminders -> {
        reminderCache.put(key, reminders);
        callback.onResult(reminders);
    });
}
```

### 2. 批量查询代替多次单独查询
```java
// ❌ 低效：N 次查询
for (String channelId : channelIds) {
    queryWithChannelAndDoneAsync(channelId, channelType, 0, callback);
}

// ✅ 高效：1 次批量查询
String sql = "SELECT * FROM reminders WHERE channel_id IN (" 
    + placeholders + ") AND done=?";
rawQueryAsync(sql, args, callback);
```

### 3. 分页加载大数据
```java
String sql = "SELECT * FROM reminders WHERE channel_id=? " +
             "ORDER BY message_seq DESC LIMIT ? OFFSET ?";
Object[] args = {channelID, pageSize, offset};
```

## 注意事项

1. **不要在异步回调中执行耗时操作**
   ```java
   @Override
   public void onResult(List<WKReminder> result) {
       // ❌ 错误：主线程执行耗时操作
       for (WKReminder r : result) {
           heavyProcessing(r);  // 耗时操作
       }
       
       // ✅ 正确：只更新 UI
       adapter.setData(result);
   }
   ```

2. **处理 Fragment/Activity 生命周期**
   ```java
   @Override
   public void onViewCreated(View view, Bundle savedInstanceState) {
       loadData();
   }
   
   private void loadData() {
       ReminderDBManager.getInstance().queryWithChannelAndDoneAsync(
           channelID, channelType, 0,
           reminders -> {
               // ✅ 检查 Fragment 是否还存在
               if (isAdded() && getView() != null) {
                   updateUI(reminders);
               }
           }
       );
   }
   ```

3. **Cursor 自动关闭**
   - 异步方法会自动关闭 Cursor，无需手动关闭
   - 在 `onQuery()` 中处理完 Cursor 即可

## 线程模型

```
┌─────────────┐
│  主线程      │
│  (UI Thread) │
└──────┬──────┘
       │
       │ 1. 调用 rawQueryAsync()
       │
       ↓
┌─────────────────────┐
│  数据库线程池        │
│  (SingleThread)     │
│                     │
│  2. 执行 SQL 查询    │
│  3. 处理 Cursor     │
│  4. 返回结果对象    │
└──────┬──────────────┘
       │
       │ 5. 回调到主线程
       │
       ↓
┌─────────────┐
│  主线程      │
│  6. 更新 UI  │
└─────────────┘
```

## 完整示例

```java
// ChatFragment.java
public class ChatFragment extends Fragment {
    
    private RecyclerView recyclerView;
    private ReminderAdapter adapter;
    
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 初始化 UI
        recyclerView = view.findViewById(R.id.recycler_view);
        adapter = new ReminderAdapter();
        recyclerView.setAdapter(adapter);
        
        // 加载数据（异步）
        loadReminders();
    }
    
    private void loadReminders() {
        // 显示加载状态
        showLoading(true);
        
        // 异步查询数据库
        ReminderDBManager.getInstance().queryWithChannelAndDoneAsync(
            channelID, 
            channelType, 
            0,
            reminders -> {
                // 检查 Fragment 状态
                if (!isAdded() || getView() == null) {
                    return;
                }
                
                // 更新 UI
                showLoading(false);
                adapter.setData(reminders);
                
                // 如果数据为空，显示空视图
                if (reminders.isEmpty()) {
                    showEmptyView();
                }
            }
        );
    }
    
    private void showLoading(boolean show) {
        // 显示/隐藏加载动画
    }
    
    private void showEmptyView() {
        // 显示空数据提示
    }
}
```

## 总结

- ✅ **保留**原有的同步方法（标记为 `@Deprecated`）
- ✅ **新增**异步方法（`xxxAsync`）
- ✅ **不影响**现有代码
- ✅ **逐步迁移**，新代码使用异步方法
- ✅ **避免 ANR**，提升用户体验

