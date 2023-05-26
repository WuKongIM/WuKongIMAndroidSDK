package com.wukong.im.db;

import android.content.ContentValues;
import android.database.Cursor;

import com.wukong.im.WKIMApplication;
import com.wukong.im.entity.WKRobot;
import com.wukong.im.entity.WKRobotMenu;

import java.util.ArrayList;
import java.util.List;

public class RobotDBManager {
    private final String robot = "robot";
    private final String robotMenu = "robot_menu";

    private RobotDBManager() {
    }

    private static class RobotDBManagerBinder {
        private final static RobotDBManager db = new RobotDBManager();
    }

    public static RobotDBManager getInstance() {
        return RobotDBManagerBinder.db;
    }

    public void insertOrUpdateMenu(List<WKRobotMenu> list) {
        for (WKRobotMenu robotMenu : list) {
            if (isExitMenu(robotMenu.robotID, robotMenu.cmd)) {
                update(robotMenu);
            } else {
                WKIMApplication.getInstance().getDbHelper().insert(this.robotMenu, getCV(robotMenu));
            }
        }
    }

    public boolean isExitMenu(String robotID, String cmd) {
        boolean isExist = false;
        String sql = "select * from " + robotMenu + " where robot_id =" + "\"" + robotID + "\" and cmd=" + "\"" + cmd + "\"";
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql)) {
            if (cursor != null && cursor.moveToLast()) {
                isExist = true;
            }
        }
        return isExist;
    }

    private void update(WKRobotMenu menu) {
        String[] updateKey = new String[3];
        String[] updateValue = new String[3];
        updateKey[0] = "type";
        updateValue[0] = menu.type;
        updateKey[1] = "remark";
        updateValue[1] = menu.remark;
        updateKey[2] = "updated_at";
        updateValue[2] = menu.updatedAT;
        String where = "robot_id=? and cmd=?";
        String[] whereValue = new String[2];
        whereValue[0] = menu.robotID;
        whereValue[1] = menu.cmd;
        WKIMApplication.getInstance().getDbHelper()
                .update(robotMenu, updateKey, updateValue, where, whereValue);
    }

    public void insertOrUpdate(List<WKRobot> list) {
        for (WKRobot robot : list) {
            if (isExist(robot.robotID)) {
                update(robot);
            } else {
                insert(robot);
            }
        }
    }

    public boolean isExist(String robotID) {
        boolean isExist = false;
        String sql = "select * from " + robot + " where robot_id =" + "\"" + robotID + "\"";
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql)) {
            if (cursor != null && cursor.moveToLast()) {
                isExist = true;
            }
        }
        return isExist;
    }

    private void update(WKRobot robot) {
        String[] updateKey = new String[6];
        String[] updateValue = new String[6];
        updateKey[0] = "status";
        updateValue[0] = String.valueOf(robot.status);
        updateKey[1] = "version";
        updateValue[1] = String.valueOf(robot.version);
        updateKey[2] = "updated_at";
        updateValue[2] = String.valueOf(robot.updatedAT);
        updateKey[3] = "username";
        updateValue[3] = robot.username;
        updateKey[4] = "placeholder";
        updateValue[4] = robot.placeholder;
        updateKey[5] = "inline_on";
        updateValue[5] = String.valueOf(robot.inlineOn);

        String where = "robot_id=?";
        String[] whereValue = new String[1];
        whereValue[0] = robot.robotID;
        WKIMApplication.getInstance().getDbHelper()
                .update(this.robot, updateKey, updateValue, where, whereValue);

    }

    private void insert(WKRobot robot) {
        ContentValues cv = getCV(robot);
        WKIMApplication.getInstance().getDbHelper().insert(this.robot, cv);
    }

    public void insertRobots(List<WKRobot> list) {
        if (list == null || list.size() == 0) return;
        List<ContentValues> cvList = new ArrayList<>();
        for (WKRobot robot : list) {
            cvList.add(getCV(robot));
        }
        try {
            WKIMApplication.getInstance().getDbHelper().getDb().beginTransaction();
            for (ContentValues cv : cvList) {
                WKIMApplication.getInstance().getDbHelper().insert(robot, cv);
            }
            WKIMApplication.getInstance().getDbHelper().getDb().setTransactionSuccessful();

        } finally {
            WKIMApplication.getInstance().getDbHelper().getDb().endTransaction();
        }
    }

    public WKRobot query(String robotID) {
        WKRobot robot = null;
        String sql = "select * from " + this.robot + " where robot_id = " + "\"" + robotID + "\"";
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql)) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                robot = serializeRobot(cursor);
            }
        }
        return robot;
    }

    public WKRobot queryWithUsername(String username) {
        WKRobot robot = null;
        String sql = "select * from " + this.robot + " where username = " + "\"" + username + "\"";
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql)) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                robot = serializeRobot(cursor);
            }
        }
        return robot;
    }

    public List<WKRobot> queryRobots(List<String> robotIds) {
        StringBuilder sb = new StringBuilder("select * from " + robot + " where robot_id in (");
        for (int i = 0; i < robotIds.size(); i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append("'").append(robotIds.get(i)).append("'");
        }
        sb.append(")");
        List<WKRobot> list = new ArrayList<>();

        try (Cursor cursor = WKIMApplication.getInstance().getDbHelper().rawQuery(sb.toString())) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKRobot robot = serializeRobot(cursor);
                list.add(robot);
            }
        }
        return list;
    }

    public List<WKRobotMenu> queryRobotMenus(List<String> robotIds) {
        StringBuilder sb = new StringBuilder("select * from " + robotMenu + " where robot_id in (");
        for (int i = 0; i < robotIds.size(); i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append("'").append(robotIds.get(i)).append("'");
        }
        sb.append(")");
        List<WKRobotMenu> list = new ArrayList<>();

        try (Cursor cursor = WKIMApplication.getInstance().getDbHelper().rawQuery(sb.toString())) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKRobotMenu robotMenu = serializeRobotMenu(cursor);
                list.add(robotMenu);
            }
        }
        return list;
    }

    public List<WKRobotMenu> queryRobotMenus(String robotID) {
        List<WKRobotMenu> list = new ArrayList<>();
        String sql = "select * from " + robotMenu + " where robot_id = " + "\"" + robotID + "\"";

        try (Cursor cursor = WKIMApplication.getInstance().getDbHelper().rawQuery(sql)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKRobotMenu robotMenu = serializeRobotMenu(cursor);
                list.add(robotMenu);
            }
        }
        return list;
    }

    public void insertMenus(List<WKRobotMenu> list) {
        if (list == null || list.size() == 0) return;
        List<ContentValues> cvList = new ArrayList<>();
        for (WKRobotMenu robot : list) {
            cvList.add(getCV(robot));
        }
        try {
            WKIMApplication.getInstance().getDbHelper().getDb().beginTransaction();
            for (ContentValues cv : cvList) {
                WKIMApplication.getInstance().getDbHelper().insert(robotMenu, cv);
            }
            WKIMApplication.getInstance().getDbHelper().getDb().setTransactionSuccessful();

        } finally {
            WKIMApplication.getInstance().getDbHelper().getDb().endTransaction();
        }
    }

    private WKRobot serializeRobot(Cursor cursor) {
        WKRobot robot = new WKRobot();
        robot.robotID = WKCursor.readString(cursor, "robot_id");
        robot.status = WKCursor.readInt(cursor, "status");
        robot.version = WKCursor.readLong(cursor, "version");
        robot.username = WKCursor.readString(cursor, "username");
        robot.inlineOn = WKCursor.readInt(cursor, "inline_on");
        robot.placeholder = WKCursor.readString(cursor, "placeholder");
        robot.createdAT = WKCursor.readString(cursor, "created_at");
        robot.updatedAT = WKCursor.readString(cursor, "updated_at");
        return robot;
    }

    private WKRobotMenu serializeRobotMenu(Cursor cursor) {
        WKRobotMenu robot = new WKRobotMenu();
        robot.robotID = WKCursor.readString(cursor, "robot_id");
        robot.type = WKCursor.readString(cursor, "type");
        robot.cmd = WKCursor.readString(cursor, "cmd");
        robot.remark = WKCursor.readString(cursor, "remark");
        robot.createdAT = WKCursor.readString(cursor, "created_at");
        robot.updatedAT = WKCursor.readString(cursor, "updated_at");
        return robot;
    }

    private ContentValues getCV(WKRobot wkRobot) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("robot_id", wkRobot.robotID);
        contentValues.put("inline_on", wkRobot.inlineOn);
        contentValues.put("username", wkRobot.username);
        contentValues.put("placeholder", wkRobot.placeholder);
        contentValues.put("status", wkRobot.status);
        contentValues.put("version", wkRobot.version);
        contentValues.put("created_at", wkRobot.createdAT);
        contentValues.put("updated_at", wkRobot.updatedAT);
        return contentValues;
    }

    private ContentValues getCV(WKRobotMenu robotMenu) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("robot_id", robotMenu.robotID);
        contentValues.put("cmd", robotMenu.cmd);
        contentValues.put("remark", robotMenu.remark);
        contentValues.put("type", robotMenu.type);
        contentValues.put("created_at", robotMenu.createdAT);
        contentValues.put("updated_at", robotMenu.updatedAT);
        return contentValues;
    }
}
