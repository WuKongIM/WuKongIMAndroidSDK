package com.wukong.im.message;

import android.text.TextUtils;
import android.util.Log;

import com.wukong.im.WKIMApplication;
import com.wukong.im.utils.WKLoggerUtils;

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
class ClientHandler implements IDataHandler, IConnectHandler,
        IDisconnectHandler, IConnectExceptionHandler,
        IConnectionTimeoutHandler, IIdleTimeoutHandler {

    private boolean isConnectSuccess;

    ClientHandler() {
        isConnectSuccess = false;
    }

    @Override
    public boolean onConnectException(INonBlockingConnection iNonBlockingConnection, IOException e) {
        WKLoggerUtils.getInstance().e("连接异常");
        ConnectionHandler.getInstance().reconnection();
        close(iNonBlockingConnection);
        return true;
    }

    @Override
    public boolean onConnect(INonBlockingConnection iNonBlockingConnection) throws BufferUnderflowException {
        if (ConnectionHandler.getInstance().connection == null) {
            Log.e("连接信息为空", "--->");
        }
        if (ConnectionHandler.getInstance().connection != null && iNonBlockingConnection != null) {
            if (!ConnectionHandler.getInstance().connection.getId().equals(iNonBlockingConnection.getId())) {
                close(iNonBlockingConnection);
                ConnectionHandler.getInstance().reconnection();
            } else {
                //连接成功
                isConnectSuccess = true;
                WKLoggerUtils.getInstance().e("连接成功");
                ConnectionHandler.getInstance().sendConnectMsg();
            }
        } else {
            close(iNonBlockingConnection);
            WKLoggerUtils.getInstance().e("连接成功连接对象为空");
            ConnectionHandler.getInstance().reconnection();
        }
        return false;
    }

    @Override
    public boolean onConnectionTimeout(INonBlockingConnection iNonBlockingConnection) {
        if (!isConnectSuccess) {
            WKLoggerUtils.getInstance().e("连接超时");
            ConnectionHandler.getInstance().reconnection();
        }
        return true;
    }

    @Override
    public boolean onData(INonBlockingConnection iNonBlockingConnection) throws BufferUnderflowException {
        Object id = iNonBlockingConnection.getAttachment();
        if (id != null) {
            if (!TextUtils.isEmpty(ConnectionHandler.getInstance().socketSingleID) && !ConnectionHandler.getInstance().socketSingleID.equals(id)) {
                WKLoggerUtils.getInstance().e("收到的消息ID和连接的ID对应不上---");
                try {
                    iNonBlockingConnection.close();
                    ConnectionHandler.getInstance().connection.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                ConnectionHandler.getInstance().reconnection();
                return true;
            }
        }
        int available_len;
//        byte[] available_bytes = null;
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
                ConnectionHandler.getInstance().receivedData(buffBytes.length, buffBytes);
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
        if (WKIMApplication.getInstance().isCanConnect) {
            ConnectionHandler.getInstance().reconnection();
        }else {
            WKLoggerUtils.getInstance().e("不能重连-->");
        }
        close(iNonBlockingConnection);
        return true;
    }

    @Override
    public boolean onIdleTimeout(INonBlockingConnection iNonBlockingConnection) {
        if (!isConnectSuccess) {
            WKLoggerUtils.getInstance().e("Idle连接超时");
            ConnectionHandler.getInstance().reconnection();
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
