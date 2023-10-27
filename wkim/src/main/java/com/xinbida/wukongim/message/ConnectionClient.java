package com.xinbida.wukongim.message;

import android.text.TextUtils;
import android.util.Log;

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

    private boolean isConnectSuccess;

    ConnectionClient() {
        isConnectSuccess = false;
    }

    @Override
    public boolean onConnectException(INonBlockingConnection iNonBlockingConnection, IOException e) {
        WKLoggerUtils.getInstance().e("连接异常");
        WKConnection.getInstance().forcedReconnection();
        close(iNonBlockingConnection);
        return true;
    }

    @Override
    public boolean onConnect(INonBlockingConnection iNonBlockingConnection) throws BufferUnderflowException {
        if (WKConnection.getInstance().connection == null) {
            Log.e("连接信息为空", "--->");
        }
        try {
            if (WKConnection.getInstance().connection != null && iNonBlockingConnection != null) {
                if (!WKConnection.getInstance().connection.getId().equals(iNonBlockingConnection.getId())) {
                    close(iNonBlockingConnection);
                    WKConnection.getInstance().forcedReconnection();
                } else {
                    //连接成功
                    isConnectSuccess = true;
                    WKLoggerUtils.getInstance().e("连接成功");
                    WKConnection.getInstance().sendConnectMsg();
                }
            } else {
                close(iNonBlockingConnection);
                WKLoggerUtils.getInstance().e("连接成功连接对象为空");
                WKConnection.getInstance().forcedReconnection();
            }
        } catch (Exception ignored) {
        }
        return false;

    }

    @Override
    public boolean onConnectionTimeout(INonBlockingConnection iNonBlockingConnection) {
        if (!isConnectSuccess) {
            WKLoggerUtils.getInstance().e("连接超时");
            WKConnection.getInstance().forcedReconnection();
        }
        return true;
    }

    @Override
    public boolean onData(INonBlockingConnection iNonBlockingConnection) throws BufferUnderflowException {
        Object id = iNonBlockingConnection.getAttachment();
        if (id instanceof String) {
            if (id.toString().startsWith("close")) {
                return true;
            }
            if (!TextUtils.isEmpty(WKConnection.getInstance().socketSingleID) && !WKConnection.getInstance().socketSingleID.equals(id)) {
                WKLoggerUtils.getInstance().e("收到的消息ID和连接的ID对应不上---");
                try {
                    iNonBlockingConnection.close();
                    if (WKConnection.getInstance().connection != null) {
                        WKConnection.getInstance().connection.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (WKIMApplication.getInstance().isCanConnect) {
                    WKConnection.getInstance().forcedReconnection();
                }
                return true;
            }
        }
        int available_len;
        int bufLen = 102400;
        try {
            available_len = iNonBlockingConnection.available();
            if (available_len == -1) {
                return true;
            }
            int readCount = available_len / bufLen;
            if (available_len % bufLen != 0) {
                readCount++;
            }

            for (int i = 0; i < readCount; i++) {
                int readLen = bufLen;
                if (i == readCount - 1) {
                    if (available_len % bufLen != 0) {
                        readLen = available_len % bufLen;
                    }
                }
                byte[] buffBytes = iNonBlockingConnection.readBytesByLength(readLen);
                if (buffBytes.length > 0) {
                    WKConnection.getInstance().receivedData(buffBytes);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            WKLoggerUtils.getInstance().e("处理接受到到数据异常:" + e.getMessage());
        }
        return true;
    }

    @Override
    public boolean onDisconnect(INonBlockingConnection iNonBlockingConnection) {
        WKLoggerUtils.getInstance().e("连接断开");
        try {
            if (iNonBlockingConnection != null && !TextUtils.isEmpty(iNonBlockingConnection.getId()) && iNonBlockingConnection.getAttachment() != null) {
                String id = iNonBlockingConnection.getId();
                Object attachmentObject = iNonBlockingConnection.getAttachment();
                if (attachmentObject instanceof String) {
                    String att = (String) attachmentObject;
                    String attStr = "close" + id;
                    if (att.equals(attStr)) {
                        return true;
                    }
                }
            }
            if (WKIMApplication.getInstance().isCanConnect) {
                WKConnection.getInstance().forcedReconnection();
            } else {
                WKLoggerUtils.getInstance().e("不能重连-->");
            }
            close(iNonBlockingConnection);
        } catch (Exception ignored) {

        }

        return true;
    }

    @Override
    public boolean onIdleTimeout(INonBlockingConnection iNonBlockingConnection) {
        if (!isConnectSuccess) {
            WKLoggerUtils.getInstance().e("Idle连接超时");
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
            e.printStackTrace();
        }
    }
}
