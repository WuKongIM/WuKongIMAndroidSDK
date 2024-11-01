package com.xinbida.wukongdemo;

import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.xinbida.wukongim.message.type.WKSendMsgResult;

import org.jetbrains.annotations.NotNull;


class MessageAdapter extends BaseMultiItemQuickAdapter<UIMessageEntity, BaseViewHolder> {

    public MessageAdapter() {
        addItemType(1, R.layout.send_text_layout);
        addItemType(0, R.layout.recv_text_layout);
        addItemType(2, R.layout.send_order_layout);
        addItemType(3, R.layout.recv_order_layout);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, UIMessageEntity uiMessageEntity) {

        if (uiMessageEntity.getItemType() == 1 || uiMessageEntity.getItemType() == 2) {
            ImageView statusIV = baseViewHolder.getView(R.id.statusTv);
            if (uiMessageEntity.msg.status == WKSendMsgResult.send_success) {
                statusIV.setImageResource(R.mipmap.success);
            } else if (uiMessageEntity.msg.status == WKSendMsgResult.send_loading) {
                statusIV.setImageResource(R.mipmap.loading);
            } else {
                statusIV.setImageResource(R.mipmap.error);
            }
        }
        if (uiMessageEntity.getItemType() == 2||uiMessageEntity.getItemType() == 3) {
            OrderMessageContent orderMsgContent = (OrderMessageContent) uiMessageEntity.msg.baseContentMsgModel;
            baseViewHolder.setText(R.id.orderNoTV, "订单号：" + orderMsgContent.getOrderNo());
            GlideUtil.Companion.showAvatarImg(getContext(), orderMsgContent.getImgUrl(), baseViewHolder.getView(R.id.orderIV));
            baseViewHolder.setText(R.id.titleTV, orderMsgContent.getTitle());
            baseViewHolder.setText(R.id.priceTV, "$" + orderMsgContent.getPrice());
            baseViewHolder.setText(R.id.countTV, "共" + orderMsgContent.getNum() + "件");
        } else {
            TextView contentTv = baseViewHolder.getView(R.id.contentTv);
            TextView nameTv = baseViewHolder.getView(R.id.nameIv);
            String name = uiMessageEntity.msg.fromUID.substring(0, 1);
            nameTv.setText(name);
            if (uiMessageEntity.msg.baseContentMsgModel != null) {
                contentTv.setText(uiMessageEntity.msg.baseContentMsgModel.getDisplayContent());
            }
        }

    }
}
