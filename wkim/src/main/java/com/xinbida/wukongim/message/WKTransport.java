package com.xinbida.wukongim.message;

/**
 * 传输层抽象接口
 * TCP 和 WebSocket 的统一抽象，上层（WKConnection）通过此接口操作传输层，
 * 无需关心底层是 xSocket TCP 还是 OkHttp WebSocket。
 */
interface WKTransport {

    interface Delegate {
        /** 传输层连接建立成功 */
        void onConnected();

        /** 传输层断开（error==null 表示主动断开） */
        void onDisconnected(Throwable error);

        /** 收到数据（TCP: 原始字节流片段；WS: 一个完整二进制帧） */
        void onReceivedData(byte[] data);
    }

    /** TCP 连接 */
    void connect(String host, int port, Delegate delegate);

    /** WebSocket 连接 */
    void connect(String url, Delegate delegate);

    /** 断开连接 */
    void disconnect();

    /** 发送数据（已编码的协议包字节） */
    boolean write(byte[] data);

    /** 连接是否处于打开状态 */
    boolean isConnected();

    /** 获取连接标识（用于日志和连接身份判断） */
    String getId();
}
