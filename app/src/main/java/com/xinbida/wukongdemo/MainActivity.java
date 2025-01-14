package com.xinbida.wukongdemo;

import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lxj.xpopup.XPopup;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.interfaces.IClearMsgListener;
import com.xinbida.wukongim.interfaces.IGetOrSyncHistoryMsgBack;
import com.xinbida.wukongim.interfaces.IRefreshMsg;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    MessageAdapter adapter;
    private TextView nameTV;
    private String channelID;
    private byte channelType;
    private EditText contentEt;
    private boolean isLoading; // 是否加载历史中
    private boolean isCanMore; // 是否能加载更多
    private boolean isCanRefresh = true; // 是否能刷新

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        channelID = getIntent().getStringExtra("channel_id");
        channelType = getIntent().getByteExtra("channel_type", WKChannelType.PERSONAL);
        recyclerView = findViewById(R.id.recycleView);
        nameTV = findViewById(R.id.nameTV);
        contentEt = findViewById(R.id.contentEt);
        adapter = new MessageAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(adapter);
        onListener();
        long orderSeq = getIntent().getLongExtra("old_order_seq", 0);
        getData(orderSeq, 0, true, true);

        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
        if (channel != null) {
            nameTV.setText(channel.channelName);
        }
        HttpUtil.getInstance().clearUnread(channelID, channelType);
    }

    private void refresh() {
        if (isLoading) {
            return;
        }
        long orderSeq = 0;
        if (!adapter.getData().isEmpty()) {
            orderSeq = adapter.getData().get(0).msg.orderSeq;
        }
        getData(orderSeq, 0, false, false);
    }

    private void more() {
        if (isLoading) {
            return;
        }
        long orderSeq = 0;
        if (!adapter.getData().isEmpty()) {
            orderSeq = adapter.getData().get(adapter.getData().size() - 1).msg.orderSeq;
        }
        getData(orderSeq, 1, false, false);
    }

    void onListener() {
        findViewById(R.id.addIV).setOnClickListener(view -> {
            final XPopup.Builder builder = new XPopup.Builder(view.getContext())
                    .atView(view).hasShadowBg(false);
            ArrayList<String> list = new ArrayList<>();
            list.add(getString(R.string.clear_channel_message));
            if (channelType == WKChannelType.GROUP) {
                list.add(getString(R.string.update_group_name));
            }
            String[] str = new String[list.size()];
            list.toArray(str);
            builder.asAttachList(str, null,
                            (position, text) -> {
                                if (position == 0) {
                                    HttpUtil.getInstance().clearChannelMsg(channelID, channelType);
                                } else {

                                    new XPopup.Builder(view.getContext()).asInputConfirm(getString(R.string.update_group_name), getString(R.string.input_group_name),
                                                    text1 -> HttpUtil.getInstance().updateGroupName(channelID, text1))
                                            .show();
                                }
                            })
                    .show();
        });
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == SCROLL_STATE_IDLE) {
                    if (!recyclerView.canScrollVertically(1)) { // 到达底部
                        more();
                    } else if (!recyclerView.canScrollVertically(-1)) { // 到达顶部
                        if (isCanRefresh) {
                            refresh();
                        }
                    }
                }
            }
        });
        findViewById(R.id.orderBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OrderMessageContent content = new OrderMessageContent();
                content.setNum(100);
                content.setOrderNo("13939223329");
                content.setPrice(666666);
                content.setTitle("华为三折叠手机");
                content.setImgUrl("https://img1.baidu.com/it/u=4053553333,3320441183&fm=253&fmt=auto&app=120&f=JPEG?w=500&h=645");
                WKIM.getInstance().getMsgManager().send(content, new WKChannel(channelID, channelType));
            }
        });
        findViewById(R.id.sendBtn).setOnClickListener(v -> {
            String content = contentEt.getText().toString();
            if (TextUtils.isEmpty(channelID)) {
                Toast.makeText(this, getString(R.string.input_channel_id), Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(content)) {
                Toast.makeText(this, getString(R.string.input_content), Toast.LENGTH_SHORT).show();
                return;
            }
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    long time = System.currentTimeMillis();
                    WKIM.getInstance().getMsgManager().send(new WKTextContent(content + time), new WKChannel(channelID, channelType));
                }
            }, 100, 100);
            contentEt.setText("");
        });


        // 新消息监听
        WKIM.getInstance().getMsgManager().addOnNewMsgListener(channelID, msgList -> {
            for (WKMsg msg : msgList) {
                if (msg.type == 56) {
                    adapter.addData(new UIMessageEntity(msg, 3));
                } else {
                    adapter.addData(new UIMessageEntity(msg, 0));
                }
            }
            recyclerView.scrollToPosition(adapter.getData().size() - 1);
        });
        // 监听发送消息入库返回
        WKIM.getInstance().getMsgManager().addOnSendMsgCallback(channelID, msg -> {
            if (msg.type == 56) {
                adapter.addData(new UIMessageEntity(msg, 2));
            } else {
                adapter.addData(new UIMessageEntity(msg, 1));
            }
            recyclerView.scrollToPosition(adapter.getData().size() - 1);
        });
        // 发送消息回执
        WKIM.getInstance().getMsgManager().addOnSendMsgAckListener(channelID, msg -> {
            for (int i = 0, size = adapter.getData().size(); i < size; i++) {
                if (adapter.getData().get(i).msg.clientSeq == msg.clientSeq) {
                    adapter.getData().get(i).msg.status = msg.status;
                    adapter.getData().get(i).msg.messageID = msg.messageID;
                    adapter.getData().get(i).msg.messageSeq = msg.messageSeq;
                    adapter.notifyItemChanged(i);
                    break;
                }
            }
        });

        // 刷新消息
        WKIM.getInstance().getMsgManager().addOnRefreshMsgListener(channelID, new IRefreshMsg() {
            @Override
            public void onRefresh(WKMsg msg, boolean left) {
                if (msg == null) {
                    return;
                }
                for (int i = 0; i < adapter.getData().size(); i++) {
                    if (adapter.getData().get(i).msg.clientMsgNO.equals(msg.clientMsgNO)) {
                        if (msg.remoteExtra != null && msg.remoteExtra.revoke == 1) {
                            adapter.getData().get(i).itemType = 4;
                            adapter.notifyItemChanged(i);
                        }
                        adapter.getData().get(i).msg.remoteExtra = msg.remoteExtra;
                        break;
                    }
                }
            }
        });

        // 频道刷新
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo(channelID, (channel, isEnd) -> {
            if (channel == null)
                return;
            nameTV.setText(channel.channelName);
        });


        // 监听清空消息
        WKIM.getInstance().getMsgManager().addOnClearMsgListener(channelID, new IClearMsgListener() {
            @Override
            public void clear(String channelID, byte channelType, String fromUID) {
                if (channelID.equals(MainActivity.this.channelID) && channelType == MainActivity.this.channelType) {
                    adapter.setList(new ArrayList<>());
                }
            }
        });

        // 监听删除消息
        WKIM.getInstance().getMsgManager().addOnDeleteMsgListener(channelID, msg -> {
            if (msg == null) {
                return;
            }
            for (int i = 0; i < adapter.getData().size(); i++) {
                if (msg.clientMsgNO.equals(adapter.getData().get(i).msg.clientMsgNO)) {
                    adapter.removeAt(i);
                    break;
                }
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        //   WKIM.getInstance().getConnectionManager().disconnect(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 连接
        //  WKIM.getInstance().getConnectionManager().connection();
    }

    private void getData(long oldOrderSeq, int pullMode, boolean contain, boolean isResetData) {
        WKIM.getInstance().getMsgManager().getOrSyncHistoryMessages(channelID, channelType, oldOrderSeq, contain, pullMode, 20, 0, new IGetOrSyncHistoryMsgBack() {

            @Override
            public void onSyncing() {

            }

            @Override
            public void onResult(List<WKMsg> msgList) {

                if (msgList.isEmpty()) {
                    if (pullMode == 0) {
                        isCanRefresh = false;
                    }
                    return;
                }
                ArrayList<UIMessageEntity> list = new ArrayList<>();
                for (int i = 0, size = msgList.size(); i < size; i++) {
                    int itemType;
                    if (msgList.get(i).fromUID.equals(Const.Companion.getUid())) {
                        if (msgList.get(i).type == 56) {
                            itemType = 2;
                        } else {
                            itemType = 1;
                        }
                    } else {
                        if (msgList.get(i).type == 56) {
                            itemType = 3;
                        } else {
                            itemType = 0;
                        }
                    }
                    if (msgList.get(i).remoteExtra != null && msgList.get(i).remoteExtra.revoke == 1) {
                        itemType = 4;
                    }
                    UIMessageEntity entity = new UIMessageEntity(msgList.get(i), itemType);
                    list.add(entity);
                }
                if (pullMode == 1) {
                    adapter.addData(list);
                } else {
                    adapter.addData(0, list);
                }
                isLoading = false;
                if (isResetData) {
                    recyclerView.scrollToPosition(adapter.getData().size() - 1);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 取消监听
        WKIM.getInstance().getMsgManager().removeNewMsgListener(channelID);
        WKIM.getInstance().getMsgManager().removeSendMsgCallBack(channelID);
        WKIM.getInstance().getMsgManager().removeSendMsgAckListener(channelID);
        WKIM.getInstance().getMsgManager().removeRefreshMsgListener(channelID);
        WKIM.getInstance().getMsgManager().removeClearMsg(channelID);
        WKIM.getInstance().getMsgManager().removeDeleteMsgListener(channelID);
    }

}