package com.wukong.im.interfaces;

import com.wukong.im.entity.WKMsg;

/**
 * 5/12/21 2:02 PM
 * 发送消息ack监听
 */
public interface ISendACK {
    void msgACK(WKMsg msg);
}
