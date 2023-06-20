package com.xinbida.wukongim.protocol;

import android.os.Parcel;
import android.os.Parcelable;

import com.xinbida.wukongim.entity.WKMentionInfo;
import com.xinbida.wukongim.msgmodel.WKReply;

import org.json.JSONObject;

import java.util.List;

/**
 * 2019-11-10 15:14
 * 基础内容消息实体
 */
public class WKMessageContent implements Parcelable {
    public boolean isCheckForceSendMsg = true;
    //内容
    public String content;
    //发送者id
    public String fromUID;
    //发送者名称
    public String fromName;
    //消息内容类型
    public int type;
    //是否@所有人
    public int mentionAll;
    //@成员列表
    public WKMentionInfo mentionInfo;
    //回复对象
    public WKReply reply;
    //搜索关键字
    public String searchableWord;
    //最近会话提示文字
    public String displayContent;
    public int isDelete;
    public String robotID;
    public int flame;
    public int flameSecond;
    public String topicID;
    public List<WKMsgEntity> entities;

    public WKMessageContent() {
    }

    protected WKMessageContent(Parcel in) {
        isCheckForceSendMsg = in.readByte() != 0;
        content = in.readString();
        fromUID = in.readString();
        fromName = in.readString();
        type = in.readInt();

        mentionAll = in.readInt();
        mentionInfo = in.readParcelable(WKMentionInfo.class.getClassLoader());
        searchableWord = in.readString();
        displayContent = in.readString();
        reply = in.readParcelable(WKReply.class.getClassLoader());
        isDelete = in.readInt();
        robotID = in.readString();
        entities = in.createTypedArrayList(WKMsgEntity.CREATOR);
        flame = in.readInt();
        flameSecond = in.readInt();
        topicID = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (isCheckForceSendMsg ? 1 : 0));
        dest.writeString(content);
        dest.writeString(fromUID);
        dest.writeString(fromName);
        dest.writeInt(type);
        dest.writeInt(mentionAll);
        dest.writeParcelable(mentionInfo, flags);
        dest.writeString(searchableWord);
        dest.writeString(displayContent);
        dest.writeParcelable(reply, flags);
        dest.writeInt(isDelete);
        dest.writeString(robotID);
        dest.writeTypedList(entities);
        dest.writeInt(flame);
        dest.writeInt(flameSecond);
        dest.writeString(topicID);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<WKMessageContent> CREATOR = new Creator<WKMessageContent>() {
        @Override
        public WKMessageContent createFromParcel(Parcel in) {
            return new WKMessageContent(in);
        }

        @Override
        public WKMessageContent[] newArray(int size) {
            return new WKMessageContent[size];
        }
    };

    public JSONObject encodeMsg() {
        return new JSONObject();
    }

    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        return this;
    }

    // 搜索本类型消息的关键字
    public String getSearchableWord() {
        return content;
    }

    // 需显示的文字
    public String getDisplayContent() {
        return displayContent;
    }
}
