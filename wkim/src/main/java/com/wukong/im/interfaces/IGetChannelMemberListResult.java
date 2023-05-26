package com.wukong.im.interfaces;

import com.wukong.im.entity.WKChannelMember;

import java.util.List;

public interface IGetChannelMemberListResult {
    public void onResult(List<WKChannelMember> list, boolean isRemote);
}
