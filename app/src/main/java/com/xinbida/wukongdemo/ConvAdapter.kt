package com.xinbida.wukongdemo

import android.content.Intent
import android.view.View
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKUIConversationMsg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConvAdapter :
    BaseQuickAdapter<WKUIConversationMsg, BaseViewHolder>(R.layout.item_conv_layout) {
    override fun convert(holder: BaseViewHolder, item: WKUIConversationMsg, payloads: List<Any>) {
        super.convert(holder, item, payloads)
        val msg = payloads[0] as WKUIConversationMsg
        if (msg.wkChannel != null) {
            holder.setText(R.id.nameTV, item.wkChannel.channelName)
            GlideUtil.showAvatarImg(
                context,
                HttpUtil.getInstance()
                    .getAvatar(item.wkChannel.channelID, item.wkChannel.channelType),
                holder.getView(R.id.avatarIV)
            )
        }
        if (msg.wkMsg != null && msg.wkMsg.baseContentMsgModel != null) {
            val content = msg.wkMsg.baseContentMsgModel.displayContent
            holder.setText(R.id.contentTV, content)
        }
    }

    override fun convert(holder: BaseViewHolder, item: WKUIConversationMsg) {
        if (item.wkMsg != null && item.wkMsg.baseContentMsgModel != null) {
            val content = item.wkMsg.baseContentMsgModel.displayContent
            holder.setText(R.id.contentTV, content)
        }
        if (item.unreadCount > 0) {
            holder.setVisible(R.id.countTV, true)
            holder.setText(R.id.countTV, "${item.unreadCount}")
        } else {
            holder.setVisible(R.id.countTV, false)
        }
        holder.setText(R.id.timeTV, getShowTime(item.lastMsgTimestamp * 1000L))
        if (item.wkChannel != null) {
            holder.setText(R.id.nameTV, item.wkChannel.channelName)
            GlideUtil.showAvatarImg(
                context,
                HttpUtil.getInstance()
                    .getAvatar(item.wkChannel.channelID, item.wkChannel.channelType),
                holder.getView(R.id.avatarIV)
            )
        } else {
            WKIM.getInstance().channelManager.fetchChannelInfo(item.channelID, item.channelType)
        }

        holder.getView<View>(R.id.contentLayout).setOnClickListener {
            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra("channel_id", item.channelID)
            intent.putExtra("channel_type", item.channelType)
            if (item.wkMsg != null) {
                intent.putExtra("old_order_seq", item.wkMsg.orderSeq)
            }
            context.startActivity(intent)
        }
    }

    private fun getShowTime(timeStamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timeStamp))
    }
}