package com.wukong.im.interfaces;


import com.wukong.im.entity.WKSyncMsg;

import java.util.List;

/**
 * 2020-09-28 15:10
 * 同步消息完成回调
 */
public interface ISyncOfflineMsgBack {
    void onBack(boolean isEnd, List<WKSyncMsg> list);
}
