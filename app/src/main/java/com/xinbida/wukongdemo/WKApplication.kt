package com.xinbida.wukongdemo

import android.app.Application
import android.text.TextUtils
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKSyncChat
import com.xinbida.wukongim.entity.WKSyncConvMsg
import com.xinbida.wukongim.entity.WKSyncRecent
import com.xinbida.wukongim.interfaces.IGetSocketIpAndPortListener
import com.xinbida.wukongim.interfaces.ISyncConversationChatBack
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs

class WKApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initListener()
    }


    private val avatars = arrayOf(
        "https://lmg.jj20.com/up/allimg/tx29/06052048151752929.png",
        "https://img1.baidu.com/it/u=1653751609,236581088&fm=253&fmt=auto&app=120&f=JPEG?w=500&h=500",
        "https://img0.baidu.com/it/u=1008951549,1654888911&fm=253&fmt=auto&app=120&f=JPEG?w=800&h=800",
        "https://lmg.jj20.com/up/allimg/tx30/10121138219844229.jpg",
        "https://lmg.jj20.com/up/allimg/tx28/430423183653303.jpg",
        "https://lmg.jj20.com/up/allimg/tx23/520420024834916.jpg",
        "https://himg.bdimg.com/sys/portraitn/item/public.1.a535a65d.tJe8MgWmP8zJ456B73Kzfg",
        "https://img2.baidu.com/it/u=3324164588,1070151830&fm=253&fmt=auto&app=120&f=JPEG?w=500&h=500",
        "https://img1.baidu.com/it/u=3916753633,2634890492&fm=253&fmt=auto&app=138&f=JPEG?w=400&h=400",
        "https://img0.baidu.com/it/u=4210586523,443489101&fm=253&fmt=auto&app=138&f=JPEG?w=304&h=304",
        "https://img2.baidu.com/it/u=2559320899,1546883787&fm=253&fmt=auto&app=138&f=JPEG?w=441&h=499",
        "https://img0.baidu.com/it/u=2952429745,3806929819&fm=253&fmt=auto&app=138&f=JPEG?w=380&h=380",
        "https://img2.baidu.com/it/u=3783923022,668713258&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=500",
    )

    private fun initListener() {
        // 注册自定义消息
        WKIM.getInstance().msgManager.registerContentMsg(OrderMessageContent::class.java)
        // 连接地址
        WKIM.getInstance().connectionManager.addOnGetIpAndPortListener { andPortListener: IGetSocketIpAndPortListener ->
            Thread {
                HttpUtil.getInstance()["/route", { code: Int, data: String ->
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
        }
        // 对接频道资料(群信息/用户信息)
        WKIM.getInstance().channelManager.addOnGetChannelInfoListener { channelId, channelType, _ ->
            val channel = WKChannel(channelId, channelType)
            if (channelType == WKChannelType.PERSONAL) {
                channel.channelName = "单聊${channelId.hashCode()}"
            } else {
                channel.channelName = "群聊${channelId.hashCode()}"
            }
            val index = (channelId.hashCode()) % (avatars.size)
            channel.avatar = avatars[abs(index)]
            //  channel.avatar ="https://api.multiavatar.com/${channel.channelID}.png"
            WKIM.getInstance().channelManager.saveOrUpdateChannel(channel)
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
    }

    private fun syncConv(
        lastMsgSeqs: String?,
        msgCount: Int,
        version: Long,
        iSyncConvChatBack: ISyncConversationChatBack?
    ) {
        val json = JSONObject()
        json.put("uid", Const.uid)
        json.put("version", version)
        json.put("last_msg_seqs", lastMsgSeqs)
        json.put("msg_count", msgCount)
        Thread {
            HttpUtil.getInstance().post(
                "/conversation/sync", json
            ) { code, data ->
                if (code != 200 || TextUtils.isEmpty(data)) {
                    iSyncConvChatBack?.onBack(null)
                }
                val arr = JSONArray(data!!)
                val chat = getWKSyncChat(arr)
                iSyncConvChatBack?.onBack(chat)
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