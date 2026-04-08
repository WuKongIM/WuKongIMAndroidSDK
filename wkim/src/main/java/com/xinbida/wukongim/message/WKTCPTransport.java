package com.xinbida.wukongim.message;

import com.xinbida.wukongim.utils.WKLoggerUtils;

import org.xsocket.connection.IConnectExceptionHandler;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnection;
import org.xsocket.connection.IConnectionTimeoutHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.IIdleTimeoutHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.UUID;

/**
 * TCP 传输层实现
 * 封装 xSocket 的 NonBlockingConnection，通过 WKTransport.Delegate 回调上层。
 * 从 xSocket 读取的原始字节流直接回调 onReceivedData，
 * 上层（WKConnection）负责流式拆包（cutBytes）。
 */
class WKTCPTransport implements WKTransport, IDataHandler, IConnectHandler,
        IDisconnectHandler, IConnectExceptionHandler,
        IConnectionTimeoutHandler, IIdleTimeoutHandler {

    private static final String TAG = "WKTCPTransport";

    private volatile INonBlockingConnection connection;
    private volatile Delegate delegate;
    private volatile String id;
    private volatile boolean connectSuccess = false;

    @Override
    public void connect(String host, int port, Delegate delegate) {
        this.delegate = delegate;
        this.id = UUID.randomUUID().toString().replace("-", "");
        this.connectSuccess = false;

        try {
            INonBlockingConnection conn = new NonBlockingConnection(host, port, this);
            conn.setAttachment(id);
            this.connection = conn;
        } catch (IOException e) {
            WKLoggerUtils.getInstance().e(TAG, "TCP connect failed: " + e.getMessage());
            if (delegate != null) {
                delegate.onDisconnected(e);
            }
        }
    }

    @Override
    public void connect(String url, Delegate delegate) {
        throw new UnsupportedOperationException("TCP transport does not support URL-based connect");
    }

    @Override
    public void disconnect() {
        Delegate d = delegate;
        delegate = null; // 先摘掉 delegate，避免 close 触发回调导致二次重连
        INonBlockingConnection conn = connection;
        connection = null;
        connectSuccess = false;

        if (conn != null) {
            try {
                String connId = conn.getId();
                conn.setAttachment("closing_" + System.currentTimeMillis() + "_" + connId);
                conn.close();
            } catch (Exception e) {
                WKLoggerUtils.getInstance().e(TAG, "TCP disconnect error: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean write(byte[] data) {
        INonBlockingConnection conn = connection;
        if (conn != null && conn.isOpen()) {
            try {
                conn.write(data, 0, data.length);
                conn.flush();
                return true;
            } catch (Exception e) {
                WKLoggerUtils.getInstance().e(TAG, "TCP write error: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean isConnected() {
        INonBlockingConnection conn = connection;
        return conn != null && conn.isOpen();
    }

    @Override
    public String getId() {
        return id;
    }

    // ========== xSocket Callbacks ==========

    @Override
    public boolean onConnect(INonBlockingConnection conn) throws BufferUnderflowException {
        try {
            conn.setIdleTimeoutMillis(1000 * 3);
            conn.setConnectionTimeoutMillis(1000 * 3);
            conn.setFlushmode(IConnection.FlushMode.ASYNC);
            conn.setAutoflush(true);
            connectSuccess = true;

            Delegate d = delegate;
            if (d != null) {
                d.onConnected();
            }
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "onConnect setup error: " + e.getMessage());
        }
        return false;
    }

    @Override
    public boolean onData(INonBlockingConnection conn) throws BufferUnderflowException {
        Delegate d = delegate;
        if (d == null) return true;

        try {
            int available = conn.available();
            if (available <= 0) return true;

            int bufLen = 512;
            while (available > 0) {
                if (!conn.isOpen()) break;

                int readLen = Math.min(bufLen, available);
                byte[] bytes = conn.readBytesByLength(readLen);
                if (bytes != null && bytes.length > 0) {
                    d.onReceivedData(bytes);
                    available -= bytes.length;
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            WKLoggerUtils.getInstance().e(TAG, "onData error: " + e.getMessage());
        }
        return true;
    }

    @Override
    public boolean onDisconnect(INonBlockingConnection conn) {
        try {
            if (conn != null && conn.getAttachment() != null) {
                Object att = conn.getAttachment();
                if (att instanceof String) {
                    String attStr = (String) att;
                    if (attStr.startsWith("closing_")) {
                        WKLoggerUtils.getInstance().i(TAG, "Planned TCP disconnect, skip reconnect");
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        connectSuccess = false;
        Delegate d = delegate;
        if (d != null) {
            d.onDisconnected(null);
        }
        return true;
    }

    @Override
    public boolean onConnectException(INonBlockingConnection conn, IOException e) {
        WKLoggerUtils.getInstance().e(TAG, "TCP connect exception: " + e.getMessage());
        close(conn);
        Delegate d = delegate;
        if (d != null) {
            d.onDisconnected(e);
        }
        return true;
    }

    @Override
    public boolean onConnectionTimeout(INonBlockingConnection conn) {
        if (!connectSuccess) {
            WKLoggerUtils.getInstance().e(TAG, "TCP connection timeout");
            close(conn);
            Delegate d = delegate;
            if (d != null) {
                d.onDisconnected(new IOException("Connection timeout"));
            }
        }
        return true;
    }

    @Override
    public boolean onIdleTimeout(INonBlockingConnection conn) {
        if (!connectSuccess) {
            WKLoggerUtils.getInstance().e(TAG, "TCP idle timeout");
            close(conn);
            Delegate d = delegate;
            if (d != null) {
                d.onDisconnected(new IOException("Idle timeout"));
            }
        }
        return true;
    }

    private void close(INonBlockingConnection conn) {
        try {
            if (conn != null) conn.close();
        } catch (IOException e) {
            WKLoggerUtils.getInstance().e(TAG, "close error: " + e.getMessage());
        }
    }
}
