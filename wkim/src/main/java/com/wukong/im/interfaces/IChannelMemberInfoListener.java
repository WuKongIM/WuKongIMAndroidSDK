package com.wukong.im.interfaces;


import com.wukong.im.entity.WKChannelMember;

/**
 * 2019-12-01 15:54
 * 频道成员
 */
public interface IChannelMemberInfoListener {
    void onResult(WKChannelMember channelMember);
}
