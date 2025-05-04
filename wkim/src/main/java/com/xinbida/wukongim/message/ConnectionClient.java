package com.xinbida.wukongim.message;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import org.xsocket.connection.IConnectExceptionHandler;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnectionTimeoutHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.IIdleTimeoutHandler;
import org.xsocket.connection.INonBlockingConnection;

import java.io.IOException;
import java.nio.BufferUnderflowException;

/**
 * 2020-12-18 10:28
 * 连接客户端
 */
class ConnectionClient implements IDataHandler, IConnectHandler,
        IDisconnectHandler, IConnectExceptionHandler,
        IConnectionTimeoutHandler, IIdleTimeoutHandler {
    private final String TAG = "ConnectionClient";
    private boolean isConnectSuccess;

    interface IConnResult {
        void onResult(INonBlockingConnection iNonBlockingConnection);
    }
    IConnResult iConnResult;
    ConnectionClient(IConnResult iConnResult) {
        this.iConnResult = iConnResult;
        isConnectSuccess = false;
    }

    @Override
    public boolean onConnectException(INonBlockingConnection iNonBlockingConnection, IOException e) {
        WKLoggerUtils.getInstance().e(TAG,"连接异常");
        WKConnection.getInstance().forcedReconnection();
        close(iNonBlockingConnection);
        return true;
    }

    @Override
    public boolean onConnect(INonBlockingConnection iNonBlockingConnection) throws BufferUnderflowException {
        isConnectSuccess = true;
        iConnResult.onResult( iNonBlockingConnection);
//        if (WKConnection.getInstance().connection == null) {
//            WKLoggerUtils.getInstance().e(TAG, "onConnect connection is null");
//        }
//        try {
//            if (WKConnection.getInstance().connection != null && iNonBlockingConnection != null) {
//                if (!WKConnection.getInstance().connection.getId().equals(iNonBlockingConnection.getId())) {
//                    close(iNonBlockingConnection);
//                    WKConnection.getInstance().forcedReconnection();
//                } else {
//                    //连接成功
//                    isConnectSuccess = true;
//                    WKLoggerUtils.getInstance().e(TAG, "connection success");
//                    WKConnection.getInstance().sendConnectMsg();
//                }
//            } else {
//                close(iNonBlockingConnection);
//                WKLoggerUtils.getInstance().e(TAG, "Connection successful but connection object is empty");
//                WKConnection.getInstance().forcedReconnection();
//            }
//        } catch (Exception ignored) {
//            WKLoggerUtils.getInstance().e(TAG, "onConnect error");
//        }
        return false;

    }

    @Override
    public boolean onConnectionTimeout(INonBlockingConnection iNonBlockingConnection) {
        if (!isConnectSuccess) {
            WKLoggerUtils.getInstance().e(TAG, "连接超时");
            WKConnection.getInstance().forcedReconnection();
        }
        return true;
    }

    @Override
    public boolean onData(INonBlockingConnection iNonBlockingConnection) throws BufferUnderflowException {
        if (WKConnection.getInstance().connectionIsNull() || WKConnection.getInstance().isReConnecting) {
            return true;
        }
        Object id = iNonBlockingConnection.getAttachment();
        if (id instanceof String) {
            if (id.toString().startsWith("close")) {
                return true;
            }
            if (!TextUtils.isEmpty(WKConnection.getInstance().socketSingleID) && !WKConnection.getInstance().socketSingleID.equals(id)) {
                WKLoggerUtils.getInstance().e(TAG, "非当前连接的消息");
                try {
                    iNonBlockingConnection.close();
                    if (WKConnection.getInstance().connection != null) {
                        WKConnection.getInstance().connection.close();
                    }
                } catch (IOException e) {
                    WKLoggerUtils.getInstance().e(TAG, "关闭连接异常");
                }
                if (WKIMApplication.getInstance().isCanConnect) {
                    WKConnection.getInstance().reconnection();
                }
                return true;
            }
        }
        MessageHandler.getInstance().handlerOnlineBytes(iNonBlockingConnection);
        return true;
    }

    @Override
    public boolean onDisconnect(INonBlockingConnection iNonBlockingConnection) {
        try {
            if (iNonBlockingConnection != null && !TextUtils.isEmpty(iNonBlockingConnection.getId()) && iNonBlockingConnection.getAttachment() != null) {
                String id = iNonBlockingConnection.getId();
                Object attachmentObject = iNonBlockingConnection.getAttachment();
                if (attachmentObject instanceof String) {
                    String att = (String) attachmentObject;
                    String attStr = "close" + id;
                    if (att.equals(attStr)) {
                        WKLoggerUtils.getInstance().e("主动断开不重连");
                        return true;
                    }
                }
            }
            if (WKIMApplication.getInstance().isCanConnect) {
                WKLoggerUtils.getInstance().e("手动关闭需要重连");
                WKConnection.getInstance().forcedReconnection();
            }
            close(iNonBlockingConnection);
        } catch (Exception ignored) {

        }

        return true;
    }

    @Override
    public boolean onIdleTimeout(INonBlockingConnection iNonBlockingConnection) {
        if (!isConnectSuccess) {
            WKConnection.getInstance().forcedReconnection();
            close(iNonBlockingConnection);
        }
        return true;
    }

    private void close(INonBlockingConnection iNonBlockingConnection) {
        try {
            if (iNonBlockingConnection != null)
                iNonBlockingConnection.close();
        } catch (IOException e) {
            WKLoggerUtils.getInstance().e(TAG, "关闭连接异常");
        }
    }
}