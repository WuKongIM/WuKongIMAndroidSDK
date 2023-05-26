package com.wukong.im.interfaces;


import com.wukong.im.entity.WKSyncChannelMsg;

/**
 * 2020-10-10 15:17
 */
public interface ISyncChannelMsgBack {
    void onBack(WKSyncChannelMsg syncChannelMsg);
}
