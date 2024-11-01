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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.interfaces.IGetOrSyncHistoryMsgBack;
import com.xinbida.wukongim.message.type.WKConnectStatus;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    MessageAdapter adapter;
    private TextView statusTv;
    private View statusIv;
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
        statusTv = findViewById(R.id.connectionTv);
        statusIv = findViewById(R.id.connectionIv);
        contentEt = findViewById(R.id.contentEt);
        adapter = new MessageAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(adapter);
        onListener();
        long orderSeq = getIntent().getLongExtra("old_order_seq", 0);
        getData(orderSeq, 0, true,true);
    }

    private void refresh() {
        if (isLoading) {
            return;
        }
        long orderSeq = adapter.getData().get(0).msg.orderSeq;
        getData(orderSeq, 0, false,false);
    }

    private void more() {
        if (isLoading) {
            return;
        }
        long orderSeq = adapter.getData().get(adapter.getData().size() - 1).msg.orderSeq;
        getData(orderSeq, 1, false,false);
    }

    void onListener() {
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
            WKIM.getInstance().getMsgManager().send(new WKTextContent(content), new WKChannel(channelID, channelType));
            contentEt.setText("");
        });

        // 连接状态监听
        WKIM.getInstance().getConnectionManager().addOnConnectionStatusListener("main_act", (code, reason) -> {
            if (code == WKConnectStatus.success) {
                statusTv.setText(R.string.connect_success);
                statusTv.setTextColor(ContextCompat.getColor(this, R.color.success));
                statusIv.setBackgroundResource(R.drawable.success);
            } else if (code == WKConnectStatus.fail) {
                statusTv.setText(R.string.connect_fail);
                statusTv.setTextColor(ContextCompat.getColor(this, R.color.error));
                statusIv.setBackgroundResource(R.drawable.error);
            } else if (code == WKConnectStatus.connecting) {
                statusTv.setText(R.string.connecting);
                statusTv.setTextColor(ContextCompat.getColor(this, R.color.black));
                statusIv.setBackgroundResource(R.drawable.conn);
            } else if (code == WKConnectStatus.noNetwork) {
                statusTv.setText(R.string.no_net);
                statusTv.setTextColor(ContextCompat.getColor(this, R.color.nonet));
                statusIv.setBackgroundResource(R.drawable.nonet);
            } else if (code == WKConnectStatus.kicked) {
                statusTv.setText(R.string.other_device_login);
                statusTv.setTextColor(ContextCompat.getColor(this, R.color.black));
                statusIv.setBackgroundResource(R.drawable.conn);
            }
        });
        // 新消息监听
        WKIM.getInstance().getMsgManager().addOnNewMsgListener("new_msg", msgList -> {
            for (WKMsg msg : msgList) {
                adapter.addData(new UIMessageEntity(msg, 0));
            }
        });
        // 监听发送消息入库返回
        WKIM.getInstance().getMsgManager().addOnSendMsgCallback("insert_msg", msg ->{
            adapter.addData(new UIMessageEntity(msg, 1));
            recyclerView.scrollToPosition(adapter.getData().size() - 1);
        } );
        // 发送消息回执
        WKIM.getInstance().getMsgManager().addOnSendMsgAckListener("ack_key", msg -> {
            for (int i = 0, size = adapter.getData().size(); i < size; i++) {
                if (adapter.getData().get(i).msg.clientSeq == msg.clientSeq) {
                    adapter.getData().get(i).msg.status = msg.status;
                    adapter.notifyItemChanged(i);
                    break;
                }
            }
        });

    }

    @Override
    protected void onStop() {
        super.onStop();
        WKIM.getInstance().getConnectionManager().disconnect(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 连接
        WKIM.getInstance().getConnectionManager().connection();
    }

    private void getData(long oldOrderSeq, int pullMode, boolean contain,boolean isResetData) {
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
                        itemType = 1;
                    } else {
                        itemType = 0;
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
        // 断开连接
        WKIM.getInstance().getConnectionManager().disconnect(true);
        // 取消监听
        WKIM.getInstance().getMsgManager().removeNewMsgListener("new_msg");
        WKIM.getInstance().getMsgManager().removeSendMsgCallBack("insert_msg");
        WKIM.getInstance().getMsgManager().removeSendMsgAckListener("ack_key");
        WKIM.getInstance().getConnectionManager().removeOnConnectionStatusListener("main_act");
    }

}