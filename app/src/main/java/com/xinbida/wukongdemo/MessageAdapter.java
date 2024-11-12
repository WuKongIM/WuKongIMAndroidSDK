package com.xinbida.wukongdemo;

import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.lxj.xpopup.XPopup;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.message.type.WKSendMsgResult;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;


class MessageAdapter extends BaseMultiItemQuickAdapter<UIMessageEntity, BaseViewHolder> {

    public MessageAdapter() {
        addItemType(1, R.layout.send_text_layout);
        addItemType(0, R.layout.recv_text_layout);
        addItemType(2, R.layout.send_order_layout);
        addItemType(3, R.layout.recv_order_layout);
        addItemType(4, R.layout.item_revoke_layout);
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


        if (uiMessageEntity.getItemType() != 4) {
            if (uiMessageEntity.getItemType() == 2 || uiMessageEntity.getItemType() == 3) {
                OrderMessageContent orderMsgContent = (OrderMessageContent) uiMessageEntity.msg.baseContentMsgModel;
                baseViewHolder.setText(R.id.orderNoTV, "订单号：" + orderMsgContent.getOrderNo());
                GlideUtil.Companion.showAvatarImg(getContext(), orderMsgContent.getImgUrl(), baseViewHolder.getView(R.id.orderIV));
                baseViewHolder.setText(R.id.titleTV, orderMsgContent.getTitle());
                baseViewHolder.setText(R.id.priceTV, "$" + orderMsgContent.getPrice());
                baseViewHolder.setText(R.id.countTV, "共" + orderMsgContent.getNum() + "件");
            } else {
                TextView contentTv = baseViewHolder.getView(R.id.contentTv);
                if (uiMessageEntity.msg.baseContentMsgModel != null) {
                    contentTv.setText(uiMessageEntity.msg.baseContentMsgModel.getDisplayContent());
                }
            }
            AppCompatImageView imageView = baseViewHolder.getViewOrNull(R.id.avatarIV);
            if (imageView != null) {
                GlideUtil.Companion.showAvatarImg(getContext(), HttpUtil.getInstance().getAvatar(uiMessageEntity.msg.fromUID, WKChannelType.PERSONAL), imageView);
            }

            View contentLayout = baseViewHolder.getViewOrNull(R.id.contentLayout);
            if (contentLayout != null) {
                final XPopup.Builder builder = new XPopup.Builder(contentLayout.getContext())
                        .atView(contentLayout).hasShadowBg(false);
                ArrayList<String> list = new ArrayList<>();
                list.add(getContext().getString(R.string.msg_delete));
                if (!TextUtils.isEmpty(uiMessageEntity.msg.fromUID) && uiMessageEntity.msg.fromUID.equals(Const.Companion.getUid())) {
                    list.add(getContext().getString(R.string.msg_revoke));
                }
                String[] str = new String[list.size()];
                list.toArray(str);
                contentLayout.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        builder.asAttachList(str, null,
                                        (position, text) -> {
                                            if (position == 0) {
                                                if (uiMessageEntity.msg.messageSeq > 0) {
                                                    HttpUtil.getInstance().deleteMsg(uiMessageEntity.msg.channelID, uiMessageEntity.msg.channelType, uiMessageEntity.msg.messageSeq, uiMessageEntity.msg.messageID, uiMessageEntity.msg.messageID);
                                                } else {
                                                    WKIM.getInstance().getMsgManager().deleteWithClientMsgNO(uiMessageEntity.msg.clientMsgNO);
                                                }
                                            } else {
                                                if (uiMessageEntity.msg.messageSeq > 0) {
                                                    HttpUtil.getInstance().revokeMsg(uiMessageEntity.msg.channelID, uiMessageEntity.msg.channelType, uiMessageEntity.msg.messageID, uiMessageEntity.msg.messageID);
                                                }
                                            }
                                        })
                                .show();
                        return false;
                    }
                });
            }
        }
    }
}
