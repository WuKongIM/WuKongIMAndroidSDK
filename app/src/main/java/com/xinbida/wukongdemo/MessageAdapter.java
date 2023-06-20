package com.xinbida.wukongdemo;

import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.xinbida.wukongim.message.type.WKSendMsgResult;

import org.jetbrains.annotations.NotNull;


class MessageAdapter extends BaseMultiItemQuickAdapter<UIMessageEntity, BaseViewHolder> {

    public MessageAdapter() {
        addItemType(1, R.layout.send_item_msg_layout);
        addItemType(0, R.layout.recv_item_msg_layout);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, UIMessageEntity uiMessageEntity) {
        TextView contentTv = baseViewHolder.getView(R.id.contentTv);
        TextView nameTv = baseViewHolder.getView(R.id.nameIv);
        if (uiMessageEntity.getItemType() == 1) {

            ImageView statusIV = baseViewHolder.getView(R.id.statusTv);
            if (uiMessageEntity.msg.status == WKSendMsgResult.send_success) {
                statusIV.setImageResource(R.mipmap.success);
            } else if (uiMessageEntity.msg.status == WKSendMsgResult.send_loading) {
                statusIV.setImageResource(R.mipmap.loading);
            } else {
                statusIV.setImageResource(R.mipmap.error);
            }
        }
        String name = uiMessageEntity.msg.fromUID.substring(0, 1);
        nameTv.setText(name);
        contentTv.setText(uiMessageEntity.msg.baseContentMsgModel.getDisplayContent());
    }
}
