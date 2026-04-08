package com.xinbida.wukongim.interfaces;

/**
 * 2019-11-11 10:05
 * 获取ip和端口的回掉
 */
public interface IGetSocketIpAndPortListener {
    void onGetSocketIpAndPort(String ip, int port);

    /**
     * 同时返回 TCP 地址和 WSS 地址
     * @param ip TCP host
     * @param port TCP port
     * @param wssAddr WebSocket 完整 URL（如 wss://im-cdn.example.com），可为 null
     */
    default void onGetSocketIpAndPort(String ip, int port, String wssAddr) {
        // 默认实现：忽略 wssAddr，向后兼容
        onGetSocketIpAndPort(ip, port);
    }
}
