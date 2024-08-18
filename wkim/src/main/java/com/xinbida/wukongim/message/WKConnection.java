package com.xinbida.wukongim.message;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.db.MsgDbManager;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsgExtra;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgSetting;
import com.xinbida.wukongim.entity.WKSyncMsgMode;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.interfaces.IReceivedMsgListener;
import com.xinbida.wukongim.manager.ConnectionManager;
import com.xinbida.wukongim.message.type.WKConnectReason;
import com.xinbida.wukongim.message.type.WKConnectStatus;
import com.xinbida.wukongim.message.type.WKMsgType;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.message.type.WKSendingMsg;
import com.xinbida.wukongim.msgmodel.WKImageContent;
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKVideoContent;
import com.xinbida.wukongim.protocol.WKBaseMsg;
import com.xinbida.wukongim.protocol.WKConnectMsg;
import com.xinbida.wukongim.protocol.WKDisconnectMsg;
import com.xinbida.wukongim.protocol.WKPingMsg;
import com.xinbida.wukongim.protocol.WKPongMsg;
import com.xinbida.wukongim.protocol.WKSendAckMsg;
import com.xinbida.wukongim.protocol.WKSendMsg;
import com.xinbida.wukongim.utils.DateUtils;
import com.xinbida.wukongim.utils.FileUtils;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import org.json.JSONObject;
import org.xsocket.connection.IConnection;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 5/21/21 10:51 AM
 * IM connect
 */
public class WKConnection {
    private final String TAG = "WKConnection";
    private WKConnection() {
    }

    private static class ConnectHandleBinder {
        private static final WKConnection CONNECT = new WKConnection();
    }

    public static WKConnection getInstance() {
        return ConnectHandleBinder.CONNECT;
    }

    final ExecutorService executors = new ThreadPoolExecutor(1, 4, 1, TimeUnit.SECONDS,
            new LinkedBlockingQueue(100),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    // 正在发送的消息
    private final ConcurrentHashMap<Integer, WKSendingMsg> sendingMsgHashMap = new ConcurrentHashMap<>();
    // 正在重连中
    private boolean isReConnecting = false;
    // 连接状态
    private int connectStatus;
    private long lastMsgTime = 0;
    private String ip;
    private int port;
    volatile INonBlockingConnection connection;
    volatile ConnectionClient connectionClient;
    private long requestIPTime;
    private final long requestIPTimeoutTime = 6;
    public String socketSingleID;
    private String lastRequestId;
    private final long reconnectDelay = 1500;
    private int unReceivePongCount = 0;
    public volatile Handler reconnectionHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));

    Runnable reconnectionRunnable = this::reconnection;

    public synchronized void forcedReconnection() {
        isReConnecting = false;
        requestIPTime = 0;
        reconnection();
    }

    public synchronized void reconnection() {
        ip = "";
        port = 0;
        if (isReConnecting) {
            long nowTime = DateUtils.getInstance().getCurrentSeconds();
            if (nowTime - requestIPTime > requestIPTimeoutTime) {
                isReConnecting = false;
            }
            return;
        }
        connectStatus = WKConnectStatus.fail;
        reconnectionHandler.removeCallbacks(reconnectionRunnable);
        boolean isHaveNetwork = WKIMApplication.getInstance().isNetworkConnected();
        if (isHaveNetwork) {
            closeConnect();
            isReConnecting = true;
            requestIPTime = DateUtils.getInstance().getCurrentSeconds();
            getIPAndPort();
        } else {
            if (!WKTimers.getInstance().checkNetWorkTimerIsRunning) {
                WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.noNetwork, WKConnectReason.NoNetwork);
                isReConnecting = false;
                reconnectionHandler.postDelayed(reconnectionRunnable, reconnectDelay);
            }
        }
    }

    private synchronized void getIPAndPort() {
        if (!WKIMApplication.getInstance().isNetworkConnected()) {
            isReConnecting = false;
            reconnectionHandler.postDelayed(reconnectionRunnable, reconnectDelay);
            return;
        }
        if (!WKIMApplication.getInstance().isCanConnect) {
            WKLoggerUtils.getInstance().e(TAG,"SDK determines that reconnection is not possible");
            stopAll();
            return;
        }
        WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.connecting, WKConnectReason.Connecting);
        // 计算获取IP时长 todo
        startRequestIPTimer();
        lastRequestId = UUID.randomUUID().toString().replace("-", "");
        ConnectionManager.getInstance().getIpAndPort(lastRequestId, (requestId, ip, port) -> {
            if (TextUtils.isEmpty(ip) || port == 0) {
                WKLoggerUtils.getInstance().e(TAG,"Return connection IP or port error，" + String.format("ip:%s & port:%s", ip, port));
                isReConnecting = false;
                reconnectionHandler.postDelayed(reconnectionRunnable, reconnectDelay);
            } else {
                if (lastRequestId.equals(requestId)) {
                    WKConnection.this.ip = ip;
                    WKConnection.this.port = port;
                    WKLoggerUtils.getInstance().e(TAG,"connection address " + ip + ":" + port);
                    if (connectionIsNull()) {
                        executors.execute(WKConnection.this::connSocket);
                      //  new Thread(WKConnection.this::connSocket).start();
                    }
                } else {
                    if (connectionIsNull()) {
                        WKLoggerUtils.getInstance().e(TAG,"The IP number requested is inconsistent, reconnecting");
                        reconnectionHandler.postDelayed(reconnectionRunnable, reconnectDelay);
                    }
                }
            }
        });
    }

    private void connSocket() {
        closeConnect();
        try {
            socketSingleID = UUID.randomUUID().toString().replace("-", "");
            connectionClient = new ConnectionClient();
//            InetAddress inetAddress = InetAddress.getByName(ip);
            connection = new NonBlockingConnection(ip, port, connectionClient);
            connection.setAttachment(socketSingleID);
            connection.setIdleTimeoutMillis(1000 * 3);
            connection.setConnectionTimeoutMillis(1000 * 3);
            connection.setFlushmode(IConnection.FlushMode.ASYNC);
            isReConnecting = false;
            if (connection != null)
                connection.setAutoflush(true);
        } catch (Exception e) {
            isReConnecting = false;
            WKLoggerUtils.getInstance().e(TAG,"connection exception:" + e.getMessage());
            reconnection();
        }
    }

    //发送连接消息
    void sendConnectMsg() {
        sendMessage(new WKConnectMsg());
    }

    void receivedData(byte[] data) {
        MessageHandler.getInstance().cutBytes(data,
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
                    public void reconnect() {
                        WKIMApplication.getInstance().isCanConnect = true;
                        reconnection();
                    }

                    @Override
                    public void loginStatusMsg(short status_code) {
                        handleLoginStatus(status_code);
                    }

                    @Override
                    public void pongMsg(WKPongMsg msgHeartbeat) {
                        // 心跳消息
                        lastMsgTime = DateUtils.getInstance().getCurrentSeconds();
                        unReceivePongCount = 0;
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
                    } catch (InterruptedException ignored) {
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
        WKLoggerUtils.getInstance().e(TAG,"connection status:" + status);
        String reason = WKConnectReason.ConnectSuccess;
        if (status == WKConnectStatus.kicked) {
            reason = WKConnectReason.ReasonAuthFail;
        }
        WKIM.getInstance().getConnectionManager().setConnectionStatus(status, reason);
        if (status == WKConnectStatus.success) {
            //等待中
            connectStatus = WKConnectStatus.success;
            WKTimers.getInstance().startAll();
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
                WKIM.getInstance().getConversationManager().setSyncConversationListener(syncChat -> {
                    WKIMApplication.getInstance().isCanConnect = true;
                    WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.success, WKConnectReason.ConnectSuccess);
                });
            }
        } else if (status == WKConnectStatus.kicked) {
            WKLoggerUtils.getInstance().e(TAG,"Received kicked message");
            MessageHandler.getInstance().updateLastSendingMsgFail();
            WKIMApplication.getInstance().isCanConnect = false;
            stopAll();
        } else {
            WKLoggerUtils.getInstance().e(TAG,"parsing login returns error type:" + status);
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
        if (mBaseMsg.packetType == WKMsgType.PING) {
            unReceivePongCount++;
        }
        if (connection == null || !connection.isOpen()) {
            reconnection();
            return;
        }
        int status = MessageHandler.getInstance().sendMessage(connection, mBaseMsg);
        if (status == 0) {
            WKLoggerUtils.getInstance().e(TAG,"send message failed");
            reconnection();
        }
    }

    // 查看心跳是否超时
    void checkHeartIsTimeOut() {
        if (unReceivePongCount >= 5) {
            reconnectionHandler.postDelayed(reconnectionRunnable, reconnectDelay);
            return;
        }
        long nowTime = DateUtils.getInstance().getCurrentSeconds();
        if (nowTime - lastMsgTime >= 60) {
            sendMessage(new WKPingMsg());
        }
    }

    private void removeSendingMsg() {
        if (!sendingMsgHashMap.isEmpty()) {
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
        if (!sendingMsgHashMap.isEmpty()) {
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
                        WKLoggerUtils.getInstance().e(TAG,"checkSendingMsg send message failed");
                    } else {
                        long nowTime = DateUtils.getInstance().getCurrentSeconds();
                        if (nowTime - wkSendingMsg.sendTime > 10) {
                            wkSendingMsg.sendTime = DateUtils.getInstance().getCurrentSeconds();
                            sendingMsgHashMap.put(item.getKey(), wkSendingMsg);
                            wkSendingMsg.sendCount++;
                            sendMessage(Objects.requireNonNull(sendingMsgHashMap.get(item.getKey())).wkSendMsg);
                            WKLoggerUtils.getInstance().e(TAG,"checkSendingMsg send message failed");
                        }
                    }
                }
            }
        }
    }


    public void sendMessage(WKMsg msg) {
        if (TextUtils.isEmpty(msg.fromUID)) {
            msg.fromUID = WKIMApplication.getInstance().getUid();
        }
        if (msg.expireTime > 0) {
            msg.expireTimestamp = DateUtils.getInstance().getCurrentSeconds() + msg.expireTime;
        }
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
        saveSendMsg(msg);
        WKSendMsg sendMsg = WKProto.getInstance().getSendBaseMsg(msg);
//        if (base != null && msg.clientSeq == 0) {
//            msg.clientSeq = base.clientSeq;
//        }

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
                WKIM.getInstance().getMsgManager().updateContentAndRefresh(msg.clientMsgNO, msg.baseContentMsgModel, false);
            }
        }
        //获取发送者信息
        WKChannel from = WKIM.getInstance().getChannelManager().getChannel(WKIMApplication.getInstance().getUid(), WKChannelType.PERSONAL);
        if (from == null) {
            WKIM.getInstance().getChannelManager().getChannel(WKIMApplication.getInstance().getUid(), WKChannelType.PERSONAL, channel -> WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel));
        } else {
            msg.setFrom(from);
        }
        //将消息push回UI层
        WKIM.getInstance().getMsgManager().setSendMsgCallback(msg);
        if (hasAttached) {
            //存在附件处理
            WKIM.getInstance().getMsgManager().setUploadAttachment(msg, (isSuccess, messageContent) -> {
                if (isSuccess) {
                    msg.baseContentMsgModel = messageContent;
                    WKIM.getInstance().getMsgManager().updateContentAndRefresh(msg.clientMsgNO, msg.baseContentMsgModel, false);
                    if (!sendingMsgHashMap.containsKey((int) msg.clientSeq)) {
                        WKSendMsg base1 = WKProto.getInstance().getSendBaseMsg(msg);
                        addSendingMsg(base1);
                        sendMessage(base1);
                    }
                } else {
                    MsgDbManager.getInstance().updateMsgStatus(msg.clientSeq, WKSendMsgResult.send_fail);
                }
            });
        } else {
            if (sendMsg != null) {
                if (msg.header != null && !msg.header.noPersist) {
                    addSendingMsg(sendMsg);
                }
                sendMessage(sendMsg);
            }
        }
    }

    public boolean connectionIsNull() {
        return connection == null || !connection.isOpen();
    }

    public void stopAll() {
        connectionClient = null;
        WKTimers.getInstance().stopAll();
        closeConnect();
        connectStatus = WKConnectStatus.fail;
        isReConnecting = false;
        System.gc();
    }

    private void closeConnect() {
        if (connection != null && connection.isOpen()) {
            try {
                WKLoggerUtils.getInstance().e("stop connection:" + connection.getId());
//                connection.flush();
                connection.setAttachment("close" + connection.getId());
                connection.close();
            } catch (IOException e) {
                WKLoggerUtils.getInstance().e("stop connection IOException" + e.getMessage());
            }
        }
        connection = null;
    }

    private Timer checkNetWorkTimer;

    private synchronized void startRequestIPTimer() {
        if (checkNetWorkTimer != null) {
            checkNetWorkTimer.cancel();
            checkNetWorkTimer = null;
        }
        checkNetWorkTimer = new Timer();
        checkNetWorkTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                long nowTime = DateUtils.getInstance().getCurrentSeconds();
                if (nowTime - requestIPTime >= requestIPTimeoutTime) {
                    checkNetWorkTimer.cancel();
                    checkNetWorkTimer.purge();
                    checkNetWorkTimer = null;
                    if (TextUtils.isEmpty(ip) || port == 0) {
                        WKLoggerUtils.getInstance().e(TAG,"Request for IP has timed out");
                        isReConnecting = false;
                        reconnection();
                    }
                } else {
                    if (!TextUtils.isEmpty(ip) && port != 0) {
                        checkNetWorkTimer.cancel();
                        checkNetWorkTimer.purge();
                        checkNetWorkTimer = null;
                        WKLoggerUtils.getInstance().e(TAG,"Request IP countdown has been destroyed");
                    } else {
                        WKLoggerUtils.getInstance().e(TAG,"Requesting IP countdown--->" + (nowTime - requestIPTime));
                    }
                }
            }
        }, 500, 1000L);
    }

    private WKMsg saveSendMsg(WKMsg msg) {
        if (msg.setting == null) msg.setting = new WKMsgSetting();
        JSONObject jsonObject = WKProto.getInstance().getSendPayload(msg);
        msg.content = jsonObject.toString();
        long tempOrderSeq = MsgDbManager.getInstance().queryMaxOrderSeqWithChannel(msg.channelID, msg.channelType);
        msg.orderSeq = tempOrderSeq + 1;
        // 需要存储的消息入库后更改消息的clientSeq
        if (!msg.header.noPersist) {
            msg.clientSeq = (int) MsgDbManager.getInstance().insert(msg);
            if (msg.clientSeq > 0) {
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
        return msg;
    }
}
