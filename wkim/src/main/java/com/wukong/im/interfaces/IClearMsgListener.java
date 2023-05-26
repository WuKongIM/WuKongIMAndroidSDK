package com.wukong.im.interfaces;

public interface IClearMsgListener {
    void clear(String channelID, byte channelType, String fromUID);
}
