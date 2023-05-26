package com.wukong.im.interfaces;


import com.wukong.im.entity.WKMsg;
import com.wukong.im.protocol.WKDisconnectMsg;
import com.wukong.im.protocol.WKPongMsg;
import com.wukong.im.protocol.WKSendAckMsg;

/**
 * 2019-11-10 17:03
 * 接受通讯协议消息
 */
public interface IReceivedMsgListener {
    /**
     * 登录状态消息
     *
     * @param statusCode 状态
     */
    void loginStatusMsg(short statusCode);

    /**
     * 心跳消息
     */
    void heartbeatMsg(WKPongMsg msgHeartbeat);

    /**
     * 被踢消息
     */
    void kickMsg(WKDisconnectMsg disconnectMsg);

    /**
     * 发送消息状态消息
     *
     * @param sendAckMsg ack
     */
    void sendAckMsg(WKSendAckMsg sendAckMsg);

    /**
     * 聊天消息
     *
     * @param msg 消息对象
     */
    void receiveMsg(WKMsg msg);

    /**
     * 重连
     */
    void reconnect();
}
