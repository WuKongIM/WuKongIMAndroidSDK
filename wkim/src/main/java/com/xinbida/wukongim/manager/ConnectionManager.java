package com.xinbida.wukongim.manager;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.interfaces.IConnectionStatus;
import com.xinbida.wukongim.interfaces.IGetIpAndPort;
import com.xinbida.wukongim.interfaces.IGetSocketIpAndPortListener;
import com.xinbida.wukongim.message.MessageHandler;
import com.xinbida.wukongim.message.WKConnection;
import com.xinbida.wukongim.message.type.WKTransportMode;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 5/21/21 10:31 AM
 * connect manager
 */
public class ConnectionManager extends BaseManager {
    private final String TAG = "ConnectionManager";
    private ConnectionManager() {

    }

    private static class ConnectionManagerBinder {
        static final ConnectionManager connectManager = new ConnectionManager();
    }

    public static ConnectionManager getInstance() {
        return ConnectionManagerBinder.connectManager;
    }


    private IGetIpAndPort iGetIpAndPort;
    private ConcurrentHashMap<String, IConnectionStatus> connectionListenerMap;

    // 传输模式（默认 TCP）
    private volatile int transportMode = WKTransportMode.TCP;

    public int getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(int mode) {
        this.transportMode = mode;
    }

    // 连接
    public void connection() {
        if (TextUtils.isEmpty(WKIMApplication.getInstance().getToken()) || TextUtils.isEmpty(WKIMApplication.getInstance().getUid())) {
            WKLoggerUtils.getInstance().e(TAG,"connection Uninitialized UID and token");
            return;
        }
        WKIMApplication.getInstance().isCanConnect = true;
        if (WKConnection.getInstance().connectionIsNull()) {
            WKConnection.getInstance().reconnection();
        }
    }


    public void disconnect(boolean isLogout) {
        if (TextUtils.isEmpty(WKIMApplication.getInstance().getToken())) return;
        if (isLogout) {
            logoutChat();
        } else {
            stopConnect();
        }
    }

    /**
     * 断开连接
     */
    private void stopConnect() {
        WKIMApplication.getInstance().isCanConnect = false;
        WKConnection.getInstance().stopAll();
    }

    /**
     * 退出登录
     */
    private void logoutChat() {
        WKLoggerUtils.getInstance().e(TAG,"exit");
        WKIMApplication.getInstance().isCanConnect = false;
        MessageHandler.getInstance().saveReceiveMsg();

        WKIMApplication.getInstance().setToken("");
        MessageHandler.getInstance().updateLastSendingMsgFail();
        WKConnection.getInstance().stopAll();
        WKIM.getInstance().getChannelManager().clearARMCache();
        WKIM.getInstance().getReminderManager().clearAllCache();
        WKIMApplication.getInstance().closeDbHelper();
    }

    public interface IRequestIP {
        void onResult(String requestId, String ip, int port);

        /** 同时返回 TCP 和 WSS 地址 */
        default void onResult(String requestId, String ip, int port, String wssAddr) {
            onResult(requestId, ip, port);
        }
    }

    public void getIpAndPort(String requestId, IRequestIP iRequestIP) {
        if (iGetIpAndPort != null) {
            runOnMainThread(() -> iGetIpAndPort.getIP(new IGetSocketIpAndPortListener() {
                @Override
                public void onGetSocketIpAndPort(String ip, int port) {
                    // 兼容旧的只返回 ip+port 的实现
                    iRequestIP.onResult(requestId, ip, port, null);
                }

                @Override
                public void onGetSocketIpAndPort(String ip, int port, String wssAddr) {
                    iRequestIP.onResult(requestId, ip, port, wssAddr);
                }
            }));
        } else {
            WKLoggerUtils.getInstance().e(TAG,"未注册获取连接地址的事件");
        }
    }

    // 监听获取IP和port
    public void addOnGetIpAndPortListener(IGetIpAndPort iGetIpAndPort) {
        this.iGetIpAndPort = iGetIpAndPort;
    }

    public void setConnectionStatus(int status, String reason) {
        if (connectionListenerMap != null && !connectionListenerMap.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, IConnectionStatus> entry : connectionListenerMap.entrySet()) {
                    entry.getValue().onStatus(status, reason);
                }
            });
        }
    }

    // 监听连接状态
    public void addOnConnectionStatusListener(String key, IConnectionStatus iConnectionStatus) {
        if (iConnectionStatus == null || TextUtils.isEmpty(key)) return;
        if (connectionListenerMap == null) connectionListenerMap = new ConcurrentHashMap<>();
        connectionListenerMap.put(key, iConnectionStatus);
    }

    // 移除监听
    public void removeOnConnectionStatusListener(String key) {
        if (!TextUtils.isEmpty(key) && connectionListenerMap != null) {
            connectionListenerMap.remove(key);
        }
    }
}
