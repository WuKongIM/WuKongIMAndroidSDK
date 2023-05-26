package com.wukong.im.interfaces;

public interface ICryptoSignalData {
    void getChannelSignalData(String channelID, byte channelTyp, ICryptoSignalDataResult iCryptoSignalDataResult);
}
