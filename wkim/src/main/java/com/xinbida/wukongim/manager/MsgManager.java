package com.xinbida.wukongim.manager;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.db.ConversationDbManager;
import com.xinbida.wukongim.db.MsgDbManager;
import com.xinbida.wukongim.db.WKDBColumns;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsg;
import com.xinbida.wukongim.entity.WKMentionInfo;
import com.xinbida.wukongim.entity.WKMessageGroupByDate;
import com.xinbida.wukongim.entity.WKMessageSearchResult;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgExtra;
import com.xinbida.wukongim.entity.WKMsgReaction;
import com.xinbida.wukongim.entity.WKMsgSetting;
import com.xinbida.wukongim.entity.WKSyncExtraMsg;
import com.xinbida.wukongim.entity.WKSyncMsg;
import com.xinbida.wukongim.entity.WKSyncMsgReaction;
import com.xinbida.wukongim.entity.WKSyncRecent;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.interfaces.IClearMsgListener;
import com.xinbida.wukongim.interfaces.IDeleteMsgListener;
import com.xinbida.wukongim.interfaces.IGetOrSyncHistoryMsgBack;
import com.xinbida.wukongim.interfaces.IMessageStoreBeforeIntercept;
import com.xinbida.wukongim.interfaces.INewMsgListener;
import com.xinbida.wukongim.interfaces.IRefreshMsg;
import com.xinbida.wukongim.interfaces.ISendACK;
import com.xinbida.wukongim.interfaces.ISendMsgCallBackListener;
import com.xinbida.wukongim.interfaces.ISyncChannelMsgBack;
import com.xinbida.wukongim.interfaces.ISyncChannelMsgListener;
import com.xinbida.wukongim.interfaces.ISyncMsgReaction;
import com.xinbida.wukongim.interfaces.ISyncOfflineMsgBack;
import com.xinbida.wukongim.interfaces.ISyncOfflineMsgListener;
import com.xinbida.wukongim.interfaces.IUploadAttacResultListener;
import com.xinbida.wukongim.interfaces.IUploadAttachmentListener;
import com.xinbida.wukongim.interfaces.IUploadMsgExtraListener;
import com.xinbida.wukongim.message.ConnectionHandler;
import com.xinbida.wukongim.message.MessageHandler;
import com.xinbida.wukongim.message.type.WKMsgContentType;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.msgmodel.WKImageContent;
import com.xinbida.wukongim.msgmodel.WKReply;
import com.xinbida.wukongim.msgmodel.WKTextContent;
import com.xinbida.wukongim.msgmodel.WKVideoContent;
import com.xinbida.wukongim.msgmodel.WKVoiceContent;
import com.xinbida.wukongim.protocol.WKMessageContent;
import com.xinbida.wukongim.protocol.WKMsgEntity;
import com.xinbida.wukongim.utils.DateUtils;
import com.xinbida.wukongim.utils.WKTypeUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 5/20/21 5:38 PM
 * 消息管理
 */
public class MsgManager extends BaseManager {
    private MsgManager() {
    }

    private static class MsgManagerBinder {
        static final MsgManager msgManager = new MsgManager();
    }

    public static MsgManager getInstance() {
        return MsgManagerBinder.msgManager;
    }

    private final long limOrderSeqFactor = 1000L;
    // 消息修改
    private ConcurrentHashMap<String, IRefreshMsg> refreshMsgListenerMap;
    // 监听发送消息回调
    private ConcurrentHashMap<String, ISendMsgCallBackListener> sendMsgCallBackListenerHashMap;
    // 删除消息监听
    private ConcurrentHashMap<String, IDeleteMsgListener> deleteMsgListenerMap;
    // 发送消息ack监听
    private ConcurrentHashMap<String, ISendACK> sendAckListenerMap;
    // 新消息监听
    private ConcurrentHashMap<String, INewMsgListener> newMsgListenerMap;
    // 清空消息
    private ConcurrentHashMap<String, IClearMsgListener> clearMsgMap;
    // 同步消息回应
    private ISyncMsgReaction iSyncMsgReaction;
    // 上传文件附件
    private IUploadAttachmentListener iUploadAttachmentListener;
    // 同步离线消息
    private ISyncOfflineMsgListener iOfflineMsgListener;
    // 同步channel内消息
    private ISyncChannelMsgListener iSyncChannelMsgListener;

    // 消息存库拦截器
    private IMessageStoreBeforeIntercept messageStoreBeforeIntercept;
    // 自定义消息model
    private List<java.lang.Class<? extends WKMessageContent>> customContentMsgList;
    // 上传消息扩展
    private IUploadMsgExtraListener iUploadMsgExtraListener;
    private Timer checkMsgNeedUploadTimer;

    // 初始化默认消息model
    public void initNormalMsg() {
        if (customContentMsgList == null) {
            customContentMsgList = new ArrayList<>();
            customContentMsgList.add(WKTextContent.class);
            customContentMsgList.add(WKImageContent.class);
            customContentMsgList.add(WKVideoContent.class);
            customContentMsgList.add(WKVoiceContent.class);
        }
    }

    /**
     * 注册消息module
     *
     * @param contentMsg 消息
     */
    public void registerContentMsg(java.lang.Class<? extends WKMessageContent> contentMsg) {
        if (customContentMsgList == null || customContentMsgList.size() == 0)
            initNormalMsg();
        try {
            boolean isAdd = true;
            for (int i = 0, size = customContentMsgList.size(); i < size; i++) {
                if (customContentMsgList.get(i).getDeclaredConstructor().newInstance().type == contentMsg.newInstance().type) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd)
                customContentMsgList.add(contentMsg);
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException |
                 InvocationTargetException e) {
            e.printStackTrace();
        }

    }

    // 通过json获取消息model
    public WKMessageContent getMsgContentModel(JSONObject jsonObject) {
        int type = jsonObject.optInt("type");
        WKMessageContent messageContent = getMsgContentModel(type, jsonObject);
        return messageContent;
    }

    public WKMessageContent getMsgContentModel(String jsonStr) {
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(jsonStr);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (jsonObject == null) {
            return new WKMessageContent();
        } else
            return getMsgContentModel(jsonObject);
    }

    public WKMessageContent getMsgContentModel(int contentType, JSONObject jsonObject) {
        if (jsonObject == null) jsonObject = new JSONObject();
        WKMessageContent baseContentMsgModel = getContentMsgModel(contentType, jsonObject);
        if (baseContentMsgModel != null) {
            //解析@成员列表
            if (jsonObject.has("mention")) {
                JSONObject tempJson = jsonObject.optJSONObject("mention");
                if (tempJson != null) {
                    //是否@所有人
                    if (tempJson.has("all"))
                        baseContentMsgModel.mentionAll = tempJson.optInt("all");
                    JSONArray uidList = tempJson.optJSONArray("uids");

                    if (uidList != null && uidList.length() > 0) {
                        WKMentionInfo mentionInfo = new WKMentionInfo();
                        List<String> mentionInfoUIDs = new ArrayList<>();
                        for (int i = 0, size = uidList.length(); i < size; i++) {
                            String uid = uidList.optString(i);
                            if (uid.equals(WKIMApplication.getInstance().getUid())) {
                                mentionInfo.isMentionMe = true;
                            }
                            mentionInfoUIDs.add(uid);
                        }
                        mentionInfo.uids = mentionInfoUIDs;
                        if (baseContentMsgModel.mentionAll == 1) {
                            mentionInfo.isMentionMe = true;
                        }
                        baseContentMsgModel.mentionInfo = mentionInfo;
                    }
                }
            }

            if (jsonObject.has("from_uid"))
                baseContentMsgModel.fromUID = jsonObject.optString("from_uid");
            if (jsonObject.has("flame"))
                baseContentMsgModel.flame = jsonObject.optInt("flame");
            if (jsonObject.has("flame_second"))
                baseContentMsgModel.flameSecond = jsonObject.optInt("flame_second");
            //判断消息中是否包含回复情况
            if (jsonObject.has("reply")) {
                baseContentMsgModel.reply = new WKReply();
                JSONObject replyJson = jsonObject.optJSONObject("reply");
                if (replyJson != null) {
                    baseContentMsgModel.reply = baseContentMsgModel.reply.decodeMsg(replyJson);
                }
            }
            if (jsonObject.has("robot_id"))
                baseContentMsgModel.robotID = jsonObject.optString("robot_id");
            if (jsonObject.has("entities")) {
                JSONArray jsonArray = jsonObject.optJSONArray("entities");
                if (jsonArray != null && jsonArray.length() > 0) {
                    List<WKMsgEntity> list = new ArrayList<>();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        WKMsgEntity entity = new WKMsgEntity();
                        JSONObject jo = jsonArray.optJSONObject(i);
                        entity.type = jo.optString("type");
                        entity.offset = jo.optInt("offset");
                        entity.length = jo.optInt("length");
                        entity.value = jo.optString("value");
                        list.add(entity);
                    }
                    baseContentMsgModel.entities = list;
                }

            }

        }
        return baseContentMsgModel;
    }

    /**
     * 将json消息转成对于的消息model
     *
     * @param type       content type
     * @param jsonObject content json
     * @return model
     */
    private WKMessageContent getContentMsgModel(int type, JSONObject jsonObject) {
        java.lang.Class<? extends WKMessageContent> baseMsg = null;
        if (customContentMsgList != null && customContentMsgList.size() > 0) {
            try {
                for (int i = 0, size = customContentMsgList.size(); i < size; i++) {
                    if (customContentMsgList.get(i).getDeclaredConstructor().newInstance().type == type) {
                        baseMsg = customContentMsgList.get(i);
                        break;
                    }
                }
            } catch (IllegalAccessException | InstantiationException | NoSuchMethodException |
                     InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        try {
            // 注册的消息model必须提供无参的构造方法
            if (baseMsg != null) {
                return baseMsg.newInstance().decodeMsg(jsonObject);
            }
        } catch (IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }
        return null;
    }

    private long getOrNearbyMsgSeq(long orderSeq) {
        if (orderSeq % limOrderSeqFactor == 0) {
            return orderSeq / limOrderSeqFactor;
        }
        return (orderSeq - orderSeq % limOrderSeqFactor) / limOrderSeqFactor;
    }

    /**
     * 查询或同步某个频道消息
     *
     * @param channelId                频道ID
     * @param channelType              频道类型
     * @param oldestOrderSeq           最后一次消息大orderSeq 第一次进入聊天传入0
     * @param contain                  是否包含 oldestOrderSeq 这条消息
     * @param pullMode                 拉取模式 0:向下拉取 1:向上拉取
     * @param aroundMsgOrderSeq        查询此消息附近消息
     * @param limit                    每次获取数量
     * @param iGetOrSyncHistoryMsgBack 请求返还
     */
    public void getOrSyncHistoryMessages(String channelId, byte channelType, long oldestOrderSeq, boolean contain, int pullMode, int limit, long aroundMsgOrderSeq, final IGetOrSyncHistoryMsgBack iGetOrSyncHistoryMsgBack) {
        if (aroundMsgOrderSeq != 0) {
            long maxMsgSeq = getMaxMessageSeq(channelId, channelType);
            long aroundMsgSeq = getOrNearbyMsgSeq(aroundMsgOrderSeq);

            if (maxMsgSeq >= aroundMsgSeq && maxMsgSeq - aroundMsgSeq <= limit) {
                // 显示最后一页数据
//                oldestOrderSeq = 0;
                oldestOrderSeq = getMessageOrderSeq(maxMsgSeq,channelId,channelType);
                contain = true;
                pullMode = 0;
            } else {
                long minOrderSeq = MsgDbManager.getInstance().getOrderSeq(channelId, channelType, aroundMsgOrderSeq, 3);
                if (minOrderSeq == 0) {
                    oldestOrderSeq = aroundMsgOrderSeq;
                } else {
                    if (minOrderSeq + limit < aroundMsgOrderSeq) {
                        if (aroundMsgOrderSeq % limOrderSeqFactor == 0) {
                            oldestOrderSeq = (aroundMsgOrderSeq / limOrderSeqFactor - 3) * limOrderSeqFactor;
                        } else
                            oldestOrderSeq = aroundMsgOrderSeq - 3;
//                        oldestOrderSeq = aroundMsgOrderSeq;
                    } else {
                        // todo 这里只会查询3条数据  oldestOrderSeq = minOrderSeq
                        long startOrderSeq = MsgDbManager.getInstance().getOrderSeq(channelId, channelType, aroundMsgOrderSeq, limit);
                        if (startOrderSeq == 0) {
                            oldestOrderSeq = aroundMsgOrderSeq;
                        } else
                            oldestOrderSeq = startOrderSeq;
                    }
                }
                pullMode = 1;
                contain = true;
            }
        }
        MsgDbManager.getInstance().getOrSyncHistoryMessages(channelId, channelType, oldestOrderSeq, contain, pullMode, limit, iGetOrSyncHistoryMsgBack);
    }

    public List<WKMsg> queryAll() {
        return MsgDbManager.getInstance().queryAll();
    }

    public List<WKMsg> getWithFromUID(String channelID, byte channelType, String fromUID, long oldestOrderSeq, int limit) {
        return MsgDbManager.getInstance().queryWithFromUID(channelID, channelType, fromUID, oldestOrderSeq, limit);
    }

    /**
     * 批量删除消息
     *
     * @param clientMsgNos 消息编号集合
     */
    public void deleteWithClientMsgNO(List<String> clientMsgNos) {
        if (clientMsgNos == null || clientMsgNos.size() == 0) return;
        List<WKMsg> list = new ArrayList<>();
        try {
            WKIMApplication.getInstance().getDbHelper().getDb()
                    .beginTransaction();
            if (clientMsgNos.size() > 0) {
                for (int i = 0, size = clientMsgNos.size(); i < size; i++) {
                    WKMsg msg = MsgDbManager.getInstance().deleteMsgWithClientMsgNo(clientMsgNos.get(i));
                    if (msg != null) {
                        list.add(msg);
                    }
                }
                WKIMApplication.getInstance().getDbHelper().getDb()
                        .setTransactionSuccessful();
            }
        } catch (Exception ignored) {
        } finally {
            if (WKIMApplication.getInstance().getDbHelper().getDb().inTransaction()) {
                WKIMApplication.getInstance().getDbHelper().getDb()
                        .endTransaction();
            }
        }
        List<WKMsg> deleteMsgList = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            setDeleteMsg(list.get(i));
            boolean isAdd = true;
            for (int j = 0, len = deleteMsgList.size(); j < len; j++) {
                if (deleteMsgList.get(j).channelID.equals(list.get(i).channelID)
                        && deleteMsgList.get(j).channelType == list.get(i).channelType) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) deleteMsgList.add(list.get(i));
        }
        for (int i = 0, size = deleteMsgList.size(); i < size; i++) {
            WKMsg msg = MsgDbManager.getInstance().getMsgMaxOrderSeqWithChannel(deleteMsgList.get(i).channelID, deleteMsgList.get(i).channelType);
            if (msg != null) {
                WKUIConversationMsg uiMsg = WKIM.getInstance().getConversationManager().updateWithWKMsg(msg);
                if (uiMsg != null) {
                    WKIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsg, i == deleteMsgList.size()
                            - 1, "deleteWithClientMsgNOList");
                }
            }
        }
    }

    /**
     * 删除某条消息
     *
     * @param client_seq 客户端序列号
     */
    public boolean deleteWithClientSeq(long client_seq) {
        return MsgDbManager.getInstance().deleteMsgWithClientSeq(client_seq);
    }

    /**
     * 查询某条消息所在行
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param clientMsgNo 客户端消息ID
     * @return int
     */
    public int getMsgRowNoWithClientMsgNO(String channelID, byte channelType, String clientMsgNo) {
        WKMsg msg = MsgDbManager.getInstance().getMsgWithClientMsgNo(clientMsgNo);
        return MsgDbManager.getInstance().getMsgRowNoWithOrderSeq(channelID, channelType, msg == null ? 0 : msg.orderSeq);
    }

    public int getMsgRowNoWithMessageID(String channelID, byte channelType, String messageID) {
        WKMsg msg = MsgDbManager.getInstance().getMsgWithMessageID(messageID, false);
        return MsgDbManager.getInstance().getMsgRowNoWithOrderSeq(channelID, channelType, msg == null ? 0 : msg.orderSeq);
    }

    public void deleteWithClientMsgNO(String clientMsgNo) {
        WKMsg msg = MsgDbManager.getInstance().deleteMsgWithClientMsgNo(clientMsgNo);
        if (msg != null) {
            setDeleteMsg(msg);
            WKConversationMsg conversationMsg = WKIM.getInstance().getConversationManager().getMsg(msg.channelID, msg.channelType);
            if (conversationMsg != null && conversationMsg.lastClientMsgNO.equals(clientMsgNo)) {
                WKMsg tempMsg = MsgDbManager.getInstance().getMsgMaxOrderSeqWithChannel(msg.channelID, msg.channelType);
                if (tempMsg != null) {
                    WKUIConversationMsg uiMsg = ConversationDbManager.getInstance().saveOrUpdateWithMsg(tempMsg, 0);
                    WKIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsg, true, "deleteWithClientMsgNO");
                }
            }
        }
    }


    public boolean deleteWithMessageID(String messageID) {
        return MsgDbManager.getInstance().deleteMsgWithMessageID(messageID);
    }

    public WKMsg getWithMessageID(String messageID) {
        return MsgDbManager.getInstance().getMsgWithMessageID(messageID, true);
    }

    public int isDeletedMsg(JSONObject jsonObject) {
        int isDelete = 0;
        //消息可见数组
        if (jsonObject != null && jsonObject.has("visibles")) {
            boolean isIncludeLoginUser = false;
            JSONArray jsonArray = jsonObject.optJSONArray("visibles");
            if (jsonArray != null && jsonArray.length() > 0) {
                for (int i = 0, size = jsonArray.length(); i < size; i++) {
                    if (jsonArray.optString(i).equals(WKIMApplication.getInstance().getUid())) {
                        isIncludeLoginUser = true;
                        break;
                    }
                }
            }
            isDelete = isIncludeLoginUser ? 0 : 1;
        }
        return isDelete;
    }

    public List<WKMsg> getWithFlame() {
        return MsgDbManager.getInstance().queryWithFlame();
    }

    public long getMessageOrderSeq(long messageSeq, String channelID, byte channelType) {
        if (messageSeq == 0) {
            long tempOrderSeq = MsgDbManager.getInstance().getMaxOrderSeq(channelID, channelType);
            return tempOrderSeq + 1;
        }
        return messageSeq * limOrderSeqFactor;
    }

    public long getMessageSeq(long messageOrderSeq) {
        if (messageOrderSeq % limOrderSeqFactor == 0) {
            return messageOrderSeq / limOrderSeqFactor;
        }
        return 0;
    }

    public long getReliableMessageSeq(long messageOrderSeq) {
        return messageOrderSeq / limOrderSeqFactor;
    }

    public long getMaxSeqWithChannel(String channelID, byte channelType) {
        return MsgDbManager.getInstance().getMaxSeqWithChannel(channelID, channelType);
    }

    // 设置消息回应
    public void setSyncMsgReaction(String channelID, byte channelType) {
        long maxSeq = MsgDbManager.getInstance().getMaxSeqWithChannel(channelID, channelType);
        if (iSyncMsgReaction != null) {
            runOnMainThread(() -> iSyncMsgReaction.onSyncMsgReaction(channelID, channelType, maxSeq));
        }
    }

    public void saveMessageReactions(List<WKSyncMsgReaction> list) {
        if (list == null || list.size() == 0) return;
        List<WKMsgReaction> reactionList = new ArrayList<>();
        List<String> msgIds = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            WKMsgReaction reaction = new WKMsgReaction();
            reaction.messageID = list.get(i).message_id;
            reaction.channelID = list.get(i).channel_id;
            reaction.channelType = list.get(i).channel_type;
            reaction.uid = list.get(i).uid;
            reaction.name = list.get(i).name;
            reaction.seq = list.get(i).seq;
            reaction.emoji = list.get(i).emoji;
            reaction.isDeleted = list.get(i).is_deleted;
            reaction.createdAt = list.get(i).created_at;
            msgIds.add(list.get(i).message_id);
            reactionList.add(reaction);
        }
        saveMsgReactions(reactionList);
        List<WKMsg> msgList = MsgDbManager.getInstance().queryWithMsgIds(msgIds);
        getMsgReactionsAndRefreshMsg(msgIds, msgList);
    }

    public int getMaxMessageSeq() {
        return MsgDbManager.getInstance().getMaxMessageSeq();
    }

    public int getMaxMessageSeq(String channelID, byte channelType) {
        return MsgDbManager.getInstance().getMaxMessageSeq(channelID, channelType);
    }

    public int getMaxMessageOrderSeq(String channelID, byte channelType) {
        return MsgDbManager.getInstance().getMaxMessageOrderSeq(channelID, channelType);
    }

    public int getMinMessageSeq(String channelID, byte channelType) {
        return MsgDbManager.getInstance().getMinMessageSeq(channelID, channelType);
    }


    public List<WKMsgReaction> getMsgReactions(String messageID) {
        List<String> ids = new ArrayList<>();
        ids.add(messageID);
        return MsgDbManager.getInstance().queryMsgReactionWithMsgIds(ids);
    }

    private void getMsgReactionsAndRefreshMsg(List<String> messageIds, List<WKMsg> updatedMsgList) {
        List<WKMsgReaction> reactionList = MsgDbManager.getInstance().queryMsgReactionWithMsgIds(messageIds);
        for (int i = 0, size = updatedMsgList.size(); i < size; i++) {
            for (int j = 0, len = reactionList.size(); j < len; j++) {
                if (updatedMsgList.get(i).messageID.equals(reactionList.get(j).messageID)) {
                    if (updatedMsgList.get(i).reactionList == null)
                        updatedMsgList.get(i).reactionList = new ArrayList<>();
                    updatedMsgList.get(i).reactionList.add(reactionList.get(j));
                }
            }
            setRefreshMsg(updatedMsgList.get(i), i == updatedMsgList.size() - 1);
        }
    }


    public synchronized long getClientSeq() {
        return MsgDbManager.getInstance().getMaxMessageSeq();
    }

    /**
     * 修改消息的扩展字段
     *
     * @param clientMsgNo 客户端ID
     * @param hashExtra   扩展字段
     */
    public boolean updateLocalExtraWithClientMsgNO(String clientMsgNo, HashMap<String, Object> hashExtra) {
        if (hashExtra != null) {
            JSONObject jsonObject = new JSONObject();
            for (String key : hashExtra.keySet()) {
                try {
                    jsonObject.put(key, hashExtra.get(key));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return MsgDbManager.getInstance().updateMsgWithClientMsgNo(clientMsgNo, WKDBColumns.WKMessageColumns.extra, jsonObject.toString(), true);
        }

        return false;
    }

    /**
     * 查询按日期分组的消息数量
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @return List<WKMessageGroupByDate>
     */
    public List<WKMessageGroupByDate> getMessageGroupByDateWithChannel(String channelID, byte channelType) {
        return MsgDbManager.getInstance().getMessageGroupByDateWithChannel(channelID, channelType);
    }

    public void clearAll() {
        MsgDbManager.getInstance().clearEmpty();
    }

    public void insertMsg(WKMsg msg) {
        int refreshType = 0;
        if (!TextUtils.isEmpty(msg.clientMsgNO)) {
            WKMsg tempMsg = MsgDbManager.getInstance().getMsgWithClientMsgNo(msg.clientMsgNO);
            if (tempMsg != null) {
                refreshType = 1;
            }
        }
        if (msg.orderSeq == 0) {
            long tempOrderSeq = getMessageOrderSeq(0, msg.channelID, msg.channelType);
            msg.orderSeq = tempOrderSeq + 1;
        }
        msg.clientSeq = MsgDbManager.getInstance().insertMsg(msg);
        if (refreshType == 0)
            pushNewMsg(msg);
        else setRefreshMsg(msg, true);
    }

    /**
     * 本地插入一条消息并更新会话记录表且未读消息数量加一
     *
     * @param wkMsg      消息对象
     * @param addRedDots 是否显示红点
     */
    public void insertAndUpdateConversationMsg(WKMsg wkMsg, boolean addRedDots) {
        int refreshType = 0;
        if (!TextUtils.isEmpty(wkMsg.clientMsgNO)) {
            WKMsg tempMsg = MsgDbManager.getInstance().getMsgWithClientMsgNo(wkMsg.clientMsgNO);
            if (tempMsg != null) {
                refreshType = 1;
            }
        }
        if (wkMsg.orderSeq == 0) {
            long tempOrderSeq = getMessageOrderSeq(0, wkMsg.channelID, wkMsg.channelType);
            wkMsg.orderSeq = tempOrderSeq + 1;
        }
        wkMsg.clientSeq = MsgDbManager.getInstance().insertMsg(wkMsg);
        if (refreshType == 0)
            pushNewMsg(wkMsg);
        else setRefreshMsg(wkMsg, true);
        WKUIConversationMsg msg = ConversationDbManager.getInstance().saveOrUpdateWithMsg(wkMsg, addRedDots ? 1 : 0);
        WKIM.getInstance().getConversationManager().setOnRefreshMsg(msg, true, "insertAndUpdateConversationMsg");
    }

    /**
     * 查询某个频道的固定类型消息
     *
     * @param channelID      频道ID
     * @param channelType    频道列席
     * @param oldestOrderSeq 最后一次消息大orderSeq
     * @param limit          每次获取数量
     * @param contentTypes   消息内容类型
     * @return List<WKMsg>
     */
    public List<WKMsg> searchMsgWithChannelAndContentTypes(String channelID, byte channelType, long oldestOrderSeq, int limit, int[] contentTypes) {
        return MsgDbManager.getInstance().searchChatMsgWithChannelAndTypes(channelID, channelType, oldestOrderSeq, limit, contentTypes);
    }

    /**
     * 搜索某个频道到消息
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param searchKey   关键字
     * @return List<WKMsg>
     */
    public List<WKMsg> searchWithChannel(String channelID, byte channelType, String searchKey) {
        return MsgDbManager.getInstance().searchMessageWithChannel(channelID, channelType, searchKey);
    }

    public List<WKMessageSearchResult> search(String searchKey) {
        return MsgDbManager.getInstance().searchMessage(searchKey);
    }

    /**
     * 修改语音是否已读
     *
     * @param clientMsgNo 客户端ID
     * @param isReaded    1：已读
     */
    public boolean updateVoiceReadStatus(String clientMsgNo, int isReaded, boolean isRefreshUI) {
        return MsgDbManager.getInstance().updateMsgWithClientMsgNo(clientMsgNo, WKDBColumns.WKMessageColumns.voice_status, String.valueOf(isReaded), isRefreshUI);
    }

    /**
     * 清空某个会话信息
     *
     * @param channelId   频道ID
     * @param channelType 频道类型
     */
    public boolean clear(String channelId, byte channelType) {
        boolean result = MsgDbManager.getInstance().deleteMsgWithChannel(channelId, channelType);
        if (result) {
            if (clearMsgMap != null && clearMsgMap.size() > 0) {
                runOnMainThread(() -> {
                    for (Map.Entry<String, IClearMsgListener> entry : clearMsgMap.entrySet()) {
                        entry.getValue().clear(channelId, channelType, "");
                    }
                });

            }
        }
        return result;
    }

    public boolean clear(String channelId, byte channelType, String fromUID) {
        boolean result = MsgDbManager.getInstance().deleteMsgWithChannel(channelId, channelType, fromUID);
        if (result) {
            if (clearMsgMap != null && clearMsgMap.size() > 0) {
                runOnMainThread(() -> {
                    for (Map.Entry<String, IClearMsgListener> entry : clearMsgMap.entrySet()) {
                        entry.getValue().clear(channelId, channelType, fromUID);
                    }
                });

            }
        }
        return result;
    }

    /**
     * 修改消息内容体
     *
     * @param clientMsgNo    客户端ID
     * @param messageContent 消息module
     */
    public boolean updateContent(String clientMsgNo, WKMessageContent messageContent) {
        return MsgDbManager.getInstance().updateMsgWithClientMsgNo(clientMsgNo, WKDBColumns.WKMessageColumns.content, messageContent.encodeMsg().toString(), true);
    }

    public boolean updateContent(String clientMsgNo, WKMessageContent messageContent, boolean isRefreshUI) {
        return MsgDbManager.getInstance().updateMsgWithClientMsgNo(clientMsgNo, WKDBColumns.WKMessageColumns.content, messageContent.encodeMsg().toString(), isRefreshUI);
    }

    public void updateViewedAt(int viewed, long viewedAt, String clientMsgNo) {
        MsgDbManager.getInstance().updateViewedAt(viewed, viewedAt, clientMsgNo);
    }

    /**
     * 获取某个类型的聊天数据
     *
     * @param type            消息类型
     * @param oldestClientSeq 最后一次消息客户端ID
     * @param limit           数量
     * @return list
     */
    public List<WKMsg> getMessagesWithType(int type, long oldestClientSeq, int limit) {
        return MsgDbManager.getInstance().getMessagesWithType(type, oldestClientSeq, limit);
    }

    public void insertAndUpdateConversationMsg(WKMsg msg) {
        int refreshType = 0;
        if (!TextUtils.isEmpty(msg.clientMsgNO)) {
            WKMsg tempMsg = MsgDbManager.getInstance().getMsgWithClientMsgNo(msg.clientMsgNO);
            if (tempMsg != null) {
                refreshType = 1;
            }
        }
        if (msg.orderSeq == 0) {
            long tempOrderSeq = getMessageOrderSeq(0, msg.channelID, msg.channelType);
            msg.orderSeq = tempOrderSeq + 1;
        }
        MsgDbManager.getInstance().insertMsg(msg);
        if (refreshType == 0)
            pushNewMsg(msg);
        else setRefreshMsg(msg, true);
        ConversationDbManager.getInstance().saveOrUpdateWithMsg(msg, 0);
    }


    public long getMsgMaxExtraVersionWithChannel(String channelID, byte channelType) {
        return MsgDbManager.getInstance().getMsgMaxExtraVersionWithChannel(channelID, channelType);
    }

    public WKMsg getWithClientMsgNO(String clientMsgNo) {
        return MsgDbManager.getInstance().getMsgWithClientMsgNo(clientMsgNo);
    }


    public void saveRemoteExtraMsg(WKChannel channel, List<WKSyncExtraMsg> list) {
        if (list == null || list.size() == 0) return;
        List<WKMsgExtra> extraList = new ArrayList<>();
        List<String> messageIds = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            if (TextUtils.isEmpty(list.get(i).message_id)) {
                continue;
            }
            WKMsgExtra extra = WKSyncExtraMsg2WKMsgExtra(channel.channelID, channel.channelType, list.get(i));
            extraList.add(extra);
            messageIds.add(list.get(i).message_id);
        }
        List<WKMsg> updatedMsgList = MsgDbManager.getInstance().saveOrUpdateMsgExtras(extraList);
        getMsgReactionsAndRefreshMsg(messageIds, updatedMsgList);
    }

    public void addOnSyncOfflineMsgListener(ISyncOfflineMsgListener iOfflineMsgListener) {
        this.iOfflineMsgListener = iOfflineMsgListener;
    }

    public void addOnSyncMsgReactionListener(ISyncMsgReaction iSyncMsgReactionListener) {
        if (iSyncMsgReactionListener != null) {
            this.iSyncMsgReaction = iSyncMsgReactionListener;
        }
    }

    //添加删除消息监听
    public void addOnDeleteMsgListener(String key, IDeleteMsgListener iDeleteMsgListener) {
        if (iDeleteMsgListener == null || TextUtils.isEmpty(key)) return;
        if (deleteMsgListenerMap == null) deleteMsgListenerMap = new ConcurrentHashMap<>();
        deleteMsgListenerMap.put(key, iDeleteMsgListener);
    }

    public void removeDeleteMsgListener(String key) {
        if (TextUtils.isEmpty(key)) return;
        if (deleteMsgListenerMap != null) deleteMsgListenerMap.remove(key);
    }

    //设置删除消息
    public void setDeleteMsg(WKMsg msg) {
        if (deleteMsgListenerMap != null && deleteMsgListenerMap.size() > 0) {
            runOnMainThread(() -> {
                for (Map.Entry<String, IDeleteMsgListener> entry : deleteMsgListenerMap.entrySet()) {
                    entry.getValue().onDeleteMsg(msg);
                }
            });
        }
    }


    void saveMsgReactions(List<WKMsgReaction> list) {
        MsgDbManager.getInstance().saveMsgReaction(list);
    }


    public void setSyncOfflineMsg(ISyncOfflineMsgBack iSyncOfflineMsgBack) {
        syncOfflineMsg(iSyncOfflineMsgBack);
    }

    private void syncOfflineMsg(ISyncOfflineMsgBack iSyncOfflineMsgBack) {
        if (iOfflineMsgListener != null) {
            runOnMainThread(() -> {
                long max_message_seq = getMaxMessageSeq();
                iOfflineMsgListener.getOfflineMsgs(max_message_seq, (isEnd, list) -> {
                    //保存同步消息
                    saveSyncMsg(list);
                    if (isEnd) {
                        iSyncOfflineMsgBack.onBack(isEnd, null);
                    } else {
                        syncOfflineMsg(iSyncOfflineMsgBack);
                    }
                });
            });
        } else iSyncOfflineMsgBack.onBack(true, null);
    }


    public void setSendMsgCallback(WKMsg msg) {
        if (sendMsgCallBackListenerHashMap != null && sendMsgCallBackListenerHashMap.size() > 0) {
            runOnMainThread(() -> {
                for (Map.Entry<String, ISendMsgCallBackListener> entry : sendMsgCallBackListenerHashMap.entrySet()) {
                    entry.getValue().onInsertMsg(msg);
                }
            });
        }
    }

    public void addOnSendMsgCallback(String key, ISendMsgCallBackListener iSendMsgCallBackListener) {
        if (TextUtils.isEmpty(key)) return;
        if (sendMsgCallBackListenerHashMap == null) {
            sendMsgCallBackListenerHashMap = new ConcurrentHashMap<>();
        }
        sendMsgCallBackListenerHashMap.put(key, iSendMsgCallBackListener);
    }

    public void removeSendMsgCallBack(String key) {
        if (sendMsgCallBackListenerHashMap != null) {
            sendMsgCallBackListenerHashMap.remove(key);
        }
    }


    //监听同步频道消息
    public void addOnSyncChannelMsgListener(ISyncChannelMsgListener listener) {
        this.iSyncChannelMsgListener = listener;
    }

    public void setSyncChannelMsgListener(String channelID, byte channelType, long startMessageSeq, long endMessageSeq, int limit, int pullMode, ISyncChannelMsgBack iSyncChannelMsgBack) {
        if (this.iSyncChannelMsgListener != null) {
            runOnMainThread(() -> iSyncChannelMsgListener.syncChannelMsgs(channelID, channelType, startMessageSeq, endMessageSeq, limit, pullMode, syncChannelMsg -> {
                if (syncChannelMsg != null && syncChannelMsg.messages != null && syncChannelMsg.messages.size() > 0) {
                    saveSyncChannelMSGs(syncChannelMsg.messages);
                }
                iSyncChannelMsgBack.onBack(syncChannelMsg);
            }));
        }
    }


    private void saveSyncChannelMSGs(List<WKSyncRecent> list) {
        if (list == null || list.size() == 0) return;
        List<WKMsg> msgList = new ArrayList<>();
        List<WKMsgExtra> msgExtraList = new ArrayList<>();
        for (int j = 0, len = list.size(); j < len; j++) {
            WKMsg wkMsg = WKSyncRecent2WKMsg(list.get(j));
            msgList.add(wkMsg);
            if (list.get(j).message_extra != null) {
                WKMsgExtra extra = WKSyncExtraMsg2WKMsgExtra(wkMsg.channelID, wkMsg.channelType, list.get(j).message_extra);
                msgExtraList.add(extra);
            }
        }
        if (msgExtraList.size() > 0) {
            MsgDbManager.getInstance().saveOrUpdateMsgExtras(msgExtraList);
        }
        if (msgList.size() > 0) {
            MsgDbManager.getInstance().insertMsgList1(msgList);
        }

    }

    public void addOnSendMsgAckListener(String key, ISendACK iSendACKListener) {
        if (iSendACKListener == null || TextUtils.isEmpty(key)) return;
        if (sendAckListenerMap == null) sendAckListenerMap = new ConcurrentHashMap<>();
        sendAckListenerMap.put(key, iSendACKListener);
    }

    public void setSendMsgAck(WKMsg msg) {
        if (sendAckListenerMap != null && sendAckListenerMap.size() > 0) {
            runOnMainThread(() -> {
                for (Map.Entry<String, ISendACK> entry : sendAckListenerMap.entrySet()) {
                    entry.getValue().msgACK(msg);
                }
            });

        }
    }

    public void removeSendMsgAckListener(String key) {
        if (!TextUtils.isEmpty(key) && sendAckListenerMap != null) {
            sendAckListenerMap.remove(key);
        }
    }

    public void addOnUploadAttachListener(IUploadAttachmentListener iUploadAttachmentListener) {
        this.iUploadAttachmentListener = iUploadAttachmentListener;
    }

    public void setUploadAttachment(WKMsg msg, IUploadAttacResultListener resultListener) {
        if (iUploadAttachmentListener != null) {
            runOnMainThread(() -> {
                iUploadAttachmentListener.onUploadAttachmentListener(msg, resultListener);
            });
        }
    }

    public void addMessageStoreBeforeIntercept(IMessageStoreBeforeIntercept iMessageStoreBeforeInterceptListener) {
        messageStoreBeforeIntercept = iMessageStoreBeforeInterceptListener;
    }

    public boolean setMessageStoreBeforeIntercept(WKMsg msg) {
        return messageStoreBeforeIntercept == null || messageStoreBeforeIntercept.isSaveMsg(msg);
    }

    //添加消息修改
    public void addOnRefreshMsgListener(String key, IRefreshMsg listener) {
        if (TextUtils.isEmpty(key) || listener == null) return;
        if (refreshMsgListenerMap == null) refreshMsgListenerMap = new ConcurrentHashMap<>();
        refreshMsgListenerMap.put(key, listener);
    }


    public void removeRefreshMsgListener(String key) {
        if (!TextUtils.isEmpty(key) && refreshMsgListenerMap != null) {
            refreshMsgListenerMap.remove(key);
        }
    }

    public void setRefreshMsg(WKMsg msg, boolean left) {
        if (refreshMsgListenerMap != null && refreshMsgListenerMap.size() > 0) {
            runOnMainThread(() -> {
                for (Map.Entry<String, IRefreshMsg> entry : refreshMsgListenerMap.entrySet()) {
                    entry.getValue().onRefresh(msg, left);
                }
            });

        }
    }

    public void addOnNewMsgListener(String key, INewMsgListener iNewMsgListener) {
        if (TextUtils.isEmpty(key) || iNewMsgListener == null) return;
        if (newMsgListenerMap == null)
            newMsgListenerMap = new ConcurrentHashMap<>();
        newMsgListenerMap.put(key, iNewMsgListener);
    }

    public void removeNewMsgListener(String key) {
        if (TextUtils.isEmpty(key)) return;
        if (newMsgListenerMap != null) newMsgListenerMap.remove(key);
    }

    public void addOnClearMsgListener(String key, IClearMsgListener iClearMsgListener) {
        if (TextUtils.isEmpty(key) || iClearMsgListener == null) return;
        if (clearMsgMap == null) clearMsgMap = new ConcurrentHashMap<>();
        clearMsgMap.put(key, iClearMsgListener);
    }

    public void removeClearMsg(String key) {
        if (TextUtils.isEmpty(key)) return;
        if (clearMsgMap != null) clearMsgMap.remove(key);
    }


    WKMsgExtra WKSyncExtraMsg2WKMsgExtra(String channelID, byte channelType, WKSyncExtraMsg extraMsg) {
        WKMsgExtra extra = new WKMsgExtra();
        extra.channelID = channelID;
        extra.channelType = channelType;
        extra.unreadCount = extraMsg.unread_count;
        extra.readedCount = extraMsg.readed_count;
        extra.readed = extraMsg.readed;
        extra.messageID = extraMsg.message_id;
        extra.isMutualDeleted = extraMsg.is_mutual_deleted;
        extra.extraVersion = extraMsg.extra_version;
        extra.revoke = extraMsg.revoke;
        extra.revoker = extraMsg.revoker;
        extra.needUpload = 0;
        if (extraMsg.content_edit != null) {
            JSONObject jsonObject = new JSONObject(extraMsg.content_edit);
            extra.contentEdit = jsonObject.toString();
        }

        extra.editedAt = extraMsg.edited_at;
        return extra;
    }

    WKMsg WKSyncRecent2WKMsg(WKSyncRecent wkSyncRecent) {
        WKMsg msg = new WKMsg();
        msg.channelID = wkSyncRecent.channel_id;
        msg.channelType = wkSyncRecent.channel_type;
        msg.messageID = wkSyncRecent.message_id;
        msg.messageSeq = wkSyncRecent.message_seq;
        msg.clientMsgNO = wkSyncRecent.client_msg_no;
        msg.fromUID = wkSyncRecent.from_uid;
        msg.timestamp = wkSyncRecent.timestamp;
        msg.orderSeq = msg.messageSeq * limOrderSeqFactor;
        msg.voiceStatus = wkSyncRecent.voice_status;
        msg.isDeleted = wkSyncRecent.is_deleted;
        msg.status = WKSendMsgResult.send_success;
        msg.remoteExtra = new WKMsgExtra();
        msg.remoteExtra.revoke = wkSyncRecent.revoke;
        msg.remoteExtra.revoker = wkSyncRecent.revoker;
        msg.remoteExtra.unreadCount = wkSyncRecent.unread_count;
        msg.remoteExtra.readedCount = wkSyncRecent.readed_count;
        msg.remoteExtra.readed = wkSyncRecent.readed;
        // msg.reactionList = wkSyncRecent.reactions;
        // msg.receipt = wkSyncRecent.receipt;
        msg.remoteExtra.extraVersion = wkSyncRecent.extra_version;
        //处理消息设置
        byte[] setting = WKTypeUtils.getInstance().intToByte(wkSyncRecent.setting);
        msg.setting = WKTypeUtils.getInstance().getMsgSetting(setting[0]);
        //如果是单聊先将channelId改成发送者ID
        if (!TextUtils.isEmpty(msg.channelID)
                && !TextUtils.isEmpty(msg.fromUID)
                && msg.channelType == WKChannelType.PERSONAL
                && msg.channelID.equals(WKIMApplication.getInstance().getUid())) {
            msg.channelID = msg.fromUID;
        }

        if (wkSyncRecent.payload != null) {
            JSONObject jsonObject = new JSONObject(wkSyncRecent.payload);
            msg.content = jsonObject.toString();
        }
        JSONObject jsonObject = null;
        if (!TextUtils.isEmpty(msg.content)) {
            try {
                jsonObject = new JSONObject(msg.content);
                if (jsonObject.has("type"))
                    msg.type = jsonObject.optInt("type");
                jsonObject.put(WKDBColumns.WKMessageColumns.from_uid, msg.fromUID);
                if (jsonObject.has("flame"))
                    msg.flame = jsonObject.optInt("flame");
                if (jsonObject.has("flame_second"))
                    msg.flameSecond = jsonObject.optInt("flame_second");
                msg.content = jsonObject.toString();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        // 处理消息回应
        if (wkSyncRecent.reactions != null && wkSyncRecent.reactions.size() > 0) {
            msg.reactionList = getMsgReaction(wkSyncRecent);
        }
        if (msg.type != WKMsgContentType.WK_SIGNAL_DECRYPT_ERROR && msg.type != WKMsgContentType.WK_CONTENT_FORMAT_ERROR)
            msg.baseContentMsgModel = WKIM.getInstance().getMsgManager().getMsgContentModel(msg.type, jsonObject);

        return msg;
    }

    private List<WKMsgReaction> getMsgReaction(WKSyncRecent wkSyncRecent) {
        List<WKMsgReaction> list = new ArrayList<>();
        for (int i = 0, size = wkSyncRecent.reactions.size(); i < size; i++) {
            WKMsgReaction reaction = new WKMsgReaction();
            reaction.channelID = wkSyncRecent.reactions.get(i).channel_id;
            reaction.channelType = wkSyncRecent.reactions.get(i).channel_type;
            reaction.uid = wkSyncRecent.reactions.get(i).uid;
            reaction.name = wkSyncRecent.reactions.get(i).name;
            reaction.emoji = wkSyncRecent.reactions.get(i).emoji;
            reaction.seq = wkSyncRecent.reactions.get(i).seq;
            reaction.isDeleted = wkSyncRecent.reactions.get(i).is_deleted;
            reaction.messageID = wkSyncRecent.reactions.get(i).message_id;
            reaction.createdAt = wkSyncRecent.reactions.get(i).created_at;
            list.add(reaction);
        }
        return list;
    }

    public void saveSyncMsg(List<WKSyncMsg> wkSyncMsgs) {
        if (wkSyncMsgs == null || wkSyncMsgs.size() == 0) return;
        for (int i = 0, size = wkSyncMsgs.size(); i < size; i++) {
            wkSyncMsgs.get(i).wkMsg = MessageHandler.getInstance().parsingMsg(wkSyncMsgs.get(i).wkMsg);
            if (wkSyncMsgs.get(i).wkMsg.timestamp != 0)
                wkSyncMsgs.get(i).wkMsg.orderSeq = wkSyncMsgs.get(i).wkMsg.timestamp;
            else
                wkSyncMsgs.get(i).wkMsg.orderSeq = getMessageOrderSeq(wkSyncMsgs.get(i).wkMsg.messageSeq, wkSyncMsgs.get(i).wkMsg.channelID, wkSyncMsgs.get(i).wkMsg.channelType);
        }
        MessageHandler.getInstance().saveSyncMsg(wkSyncMsgs);
    }


    public void updateMsgEdit(String msgID, String channelID, byte channelType, String content) {
        WKMsgExtra wkMsgExtra = MsgDbManager.getInstance().queryMsgExtraWithMsgID(msgID);
        if (wkMsgExtra == null) {
            wkMsgExtra = new WKMsgExtra();
        }
        wkMsgExtra.messageID = msgID;
        wkMsgExtra.channelID = channelID;
        wkMsgExtra.channelType = channelType;
        wkMsgExtra.editedAt = DateUtils.getInstance().getCurrentSeconds();
        wkMsgExtra.contentEdit = content;
        wkMsgExtra.needUpload = 1;
        List<WKMsgExtra> list = new ArrayList<>();
        list.add(wkMsgExtra);
        List<WKMsg> wkMsgs = MsgDbManager.getInstance().saveOrUpdateMsgExtras(list);
        List<String> messageIds = new ArrayList<>();
        messageIds.add(msgID);
        if (wkMsgs != null && wkMsgs.size() > 0) {
            getMsgReactionsAndRefreshMsg(messageIds, wkMsgs);
            setUploadMsgExtra(wkMsgExtra);
        }
    }

    private synchronized void startCheckTimer() {
        if (checkMsgNeedUploadTimer == null) {
            checkMsgNeedUploadTimer = new Timer();
        }
        checkMsgNeedUploadTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                List<WKMsgExtra> list = MsgDbManager.getInstance().queryMsgExtraWithNeedUpload(1);
                if (list != null && list.size() > 0) {
                    for (WKMsgExtra extra : list) {
                        setUploadMsgExtra(extra);
                    }
                } else {
                    checkMsgNeedUploadTimer.cancel();
                    checkMsgNeedUploadTimer.purge();
                    checkMsgNeedUploadTimer = null;
                }
            }
        }, 1000 * 5, 1000 * 5);
    }

    private void setUploadMsgExtra(WKMsgExtra extra) {
        if (iUploadMsgExtraListener != null) {
            iUploadMsgExtraListener.onUpload(extra);
        }
        startCheckTimer();
    }

    public void addOnUploadMsgExtraListener(IUploadMsgExtraListener iUploadMsgExtraListener) {
        this.iUploadMsgExtraListener = iUploadMsgExtraListener;
    }

    public void pushNewMsg(List<WKMsg> wkMsgList) {
        if (newMsgListenerMap != null && newMsgListenerMap.size() > 0) {
            runOnMainThread(() -> {
                for (Map.Entry<String, INewMsgListener> entry : newMsgListenerMap.entrySet()) {
                    entry.getValue().newMsg(wkMsgList);
                }
            });
        }
    }

    /**
     * push新消息
     *
     * @param msg 消息
     */
    public void pushNewMsg(WKMsg msg) {
        if (msg == null) return;
        List<WKMsg> msgs = new ArrayList<>();
        msgs.add(msg);
        pushNewMsg(msgs);
    }


    public void sendMessage(WKMessageContent messageContent, String channelID, byte channelType) {
        ConnectionHandler.getInstance().sendMessage(messageContent, channelID, channelType);
    }

    public void sendMessage(WKMessageContent messageContent, WKMsgSetting setting, String channelID, byte channelType) {
        ConnectionHandler.getInstance().sendMessage(messageContent, setting, channelID, channelType);
    }

    /**
     * 发送消息
     *
     * @param msg 消息对象
     */
    public void sendMessage(WKMsg msg) {
        ConnectionHandler.getInstance().sendMessage(msg);
    }

    public String createClientMsgNO() {
        return UUID.randomUUID().toString().replaceAll("-", "") + "1";
    }
}
