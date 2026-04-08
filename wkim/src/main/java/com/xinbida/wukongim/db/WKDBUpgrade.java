package com.xinbida.wukongim.db;

import android.content.res.AssetManager;
import android.text.TextUtils;

import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.utils.WKLoggerUtils;


import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 2020-07-31 09:36
 * 数据库升级管理
 */
public class WKDBUpgrade {
    private static final String TAG = "WKDBUpgrade";

    private WKDBUpgrade() {
    }

    static class DBUpgradeBinder {
        final static WKDBUpgrade db = new WKDBUpgrade();
    }

    public static WKDBUpgrade getInstance() {
        return DBUpgradeBinder.db;
    }

    void onUpgrade(SQLiteDatabase db) {
        long maxIndex = WKIMApplication.getInstance().getDBUpgradeIndex();

        // 防护：如果 SharedPreferences 记录了升级进度，但核心表不存在（DB 文件被删除/损坏后重建），
        // 则重置升级索引，从头执行所有建表和迁移 SQL
        if (maxIndex > 0 && !tableExists(db, "message")) {
            WKLoggerUtils.getInstance().e(TAG, "检测到核心表缺失（message），重置升级索引从头执行迁移");
            maxIndex = 0;
        }

        long tempIndex = maxIndex;
        List<WKDBSql> list = getExecSQL();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).index > maxIndex && list.get(i).sqlList != null && !list.get(i).sqlList.isEmpty()) {
                for (String sql : list.get(i).sqlList) {
                    if (!TextUtils.isEmpty(sql)) {
                        try {
                            db.execSQL(sql);
                        } catch (Exception e) {
                            // ALTER TABLE ADD COLUMN 如果列已存在会报错，跳过继续
                            WKLoggerUtils.getInstance().e(TAG, "执行迁移SQL异常（已跳过）: " + e.getMessage());
                        }
                    }
                }
                if (list.get(i).index > tempIndex) {
                    tempIndex = list.get(i).index;
                }
            }
        }
        WKIMApplication.getInstance().setDBUpgradeIndex(tempIndex);
    }

    /**
     * 检查表是否存在
     */
    private boolean tableExists(SQLiteDatabase db, String tableName) {
        try (android.database.Cursor cursor = db.rawQuery(
                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{tableName})) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0) > 0;
            }
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "检查表存在性异常: " + e.getMessage());
        }
        return false;
    }

    private List<WKDBSql> getExecSQL() {
        List<WKDBSql> sqlList = new ArrayList<>();

        AssetManager assetManager = WKIMApplication.getInstance().getContext().getAssets();
        if (assetManager != null) {
            try {
                String[] strings = assetManager.list("wk_sql");
                if (strings == null || strings.length == 0) {
                    WKLoggerUtils.getInstance().e(TAG,"Failed to read SQL");
                }
                assert strings != null;
                for (String str : strings) {
                    StringBuilder stringBuilder = new StringBuilder();
                    BufferedReader bf = new BufferedReader(new InputStreamReader(
                            assetManager.open("wk_sql/" + str)));
                    String line;
                    while ((line = bf.readLine()) != null) {
                        stringBuilder.append(line);
                    }

                    String temp = str.replaceAll(".sql", "");
                    List<String> list = new ArrayList<>();
                    if (stringBuilder.toString().contains(";")) {
                        list = Arrays.asList(stringBuilder.toString().split(";"));
                    } else list.add(stringBuilder.toString());
                    sqlList.add(new WKDBSql(Long.parseLong(temp), list));
                }
            } catch (IOException e) {
                WKLoggerUtils.getInstance().e(TAG , "getExecSQL error");
            }
        }
        return sqlList;
    }
}
