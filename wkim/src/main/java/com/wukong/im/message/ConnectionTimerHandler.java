package com.wukong.im.message;

import com.wukong.im.WKIM;
import com.wukong.im.WKIMApplication;
import com.wukong.im.message.type.WKConnectReason;
import com.wukong.im.message.type.WKConnectStatus;
import com.wukong.im.protocol.WKPingMsg;
import com.wukong.im.utils.WKLoggerUtils;

import java.util.Timer;
import java.util.TimerTask;

/**
 * 5/21/21 11:19 AM
 */
class ConnectionTimerHandler {
    private ConnectionTimerHandler() {
    }

    private static class ConnectionTimerHandlerBinder {
        static final ConnectionTimerHandler timeHandle = new ConnectionTimerHandler();
    }

    public static ConnectionTimerHandler getInstance() {
        return ConnectionTimerHandlerBinder.timeHandle;
    }


    // 发送心跳定时器
    private Timer heartBeatTimer;
    // 检查心跳定时器
    private Timer checkHeartTimer;
    // 检查网络状态定时器
    private Timer checkNetWorkTimer;
    boolean checkNetWorkTimerIsRunning = false;

    //关闭所有定时器
    void stopAll() {
        stopHeartBeatTimer();
        stopCheckHeartTimer();
        stopCheckNetWorkTimer();
    }

    //开启所有定时器
    void startAll() {
        startHeartBeatTimer();
        startCheckHeartTimer();
        startCheckNetWorkTimer();
    }

    //检测网络
    private void stopCheckNetWorkTimer() {
        if (checkNetWorkTimer != null) {
            checkNetWorkTimer.cancel();
            checkNetWorkTimer.purge();
            checkNetWorkTimer = null;
            checkNetWorkTimerIsRunning = false;
        }
    }

    //检测心跳
    private void stopCheckHeartTimer() {
        if (checkHeartTimer != null) {
            checkHeartTimer.cancel();
            checkHeartTimer.purge();
            checkHeartTimer = null;
        }
    }

    //停止心跳Timer
    private void stopHeartBeatTimer() {
        if (heartBeatTimer != null) {
            heartBeatTimer.cancel();
            heartBeatTimer.purge();
            heartBeatTimer = null;
        }
    }

    //开始心跳
    private void startHeartBeatTimer() {
        stopHeartBeatTimer();
        heartBeatTimer = new Timer();
        // 心跳时间
        int heart_time = 60 * 2;
        heartBeatTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                //发送心跳
                ConnectionHandler.getInstance().sendMessage(new WKPingMsg());
            }
        }, 0, heart_time * 1000);
    }

    //开始检查心跳Timer
    private void startCheckHeartTimer() {
        stopCheckHeartTimer();
        checkHeartTimer = new Timer();
        checkHeartTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                if (ConnectionHandler.getInstance().connection == null || heartBeatTimer == null) {
                    ConnectionHandler.getInstance().reconnection();
                }
                ConnectionHandler.getInstance().checkHeartIsTimeOut();
            }
        }, 1000 * 7, 1000 * 7);
    }


    //开启检测网络定时器
    void startCheckNetWorkTimer() {
        stopCheckNetWorkTimer();
        checkNetWorkTimer = new Timer();
        checkNetWorkTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                boolean is_have_network = WKIMApplication.getInstance().isNetworkConnected();
                if (!is_have_network) {
                    WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.noNetwork, WKConnectReason.NoNetwork);
                    WKLoggerUtils.getInstance().e("无网络连接...");
                    ConnectionHandler.getInstance().checkSendingMsg();
                } else {
                    //有网络
                    if (ConnectionHandler.getInstance().connectionIsNull())
                        ConnectionHandler.getInstance().reconnection();
                }
                if (ConnectionHandler.getInstance().connection == null || !ConnectionHandler.getInstance().connection.isOpen()) {
                    ConnectionHandler.getInstance().reconnection();
                }
                checkNetWorkTimerIsRunning = true;
            }
        }, 0, 1000);
    }
}
