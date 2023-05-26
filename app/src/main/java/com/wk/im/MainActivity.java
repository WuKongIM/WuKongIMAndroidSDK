package com.wk.im;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lxj.xpopup.XPopup;
import com.wukong.im.WKIM;
import com.wukong.im.entity.WKChannelType;
import com.wukong.im.entity.WKMsg;
import com.wukong.im.message.type.WKConnectStatus;
import com.wukong.im.msgmodel.WKTextContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    MessageAdapter adapter;
    private TextView statusTv;
    private View statusIv;
    private String channelID;
    private byte channelType;
    private TextView inputChannelIDTV;
    private EditText contentEt;
    private String loginUID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recyclerView = findViewById(R.id.recycleView);
        statusTv = findViewById(R.id.connectionTv);
        statusIv = findViewById(R.id.connectionIv);
        inputChannelIDTV = findViewById(R.id.inputChannelIDTV);
        contentEt = findViewById(R.id.contentEt);
        loginUID = getIntent().getStringExtra("uid");
        String token = getIntent().getStringExtra("token");
        channelType = WKChannelType.PERSONAL;
        adapter = new MessageAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(adapter);
        onListener();
        WKIM.getInstance().setDebug(true);
        WKIM.getInstance().init(MainActivity.this, loginUID, token);
    }

    void onListener() {
        inputChannelIDTV.setOnClickListener(v -> showInputChannelIDDialog(MainActivity.this));

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

            WKIM.getInstance().getMsgManager().sendMessage(new WKTextContent(content), channelID, channelType);
            contentEt.setText("");
        });

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                recyclerView.scrollToPosition(adapter.getData().size() - 1);
            }
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
        WKIM.getInstance().getMsgManager().addOnSendMsgCallback("insert_msg", msg -> adapter.addData(new UIMessageEntity(msg, 1)));
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
        WKIM.getInstance().getConnectionManager().addOnGetIpAndPortListener(andPortListener -> new Thread(() -> HttpUtil.getInstance().get("/route", (code, data) -> {
            if (code == 200) {
                try {
                    JSONObject jsonObject = new JSONObject(data);
                    String tcp_addr = jsonObject.optString("tcp_addr");
                    String[] strings = tcp_addr.split(":");
                    andPortListener.onGetSocketIpAndPort(strings[0], Integer.parseInt(strings[1]));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        })).start());
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

    public void showInputChannelIDDialog(Context context) {
        new XPopup.Builder(context).moveUpToKeyboard(true).autoOpenSoftInput(true).asCustom(new UpdateChannelIDView(context, loginUID, channelType, (channelID, channelType) -> {
            runOnUiThread(() -> {
                MainActivity.this.channelType = channelType;
                MainActivity.this.channelID = channelID;
                String chat = getString(R.string.personal_chat);
                if (channelType == WKChannelType.GROUP) {
                    chat = getString(R.string.group_chat);
                }
                inputChannelIDTV.setText(chat + "【" + channelID + "】");
            });
            new Thread(() -> HttpUtil.getInstance().getHistoryMsg(loginUID, channelID, channelType, list -> {
                if (list != null && list.size() > 0) {
                    runOnUiThread(() -> {
                        adapter.setList(list);
                        recyclerView.scrollToPosition(adapter.getData().size() - 1);
                    });
                } else {
                    adapter.setList(new ArrayList<>());
                }
            })).start();
        })).show();
    }

}