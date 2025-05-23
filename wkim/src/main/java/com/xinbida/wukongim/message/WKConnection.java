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
import com.xinbida.wukongim.message.timer.HeartbeatManager;
import com.xinbida.wukongim.message.timer.NetworkChecker;
import com.xinbida.wukongim.message.timer.TimerManager;
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
import com.xinbida.wukongim.protocol.WKPongMsg;
import com.xinbida.wukongim.protocol.WKSendAckMsg;
import com.xinbida.wukongim.protocol.WKSendMsg;
import com.xinbida.wukongim.utils.DateUtils;
import com.xinbida.wukongim.utils.DispatchQueuePool;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final DispatchQueuePool dispatchQueuePool = new DispatchQueuePool(3);
    // 正在发送的消息
    private final ConcurrentHashMap<Integer, WKSendingMsg> sendingMsgHashMap = new ConcurrentHashMap<>();
    // 正在重连中
    public boolean isReConnecting = false;
    // 连接状态
    private int connectStatus;
    private long lastMsgTime = 0;
    private String ip;
    private int port;
    public volatile INonBlockingConnection connection;
    volatile ConnectionClient connectionClient;
    private long requestIPTime;
    private long connAckTime;
    private final long requestIPTimeoutTime = 6;
    private final long connAckTimeoutTime = 10;
    public String socketSingleID;
    private String lastRequestId;
    public volatile Handler reconnectionHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));
    Runnable reconnectionRunnable = this::reconnection;
    private int connCount = 0;
    private HeartbeatManager heartbeatManager;
    private NetworkChecker networkChecker;

    private final Handler checkRequestAddressHandler = new Handler(Looper.getMainLooper());
    private final Runnable checkRequestAddressRunnable = new Runnable() {
        @Override
        public void run() {
            long nowTime = DateUtils.getInstance().getCurrentSeconds();
            if (nowTime - requestIPTime >= requestIPTimeoutTime) {
                if (TextUtils.isEmpty(ip) || port == 0) {
                    WKLoggerUtils.getInstance().e(TAG, "获取连接地址超时");
                    isReConnecting = false;
                    reconnection();
                }
            } else {
                if (TextUtils.isEmpty(ip) || port == 0) {
                    WKLoggerUtils.getInstance().e(TAG, "请求连接地址--->" + (nowTime - requestIPTime));
                    // 继续检查
                    checkRequestAddressHandler.postDelayed(this, 1000);
                }
            }
        }
    };

    private final Handler checkConnAckHandler = new Handler(Looper.getMainLooper());
    private final Runnable checkConnAckRunnable = new Runnable() {
        @Override
        public void run() {
            long nowTime = DateUtils.getInstance().getCurrentSeconds();
            if (nowTime - connAckTime > connAckTimeoutTime && connectStatus != WKConnectStatus.success && connectStatus != WKConnectStatus.syncMsg) {
                WKLoggerUtils.getInstance().e(TAG, "连接确认超时");
                isReConnecting = false;
                closeConnect();
                reconnection();
            } else {
                if (connectStatus == WKConnectStatus.success || connectStatus == WKConnectStatus.syncMsg) {
                    WKLoggerUtils.getInstance().e(TAG, "连接确认成功");
                } else {
                    WKLoggerUtils.getInstance().e(TAG, "等待连接确认--->" + (nowTime - connAckTime));
                    // 继续检查
                    checkConnAckHandler.postDelayed(this, 1000);
                }
            }
        }
    };

    // 添加一个专门用于同步connection访问的锁对象
    private final Object connectionLock = new Object();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final long CONNECTION_CLOSE_TIMEOUT = 5000; // 5 seconds timeout

    private final AtomicBoolean isClosing = new AtomicBoolean(false);

    private void startAll() {
        heartbeatManager = new HeartbeatManager();
        networkChecker = new NetworkChecker();
        heartbeatManager.startHeartbeat();
        networkChecker.startNetworkCheck();
    }

    public synchronized void forcedReconnection() {
        connCount++;
        isReConnecting = false;
        requestIPTime = 0;
        long connIntervalMillisecond = 150;
        reconnectionHandler.postDelayed(reconnectionRunnable, connIntervalMillisecond * connCount);
    }

    public synchronized void reconnection() {
        // 如果正在关闭连接，等待关闭完成
        if (isClosing.get()) {
            WKLoggerUtils.getInstance().e(TAG, "等待连接关闭完成后再重连");
            mainHandler.postDelayed(this::reconnection, 100);
            return;
        }

        if (!WKIMApplication.getInstance().isCanConnect) {
            WKLoggerUtils.getInstance().e(TAG, "断开");
            stopAll();
            return;
        }
        
        ip = "";
        port = 0;
        if (isReConnecting) {
            long nowTime = DateUtils.getInstance().getCurrentSeconds();
            if (nowTime - requestIPTime > requestIPTimeoutTime) {
                WKLoggerUtils.getInstance().e("重置了正在连接");
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
            getConnAddress();
        } else {
            if (networkChecker != null && networkChecker.checkNetWorkTimerIsRunning) {
                WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.noNetwork, WKConnectReason.NoNetwork);
                forcedReconnection();
            }
        }
    }

    private synchronized void getConnAddress() {

        WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.connecting, WKConnectReason.Connecting);
        // 计算获取IP时长 todo
        startGetConnAddressTimer();
        lastRequestId = UUID.randomUUID().toString().replace("-", "");
        ConnectionManager.getInstance().getIpAndPort(lastRequestId, (requestId, ip, port) -> {
            WKLoggerUtils.getInstance().e(TAG, "连接地址 " + ip + ":" + port);
            if (TextUtils.isEmpty(ip) || port == 0) {
                WKLoggerUtils.getInstance().e(TAG, "连接地址错误" + String.format("ip:%s & port:%s", ip, port));
                isReConnecting = false;
                forcedReconnection();
                return;
            }
            if (lastRequestId.equals(requestId)) {
                WKConnection.this.ip = ip;
                WKConnection.this.port = port;
                if (connectionIsNull()) {
                    connSocket();
                }
                return;
            }
            if (connectionIsNull()) {
                forcedReconnection();
            }
        });
    }

    private synchronized void connSocket() {
        synchronized (connectionLock) {  // 使用专门的锁
            closeConnect();
            socketSingleID = UUID.randomUUID().toString().replace("-", "");
            connectionClient = new ConnectionClient(iNonBlockingConnection -> {
                synchronized (connectionLock) {  // 回调中也需要使用相同的锁
                    connCount = 0;
                    if (iNonBlockingConnection == null || connection == null || 
                        !connection.getId().equals(iNonBlockingConnection.getId())) {
                        WKLoggerUtils.getInstance().e(TAG, "重复连接");
                        forcedReconnection();
                        return;
                    }
                    Object att = iNonBlockingConnection.getAttachment();
                    if (att == null || !att.equals(socketSingleID)) {
                        WKLoggerUtils.getInstance().e(TAG, "不属于当前连接");
                        forcedReconnection();
                        return;
                    }
                    connection.setIdleTimeoutMillis(1000 * 3);
                    connection.setConnectionTimeoutMillis(1000 * 3);
                    connection.setFlushmode(IConnection.FlushMode.ASYNC);
                    isReConnecting = false;
                    if (connection != null)
                        connection.setAutoflush(true);
                    WKConnection.getInstance().sendConnectMsg();
                }
            });
            
            dispatchQueuePool.execute(() -> {
                synchronized (connectionLock) {  // 在设置connection时也使用锁
                    try {
                        connection = new NonBlockingConnection(ip, port, connectionClient);
                        WKLoggerUtils.getInstance().e("当前连接ID",connection.getId());
                        connection.setAttachment(socketSingleID);
                    } catch (IOException e) {
                        isReConnecting = false;
                        WKLoggerUtils.getInstance().e(TAG, "connection exception:" + e.getMessage());
                        forcedReconnection();
                    }
                }
            });
        }
    }

    //发送连接消息
    void sendConnectMsg() {
        startConnAckTimer();
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
        WKLoggerUtils.getInstance().e(TAG, "连接状态:" + status);
        String reason = WKConnectReason.ConnectSuccess;
        if (status == WKConnectStatus.kicked) {
            reason = WKConnectReason.ReasonAuthFail;
        }
        connectStatus = status;
        WKIM.getInstance().getConnectionManager().setConnectionStatus(status, reason);
        if (status == WKConnectStatus.success) {
            //等待中
            connectStatus = WKConnectStatus.syncMsg;
            // WKTimers.getInstance().startAll();
            WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.syncMsg, WKConnectReason.SyncMsg);
            // 判断同步模式
            if (WKIMApplication.getInstance().getSyncMsgMode() == WKSyncMsgMode.WRITE) {
                WKIM.getInstance().getMsgManager().setSyncOfflineMsg((isEnd, list) -> {
                    if (isEnd) {
                        connectStatus = WKConnectStatus.success;
                        MessageHandler.getInstance().saveReceiveMsg();
                        WKIMApplication.getInstance().isCanConnect = true;
                        MessageHandler.getInstance().sendAck();
                        startAll();
                        resendMsg();
                        WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.success, WKConnectReason.ConnectSuccess);
                    }
                });
            } else {
                WKIM.getInstance().getConversationManager().setSyncConversationListener(syncChat -> {
                    connectStatus = WKConnectStatus.success;
                    WKIMApplication.getInstance().isCanConnect = true;
                    MessageHandler.getInstance().sendAck();
                    startAll();
                    resendMsg();
                    WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.success, WKConnectReason.ConnectSuccess);
                });
            }
        } else if (status == WKConnectStatus.kicked) {
            WKLoggerUtils.getInstance().e(TAG, "收到被踢消息");
            MessageHandler.getInstance().updateLastSendingMsgFail();
            WKIMApplication.getInstance().isCanConnect = false;
            stopAll();
        } else {
            reconnection();
            WKLoggerUtils.getInstance().e(TAG, "登录状态:" + status);
            stopAll();

        }
    }

    public void sendMessage(WKBaseMsg mBaseMsg) {
        if (mBaseMsg == null) {
            WKLoggerUtils.getInstance().w(TAG + ": sendMessage called with null mBaseMsg.");
            return;
        }
        if (mBaseMsg.packetType != WKMsgType.CONNECT) {
            if (connectStatus == WKConnectStatus.syncMsg) {
                WKLoggerUtils.getInstance().i(TAG + ": sendMessage: In syncMsg status, message not sent: " + mBaseMsg.packetType);
                return;
            }
            if (connectStatus != WKConnectStatus.success) {
                WKLoggerUtils.getInstance().w(TAG + ": sendMessage: Not in success status (is " + connectStatus + "), attempting reconnection for: " + mBaseMsg.packetType);
                reconnection();
                return;
            }
        }

        INonBlockingConnection currentConnection;
        synchronized (connectionLock) {
            currentConnection = this.connection;
        }

        if (currentConnection == null || !currentConnection.isOpen()) {
            WKLoggerUtils.getInstance().w(TAG + ": sendMessage: Connection is null or not open, attempting reconnection for: " + mBaseMsg.packetType);
            reconnection();
            return;
        }
        // Pass the local reference to MessageHandler
        int status = MessageHandler.getInstance().sendMessage(currentConnection, mBaseMsg);
        if (status == 0) {
            WKLoggerUtils.getInstance().e(TAG, "发消息失败 (status 0 from MessageHandler), attempting reconnection for: " + mBaseMsg.packetType);
            reconnection();
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
    public synchronized void checkSendingMsg() {
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
                    } else {
                        long nowTime = DateUtils.getInstance().getCurrentSeconds();
                        if (nowTime - wkSendingMsg.sendTime > 10) {
                            wkSendingMsg.sendTime = DateUtils.getInstance().getCurrentSeconds();
                            sendingMsgHashMap.put(item.getKey(), wkSendingMsg);
                            wkSendingMsg.sendCount++;
                            sendMessage(Objects.requireNonNull(sendingMsgHashMap.get(item.getKey())).wkSendMsg);
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
                JSONObject jsonObject = WKProto.getInstance().getSendPayload(msg);
                if (jsonObject != null) {
                    msg.content = jsonObject.toString();
                } else {
                    msg.content = msg.baseContentMsgModel.encodeMsg().toString();
                }
                WKIM.getInstance().getMsgManager().updateContentAndRefresh(msg.clientMsgNO, msg.content, false);
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
                    JSONObject jsonObject = WKProto.getInstance().getSendPayload(msg);
                    if (jsonObject != null) {
                        msg.content = jsonObject.toString();
                    } else {
                        msg.content = msg.baseContentMsgModel.encodeMsg().toString();
                    }
                    WKIM.getInstance().getMsgManager().updateContentAndRefresh(msg.clientMsgNO, msg.content, false);
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
        synchronized (connectionLock) {
            return connection == null || !connection.isOpen();
        }
    }

    private synchronized void startConnAckTimer() {
        // 移除之前的回调
        checkConnAckHandler.removeCallbacks(checkConnAckRunnable);
        connAckTime = DateUtils.getInstance().getCurrentSeconds();
        // 开始新的检查
        checkConnAckHandler.postDelayed(checkConnAckRunnable, 1000);
    }

    private synchronized void startGetConnAddressTimer() {
        // 移除之前的回调
        checkRequestAddressHandler.removeCallbacks(checkRequestAddressRunnable);
        // 开始新的检查
        checkRequestAddressHandler.postDelayed(checkRequestAddressRunnable, 1000);
    }

    private void saveSendMsg(WKMsg msg) {
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
                    WKIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsg, "getSendBaseMsg");
                }
            }
        }
    }

    public void destroy() {
        if (checkRequestAddressHandler != null) {
            checkRequestAddressHandler.removeCallbacks(checkRequestAddressRunnable);
        }
        if (checkConnAckHandler != null) {
            checkConnAckHandler.removeCallbacks(checkConnAckRunnable);
        }
    }

    public void stopAll() {
        // 先设置连接状态为失败
        WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.fail, "");
        // 清理连接相关资源
        closeConnect();
        // 关闭定时器管理器
        TimerManager.getInstance().shutdown();
        MessageHandler.getInstance().clearCacheData();
        // 移除所有Handler回调
        if (checkRequestAddressHandler != null) {
            checkRequestAddressHandler.removeCallbacks(checkRequestAddressRunnable);
        }
        if (checkConnAckHandler != null) {
            checkConnAckHandler.removeCallbacks(checkConnAckRunnable);
        }
        if (reconnectionHandler != null) {
            reconnectionHandler.removeCallbacks(reconnectionRunnable);
        }

        // 重置所有状态
        connectStatus = WKConnectStatus.fail;
        isReConnecting = false;
        ip = "";
        port = 0;
        requestIPTime = 0;
        connAckTime = 0;
        lastMsgTime = 0;
        connCount = 0;
        // 清空发送消息队列
        if (sendingMsgHashMap != null) {
            sendingMsgHashMap.clear();
        }
        // 清理连接客户端
        connectionClient = null;
        System.gc();
    }

    private void closeConnect() {
        final INonBlockingConnection connectionToCloseActual;
        
        // 如果已经在关闭过程中，直接返回
        if (!isClosing.compareAndSet(false, true)) {
            WKLoggerUtils.getInstance().i(TAG + ": Close operation already in progress");
            return;
        }

        synchronized (connectionLock) {
            if (connection == null) {
                isClosing.set(false);
                WKLoggerUtils.getInstance().i(TAG + ": closeConnect called but connection is already null.");
                return;
            }
            connectionToCloseActual = connection;
            String connId = connectionToCloseActual.getId();
            connection = null;
            connectionClient = null;
            WKLoggerUtils.getInstance().i(TAG + ": Connection object nulled, preparing for async close of: " + connId);
        }

        // Create a timeout handler to force close after timeout
        final Runnable timeoutRunnable = () -> {
            try {
                if (connectionToCloseActual.isOpen()) {
                    String connId = connectionToCloseActual.getId();
                    WKLoggerUtils.getInstance().w(TAG + ": Connection close timeout reached for: " + connId);
                    connectionToCloseActual.close();
                }
            } catch (Exception e) {
                WKLoggerUtils.getInstance().e(TAG, "Force close connection exception: " + e.getMessage());
            } finally {
                isClosing.set(false);
            }
        };

        // Schedule the timeout
        mainHandler.postDelayed(timeoutRunnable, CONNECTION_CLOSE_TIMEOUT);

        // Execute the close operation on a background thread
        Thread closeThread = new Thread(() -> {
            try {
                if (connectionToCloseActual.isOpen()) {
                    String connId = connectionToCloseActual.getId();
                    WKLoggerUtils.getInstance().i(TAG + ": Attempting to close connection: " + connId);
                    connectionToCloseActual.setAttachment("closing_" + System.currentTimeMillis() + "_" + connId);
                    connectionToCloseActual.close();
                    // Remove the timeout handler since we closed successfully
                    mainHandler.removeCallbacks(timeoutRunnable);
                    WKLoggerUtils.getInstance().i(TAG + ": Successfully closed connection: " + connId);
                } else {
                    WKLoggerUtils.getInstance().i(TAG + ": Connection was already closed or not open when async close executed: " + connectionToCloseActual.getId());
                }
            } catch (IOException e) {
                WKLoggerUtils.getInstance().e(TAG, "IOException during async connection close for " + connectionToCloseActual.getId() + ": " + e.getMessage());
            } catch (Exception e) {
                WKLoggerUtils.getInstance().e(TAG, "Exception during async connection close for " + connectionToCloseActual.getId() + ": " + e.getMessage());
            } finally {
                isClosing.set(false);
            }
        }, "ConnectionCloser");
        closeThread.setDaemon(true);
        closeThread.start();
    }
}