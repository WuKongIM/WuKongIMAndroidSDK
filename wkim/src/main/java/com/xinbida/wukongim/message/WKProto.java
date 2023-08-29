package com.xinbida.wukongim.message;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.db.MsgDbManager;
import com.xinbida.wukongim.db.WKDBColumns;
import com.xinbida.wukongim.entity.WKConversationMsgExtra;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgSetting;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.message.type.WKMsgType;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;
import com.xinbida.wukongim.msgmodel.WKMsgEntity;
import com.xinbida.wukongim.protocol.WKBaseMsg;
import com.xinbida.wukongim.protocol.WKConnectAckMsg;
import com.xinbida.wukongim.protocol.WKConnectMsg;
import com.xinbida.wukongim.protocol.WKDisconnectMsg;
import com.xinbida.wukongim.protocol.WKPingMsg;
import com.xinbida.wukongim.protocol.WKPongMsg;
import com.xinbida.wukongim.protocol.WKReceivedAckMsg;
import com.xinbida.wukongim.protocol.WKReceivedMsg;
import com.xinbida.wukongim.protocol.WKSendAckMsg;
import com.xinbida.wukongim.protocol.WKSendMsg;
import com.xinbida.wukongim.utils.CryptoUtils;
import com.xinbida.wukongim.utils.WKLoggerUtils;
import com.xinbida.wukongim.utils.WKTypeUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;

/**
 * 5/21/21 11:28 AM
 * 收发消息转换
 */
class WKProto {

    private WKProto() {
    }

    private static class MessageConvertHandlerBinder {
        static final WKProto msgConvert = new WKProto();
    }

    public static WKProto getInstance() {
        return MessageConvertHandlerBinder.msgConvert;
    }

    byte[] encodeMsg(WKBaseMsg msg) {
        byte[] bytes = null;
        if (msg.packetType == WKMsgType.CONNECT) {
            // 连接
            bytes = WKProto.getInstance().enConnectMsg((WKConnectMsg) msg);
        } else if (msg.packetType == WKMsgType.REVACK) {
            // 收到消息回执
            bytes = WKProto.getInstance().enReceivedAckMsg((WKReceivedAckMsg) msg);
        } else if (msg.packetType == WKMsgType.SEND) {
            // 发送聊天消息
            bytes = WKProto.getInstance().enSendMsg((WKSendMsg) msg);
        } else if (msg.packetType == WKMsgType.PING) {
            // 发送心跳
            bytes = WKProto.getInstance().enPingMsg((WKPingMsg) msg);
            WKLoggerUtils.getInstance().e("ping...");
        }
        return bytes;
    }

    byte[] enConnectMsg(WKConnectMsg connectMsg) {
        byte[] remainingBytes = WKTypeUtils.getInstance().getRemainingLengthByte(connectMsg.getRemainingLength());
        int totalLen = connectMsg.getTotalLen();
        WKWrite wkWrite = new WKWrite(totalLen);
        try {
            wkWrite.writeByte(WKTypeUtils.getInstance().getHeader(connectMsg.packetType, connectMsg.flag, 0, 0));
            wkWrite.writeBytes(remainingBytes);
            wkWrite.writeByte(connectMsg.protocolVersion);
            wkWrite.writeByte(connectMsg.deviceFlag);
            wkWrite.writeString(connectMsg.deviceID);
            wkWrite.writeString(WKIMApplication.getInstance().getUid());
            wkWrite.writeString(WKIMApplication.getInstance().getToken());
            wkWrite.writeLong(connectMsg.clientTimestamp);
            wkWrite.writeString(CryptoUtils.getInstance().getPublicKey());

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return wkWrite.getWriteBytes();
    }

    synchronized byte[] enReceivedAckMsg(WKReceivedAckMsg receivedAckMsg) {
        byte[] remainingBytes = WKTypeUtils.getInstance().getRemainingLengthByte(8 + 4);

        int totalLen = 1 + remainingBytes.length + 8 + 4;
        WKWrite wkWrite = new WKWrite(totalLen);
        wkWrite.writeByte(WKTypeUtils.getInstance().getHeader(receivedAckMsg.packetType, receivedAckMsg.no_persist ? 1 : 0, receivedAckMsg.red_dot ? 1 : 0, receivedAckMsg.sync_once ? 1 : 0));
        wkWrite.writeBytes(remainingBytes);
        BigInteger bigInteger = new BigInteger(receivedAckMsg.messageID);
        wkWrite.writeLong(bigInteger.longValue());
        wkWrite.writeInt(receivedAckMsg.messageSeq);
        return wkWrite.getWriteBytes();
    }

    byte[] enPingMsg(WKPingMsg pingMsg) {
        WKWrite wkWrite = new WKWrite(1);
        wkWrite.writeByte(WKTypeUtils.getInstance().getHeader(pingMsg.packetType, pingMsg.flag, 0, 0));
        return wkWrite.getWriteBytes();
    }

    byte[] enSendMsg(WKSendMsg sendMsg) {
        // 先加密内容
        String sendContent = sendMsg.getSendContent();
        String msgKeyContent = sendMsg.getMsgKey();
        byte[] remainingBytes = WKTypeUtils.getInstance().getRemainingLengthByte(sendMsg.getRemainingLength());
        int totalLen = sendMsg.getTotalLength();
        WKWrite wkWrite = new WKWrite(totalLen);
        try {
            wkWrite.writeByte(WKTypeUtils.getInstance().getHeader(sendMsg.packetType, sendMsg.no_persist ? 1 : 0, sendMsg.red_dot ? 1 : 0, sendMsg.sync_once ? 1 : 0));
            wkWrite.writeBytes(remainingBytes);
            wkWrite.writeByte(WKTypeUtils.getInstance().getMsgSetting(sendMsg.setting));
            wkWrite.writeInt(sendMsg.clientSeq);
            wkWrite.writeString(sendMsg.clientMsgNo);
            wkWrite.writeString(sendMsg.channelId);
            wkWrite.writeByte(sendMsg.channelType);
            wkWrite.writeString(msgKeyContent);
            if (sendMsg.setting.topic == 1) {
                wkWrite.writeString(sendMsg.topicID);
            }
            wkWrite.writePayload(sendContent);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return wkWrite.getWriteBytes();
    }

    private WKConnectAckMsg deConnectAckMsg(WKRead wkRead) {
        WKConnectAckMsg connectAckMsg = new WKConnectAckMsg();
        try {
            long time = wkRead.readLong();
            short reasonCode = wkRead.readByte();
            String serverKey = wkRead.readString();
            String salt = wkRead.readString();
            connectAckMsg.serverKey = serverKey;
            connectAckMsg.salt = salt;
            //保存公钥和安全码
            CryptoUtils.getInstance().setServerKeyAndSalt(connectAckMsg.serverKey, connectAckMsg.salt);
            connectAckMsg.timeDiff = time;
            connectAckMsg.reasonCode = reasonCode;
        } catch (IOException e) {
            WKLoggerUtils.getInstance().d("解码连接ack错误");
        }

        return connectAckMsg;
    }

    private WKSendAckMsg deSendAckMsg(WKRead wkRead) {
        WKSendAckMsg sendAckMsg = new WKSendAckMsg();
        try {
            sendAckMsg.messageID = wkRead.readMsgID();
            sendAckMsg.clientSeq = wkRead.readInt();
            sendAckMsg.messageSeq = wkRead.readInt();
            sendAckMsg.reasonCode = wkRead.readByte();
            WKLoggerUtils.getInstance().e("发送返回状态：" + sendAckMsg.reasonCode);
        } catch (IOException e) {
            WKLoggerUtils.getInstance().e("解码发送消息ack错误");
        }
        return sendAckMsg;
    }

    private WKDisconnectMsg deDisconnectMsg(WKRead wkRead) {
        WKDisconnectMsg disconnectMsg = new WKDisconnectMsg();
        try {
            disconnectMsg.reasonCode = wkRead.readByte();
            disconnectMsg.reason = wkRead.readString();
            WKLoggerUtils.getInstance().e("sdk收到被踢的消息code:" + disconnectMsg.reasonCode + ",reason:" + disconnectMsg.reason);
            return disconnectMsg;

        } catch (IOException e) {
            WKLoggerUtils.getInstance().e("解码断开连接错误");
        }
        return disconnectMsg;
    }

    private WKReceivedMsg deReceivedMsg(WKRead wkRead) {
        WKReceivedMsg receivedMsg = new WKReceivedMsg();
        try {
            byte settingByte = wkRead.readByte();
            receivedMsg.setting = WKTypeUtils.getInstance().getMsgSetting(settingByte);
            receivedMsg.msgKey = wkRead.readString();
            receivedMsg.fromUID = wkRead.readString();
            receivedMsg.channelID = wkRead.readString();
            receivedMsg.channelType = wkRead.readByte();
            receivedMsg.clientMsgNo = wkRead.readString();
            if (receivedMsg.setting.stream == 1) {
                receivedMsg.streamNO = wkRead.readString();
                receivedMsg.streamSeq = wkRead.readInt();
                receivedMsg.streamFlag = wkRead.readByte();
            }
            receivedMsg.messageID = wkRead.readMsgID();
            receivedMsg.messageSeq = wkRead.readInt();
            receivedMsg.messageTimestamp = wkRead.readInt();
            if (receivedMsg.setting.topic == 1) {
                receivedMsg.topicID = wkRead.readString();
            }
            String content = wkRead.readPayload();
            receivedMsg.payload = CryptoUtils.getInstance().aesDecrypt(CryptoUtils.getInstance().base64Decode(content));
            String msgKey = receivedMsg.messageID
                    + receivedMsg.messageSeq
                    + receivedMsg.clientMsgNo
                    + receivedMsg.messageTimestamp
                    + receivedMsg.fromUID
                    + receivedMsg.channelID
                    + receivedMsg.channelType
                    + content;
            byte[] result = CryptoUtils.getInstance().aesEncrypt(msgKey);
            String base64Result = CryptoUtils.getInstance().base64Encode(result);
            String localMsgKey = CryptoUtils.getInstance().digestMD5(base64Result);
            if (!localMsgKey.equals(receivedMsg.msgKey)) {
                return null;
            }
            WKLoggerUtils.getInstance().e("接受到消息:" + receivedMsg.payload);
        } catch (IOException e) {
            WKLoggerUtils.getInstance().e("解码收到消息错误");
        }
        return receivedMsg;
    }

    WKBaseMsg decodeMessage(byte[] bytes) {
        try {
            WKRead wkRead = new WKRead(bytes);
            int packetType = wkRead.readPacketType();
            wkRead.readRemainingLength();
            if (packetType == WKMsgType.CONNACK) {
                return deConnectAckMsg(wkRead);
            } else if (packetType == WKMsgType.SENDACK) {
                return deSendAckMsg(wkRead);
            } else if (packetType == WKMsgType.DISCONNECT) {
                return deDisconnectMsg(wkRead);
            } else if (packetType == WKMsgType.RECVEIVED) {
                return deReceivedMsg(wkRead);
            } else if (packetType == WKMsgType.PONG) {
                return new WKPongMsg();
            } else {
                WKLoggerUtils.getInstance().e("解析协议类型失败--->：" + packetType);
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            WKLoggerUtils.getInstance().e("解析数据异常------>：" + e.getMessage());
            return null;
        }
    }

    /**
     * 获取发送的消息
     *
     * @param msg 本地消息
     * @return 网络消息
     */
    WKBaseMsg getSendBaseMsg(WKMsg msg) {
        //发送消息
        JSONObject jsonObject = null;
        if (msg.baseContentMsgModel != null) {
            jsonObject = msg.baseContentMsgModel.encodeMsg();
        } else {
            msg.baseContentMsgModel = new WKMessageContent();
        }
        try {
            if (jsonObject == null) jsonObject = new JSONObject();
            if (!jsonObject.has(WKDBColumns.WKMessageColumns.from_uid)) {
                jsonObject.put(WKDBColumns.WKMessageColumns.from_uid, WKIMApplication.getInstance().getUid());
            }
            jsonObject.put(WKDBColumns.WKMessageColumns.type, msg.type);
            //判断@情况
            if (msg.baseContentMsgModel.mentionInfo != null
                    && msg.baseContentMsgModel.mentionInfo.uids != null
                    && msg.baseContentMsgModel.mentionInfo.uids.size() > 0) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0, size = msg.baseContentMsgModel.mentionInfo.uids.size(); i < size; i++) {
                    jsonArray.put(msg.baseContentMsgModel.mentionInfo.uids.get(i));
                }
                if (!jsonObject.has("mention")) {
                    JSONObject mentionJson = new JSONObject();
                    mentionJson.put("all", msg.baseContentMsgModel.mentionAll);
                    mentionJson.put("uids", jsonArray);
                    jsonObject.put("mention", mentionJson);
                }

            } else {
                if (msg.baseContentMsgModel.mentionAll == 1) {
                    JSONObject mentionJson = new JSONObject();
                    mentionJson.put("all", msg.baseContentMsgModel.mentionAll);
                    jsonObject.put("mention", mentionJson);
                }
            }
            // 被回复消息
            if (msg.baseContentMsgModel.reply != null) {
                jsonObject.put("reply", msg.baseContentMsgModel.reply.encodeMsg());
            }
            // 机器人ID
            if (!TextUtils.isEmpty(msg.baseContentMsgModel.robotID)) {
                jsonObject.put("robot_id", msg.baseContentMsgModel.robotID);
            }
            if (msg.baseContentMsgModel.entities != null && msg.baseContentMsgModel.entities.size() > 0) {
                JSONArray jsonArray = new JSONArray();
                for (WKMsgEntity entity : msg.baseContentMsgModel.entities) {
                    JSONObject jo = new JSONObject();
                    jo.put("offset", entity.offset);
                    jo.put("length", entity.length);
                    jo.put("type", entity.type);
                    jo.put("value", entity.value);
                    jsonArray.put(jo);
                }
                jsonObject.put("entities", jsonArray);
            }
            if (msg.baseContentMsgModel.flame != 0) {
                jsonObject.put("flame_second", msg.baseContentMsgModel.flameSecond);
                jsonObject.put("flame", msg.baseContentMsgModel.flame);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        WKSendMsg sendMsg = new WKSendMsg();
        // 默认先设置clientSeq，因为有可能本条消息并不需要入库，UI上自己设置了clientSeq
        sendMsg.clientSeq = (int) msg.clientSeq;
        sendMsg.sync_once = msg.header.syncOnce;
        sendMsg.no_persist = msg.header.noPersist;
        sendMsg.red_dot = msg.header.redDot;
        sendMsg.clientMsgNo = msg.clientMsgNO;
        sendMsg.channelId = msg.channelID;
        sendMsg.channelType = msg.channelType;
        sendMsg.topicID = msg.topicID;
        if (msg.setting == null) msg.setting = new WKMsgSetting();
        sendMsg.setting = msg.setting;
        msg.content = jsonObject.toString();
        long tempOrderSeq = MsgDbManager.getInstance().queryMaxOrderSeqWithChannel(msg.channelID, msg.channelType);
        msg.orderSeq = tempOrderSeq + 1;
        // 需要存储的消息入库后更改消息的clientSeq
        if (!sendMsg.no_persist) {
            sendMsg.clientSeq = (int) (msg.clientSeq = (int) MsgDbManager.getInstance().insert(msg));
            if (msg.clientSeq > 0) {
                // 2022/4/27
                WKUIConversationMsg uiMsg = WKIM.getInstance().getConversationManager().updateWithWKMsg(msg);
                if (uiMsg != null) {
                    long browseTo = WKIM.getInstance().getMsgManager().getMaxMessageSeqWithChannel(uiMsg.channelID, uiMsg.channelType);
                    if (uiMsg.getRemoteMsgExtra() == null) {
                        uiMsg.setRemoteMsgExtra(new WKConversationMsgExtra());
                    }
                    uiMsg.getRemoteMsgExtra().browseTo = browseTo;
                    WKIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsg, true, "getSendBaseMsg");
                }
            }
        }
        if (WKMediaMessageContent.class.isAssignableFrom(msg.baseContentMsgModel.getClass())) {
            //多媒体数据
            if (jsonObject.has("localPath")) {
                jsonObject.remove("localPath");
            }
            //视频地址
            if (jsonObject.has("videoLocalPath")) {
                jsonObject.remove("videoLocalPath");
            }
        }
        sendMsg.payload = jsonObject.toString();
        WKLoggerUtils.getInstance().e(jsonObject.toString());
        return sendMsg;
    }

    WKMsg baseMsg2WKMsg(WKBaseMsg baseMsg) {
        WKReceivedMsg receivedMsg = (WKReceivedMsg) baseMsg;
        WKMsg msg = new WKMsg();
        msg.channelType = receivedMsg.channelType;
        msg.channelID = receivedMsg.channelID;
        msg.content = receivedMsg.payload;
        msg.messageID = receivedMsg.messageID;
        msg.messageSeq = receivedMsg.messageSeq;
        msg.timestamp = receivedMsg.messageTimestamp;
        msg.fromUID = receivedMsg.fromUID;
        msg.setting = receivedMsg.setting;
        msg.clientMsgNO = receivedMsg.clientMsgNo;
        msg.status = WKSendMsgResult.send_success;
        msg.topicID = receivedMsg.topicID;

        msg.orderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(msg.messageSeq, msg.channelID, msg.channelType);
        msg.isDeleted = isDelete(msg.content);
        return msg;
    }

    private int isDelete(String contentJson) {
        int isDelete = 0;
        if (!TextUtils.isEmpty(contentJson)) {
            try {
                JSONObject jsonObject = new JSONObject(contentJson);
                isDelete = WKIM.getInstance().getMsgManager().isDeletedMsg(jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return isDelete;
    }
}