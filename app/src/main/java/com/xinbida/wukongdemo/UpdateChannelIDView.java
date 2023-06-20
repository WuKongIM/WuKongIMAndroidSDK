package com.xinbida.wukongdemo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.lxj.xpopup.core.CenterPopupView;
import com.xinbida.wukongim.entity.WKChannelType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@SuppressLint("ViewConstructor")
public class UpdateChannelIDView extends CenterPopupView {
    private byte channelType;
    private final IUpdateListener iUpdateListener;
    private final String loginUID;

    public UpdateChannelIDView(@NonNull Context context, String loginUID, byte channelType, final IUpdateListener iUpdateListener) {
        super(context);
        this.loginUID = loginUID;
        this.iUpdateListener = iUpdateListener;
        this.channelType = channelType;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.input_uid_dialog;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        EditText nameEt = findViewById(R.id.nameEt);
        TextView desTv = findViewById(R.id.desTv);
        RadioGroup radio = findViewById(R.id.radio);
        RadioButton personalRB = findViewById(R.id.personalRB);
        RadioButton groupRB = findViewById(R.id.groupRB);
        if (channelType == WKChannelType.GROUP) {
            desTv.setText(R.string.input_group_id);
            groupRB.setChecked(true);
        } else {
            desTv.setText(R.string.input_uid);
            personalRB.setChecked(true);
        }

        radio.setOnCheckedChangeListener((group, checkedId) -> {
            if (personalRB.isChecked()) {
                channelType = WKChannelType.PERSONAL;
                desTv.setText(R.string.input_uid);
            } else if (groupRB.isChecked()) {
                channelType = WKChannelType.GROUP;
                desTv.setText(R.string.input_group_id);
            }
        });
        findViewById(R.id.sureTv).setOnClickListener(v -> {
            String name = nameEt.getText().toString();
            if (!TextUtils.isEmpty(name)) {
                if (channelType == WKChannelType.GROUP) {
                    new Thread(() -> {
                        JSONObject jsonObject = new JSONObject();
                        try {
                            JSONArray jsonArray = new JSONArray();
                            jsonArray.put(loginUID);
                            jsonObject.put("channel_id", name);
                            jsonObject.put("channel_type", WKChannelType.GROUP);
                            jsonObject.put("reset", 0);
                            jsonObject.put("subscribers", jsonArray);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        HttpUtil.getInstance().post("/channel/subscriber_add", jsonObject, (code, data) -> {
                            if (code == 200) {
                                iUpdateListener.onResult(name, channelType);
                                ((Activity) getContext()).runOnUiThread(this::dismiss);
                            }
                        });
                    }).start();
                } else {
                    iUpdateListener.onResult(name, channelType);
                    dismiss();
                }
            }
        });
    }

    public interface IUpdateListener {
        void onResult(String channelID, byte channelType);
    }
}
