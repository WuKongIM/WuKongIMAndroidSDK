package com.wukong.im.message;

import android.text.TextUtils;

import com.wukong.im.WKIM;
import com.wukong.im.WKIMApplication;
import com.wukong.im.db.MsgDbManager;
import com.wukong.im.db.WKDBColumns;
import com.wukong.im.entity.WKConversationMsgExtra;
import com.wukong.im.entity.WKMsg;
import com.wukong.im.entity.WKMsgSetting;
import com.wukong.im.entity.WKUIConversationMsg;
import com.wukong.im.message.type.WKMsgType;
import com.wukong.im.message.type.WKSendMsgResult;
import com.wukong.im.msgmodel.WKMediaMessageContent;
import com.wukong.im.protocol.WKBaseMsg;
import com.wukong.im.protocol.WKConnectAckMsg;
import com.wukong.im.protocol.WKConnectMsg;
import com.wukong.im.protocol.WKDisconnectMsg;
import com.wukong.im.protocol.WKMessageContent;
import com.wukong.im.protocol.WKMsgEntity;
import com.wukong.im.protocol.WKPingMsg;
import com.wukong.im.protocol.WKPongMsg;
import com.wukong.im.protocol.WKReceivedAckMsg;
import com.wukong.im.protocol.WKReceivedMsg;
import com.wukong.im.protocol.WKSendAckMsg;
import com.wukong.im.protocol.WKSendMsg;
import com.wukong.im.utils.AESEncryptUtils;
import com.wukong.im.utils.BigTypeUtils;
import com.wukong.im.utils.Curve25519Utils;
import com.wukong.im.utils.WKLoggerUtils;
import com.wukong.im.utils.WKTypeUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 5/21/21 11:28 AM
 * 收发消息转换
 */
class MessageConvertHandler {

    private MessageConvertHandler() {
    }

    private static class MessageConvertHandlerBinder {
        static final MessageConvertHandler msgConvert = new MessageConvertHandler();
    }

    public static MessageConvertHandler getInstance() {
        return MessageConvertHandlerBinder.msgConvert;
    }

    byte[] enConnectMsg(WKConnectMsg connectMsg) {
        int remainingLength = connectMsg.getFixedHeaderLength()
                + connectMsg.deviceIDLength
                + connectMsg.deviceID.length()
                + connectMsg.uidLength
                + WKIMApplication.getInstance().getUid().length()
                + connectMsg.tokenLength
                + WKIMApplication.getInstance().getToken().length()
                + connectMsg.clientTimeStampLength
                + connectMsg.clientKeyLength
                + Curve25519Utils.getInstance().getPublicKey().length();

        byte[] remainingBytes = WKTypeUtils.getInstance().getRemainingLengthByte(remainingLength);
        int totalLen = 1 + remainingBytes.length
                + connectMsg.protocolVersionLength
                + connectMsg.deviceFlagLength
                + connectMsg.deviceIDLength
                + connectMsg.deviceID.length()
                + connectMsg.uidLength
                + WKIMApplication.getInstance().getUid().length()
                + connectMsg.tokenLength
                + WKIMApplication.getInstance().getToken().length()
                + connectMsg.clientTimeStampLength
                + connectMsg.clientKeyLength
                + Curve25519Utils.getInstance().getPublicKey().length();

        byte[] bytes = new byte[totalLen];
        ByteBuffer buffer = ByteBuffer.allocate(totalLen).order(
                ByteOrder.BIG_ENDIAN);
        try {
            //固定头
            buffer.put(WKTypeUtils.getInstance().getHeader(connectMsg.packetType, connectMsg.flag, 0, 0));
            buffer.put(remainingBytes);
            buffer.put(connectMsg.protocolVersion);
            buffer.put(connectMsg.deviceFlag);
            buffer.putShort((short) connectMsg.deviceID.length());
            buffer.put(WKTypeUtils.getInstance().stringToByte(connectMsg.deviceID));
            buffer.putShort((short) WKIMApplication.getInstance().getUid().length());
            buffer.put(WKTypeUtils.getInstance().stringToByte(WKIMApplication.getInstance().getUid()));
            buffer.putShort((short) WKIMApplication.getInstance().getToken().length());
            buffer.put(WKTypeUtils.getInstance().stringToByte(WKIMApplication.getInstance().getToken()));
            buffer.putLong(connectMsg.clientTimestamp);
            buffer.putShort((short) Curve25519Utils.getInstance().getPublicKey().length());
            buffer.put(WKTypeUtils.getInstance().stringToByte(Curve25519Utils.getInstance().getPublicKey()));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        buffer.position(0);
        buffer.get(bytes);
        return bytes;
    }

    synchronized byte[] enReceivedAckMsg(WKReceivedAckMsg receivedAckMsg) {
        byte[] remainingBytes = WKTypeUtils.getInstance().getRemainingLengthByte(8 + 4);

        int totalLen = 1 + remainingBytes.length + 8 + 4;
        byte[] bytes = new byte[totalLen];
        ByteBuffer buffer = ByteBuffer.allocate(totalLen).order(
                ByteOrder.BIG_ENDIAN);
        //固定头
        buffer.put(WKTypeUtils.getInstance().getHeader(receivedAckMsg.packetType, receivedAckMsg.no_persist ? 1 : 0, receivedAckMsg.red_dot ? 1 : 0, receivedAckMsg.sync_once ? 1 : 0));
        buffer.put(remainingBytes);
        BigInteger bigInteger = new BigInteger(receivedAckMsg.messageID);
        buffer.putLong(bigInteger.longValue());
        buffer.putInt(receivedAckMsg.messageSeq);
        buffer.position(0);
        buffer.get(bytes);
        return bytes;
    }

    byte[] enPingMsg(WKPingMsg pingMsg) {
        int totalLen = 1;
        byte[] bytes = new byte[totalLen];
        ByteBuffer buffer = ByteBuffer.allocate(totalLen).order(
                ByteOrder.BIG_ENDIAN);
        //固定头
        buffer.put(WKTypeUtils.getInstance().getHeader(pingMsg.packetType, pingMsg.flag, 0, 0));
        buffer.position(0);
        buffer.get(bytes);
        return bytes;
    }

    byte[] enSendMsg(WKSendMsg sendMsg) {
        // 先加密内容
        byte[] contentByte = AESEncryptUtils.aesEncrypt(sendMsg.payload, Curve25519Utils.getInstance().aesKey, Curve25519Utils.getInstance().salt);
        String sendContent = AESEncryptUtils.base64Encode(contentByte);
        String msgKey = sendMsg.clientSeq
                + sendMsg.clientMsgNo
                + sendMsg.channelId
                + sendMsg.channelType
                + sendContent;
        byte[] msgKeyByte = AESEncryptUtils.aesEncrypt(msgKey, Curve25519Utils.getInstance().aesKey, Curve25519Utils.getInstance().salt);
        String msgKeyContent = AESEncryptUtils.base64Encode(msgKeyByte);
        msgKeyContent = AESEncryptUtils.digest(msgKeyContent);

        int topicLen = 0;
        if (sendMsg.setting.topic == 1) {
            topicLen = sendMsg.topicID.length();
            topicLen += sendMsg.topicIDLength;
        }
        int remainingLength = sendMsg.settingLength
                + sendMsg.clientSeqLength
                + sendMsg.clientMsgNoLength + sendMsg.clientMsgNo.length()
                + sendMsg.channelIdLength + sendMsg.channelId.length()
                + sendMsg.channelTypeLength
                + sendMsg.msgKeyLength + msgKeyContent.length()
                + topicLen
                + sendContent.getBytes().length;

        byte[] remainingBytes = WKTypeUtils.getInstance().getRemainingLengthByte(remainingLength);

        int totalLen = 1 + remainingBytes.length
                + sendMsg.settingLength
                + sendMsg.clientSeqLength
                + sendMsg.clientMsgNoLength
                + sendMsg.clientMsgNo.length()
                + sendMsg.channelIdLength
                + sendMsg.channelId.length()
                + sendMsg.channelTypeLength
                + sendMsg.msgKeyLength
                + msgKeyContent.length()
                + topicLen
                + sendContent.getBytes().length;

        byte[] bytes = new byte[totalLen];
        ByteBuffer buffer = ByteBuffer.allocate(totalLen).order(
                ByteOrder.BIG_ENDIAN);
        try {
            //固定头
            buffer.put(WKTypeUtils.getInstance().getHeader(sendMsg.packetType, sendMsg.no_persist ? 1 : 0, sendMsg.red_dot ? 1 : 0, sendMsg.sync_once ? 1 : 0));
            buffer.put(remainingBytes);
            //消息设置
            buffer.put(WKTypeUtils.getInstance().getMsgSetting(sendMsg.setting));

            buffer.putInt(sendMsg.clientSeq);
            buffer.putShort((short) sendMsg.clientMsgNo.length());
            buffer.put(WKTypeUtils.getInstance().stringToByte(sendMsg.clientMsgNo));
            buffer.putShort((short) sendMsg.channelId.length());
            buffer.put(WKTypeUtils.getInstance().stringToByte(sendMsg.channelId));
            buffer.put(sendMsg.channelType);
            buffer.putShort((short) msgKeyContent.length());
            buffer.put(WKTypeUtils.getInstance().stringToByte(msgKeyContent));
            // 添加话题
            if (sendMsg.setting.topic == 1) {
                buffer.putShort((short) sendMsg.topicID.length());
                buffer.put(WKTypeUtils.getInstance().stringToByte(sendMsg.topicID));
            }
            byte[] contentBytes = WKTypeUtils.getInstance().stringToByte(sendContent);
            buffer.put(contentBytes);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        buffer.position(0);
        buffer.get(bytes);
        return bytes;
    }

    WKBaseMsg decodeMessage(byte[] bytes) {
        //包类型
        InputStream inputStream = new ByteArrayInputStream(bytes);
        byte[] fixedHeader = new byte[1];
        try {
            int headerRead = inputStream.read(fixedHeader);
            if (headerRead == -1) return null;
            int packetType = WKTypeUtils.getInstance().getHeight4(fixedHeader[0]);
            int remainingLength = WKTypeUtils.getInstance().bytes2Length(inputStream);
            if (packetType == WKMsgType.CONNACK) {
                // 连接ack
                WKConnectAckMsg connectAckMsg = new WKConnectAckMsg();
                int read;

                // 客户端时间与服务器的差值，单位毫秒
                byte[] length_byte = new byte[8];
                read = inputStream.read(length_byte);
                if (read == -1) {
                    return connectAckMsg;
                }
                long time = BigTypeUtils.getInstance().bytesToLong(length_byte);

                // 连接原因码
                byte[] reasonByte = new byte[1];
                read = inputStream.read(reasonByte);
                if (read == -1) {
                    return connectAckMsg;
                }

                // 获取公钥长度
                byte[] serverKeyLengthByte = new byte[2];
                read = inputStream.read(serverKeyLengthByte);
                if (read == -1) return connectAckMsg;
                short serverKeyLength = BigTypeUtils.getInstance().byteToShort(serverKeyLengthByte);
                // 服务端公钥
                byte[] serverKeyByte = new byte[serverKeyLength];
                read = inputStream.read(serverKeyByte);
                if (read == -1) return connectAckMsg;
                String serverKey = WKTypeUtils.getInstance().bytesToString(serverKeyByte);
                // 获取安全码AES加密需要
                byte[] saltLengthByte = new byte[2];
                read = inputStream.read(saltLengthByte);
                if (read == -1) return connectAckMsg;
                short saltLength = BigTypeUtils.getInstance().byteToShort(saltLengthByte);
                // 安全码
                byte[] saltByte = new byte[saltLength];
                read = inputStream.read(saltByte);
                if (read == -1) return connectAckMsg;
                String salt = WKTypeUtils.getInstance().bytesToString(saltByte);
                connectAckMsg.serverKey = serverKey;
                connectAckMsg.salt = salt;
                //保存公钥和安全码
                Curve25519Utils.getInstance().setServerKeyAndSalt(connectAckMsg.serverKey, connectAckMsg.salt);

                connectAckMsg.timeDiff = time;
                connectAckMsg.remainingLength = remainingLength;
                connectAckMsg.reasonCode = reasonByte[0];
                return connectAckMsg;
            } else if (packetType == WKMsgType.SENDACK) {
                WKSendAckMsg sendAckMsg = new WKSendAckMsg();
                // 发送消息ack
                byte[] messageId = new byte[8];
                int read = inputStream.read(messageId);
                if (read == -1) return sendAckMsg;
                BigInteger bigInteger = new BigInteger(messageId);
                if (bigInteger.toString().startsWith("-")) {
                    BigInteger temp = new BigInteger("18446744073709551616");
                    sendAckMsg.messageID = temp.add(bigInteger).toString();
                } else
                    sendAckMsg.messageID = bigInteger.toString();
                //sendAckMsg.messageID = BigTypeUtils.getInstance().bytesToLong(messageId) + "";
                WKLoggerUtils.getInstance().e("发送ack msgid：" + sendAckMsg.messageID);
                // 客户端序列号
                byte[] clientSeq = new byte[4];
                read = inputStream.read(clientSeq);
                if (read == -1) return sendAckMsg;
                sendAckMsg.clientSeq = BigTypeUtils.getInstance().bytesToInt(clientSeq);

                // 服务器序号
                byte[] messageSqe = new byte[4];
                read = inputStream.read(messageSqe);
                if (read == -1) return sendAckMsg;
                sendAckMsg.messageSeq = BigTypeUtils.getInstance().bytesToInt(messageSqe);

                // 失败原因
                byte[] reasonCode = new byte[1];
                read = inputStream.read(reasonCode);
                if (read == -1) return sendAckMsg;
                sendAckMsg.reasonCode = reasonCode[0];
                WKLoggerUtils.getInstance().e("发送返回原因："+sendAckMsg.reasonCode);
                return sendAckMsg;
            } else if (packetType == WKMsgType.DISCONNECT) {
                WKDisconnectMsg disconnectMsg = new WKDisconnectMsg();
                byte[] reasonCode = new byte[1];
                int read = inputStream.read(reasonCode);
                if (read == -1) return disconnectMsg;
                disconnectMsg.reasonCode = reasonCode[0];
                byte[] reasonByte = new byte[remainingLength - 1];
                if (reasonByte.length != 0) {
                    read = inputStream.read(reasonByte);
                    if (read == -1) return disconnectMsg;
                    disconnectMsg.reason = WKTypeUtils.getInstance().bytesToString(reasonByte);
                }
                WKLoggerUtils.getInstance().e("sdk收到被踢的消息--->");
                WKLoggerUtils.getInstance().e("被踢的原因：" + disconnectMsg.reason);
                return disconnectMsg;
            } else if (packetType == WKMsgType.RECVEIVED) {
                //接受消息
                WKReceivedMsg receivedMsg = new WKReceivedMsg();
                int read;
                //消息设置
                byte[] setting = new byte[1];
                read = inputStream.read(setting);
                if (read == -1) return receivedMsg;
                receivedMsg.setting = WKTypeUtils.getInstance().getMsgSetting(setting[0]);

                // 消息Key
                byte[] msgKeyLengthByte = new byte[2];
                read = inputStream.read(msgKeyLengthByte);
                if (read == -1) return receivedMsg;
                short msgKeyLength = BigTypeUtils.getInstance().byteToShort(msgKeyLengthByte);
                byte[] msgKeyByte = new byte[msgKeyLength];
                read = inputStream.read(msgKeyByte);
                if (read == -1) return receivedMsg;
                receivedMsg.msgKey = WKTypeUtils.getInstance().bytesToString(msgKeyByte);

                // 发送者ID
                byte[] fromUIDLengthByte = new byte[2];
                read = inputStream.read(fromUIDLengthByte);
                if (read == -1) return receivedMsg;
                short fromUIDLength = BigTypeUtils.getInstance().byteToShort(fromUIDLengthByte);
                byte[] fromUIDByte = new byte[fromUIDLength];
                read = inputStream.read(fromUIDByte);
                if (read == -1) return receivedMsg;
                receivedMsg.fromUID = WKTypeUtils.getInstance().bytesToString(fromUIDByte);

                // 频道id长度
                byte[] channelIdLengthByte = new byte[2];
                read = inputStream.read(channelIdLengthByte);
                if (read == -1) return receivedMsg;
                // 频道id
                short channelIdLength = BigTypeUtils.getInstance().byteToShort(channelIdLengthByte);
                byte[] channelIDByte = new byte[channelIdLength];
                read = inputStream.read(channelIDByte);
                if (read == -1) return receivedMsg;
                receivedMsg.channelID = WKTypeUtils.getInstance().bytesToString(channelIDByte);

                // 频道类型
                byte[] channelType = new byte[1];
                read = inputStream.read(channelType);
                if (read == -1) return receivedMsg;
                receivedMsg.channelType = channelType[0];

                //解析客户端ID
                byte[] clientMsgNoLengthByte = new byte[2];
                read = inputStream.read(clientMsgNoLengthByte);
                if (read == -1) return receivedMsg;
                short clientMsgNoLength = BigTypeUtils.getInstance().byteToShort(clientMsgNoLengthByte);
                // 客户端编号
                byte[] clientMsgNoByte = new byte[clientMsgNoLength];
                read = inputStream.read(clientMsgNoByte);
                if (read == -1) return receivedMsg;
                receivedMsg.clientMsgNo = WKTypeUtils.getInstance().bytesToString(clientMsgNoByte);

                // 消息ID
                byte[] messageId = new byte[8];
                read = inputStream.read(messageId);
                if (read == -1) return receivedMsg;
                BigInteger bigInteger = new BigInteger(messageId);
                if (bigInteger.toString().startsWith("-")) {
                    BigInteger temp = new BigInteger("18446744073709551616");
                    receivedMsg.messageID = temp.add(bigInteger).toString();
                } else
                    receivedMsg.messageID = bigInteger.toString();
                //receivedMsg.messageID = BigTypeUtils.getInstance().bytesToLong(messageId)+ "";

                //消息序列号
                byte[] messageSqe = new byte[4];
                read = inputStream.read(messageSqe);
                if (read == -1) return receivedMsg;
                receivedMsg.messageSeq = BigTypeUtils.getInstance().bytesToInt(messageSqe);

                //消息时间
                byte[] messageTime = new byte[4];
                read = inputStream.read(messageTime);
                if (read == -1) return receivedMsg;
                receivedMsg.messageTimestamp = BigTypeUtils.getInstance().bytesToInt(messageTime);


                // 话题ID
                short topicLength = 0;
                if (receivedMsg.setting.topic == 1) {
                    byte[] topicLengthByte = new byte[2];
                    read = inputStream.read(topicLengthByte);
                    if (read == -1) return receivedMsg;
                    topicLength = BigTypeUtils.getInstance().byteToShort(topicLengthByte);
                    byte[] topicByte = new byte[topicLength];
                    read = inputStream.read(topicByte);
                    if (read == -1) return receivedMsg;
                    receivedMsg.topicID = WKTypeUtils.getInstance().bytesToString(topicByte);
                    // 默认加上字符串标示长度2
                    topicLength += 2;
                }

                // 消息内容
                // 消息ID长度8 + 消息序列号长度4 + 消息时间长度4 + setting1 + (客户端ID长度+字符串标示长度2) （频道ID长度+字符串标示长度2） + 频道类型长度1 +(话题长度+字符串标示长度2) +（发送者uid长度+字符串标示长度2）
                byte[] payload = new byte[remainingLength - (8 + 4 + 2 + 1 + msgKeyLength + 4 + (clientMsgNoLength + 2) + (channelIdLength + 2) + 1 + topicLength + (2 + fromUIDLength))];
                read = inputStream.read(payload);
                if (read == -1) return receivedMsg;

                String content = WKTypeUtils.getInstance().bytesToString(payload);
                receivedMsg.payload = AESEncryptUtils.aesDecrypt(AESEncryptUtils.base64Decode(content), Curve25519Utils.getInstance().aesKey, Curve25519Utils.getInstance().salt);
                String msgKey = receivedMsg.messageID
                        + receivedMsg.messageSeq
                        + receivedMsg.clientMsgNo
                        + receivedMsg.messageTimestamp
                        + receivedMsg.fromUID
                        + receivedMsg.channelID
                        + receivedMsg.channelType
                        + content;
                byte[] result = AESEncryptUtils.aesEncrypt(msgKey, Curve25519Utils.getInstance().aesKey, Curve25519Utils.getInstance().salt);
                String base64Result = AESEncryptUtils.base64Encode(result);
                String localMsgKey = AESEncryptUtils.digest(base64Result);
                if (!localMsgKey.equals(receivedMsg.msgKey)) {
                    return null;
                }

                WKLoggerUtils.getInstance().e("接受到消息:" + receivedMsg.payload);
                return receivedMsg;
            } else if (packetType == WKMsgType.PONG) {
                WKLoggerUtils.getInstance().e("Pong消息--->");
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
        long tempOrderSeq = MsgDbManager.getInstance().getMaxOrderSeq(msg.channelID, msg.channelType);
        msg.orderSeq = tempOrderSeq + 1;
        // 需要存储的消息入库后更改消息的clientSeq
        if (!sendMsg.no_persist) {
            sendMsg.clientSeq = (int) (msg.clientSeq = (int) MsgDbManager.getInstance().insertMsg(msg));
            if (msg.clientSeq > 0) {
                // TODO: 2022/4/27
                WKUIConversationMsg uiMsg = WKIM.getInstance().getConversationManager().updateWithWKMsg(msg);
                if (uiMsg != null) {
                    long browseTo = WKIM.getInstance().getMsgManager().getMaxMessageSeq(uiMsg.channelID, uiMsg.channelType);
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

    private static int isDelete(String contentJson) {
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
