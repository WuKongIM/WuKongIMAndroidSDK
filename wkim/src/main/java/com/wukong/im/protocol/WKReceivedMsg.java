package com.wukong.im.protocol;

import com.wukong.im.entity.WKMsgSetting;
import com.wukong.im.message.type.WKMsgType;

/**
 * 2019-11-11 10:37
 * 接受消息
 */
public class WKReceivedMsg extends WKBaseMsg {
    //服务端的消息ID(全局唯一)
    public String messageID;
    //服务端的消息序列号(有序递增，用户唯一)
    public int messageSeq;
    //服务器消息时间戳(10位，到秒)
    public long messageTimestamp;
    //客户端ID
    public String clientMsgNo;
    //频道ID
    public String channelID;
    //频道类型
    public byte channelType;
    //发送者ID
    public String fromUID;
    // 话题ID
    public String topicID;
    //消息内容
    public String payload;
    //消息key
    public String msgKey;
    // 消息设置
    public WKMsgSetting setting;

    public WKReceivedMsg() {
        packetType = WKMsgType.RECVEIVED;
    }
}
