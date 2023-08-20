package com.xinbida.wukongim.message;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.db.ConversationDbManager;
import com.xinbida.wukongim.db.WKDBColumns;
import com.xinbida.wukongim.db.MsgDbManager;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKSyncMsg;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.interfaces.IReceivedMsgListener;
import com.xinbida.wukongim.manager.CMDManager;
import com.xinbida.wukongim.manager.ConversationManager;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.message.type.WKMsgContentType;
import com.xinbida.wukongim.message.type.WKMsgType;
import com.xinbida.wukongim.protocol.WKBaseMsg;
import com.xinbida.wukongim.protocol.WKConnectAckMsg;
import com.xinbida.wukongim.protocol.WKConnectMsg;
import com.xinbida.wukongim.protocol.WKDisconnectMsg;
import com.xinbida.wukongim.protocol.WKPingMsg;
import com.xinbida.wukongim.protocol.WKPongMsg;
import com.xinbida.wukongim.protocol.WKReceivedAckMsg;
import com.xinbida.wukongim.protocol.WKSendAckMsg;
import com.xinbida.wukongim.protocol.WKSendMsg;
import com.xinbida.wukongim.utils.WKLoggerUtils;
import com.xinbida.wukongim.utils.WKTypeUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.xsocket.connection.INonBlockingConnection;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 5/21/21 11:25 AM
 * msg handler
 */
public class MessageHandler {
    private MessageHandler() {
    }

    private static class MessageHandlerBinder {
        static final MessageHandler handler = new MessageHandler();
    }

    public static MessageHandler getInstance() {
        return MessageHandlerBinder.handler;
    }

    int sendMessage(INonBlockingConnection connection, WKBaseMsg msg) {
        if (msg == null) {
            return 1;
        }
        byte[] bytes;
        if (msg.packetType == WKMsgType.CONNECT) {
            // 连接
            bytes = MessageConvertHandler.getInstance().enConnectMsg((WKConnectMsg) msg);
        } else if (msg.packetType == WKMsgType.REVACK) {
            // 收到消息回执
            bytes = MessageConvertHandler.getInstance().enReceivedAckMsg((WKReceivedAckMsg) msg);
        } else if (msg.packetType == WKMsgType.SEND) {
            // 发送聊天消息
            bytes = MessageConvertHandler.getInstance().enSendMsg((WKSendMsg) msg);
        } else if (msg.packetType == WKMsgType.PING) {
            // 发送心跳
            bytes = MessageConvertHandler.getInstance().enPingMsg((WKPingMsg) msg);
            WKLoggerUtils.getInstance().e("ping...");
        } else {
            // 其他消息
            WKLoggerUtils.getInstance().e("发送未知消息类型");
            return 1;
        }

        if (connection != null && connection.isOpen()) {
            try {
                connection.write(bytes, 0, bytes.length);
                connection.flush();
                return 1;
            } catch (BufferOverflowException e) {
                e.printStackTrace();
                WKLoggerUtils.getInstance().e("sendMessages Exception BufferOverflowException"
                        + e.getMessage());
                return 0;
            } catch (ClosedChannelException e) {
                e.printStackTrace();
                WKLoggerUtils.getInstance().e("sendMessages Exception ClosedChannelException"
                        + e.getMessage());
                return 0;
            } catch (SocketTimeoutException e) {
                e.printStackTrace();
                WKLoggerUtils.getInstance().e("sendMessages Exception SocketTimeoutException"
                        + e.getMessage());
                return 0;
            } catch (IOException e) {
                e.printStackTrace();
                WKLoggerUtils.getInstance().e("sendMessages Exception IOException" + e.getMessage());
                return 0;
            }
        } else {
            WKLoggerUtils.getInstance().e("sendMessages Exception sendMessage conn null:"
                    + connection);
            return 0;
        }
    }


    private List<WKSyncMsg> receivedMsgList;
    private byte[] cacheData = null;

    synchronized void cutBytes(byte[] available_bytes,
                               IReceivedMsgListener mIReceivedMsgListener) {

        if (cacheData == null || cacheData.length == 0) cacheData = available_bytes;
        else {
            //如果上次还存在未解析完的消息将新数据追加到缓存数据中
            byte[] temp = new byte[available_bytes.length + cacheData.length];
            try {
                System.arraycopy(cacheData, 0, temp, 0, cacheData.length);
                System.arraycopy(available_bytes, 0, temp, cacheData.length, available_bytes.length);
                cacheData = temp;
            } catch (Exception e) {
                WKLoggerUtils.getInstance().e("多条消息合并错误" + e.getMessage());
            }

        }
        byte[] lastMsgBytes = cacheData;
        int readLength = 0;

        while (lastMsgBytes.length > 0 && readLength != lastMsgBytes.length) {

            readLength = lastMsgBytes.length;
            int packetType = WKTypeUtils.getInstance().getHeight4(lastMsgBytes[0]);
            // 是否不持久化：0。 是否显示红点：1。是否只同步一次：0
            //是否持久化[是否保存在数据库]
            int no_persist = WKTypeUtils.getInstance().getBit(lastMsgBytes[0], 0);
            //是否显示红点
            int red_dot = WKTypeUtils.getInstance().getBit(lastMsgBytes[0], 1);
            //是否只同步一次
            int sync_once = WKTypeUtils.getInstance().getBit(lastMsgBytes[0], 2);
            WKLoggerUtils.getInstance().e("是否不存储：" + no_persist + "是否显示红点：" + red_dot + "是否只同步一次：" + sync_once);
            WKLoggerUtils.getInstance().e("消息包类型" + packetType);
            if (packetType == WKMsgType.PONG) {
                //心跳ack
                mIReceivedMsgListener.heartbeatMsg(new WKPongMsg());
                WKLoggerUtils.getInstance().e("pong...");
                byte[] bytes = Arrays.copyOfRange(lastMsgBytes, 1, lastMsgBytes.length);
                cacheData = lastMsgBytes = bytes;
            } else {
                if (packetType < 10) {
                    // TODO: 2019-12-21 计算剩余长度
                    if (lastMsgBytes.length < 5) {
                        cacheData = lastMsgBytes;
                        break;
                    }
                    //其他消息类型
                    int remainingLength = WKTypeUtils.getInstance().getRemainingLength(Arrays.copyOfRange(lastMsgBytes, 1, lastMsgBytes.length));
                    if (remainingLength == -1) {
                        //剩余长度被分包
                        cacheData = lastMsgBytes;
                        break;
                    }
                    if (remainingLength > 1 << 21) {
                        cacheData = null;
                        break;
                    }
                    byte[] bytes = WKTypeUtils.getInstance().getRemainingLengthByte(remainingLength);
                    if (remainingLength + 1 + bytes.length > lastMsgBytes.length) {
                        //半包情况
                        cacheData = lastMsgBytes;
                    } else {
                        byte[] msg = Arrays.copyOfRange(lastMsgBytes, 0, remainingLength + 1 + bytes.length);
                        acceptMsg(msg, no_persist, sync_once, red_dot, mIReceivedMsgListener);
                        byte[] temps = Arrays.copyOfRange(lastMsgBytes, msg.length, lastMsgBytes.length);
                        cacheData = lastMsgBytes = temps;
                    }

                } else {
                    cacheData = null;
                    mIReceivedMsgListener.reconnect();
                    break;
                }
            }
        }
        saveReceiveMsg();
    }

    private void acceptMsg(byte[] bytes, int no_persist, int sync_once, int red_dot,
                           IReceivedMsgListener mIReceivedMsgListener) {

        if (bytes != null && bytes.length > 0) {
            WKBaseMsg g_msg;
            g_msg = MessageConvertHandler.getInstance().decodeMessage(bytes);
            if (g_msg != null) {
                //连接ack
                if (g_msg.packetType == WKMsgType.CONNACK) {
                    WKConnectAckMsg loginStatusMsg = (WKConnectAckMsg) g_msg;
                    mIReceivedMsgListener.loginStatusMsg(loginStatusMsg.reasonCode);
                } else if (g_msg.packetType == WKMsgType.SENDACK) {
                    //发送ack
                    WKSendAckMsg talkSendStatus = (WKSendAckMsg) g_msg;
                    WKMsg wkMsg = null;
                    if (no_persist == 0) {
                        wkMsg = MsgDbManager.getInstance().updateMsgSendStatus(talkSendStatus.clientSeq, talkSendStatus.messageSeq, talkSendStatus.messageID, talkSendStatus.reasonCode);
                    }
                    if (wkMsg == null) {
                        wkMsg = new WKMsg();
                        wkMsg.clientSeq = talkSendStatus.clientSeq;
                        wkMsg.messageID = talkSendStatus.messageID;
                        wkMsg.status = talkSendStatus.reasonCode;
                        wkMsg.messageSeq = (int) talkSendStatus.messageSeq;
                    }
                    WKIM.getInstance().getMsgManager().setSendMsgAck(wkMsg);

                    mIReceivedMsgListener
                            .sendAckMsg(talkSendStatus);
                } else if (g_msg.packetType == WKMsgType.RECVEIVED) {
                    //收到消息
                    WKMsg message = MessageConvertHandler.getInstance().baseMsg2WKMsg(g_msg);
                    message.header.noPersist = no_persist == 1;
                    message.header.redDot = red_dot == 1;
                    message.header.syncOnce = sync_once == 1;
                    handleReceiveMsg(message);
                    // mIReceivedMsgListener.receiveMsg(message);
                } else if (g_msg.packetType == WKMsgType.DISCONNECT) {
                    //被踢消息
                    WKDisconnectMsg disconnectMsg = (WKDisconnectMsg) g_msg;
                    mIReceivedMsgListener.kickMsg(disconnectMsg);
                } else if (g_msg.packetType == WKMsgType.PONG) {
                    mIReceivedMsgListener.heartbeatMsg((WKPongMsg) g_msg);
                }
            }
        }
    }

    private void handleReceiveMsg(WKMsg message) {
        message = parsingMsg(message);
        addReceivedMsg(message);
    }

    private synchronized void addReceivedMsg(WKMsg msg) {

        if (receivedMsgList == null) receivedMsgList = new ArrayList<>();
        WKSyncMsg syncMsg = new WKSyncMsg();
        syncMsg.no_persist = msg.header.noPersist ? 1 : 0;
        syncMsg.sync_once = msg.header.syncOnce ? 1 : 0;
        syncMsg.red_dot = msg.header.redDot ? 1 : 0;
        syncMsg.wkMsg = msg;
        receivedMsgList.add(syncMsg);
    }

    public synchronized void saveReceiveMsg() {

        if (receivedMsgList != null && receivedMsgList.size() > 0) {
            saveSyncMsg(receivedMsgList);

            List<WKReceivedAckMsg> list = new ArrayList<>();
            for (int i = 0, size = receivedMsgList.size(); i < size; i++) {
                WKReceivedAckMsg receivedAckMsg = new WKReceivedAckMsg();
                receivedAckMsg.messageID = receivedMsgList.get(i).wkMsg.messageID;
                receivedAckMsg.messageSeq = receivedMsgList.get(i).wkMsg.messageSeq;
                receivedAckMsg.no_persist = receivedMsgList.get(i).no_persist == 1;
                receivedAckMsg.red_dot = receivedMsgList.get(i).red_dot == 1;
                receivedAckMsg.sync_once = receivedMsgList.get(i).sync_once == 1;
                list.add(receivedAckMsg);
            }
            sendAck(list);
            receivedMsgList.clear();
        }
    }

    //回复消息ack
    private void sendAck(List<WKReceivedAckMsg> list) {
        if (list.size() == 1) {
            ConnectionHandler.getInstance().sendMessage(list.get(0));
            return;
        }
        final Timer sendAckTimer = new Timer();
        sendAckTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (list.size() > 0) {
                    ConnectionHandler.getInstance().sendMessage(list.get(0));
                    list.remove(0);
                } else {
                    sendAckTimer.cancel();
                }
            }
        }, 0, 100);
    }


    /**
     * 保存同步消息
     *
     * @param list 同步消息对象
     */
    public synchronized void saveSyncMsg(List<WKSyncMsg> list) {
        List<WKMsg> saveMsgList = new ArrayList<>();
        List<WKMsg> allList = new ArrayList<>();
        for (WKSyncMsg mMsg : list) {
            if (mMsg.no_persist == 0 && mMsg.sync_once == 0) {
                saveMsgList.add(mMsg.wkMsg);
            }
            allList.add(mMsg.wkMsg);
        }
        MsgDbManager.getInstance().insertMsgs(saveMsgList);
        //将消息push给UI
        WKIM.getInstance().getMsgManager().pushNewMsg(allList);
        groupMsg(list);
    }

    private void groupMsg(List<WKSyncMsg> list) {
        LinkedHashMap<String, SavedMsg> savedList = new LinkedHashMap<>();
        //再将消息分组
        for (int i = 0, size = list.size(); i < size; i++) {
            WKMsg lastMsg = null;
            int count;

            if (list.get(i).wkMsg.channelType == WKChannelType.PERSONAL) {
                //如果是单聊先将channelId改成发送者ID
                if (!TextUtils.isEmpty(list.get(i).wkMsg.channelID) && !TextUtils.isEmpty(list.get(i).wkMsg.fromUID) && list.get(i).wkMsg.channelID.equals(WKIMApplication.getInstance().getUid())) {
                    list.get(i).wkMsg.channelID = list.get(i).wkMsg.fromUID;
                }
            }

            //将要存库的最后一条消息更新到会话记录表
            if (list.get(i).no_persist == 0
                    && list.get(i).wkMsg.type != WKMsgContentType.WK_INSIDE_MSG
                    && list.get(i).wkMsg.isDeleted == 0) {
                lastMsg = list.get(i).wkMsg;
            }
            count = list.get(i).red_dot;
            if (lastMsg == null) {
//                Log.e("消息不存在", "---->");
                continue;
            }
            JSONObject jsonObject = null;
            if (!TextUtils.isEmpty(list.get(i).wkMsg.content)) {
                try {
                    jsonObject = new JSONObject(list.get(i).wkMsg.content);
                } catch (JSONException e) {
                    e.printStackTrace();
                    jsonObject = new JSONObject();
                }
            }
            if (lastMsg.baseContentMsgModel == null) {
                lastMsg.baseContentMsgModel = WKIM.getInstance().getMsgManager().getMsgContentModel(lastMsg.type, jsonObject);
                if (lastMsg.baseContentMsgModel != null) {
                    lastMsg.flame = lastMsg.baseContentMsgModel.flame;
                    lastMsg.flameSecond = lastMsg.baseContentMsgModel.flameSecond;
                }
            }
            boolean isSave = false;
            if (lastMsg.baseContentMsgModel != null && lastMsg.baseContentMsgModel.mentionAll == 1 && list.get(i).red_dot == 1) {
                isSave = true;
            } else {
                if (lastMsg.baseContentMsgModel != null && lastMsg.baseContentMsgModel.mentionInfo != null && lastMsg.baseContentMsgModel.mentionInfo.uids.size() > 0 && count == 1) {
                    for (int j = 0, len = lastMsg.baseContentMsgModel.mentionInfo.uids.size(); j < len; j++) {
                        if (!TextUtils.isEmpty(lastMsg.baseContentMsgModel.mentionInfo.uids.get(j)) && !TextUtils.isEmpty(WKIMApplication.getInstance().getUid()) && lastMsg.baseContentMsgModel.mentionInfo.uids.get(j).equalsIgnoreCase(WKIMApplication.getInstance().getUid())) {
                            isSave = true;
                        }
                    }
                }
            }
            if (isSave) {
                //如果存在艾特情况直接将消息存储
                WKUIConversationMsg conversationMsg = ConversationDbManager.getInstance().insertOrUpdateWithMsg(lastMsg, 1);
                WKIM.getInstance().getConversationManager().setOnRefreshMsg(conversationMsg, true, "cutData");
                continue;
            }

            SavedMsg savedMsg = null;
            if (savedList.containsKey(lastMsg.channelID + "_" + lastMsg.channelType)) {
                savedMsg = savedList.get(lastMsg.channelID + "_" + lastMsg.channelType);
            }
            if (savedMsg == null) {
                savedMsg = new SavedMsg(lastMsg, count);
            } else {
                savedMsg.wkMsg = lastMsg;
                savedMsg.redDot = savedMsg.redDot + count;
            }
            savedList.put(lastMsg.channelID + "_" + lastMsg.channelType, savedMsg);
        }

        List<WKUIConversationMsg> refreshList = new ArrayList<>();
        // TODO: 4/27/21 这里未开事物是因为消息太多太快。事物来不及关闭
        for (Map.Entry<String, SavedMsg> entry : savedList.entrySet()) {
            WKUIConversationMsg conversationMsg = ConversationDbManager.getInstance().insertOrUpdateWithMsg(entry.getValue().wkMsg, entry.getValue().redDot);
            if (conversationMsg != null) {
                refreshList.add(conversationMsg);
            }
        }
        for (int i = 0, size = refreshList.size(); i < size; i++) {
            ConversationManager.getInstance().setOnRefreshMsg(refreshList.get(i), i == refreshList.size() - 1, "groupMsg");
        }
    }

    public WKMsg parsingMsg(WKMsg message) {
        JSONObject json = null;
        if (message.type != WKMsgContentType.WK_SIGNAL_DECRYPT_ERROR) {
            try {
                if (TextUtils.isEmpty(message.content)) return message;
                json = new JSONObject(message.content);
                if (json.has(WKDBColumns.WKMessageColumns.type)) {
                    message.content = json.toString();
                    message.type = json.optInt(WKDBColumns.WKMessageColumns.type);
                }
                if (TextUtils.isEmpty(message.fromUID)) {
                    if (json.has(WKDBColumns.WKMessageColumns.from_uid)) {
                        message.fromUID = json.optString(WKDBColumns.WKMessageColumns.from_uid);
                    } else {
                        message.fromUID = message.channelID;
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
                message.type = WKMsgContentType.WK_CONTENT_FORMAT_ERROR;
                WKLoggerUtils.getInstance().e("解析消息错误，消息非json结构");
            }
        }


        if (json == null) {
            if (message.type != WKMsgContentType.WK_SIGNAL_DECRYPT_ERROR)
                message.type = WKMsgContentType.WK_CONTENT_FORMAT_ERROR;
        }

        if (message.type == WKMsgContentType.WK_INSIDE_MSG) {
            CMDManager.getInstance().handleCMD(json, message.channelID, message.channelType);
        }
        if (message.type != WKMsgContentType.WK_SIGNAL_DECRYPT_ERROR && message.type != WKMsgContentType.WK_CONTENT_FORMAT_ERROR) {
            message.baseContentMsgModel = WKIM.getInstance().getMsgManager().getMsgContentModel(message.type, json);
            if (message.baseContentMsgModel != null) {
                message.flame = message.baseContentMsgModel.flame;
                message.flameSecond = message.baseContentMsgModel.flameSecond;
            }
        }

        //如果是单聊先将channelId改成发送者ID
        if (!TextUtils.isEmpty(message.channelID)
                && !TextUtils.isEmpty(message.fromUID)
                && message.channelType == WKChannelType.PERSONAL
                && message.channelID.equals(WKIMApplication.getInstance().getUid())) {
            message.channelID = message.fromUID;
        }
        return message;
    }

    public void updateLastSendingMsgFail() {
        MsgDbManager.getInstance().updateAllMsgSendFail();
    }


}
