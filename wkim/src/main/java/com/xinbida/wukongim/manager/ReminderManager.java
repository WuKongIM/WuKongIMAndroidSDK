package com.xinbida.wukongim.manager;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.db.ReminderDBManager;
import com.xinbida.wukongim.entity.WKReminder;
import com.xinbida.wukongim.interfaces.INewReminderListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 5/21/21 12:13 PM
 * 提醒管理
 */
public class ReminderManager extends BaseManager {
    private ReminderManager() {
    }

    private static class RemindManagerBinder {
        final static ReminderManager manager = new ReminderManager();
    }

    public static ReminderManager getInstance() {
        return RemindManagerBinder.manager;
    }

    private ConcurrentHashMap<String, INewReminderListener> newReminderMaps;

    public void addOnNewReminderListener(String key, INewReminderListener iNewReminderListener) {
        if (TextUtils.isEmpty(key) || iNewReminderListener == null) return;
        if (newReminderMaps == null) newReminderMaps = new ConcurrentHashMap<>();
        newReminderMaps.put(key, iNewReminderListener);
    }

    public void removeNewReminderListener(String key) {
        if (newReminderMaps != null) newReminderMaps.remove(key);
    }

    private void setNewReminders(List<WKReminder> list) {
        if (newReminderMaps != null && !newReminderMaps.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, INewReminderListener> entry : newReminderMaps.entrySet()) {
                    entry.getValue().newReminder(list);
                }
            });
        }
    }

    /**
     * 获取某个类型的提醒
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param type        提醒类型
     * @return WKReminder
     */
    public WKReminder getReminder(String channelID, byte channelType, int type) {
        List<WKReminder> list = getReminders(channelID, channelType);
        WKReminder wkReminder = null;
        for (int i = 0, size = list.size(); i < size; i++) {
            if (list.get(i).type == type) {
                wkReminder = list.get(i);
                break;
            }
        }
        return wkReminder;
    }

    /**
     * 查询某个会话的高光内容
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @return List<WKReminder>
     */
    public List<WKReminder> getReminders(String channelID, byte channelType) {
        return ReminderDBManager.getInstance().queryWithChannelAndDone(channelID, channelType, 0);
    }

    public List<WKReminder> getRemindersWithType(String channelID, byte channelType, int type) {
        return ReminderDBManager.getInstance().queryWithChannelAndTypeAndDone(channelID, channelType, type, 0);
    }

    public void saveOrUpdateReminders(List<WKReminder> reminderList) {
        List<WKReminder> wkReminders = ReminderDBManager.getInstance().insertOrUpdateReminders(reminderList);
        if (wkReminders != null && !wkReminders.isEmpty()) {
            setNewReminders(reminderList);
        }
    }

    public long getMaxVersion() {
        return ReminderDBManager.getInstance().queryMaxVersion();
    }

    public void done() {

    }
}
