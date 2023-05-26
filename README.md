# 悟空IM Android sdk 源码
该项目是一个完全自定义协议的即时通讯sdk。

## 快速入门

**集成**

在主程序的build.gradle文件中添加：

```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```
然后在app model中导入
```
implementation 'com.github.WuKongIM:wkim:1.0.0'
```
**混淆**
```
-dontwarn com.wukong.im.**
-keep class com.wukong.im.**{*;}
```
***初始化sdk***
```
WKIM.getInstance().init(context, uid, token);
```
***连接服务端***
```
WKIM.getInstance().getConnectionManager().connection();
```
***发消息***
```
WKIM.getInstance().getConnectionManager().sendMessage(new WKTextContent("我是文本消息"), channelID, channelType);
```

## 监听
***连接状态监听***
```
WKIM.getInstance().getConnectionManager().addOnConnectionStatusListener("listener_key",new IConnectionStatus() {
            @Override
            public void onStatus(int status) {
                // 0 失败 【WKConnectStatus.fail】
                // 1 成功 【WKConnectStatus.success】
                // 2 被踢 【WKConnectStatus.kicked】
                // 3 同步消息中【WKConnectStatus.syncMsg】
                // 4 连接中 【WKConnectStatus.connecting】
                // 5 无网络连接 【WKConnectStatus.noNetwork】
            }
        });
```
***发送消息结果监听***
```
WKIM.getInstance().getMsgManager().addSendMsgAckListener("listener_key", new ISendACK() {
            @Override
            public void msgACK(long clientSeq, String messageID, long messageSeq, byte reasonCode) {
                // clientSeq 客户端序列号
                // messageID 服务器消息ID
                // messageSeq 服务器序列号
                // reasonCode 消息状态码【0:发送中1:成功2:发送失败3:不是好友或不在群内4:黑名单】
            }
        })
 ```
***监听新消息***
```
 WKIM.getInstance().getMsgManager().addOnNewMsgListener("listener_key", new INewMsgListener() {
            @Override
            public void newMsg(List<WKMsg> list) {
                // todo 
            }
        });
```
***命令消息(cmd)监听***
```
WKIM.getInstance().getCMDManager().addCmdListener("listener_key", new ICMDListener() {
            @Override
            public void onMsg(WKCMD cmd) {
                // todo
            }
        });
```

## [详细文档信息点击这里](http://githubim.com "文档")

