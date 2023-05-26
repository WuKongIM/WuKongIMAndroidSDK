package com.wukong.im.manager;

import android.text.TextUtils;

import com.wukong.im.db.RobotDBManager;
import com.wukong.im.entity.WKRobot;
import com.wukong.im.entity.WKRobotMenu;
import com.wukong.im.interfaces.IRefreshRobotMenu;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RobotManager extends BaseManager {

    private RobotManager() {
    }

    private static class RobotManagerBinder {
        final static RobotManager manager = new RobotManager();
    }

    public static RobotManager getInstance() {
        return RobotManagerBinder.manager;
    }

    private ConcurrentHashMap<String, IRefreshRobotMenu> refreshRobotMenu;

    public WKRobot getWithRobotID(String robotID) {
        return RobotDBManager.getInstance().query(robotID);
    }

    public WKRobot getWithUsername(String username) {
        return RobotDBManager.getInstance().queryWithUsername(username);
    }

    public List<WKRobot> getWithRobotIds(List<String> robotIds) {
        return RobotDBManager.getInstance().queryRobots(robotIds);
    }

    public List<WKRobotMenu> getRobotMenus(String robotID) {
        return RobotDBManager.getInstance().queryRobotMenus(robotID);
    }

    public List<WKRobotMenu> getRobotMenus(List<String> robotIds) {
        return RobotDBManager.getInstance().queryRobotMenus(robotIds);
    }

    public void saveOrUpdateRobots(List<WKRobot> list) {
        if (list != null && list.size() > 0) {
            RobotDBManager.getInstance().insertOrUpdate(list);
        }
    }

    public void saveOrUpdateRobotMenus(List<WKRobotMenu> list) {
        if (list != null && list.size() > 0) {
            RobotDBManager.getInstance().insertOrUpdateMenu(list);
        }
        setRefreshRobotMenu();
    }

    public void addOnRefreshRobotMenu(String key, IRefreshRobotMenu iRefreshRobotMenu) {
        if (TextUtils.isEmpty(key) || iRefreshRobotMenu == null) return;
        if (refreshRobotMenu == null) refreshRobotMenu = new ConcurrentHashMap<>();
        refreshRobotMenu.put(key, iRefreshRobotMenu);
    }

    public void removeRefreshRobotMenu(String key) {
        if (TextUtils.isEmpty(key) || refreshRobotMenu == null) return;
        refreshRobotMenu.remove(key);
    }

    private void setRefreshRobotMenu() {
        runOnMainThread(() -> {
            for (Map.Entry<String, IRefreshRobotMenu> entry : refreshRobotMenu.entrySet()) {
                entry.getValue().onRefreshRobotMenu();
            }
        });
    }
}
