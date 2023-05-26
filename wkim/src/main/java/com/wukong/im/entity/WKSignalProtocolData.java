package com.wukong.im.entity;

import java.util.List;

public class WKSignalProtocolData {
    public byte channelType;
    public String channelID;
    public String identityKey;
    public int signedPreKeyID;
    public String signedPubKey;
    public String signedSignature;
    public int registrationID;
    public List<WKOneTimePreKey> oneTimePreKeyList;
}
