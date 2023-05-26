package com.wk.im;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.wukong.im.entity.WKMsg;

class UIMessageEntity implements MultiItemEntity {
    public WKMsg msg;
    public int itemType = 1;

    UIMessageEntity(WKMsg msg, int itemType) {
        this.itemType = itemType;
        this.msg = msg;
    }

    @Override
    public int getItemType() {
        return itemType;
    }
}
