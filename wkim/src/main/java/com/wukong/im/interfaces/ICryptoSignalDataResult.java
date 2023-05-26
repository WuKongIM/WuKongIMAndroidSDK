package com.wukong.im.interfaces;

import com.wukong.im.entity.WKSignalKey;

public interface ICryptoSignalDataResult {
    void onResult(WKSignalKey signalKey);
}
