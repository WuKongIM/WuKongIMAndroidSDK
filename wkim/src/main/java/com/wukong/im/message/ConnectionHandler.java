package com.wukong.im.message;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.wukong.im.WKIM;
import com.wukong.im.WKIMApplication;
import com.wukong.im.db.MsgDbManager;
import com.wukong.im.entity.WKChannel;
import com.wukong.im.entity.WKChannelType;
import com.wukong.im.entity.WKMsg;
import com.wukong.im.entity.WKMsgSetting;
import com.wukong.im.entity.WKSyncMsgMode;
import com.wukong.im.interfaces.IReceivedMsgListener;
import com.wukong.im.manager.ConnectionManager;
import com.wukong.im.message.type.WKConnectReason;
import com.wukong.im.message.type.WKConnectStatus;
import com.wukong.im.message.type.WKMsgType;
import com.wukong.im.message.type.WKSendMsgResult;
import com.wukong.im.message.type.WKSendingMsg;
import com.wukong.im.msgmodel.WKImageContent;
import com.wukong.im.msgmodel.WKMediaMessageContent;
import com.wukong.im.msgmodel.WKVideoContent;
import com.wukong.im.protocol.WKBaseMsg;
import com.wukong.im.protocol.WKConnectMsg;
import com.wukong.im.protocol.WKDisconnectMsg;
import com.wukong.im.protocol.WKMessageContent;
import com.wukong.im.protocol.WKPingMsg;
import com.wukong.im.protocol.WKPongMsg;
import com.wukong.im.protocol.WKSendAckMsg;
import com.wukong.im.protocol.WKSendMsg;
import com.wukong.im.utils.DateUtils;
import com.wukong.im.utils.FileUtils;
import com.wukong.im.utils.WKLoggerUtils;

import org.xsocket.connection.IConnection;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 5/21/21 10:51 AM
 * IM connect
 */
public class ConnectionHandler {
    private ConnectionHandler() {
    }

    private static class ConnectHandleBinder {
        private static final ConnectionHandler CONNECT = new ConnectionHandler();
    }

    public static ConnectionHandler getInstance() {
        return ConnectHandleBinder.CONNECT;
    }

    // 正在发送的消息
    private final ConcurrentHashMap<Integer, WKSendingMsg> sendingMsgHashMap = new ConcurrentHashMap<>();
    // 正在重连中
    private boolean isReConnecting = false;
    // 连接状态
    private int connectStatus;
    private long lastMsgTime = 0;
    private String ip;
    private int port;
    INonBlockingConnection connection;
    ClientHandler clientHandler;
    private long requestIPTime;
    public String socketSingleID;

    public synchronized void reconnection() {
        if (isReConnecting) {
            long nowTime = DateUtils.getInstance().getCurrentSeconds();
            if (nowTime - requestIPTime > 5) {
                isReConnecting = false;
            }
            return;
        }
        connectStatus = WKConnectStatus.fail;
        boolean isHaveNetwork = WKIMApplication.getInstance().isNetworkConnected();
        if (isHaveNetwork) {
            closeConnect();
            isReConnecting = true;
            requestIPTime = DateUtils.getInstance().getCurrentSeconds();
            getIPAndPort();
        } else {
            if (!ConnectionTimerHandler.getInstance().checkNetWorkTimerIsRunning) {
                WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.noNetwork, WKConnectReason.NoNetwork);
                isReConnecting = false;
                new Thread() {
                    @Override
                    public void run() {
                        super.run();
                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        reconnection();
                    }
                }.start();
            }
        }
    }

    private void getIPAndPort() {
        if (!WKIMApplication.getInstance().isNetworkConnected()) {
            isReConnecting = false;
            reconnection();
            return;
        }
        if (!WKIMApplication.getInstance().isCanConnect) {
            WKLoggerUtils.getInstance().e("sdk判断不能重连-->");
            stopAll();
            return;
        }
        WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.connecting, WKConnectReason.Connecting);
        // 计算获取IP时长 todo
        ConnectionManager.getInstance().getIpAndPort((ip, port) -> {
            if (countDownTimer != null) {
                countDownTimer.cancel();
                countDownTimer = null;
                WKLoggerUtils.getInstance().e("取消请求socket IP倒计时器--->");
            }
            if (TextUtils.isEmpty(ip) || port == 0) {
                WKLoggerUtils.getInstance().e("返回连接IP或port错误，" + String.format("ip:%s & port:%s", ip, port));
                isReConnecting = false;
                reconnection();
            } else {
                this.ip = ip;
                this.port = port;
                WKLoggerUtils.getInstance().e("连接的IP和Port" + ip + ":" + port);
                new Thread(this::connSocket).start();
            }
        });
        new Handler(Looper.getMainLooper()).post(this::startRequestIPTimer);
    }

    private void connSocket() {
        closeConnect();
        try {
            socketSingleID = UUID.randomUUID().toString().replace("-", "");
            clientHandler = new ClientHandler();
//            InetAddress inetAddress = InetAddress.getByName(ip);
            connection = new NonBlockingConnection(ip, port, clientHandler);
            connection.setAttachment(socketSingleID);
            connection.setIdleTimeoutMillis(1000 * 3);
            connection.setConnectionTimeoutMillis(1000 * 3);
            connection.setFlushmode(IConnection.FlushMode.ASYNC);
            isReConnecting = false;
            if (connection != null)
                connection.setAutoflush(true);
        } catch (Exception e) {
            isReConnecting = false;
            WKLoggerUtils.getInstance().e("连接异常:" + e.getMessage());
            reconnection();
            e.printStackTrace();
        }
    }

    //发送连接消息
    void sendConnectMsg() {
        sendMessage(new WKConnectMsg());
    }

    void receivedData(int length, byte[] data) {
        MessageHandler.getInstance().cutBytes(length, data,
                new IReceivedMsgListener() {

                    public void sendAckMsg(
                            WKSendAckMsg talkSendStatus) {
                        // 删除队列中正在发送的消息对象
                        WKSendingMsg object = sendingMsgHashMap.get(talkSendStatus.clientSeq);
                        if (object != null) {
                            object.isCanResend = false;
                            sendingMsgHashMap.put(talkSendStatus.clientSeq, object);
                        }
                    }

                    @Override
                    public void receiveMsg(WKMsg message) {
                        // 收到在线消息，回服务器ack
                    }

                    @Override
                    public void reconnect() {
                        WKIMApplication.getInstance().isCanConnect = true;
                        reconnection();
                    }

                    @Override
                    public void loginStatusMsg(short status_code) {
                        handleLoginStatus(status_code);
                    }

                    @Override
                    public void heartbeatMsg(WKPongMsg msgHeartbeat) {
                        // 心跳消息
                        lastMsgTime = DateUtils.getInstance().getCurrentSeconds();
                    }

                    @Override
                    public void kickMsg(WKDisconnectMsg disconnectMsg) {
                        WKIM.getInstance().getConnectionManager().disconnect(true);
                        WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.kicked, WKConnectReason.ReasonConnectKick);
                    }

                });
    }


    //重发未发送成功的消息
    public void resendMsg() {
        removeSendingMsg();
        new Thread(() -> {
            for (Map.Entry<Integer, WKSendingMsg> entry : sendingMsgHashMap.entrySet()) {
                if (entry.getValue().isCanResend) {
                    sendMessage(Objects.requireNonNull(sendingMsgHashMap.get(entry.getKey())).wkSendMsg);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();

    }

    //将要发送的消息添加到队列
    private synchronized void addSendingMsg(WKSendMsg sendingMsg) {
        removeSendingMsg();
        sendingMsgHashMap.put(sendingMsg.clientSeq, new WKSendingMsg(1, sendingMsg, true));
    }

    //处理登录消息状态
    private void handleLoginStatus(short status) {
        WKLoggerUtils.getInstance().e("IM连接返回状态:" + status);
        String reason = WKConnectReason.ConnectSuccess;
        if (status == WKConnectStatus.kicked) {
            reason = WKConnectReason.ReasonAuthFail;
        }
        WKIM.getInstance().getConnectionManager().setConnectionStatus(status, reason);
        if (status == WKConnectStatus.success) {
            //等待中
            connectStatus = WKConnectStatus.success;
            ConnectionTimerHandler.getInstance().startAll();
            resendMsg();
            WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.syncMsg, WKConnectReason.SyncMsg);
            // 判断同步模式
            if (WKIMApplication.getInstance().getSyncMsgMode() == WKSyncMsgMode.WRITE) {
                WKIM.getInstance().getMsgManager().setSyncOfflineMsg((isEnd, list) -> {
                    if (isEnd) {
                        MessageHandler.getInstance().saveReceiveMsg();
                        WKIMApplication.getInstance().isCanConnect = true;
                        WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.success, WKConnectReason.ConnectSuccess);
                    }
                });
            } else {
                WKLoggerUtils.getInstance().e("通知UI同步会话-->");
                WKIM.getInstance().getConversationManager().setSyncConversationListener(syncChat -> {
                    WKIMApplication.getInstance().isCanConnect = true;
                    WKLoggerUtils.getInstance().e("同步会话完成-->");
                    WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.success, WKConnectReason.ConnectSuccess);
                });
            }
        } else if (status == WKConnectStatus.kicked) {
            WKLoggerUtils.getInstance().e("解析登录返回被踢设置不能连接");
            MessageHandler.getInstance().updateLastSendingMsgFail();
            WKIMApplication.getInstance().isCanConnect = false;
            stopAll();
        } else {
            WKLoggerUtils.getInstance().e("sdk解析登录返回错误类型:" + status);
            stopAll();
            reconnection();
        }
    }

    void sendMessage(WKBaseMsg mBaseMsg) {
        if (mBaseMsg == null) return;
        if (mBaseMsg.packetType != WKMsgType.CONNECT) {
            if (connectStatus != WKConnectStatus.success) {
                return;
            }
        }
        if (connection == null || !connection.isOpen()) {
            reconnection();
            return;
        }
        int status = MessageHandler.getInstance().sendMessage(connection, mBaseMsg);
        if (status == 0) {
            WKLoggerUtils.getInstance().e("发送消息失败");
            reconnection();
        }
    }

    // 查看心跳是否超时
    void checkHeartIsTimeOut() {
        long nowTime = DateUtils.getInstance().getCurrentSeconds();
        if (nowTime - lastMsgTime >= 60) {
            sendMessage(new WKPingMsg());
        }
    }

    private void removeSendingMsg() {
        if (sendingMsgHashMap.size() > 0) {
            Iterator<Map.Entry<Integer, WKSendingMsg>> it = sendingMsgHashMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, WKSendingMsg> entry = it.next();
                if (!entry.getValue().isCanResend) {
                    it.remove();
                }
            }
        }
    }

    //检测正在发送的消息
    synchronized void checkSendingMsg() {
        removeSendingMsg();
        if (sendingMsgHashMap.size() > 0) {
            Iterator<Map.Entry<Integer, WKSendingMsg>> it = sendingMsgHashMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, WKSendingMsg> item = it.next();
                WKSendingMsg wkSendingMsg = sendingMsgHashMap.get(item.getKey());
                if (wkSendingMsg != null) {
                    if (wkSendingMsg.sendCount == 5 && wkSendingMsg.isCanResend) {
                        //标示消息发送失败
                        MsgDbManager.getInstance().updateMsgStatus(item.getKey(), WKSendMsgResult.send_fail);
                        it.remove();
                        wkSendingMsg.isCanResend = false;
                        WKLoggerUtils.getInstance().e("消息发送失败...");
                    } else {
                        long nowTime = DateUtils.getInstance().getCurrentSeconds();
                        if (nowTime - wkSendingMsg.sendTime > 10) {
                            wkSendingMsg.sendTime = DateUtils.getInstance().getCurrentSeconds();
                            sendingMsgHashMap.put(item.getKey(), wkSendingMsg);
                            wkSendingMsg.sendCount++;
                            sendMessage(Objects.requireNonNull(sendingMsgHashMap.get(item.getKey())).wkSendMsg);
                            WKLoggerUtils.getInstance().e("消息发送失败...");
                        }
                    }
                }
            }
        }
    }

    public void sendMessage(WKMessageContent baseContentModel, WKMsgSetting wkMsgSetting, String channelID, byte channelType) {
        final WKMsg wkMsg = new WKMsg();
        if (!TextUtils.isEmpty(WKIMApplication.getInstance().getUid())) {
            wkMsg.fromUID = WKIMApplication.getInstance().getUid();
        }
//        wkMsg.content = baseContentModel.content;
        wkMsg.type = baseContentModel.type;
        wkMsg.setting = wkMsgSetting;
        //设置会话信息
        wkMsg.channelID = channelID;
        wkMsg.channelType = channelType;
        //检查频道信息
        wkMsg.baseContentMsgModel = baseContentModel;
        wkMsg.baseContentMsgModel.fromUID = wkMsg.fromUID;
        wkMsg.flame = baseContentModel.flame;
        wkMsg.flameSecond = baseContentModel.flameSecond;
        wkMsg.topicID = baseContentModel.topicID;
        sendMessage(wkMsg);
    }

    /**
     * 发送消息
     *
     * @param baseContentModel 消息model
     * @param channelID        频道ID
     * @param channelType      频道类型
     */
    public void sendMessage(WKMessageContent baseContentModel, String channelID, byte channelType) {
        WKMsgSetting setting = new WKMsgSetting();
        sendMessage(baseContentModel, setting, channelID, channelType);
    }

    public void sendMessage(WKMsg msg) {
        boolean hasAttached = false;
        //如果是图片消息
        if (msg.baseContentMsgModel instanceof WKImageContent) {
            WKImageContent imageContent = (WKImageContent) msg.baseContentMsgModel;
            if (!TextUtils.isEmpty(imageContent.localPath)) {
                try {
                    File file = new File(imageContent.localPath);
                    if (file.exists() && file.length() > 0) {
                        hasAttached = true;
                        Bitmap bitmap = BitmapFactory.decodeFile(imageContent.localPath);
                        if (bitmap != null) {
                            imageContent.width = bitmap.getWidth();
                            imageContent.height = bitmap.getHeight();
                            msg.baseContentMsgModel = imageContent;
                        }
                    }
                } catch (Exception ignored) {
                }

            }

        }
        //视频消息
        if (msg.baseContentMsgModel instanceof WKVideoContent) {
            WKVideoContent videoContent = (WKVideoContent) msg.baseContentMsgModel;
            if (!TextUtils.isEmpty(videoContent.localPath)) {
                try {
                    File file = new File(videoContent.coverLocalPath);
                    if (file.exists() && file.length() > 0) {
                        hasAttached = true;
                        Bitmap bitmap = BitmapFactory.decodeFile(videoContent.coverLocalPath);
                        if (bitmap != null) {
                            videoContent.width = bitmap.getWidth();
                            videoContent.height = bitmap.getHeight();
                            msg.baseContentMsgModel = videoContent;
                        }
                    }
                } catch (Exception ignored) {

                }
            }

        }
        WKBaseMsg base = MessageConvertHandler.getInstance().getSendBaseMsg(msg);
        if (base != null && msg.clientSeq != 0) {
            msg.clientSeq = ((WKSendMsg) base).clientSeq;
        }

        if (WKMediaMessageContent.class.isAssignableFrom(msg.baseContentMsgModel.getClass())) {
            //如果是多媒体消息类型说明存在附件
            String url = ((WKMediaMessageContent) msg.baseContentMsgModel).url;
            if (TextUtils.isEmpty(url)) {
                String localPath = ((WKMediaMessageContent) msg.baseContentMsgModel).localPath;
                if (!TextUtils.isEmpty(localPath)) {
                    hasAttached = true;
                    ((WKMediaMessageContent) msg.baseContentMsgModel).localPath = FileUtils.getInstance().saveFile(localPath, msg.channelID, msg.channelType, msg.clientSeq + "");
                }
            }
            if (msg.baseContentMsgModel instanceof WKVideoContent) {
                String coverLocalPath = ((WKVideoContent) msg.baseContentMsgModel).coverLocalPath;
                if (!TextUtils.isEmpty(coverLocalPath)) {
                    ((WKVideoContent) msg.baseContentMsgModel).coverLocalPath = FileUtils.getInstance().saveFile(coverLocalPath, msg.channelID, msg.channelType, msg.clientSeq + "_1");
                    hasAttached = true;
                }
            }
            if (hasAttached) {
                msg.content = msg.baseContentMsgModel.encodeMsg().toString();
                MsgDbManager.getInstance().insertMsg(msg);
            }
        }
        //获取发送者信息
        WKChannel from = WKIM.getInstance().getChannelManager().getChannel(WKIMApplication.getInstance().getUid(), WKChannelType.PERSONAL);
        if (from == null) {
            WKIM.getInstance().getChannelManager().getChannel(WKIMApplication.getInstance().getUid(), WKChannelType.PERSONAL, channel -> WKIM.getInstance().getChannelManager().addOrUpdateChannel(channel));
        } else {
            msg.setFrom(from);
        }
        //将消息push回UI层
        WKIM.getInstance().getMsgManager().setSendMsgCallback(msg);
        if (hasAttached) {
            //存在附件处理
            WKIM.getInstance().getMsgManager().setUploadAttachment(msg, (isSuccess, messageContent) -> {
                if (isSuccess) {
                    if (!sendingMsgHashMap.containsKey((int) msg.clientSeq)) {
                        msg.baseContentMsgModel = messageContent;
                        WKBaseMsg base1 = MessageConvertHandler.getInstance().getSendBaseMsg(msg);
                        addSendingMsg((WKSendMsg) base1);
                        sendMessage(base1);
                    }
                } else {
                    msg.status = WKSendMsgResult.send_fail;
                    MsgDbManager.getInstance().updateMsgStatus(msg.clientSeq, msg.status);
                }
            });
        } else {
            if (base != null) {
                if (msg.type != 9994) {
                    addSendingMsg((WKSendMsg) base);
                }
                sendMessage(base);
            }
        }
    }

    public boolean connectionIsNull() {
        return connection == null || !connection.isOpen();
    }

    public void stopAll() {
        clientHandler = null;
        ConnectionTimerHandler.getInstance().stopAll();
        closeConnect();
        connectStatus = WKConnectStatus.fail;
        isReConnecting = false;
        System.gc();
    }

    private void closeConnect() {
        if (connection != null && connection.isOpen()) {
            try {
                WKLoggerUtils.getInstance().e("stop connection" + connection.getId());
                connection.flush();
                connection.close();
            } catch (IOException e) {
                e.printStackTrace();
                WKLoggerUtils.getInstance().e("stop connection IOException" + e.getMessage());
            }
        }
        connection = null;
    }

    CountDownTimer countDownTimer;

    private synchronized void startRequestIPTimer() {
        if (countDownTimer != null) {
            return;
        }
        countDownTimer = new CountDownTimer(5000, 1000) {

            @Override
            public void onTick(long l) {
                WKLoggerUtils.getInstance().e("请求socket IP倒计时中--->");
            }

            @Override
            public void onFinish() {
                if (connectionIsNull()) {
                    WKLoggerUtils.getInstance().e("请求socket IP已超时--->");
                    isReConnecting = false;
                    countDownTimer = null;
                    reconnection();
                }
            }
        };
    }
}
