package com.wukong.im.interfaces;

/**
 * 2019-11-18 11:54
 * 连接状态
 */
public interface IConnectionStatus {
    void onStatus(int code,String reason);
}
