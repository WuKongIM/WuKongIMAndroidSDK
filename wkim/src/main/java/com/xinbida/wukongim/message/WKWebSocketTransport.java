package com.xinbida.wukongim.message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xinbida.wukongim.utils.WKLoggerUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * WebSocket 传输层实现
 * 封装 OkHttp WebSocket，通过 WKTransport.Delegate 回调上层。
 * WebSocket 每帧 = 一个完整的协议包，无需流式拆包。
 * 上层 cutBytes 仍然可以正常工作（每次收到的都是完整包，不会产生粘包/分包）。
 */
class WKWebSocketTransport implements WKTransport {

    private static final String TAG = "WKWebSocketTransport";

    private static final OkHttpClient wsClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)    // WebSocket 不设读超时
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.SECONDS)   // 心跳由应用层协议管理
            .build();

    private volatile WebSocket webSocket;
    private volatile Delegate delegate;
    private volatile String id;
    private volatile boolean connected = false;

    @Override
    public void connect(String host, int port, Delegate delegate) {
        throw new UnsupportedOperationException("WebSocket transport does not support host:port connect");
    }

    @Override
    public void connect(String url, Delegate delegate) {
        this.delegate = delegate;
        this.id = UUID.randomUUID().toString().replace("-", "");
        this.connected = false;

        Request request = new Request.Builder().url(url).build();
        webSocket = wsClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
                connected = true;
                WKLoggerUtils.getInstance().i(TAG, "WebSocket connected: " + url);
                Delegate d = WKWebSocketTransport.this.delegate;
                if (d != null) {
                    d.onConnected();
                }
            }

            @Override
            public void onMessage(@NonNull WebSocket ws, @NonNull ByteString bytes) {
                Delegate d = WKWebSocketTransport.this.delegate;
                if (d != null) {
                    d.onReceivedData(bytes.toByteArray());
                }
            }

            @Override
            public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, @Nullable Response response) {
                WKLoggerUtils.getInstance().e(TAG, "WebSocket failure: " + t.getMessage());
                connected = false;
                Delegate d = WKWebSocketTransport.this.delegate;
                if (d != null) {
                    d.onDisconnected(t);
                }
            }

            @Override
            public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
                WKLoggerUtils.getInstance().i(TAG, "WebSocket closed: " + code + " " + reason);
                connected = false;
                // onClosed 只在正常关闭时回调，delegate 可能已被置空
                Delegate d = WKWebSocketTransport.this.delegate;
                if (d != null) {
                    d.onDisconnected(null);
                }
            }
        });
    }

    @Override
    public void disconnect() {
        Delegate d = delegate;
        delegate = null; // 先摘掉 delegate，避免 close 回调触发二次重连（踩坑2）
        connected = false;

        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            ws.close(1000, "client disconnect");
        }

        // WebSocket close() 不一定触发 onClosed 回调（踩坑1），主动回调
        if (d != null) {
            d.onDisconnected(null);
        }
    }

    @Override
    public boolean write(byte[] data) {
        WebSocket ws = webSocket;
        if (ws != null && connected) {
            return ws.send(ByteString.of(data));
        }
        return false;
    }

    @Override
    public boolean isConnected() {
        return connected && webSocket != null;
    }

    @Override
    public String getId() {
        return id;
    }
}
