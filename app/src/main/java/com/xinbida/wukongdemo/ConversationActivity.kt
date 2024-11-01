package com.xinbida.wukongdemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lxj.xpopup.XPopup
import com.xinbida.wukongdemo.Const.Companion.token
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.message.type.WKConnectStatus

class ConversationActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ConvAdapter
    private lateinit var titleTv: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_conv_layout)



        initView()
        initListener()
        initData()

        WKIM.getInstance().isDebug = true
        // 初始化
        WKIM.getInstance().init(this, Const.uid, token)
        // 连接
        WKIM.getInstance().connectionManager.connection()
    }

    private fun initView() {
        titleTv = findViewById(R.id.titleTv)
        recyclerView = findViewById(R.id.recyclerView)
        adapter = ConvAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    }

    private fun initListener() {
        // 监听连接状态
        WKIM.getInstance().connectionManager.addOnConnectionStatusListener(
            "conv"
        ) { code, _ ->
            Log.e("连接撞头", "$code")
            when (code) {
                WKConnectStatus.connecting -> {
                    titleTv.setText(R.string.connecting)
                }

                WKConnectStatus.fail -> {
                    titleTv.setText(R.string.connect_fail)
                }

                WKConnectStatus.success -> {
                    titleTv.setText(R.string.connect_success)
                }

                WKConnectStatus.syncMsg -> {
                    titleTv.setText(R.string.connect_syncing)
                }
                WKConnectStatus.noNetwork -> {
                    titleTv.setText(R.string.no_net)
                }
                WKConnectStatus.syncCompleted -> {
                    titleTv.setText(R.string.connect_success)
                }
            }
        }
        // 监听刷新频道资料
        WKIM.getInstance().channelManager.addOnRefreshChannelInfo(
            "conv"
        ) { channel, _ ->
            for (index in adapter.data.indices) {
                if (adapter.data[index].channelID == channel?.channelID) {
                    adapter.data[index].wkChannel = channel!!
                    if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE || (!recyclerView.isComputingLayout)) {
                        recyclerView.post {
                            adapter.notifyItemChanged(index, adapter.data[index])
                        }
                    }
                    break
                }
            }
        }

        WKIM.getInstance().conversationManager.addOnRefreshMsgListener(
            "conv"
        ) { uiConversationMsg, _ ->
            var isAdd = true
            for (index in adapter.data.indices) {
                if (adapter.data[index].channelID == uiConversationMsg?.channelID) {
                    isAdd = false
                    adapter.data[index].wkMsg = uiConversationMsg?.wkMsg
                    adapter.data[index].lastMsgSeq =
                        uiConversationMsg?.lastMsgSeq!!
                    adapter.data[index].clientMsgNo =
                        uiConversationMsg.clientMsgNo
                    adapter.data[index].unreadCount =
                        uiConversationMsg.unreadCount
                    adapter.data[index].lastMsgTimestamp =
                        uiConversationMsg.lastMsgTimestamp
                    if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE || (!recyclerView.isComputingLayout)) {
                        adapter.notifyItemChanged(index)
                    }
                    break
                }
            }
            if (isAdd) {
                adapter.addData(0, uiConversationMsg!!)
                recyclerView.scrollToPosition(0)
            }
        }
        findViewById<AppCompatImageView>(R.id.addIV).setOnClickListener {
            showInputChannelIDDialog()
        }
    }

    private fun initData() {
        val all = WKIM.getInstance().conversationManager.all
        all.sortByDescending { it.lastMsgTimestamp }
        adapter.setList(all)
    }

    private fun showInputChannelIDDialog() {
        XPopup.Builder(this).moveUpToKeyboard(true).autoOpenSoftInput(true)
            .asCustom(UpdateChannelIDView(
                this, "", WKChannelType.PERSONAL
            ) { channelID: String, channelType: Byte ->
                runOnUiThread {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("channel_id", channelID)
                    intent.putExtra("channel_type", channelType)
                    startActivity(intent)
                }

            }).show()
    }

    override fun onResume() {
        super.onResume()
        // 连接
        WKIM.getInstance().connectionManager.connection()
    }

    override fun onDestroy() {
        super.onDestroy()
        WKIM.getInstance().connectionManager.removeOnConnectionStatusListener("conv")
        WKIM.getInstance().channelManager.removeRefreshChannelInfo("conv")
        WKIM.getInstance().conversationManager.removeOnRefreshMsgListener("conv")
    }
}