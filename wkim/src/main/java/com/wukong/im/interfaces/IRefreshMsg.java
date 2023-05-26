package com.wukong.im.interfaces;


import com.wukong.im.entity.WKMsg;

/**
 * 2020-08-27 21:18
 * 消息修改监听
 */
public interface IRefreshMsg {
    void onRefresh(WKMsg msg, boolean left);
}
