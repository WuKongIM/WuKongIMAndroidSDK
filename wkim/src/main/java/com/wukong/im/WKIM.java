package com.wukong.im;

import android.content.Context;
import android.text.TextUtils;

import com.wukong.im.manager.CMDManager;
import com.wukong.im.manager.ChannelManager;
import com.wukong.im.manager.ChannelMembersManager;
import com.wukong.im.manager.ConnectionManager;
import com.wukong.im.manager.ConversationManager;
import com.wukong.im.manager.MsgManager;
import com.wukong.im.manager.ReminderManager;
import com.wukong.im.manager.RobotManager;
import com.wukong.im.message.MessageHandler;
import com.wukong.im.utils.Curve25519Utils;
import com.wukong.im.utils.WKLoggerUtils;

/**
 * 5/20/21 5:25 PM
 */
public class WKIM {
    private final String Version = "V1.0.0";

    private WKIM() {

    }

    private static class WKIMBinder {
        static final WKIM im = new WKIM();
    }

    public static WKIM getInstance() {
        return WKIMBinder.im;
    }

    private boolean isDebug;

    public boolean isDebug() {
        return isDebug;
    }

    // debug模式会输出一些连接信息，发送消息情况等
    public void setDebug(boolean isDebug) {
        this.isDebug = isDebug;
    }

    //设置文件目录
    public void setFileCacheDir(String fileDir) {
        WKIMApplication.getInstance().setFileCacheDir(fileDir);
    }

    public String getVersion() {
        return Version;
    }

    /**
     * 初始化IM
     *
     * @param context context
     * @param uid     用户ID
     * @param token   im token
     */
    public void init(Context context, String uid, String token) {
        if (context == null || TextUtils.isEmpty(uid) || TextUtils.isEmpty(token)) {
            throw new NullPointerException("context,uid and token cannot be null");
        }

        WKIMApplication.getInstance().closeDbHelper();
        WKIMApplication.getInstance().initContext(context);
        WKIMApplication.getInstance().setUid(uid);
        WKIMApplication.getInstance().setToken(token);
        // 初始化加密key
        Curve25519Utils.getInstance().initKey();
        // 初始化默认消息类型
        getMsgManager().initNormalMsg();
        // 初始化数据库
        WKIMApplication.getInstance().getDbHelper();
        // 将上次发送消息中的队列标志为失败
        MessageHandler.getInstance().updateLastSendingMsgFail();
        WKLoggerUtils.getInstance().e("init uid:" + uid);
        WKLoggerUtils.getInstance().e("init token:" + token);
    }

    // 获取消息管理
    public MsgManager getMsgManager() {
        return MsgManager.getInstance();
    }

    // 获取连接管理
    public ConnectionManager getConnectionManager() {
        return ConnectionManager.getInstance();
    }

    // 获取频道管理
    public ChannelManager getChannelManager() {
        return ChannelManager.getInstance();
    }

    // 获取最近会话管理
    public ConversationManager getConversationManager() {
        return ConversationManager.getInstance();
    }

    // 获取频道成员管理
    public ChannelMembersManager getChannelMembersManager() {
        return ChannelMembersManager.getInstance();
    }

    //获取提醒管理
    public ReminderManager getReminderManager() {
        return ReminderManager.getInstance();
    }

    // 获取cmd管理
    public CMDManager getCMDManager() {
        return CMDManager.getInstance();
    }

    public RobotManager getRobotManager() {
        return RobotManager.getInstance();
    }

}
