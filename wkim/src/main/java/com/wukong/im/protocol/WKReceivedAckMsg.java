package com.wukong.im.protocol;


import com.wukong.im.message.type.WKMsgType;

/**
 * 2019-11-11 10:46
 * 收到消息Ack消息
 */
public class WKReceivedAckMsg extends WKBaseMsg {
    //服务端的消息ID(全局唯一)
    public String messageID;
    //序列号
    public int messageSeq;
    //消息id长度
    public char messageIDLength = 2;

    public WKReceivedAckMsg() {
        packetType = WKMsgType.REVACK;
        remainingLength = 8;//序列号
    }
}
