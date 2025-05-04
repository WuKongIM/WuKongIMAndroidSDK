package com.xinbida.wukongdemo

import android.app.Application
import android.text.TextUtils
import android.util.Log
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKSyncChat
import com.xinbida.wukongim.entity.WKSyncConvMsg
import com.xinbida.wukongim.entity.WKSyncRecent
import com.xinbida.wukongim.interfaces.IGetSocketIpAndPortListener
import com.xinbida.wukongim.interfaces.ISyncConversationChatBack
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class WKApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initListener()
    }


    private fun initListener() {
        // 注册自定义消息
        WKIM.getInstance().msgManager.registerContentMsg(OrderMessageContent::class.java)
        // 连接地址
        WKIM.getInstance().connectionManager.addOnGetIpAndPortListener { andPortListener: IGetSocketIpAndPortListener ->
            Thread {
                HttpUtil.getInstance()["/users/${Const.uid}/route", { code: Int, data: String ->
                    if (code == 200 && !TextUtils.isEmpty(data)) {
                        try {
                            val jsonObject = JSONObject(data)
                            val tcp_addr = jsonObject.optString("tcp_addr")
                            val strings =
                                tcp_addr.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                                    .toTypedArray()
                            andPortListener.onGetSocketIpAndPort(
                                strings[0],
                                strings[1].toInt()
                            )
                        } catch (e: JSONException) {
                            throw RuntimeException(e)
                        }
                    }
                }]
            }.start()
//            andPortListener.onGetSocketIpAndPort(
//              "192.168.3.13",
//                5100
//            )
        }
        // 对接频道资料(群信息/用户信息)
        WKIM.getInstance().channelManager.addOnGetChannelInfoListener { channelId, channelType, _ ->
            if (channelType == WKChannelType.PERSONAL) {
                HttpUtil.getInstance().getUserInfo(channelId)
            } else {
                HttpUtil.getInstance().getGroupInfo(channelId)
            }
            null
        }

        // 对接离线最近会话
        WKIM.getInstance().conversationManager.addOnSyncConversationListener { lastMsgSeqs, msgCount, version, iSyncConvChatBack ->
            syncConv(
                lastMsgSeqs,
                msgCount,
                version,
                iSyncConvChatBack
            )
        }

        // 对接频道消息
        WKIM.getInstance().msgManager.addOnSyncChannelMsgListener { channelID, channelType, startMessageSeq, endMessageSeq, limit, pullMode, iSyncChannelMsgBack ->
            Thread {
                HttpUtil.getInstance().getHistoryMsg(
                    Const.uid,
                    channelID!!,
                    channelType,
                    startMessageSeq,
                    endMessageSeq,
                    limit,
                    pullMode
                ) { msg -> iSyncChannelMsgBack?.onBack(msg) }
            }.start()
        }

        // cmd监听
        WKIM.getInstance().cmdManager.addCmdListener("application"
        ) { cmd ->
            if (cmd?.cmdKey == "channelUpdate") {
                val channelId = cmd.paramJsonObject?.optString("channel_id")
                val channelType = cmd.paramJsonObject?.optInt("channel_type")
                if (!TextUtils.isEmpty(channelId)) {
                    if (channelType?.toByte() == WKChannelType.GROUP) {
                        HttpUtil.getInstance().getGroupInfo(channelId)
                    } else {
                        HttpUtil.getInstance().getUserInfo(channelId)
                    }
                }
            } else if (cmd?.cmdKey == "unreadClear") {
                // sdk内部处理了该cmd
//                val channelId = cmd.paramJsonObject?.optString("channel_id")
//                val channelType = cmd.paramJsonObject?.optInt("channel_type")
//                val unread = cmd.paramJsonObject?.optInt("unread")
//                WKIM.getInstance().conversationManager.updateRedDot(
//                    channelId,
//                    channelType!!.toByte(),
//                    unread!!
//                )
            } else if (cmd?.cmdKey == "messageRevoke") {
                val channelId = cmd.paramJsonObject?.optString("channel_id")
                val channelType = cmd.paramJsonObject?.optInt("channel_type")
                HttpUtil.getInstance().syncMsgExtra(channelId, channelType!!.toByte())
            }
        }
    }

    private fun syncConv(
        lastMsgSeqs: String?,
        msgCount: Int,
        version: Long,
        iSyncConvChatBack: ISyncConversationChatBack?
    ) {
        val json = JSONObject()
        json.put("login_uid", Const.uid)
        json.put("version", version)
        json.put("last_msg_seqs", lastMsgSeqs)
        json.put("msg_count", msgCount)
        json.put("device_uuid", Const.uid)
        Thread {
            HttpUtil.getInstance().post(
                "/conversation/sync", json
            ) { code, data ->
                if (code != 200 || TextUtils.isEmpty(data)) {
                    Log.e("同步失败","-->")
                    iSyncConvChatBack?.onBack(null)
                    return@post
                }
                val dataJson = JSONObject(data)
                val arr = dataJson.optJSONArray("conversations")
                if (arr != null && arr.length() > 0) {
                    val chat = getWKSyncChat(arr)
                    iSyncConvChatBack?.onBack(chat)
                    return@post
                }
                iSyncConvChatBack?.onBack(null)
            }
        }.start()
    }

    private fun getWKSyncChat(arr: JSONArray): WKSyncChat {
        val chat = WKSyncChat()
        chat.conversations = ArrayList<WKSyncConvMsg>()
        for (i in 0 until arr.length()) {
            val json = arr.getJSONObject(i)
            val convMsg = WKSyncConvMsg()
            convMsg.channel_id = json.optString("channel_id")
            convMsg.channel_type = json.optInt("channel_type").toByte()
            convMsg.unread = json.optInt("unread")
            convMsg.timestamp = json.optLong("timestamp")
            convMsg.last_msg_seq = json.optLong("last_msg_seq")
            convMsg.last_client_msg_no = json.optString("last_client_msg_no")
            convMsg.version = json.optLong("version")
            val recents: ArrayList<WKSyncRecent> = ArrayList()
            val msgArr = json.optJSONArray("recents")
            for (j in 0 until msgArr!!.length()) {
                val msgJson = msgArr.getJSONObject(j)
                val recent = HttpUtil.getInstance().getWKSyncRecent(msgJson)
                recents.add(recent)
                convMsg.recents = recents
            }
            chat.conversations.add(convMsg)
        }
        return chat
    }
}