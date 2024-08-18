package com.xinbida.wukongim.msgmodel;

import com.xinbida.wukongim.message.type.WKMsgContentType;

public class WKUnknownContent extends WKMessageContent{
    public WKUnknownContent(){
        this.type = WKMsgContentType.WK_CONTENT_FORMAT_ERROR;
    }

    @Override
    public String getDisplayContent() {
        return "[未知消息]";
    }
}
