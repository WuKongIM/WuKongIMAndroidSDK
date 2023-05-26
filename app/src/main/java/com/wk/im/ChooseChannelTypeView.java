package com.wk.im;

import android.content.Context;

import androidx.annotation.NonNull;

import com.lxj.xpopup.core.AttachPopupView;
import com.wukong.im.entity.WKChannelType;

public class ChooseChannelTypeView extends AttachPopupView {
    public ChooseChannelTypeView(@NonNull Context context, IResult iResult) {
        super(context);
        this.iResult = iResult;
    }

    private final IResult iResult;

    @Override
    protected void onCreate() {
        super.onCreate();
        findViewById(R.id.personalChatTv).setOnClickListener(v -> {
            iResult.onResult(WKChannelType.PERSONAL);
            dismiss();
        });
        findViewById(R.id.groupChatTv).setOnClickListener(v -> {
            iResult.onResult(WKChannelType.GROUP);
            dismiss();
        });
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.choose_channel_type_view;
    }

    public interface IResult {
        void onResult(byte channelType);
    }
}
