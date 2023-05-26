package com.wukong.im.protocol;

import com.wukong.im.message.type.WKMsgType;

/**
 * 2019-11-11 10:27
 * 连接talk service确认消息
 */
public class WKConnectAckMsg extends WKBaseMsg {
    //客户端时间与服务器的差值，单位毫秒。
    public long timeDiff;
    //连接原因码
    public short reasonCode;
    //时间戳长度
    public long timeDiffLength = 8;
    // 服务端公钥
    public String serverKey;
    // 安全码
    public String salt;

    public WKConnectAckMsg() {
        packetType = WKMsgType.CONNACK;
    }
}
