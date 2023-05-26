package com.wukong.im.protocol;


import com.wukong.im.message.type.WKMsgType;

/**
 * 2019-11-11 10:49
 * 心跳消息
 */
public class WKPingMsg extends WKBaseMsg {
    public WKPingMsg() {
        packetType = WKMsgType.PING;
    }
}
