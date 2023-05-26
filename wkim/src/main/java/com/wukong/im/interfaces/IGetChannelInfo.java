package com.wukong.im.interfaces;


import com.wukong.im.entity.WKChannel;

/**
 * 2019-12-01 15:40
 * 获取频道信息
 */
public interface IGetChannelInfo {
    WKChannel onGetChannelInfo(String channelId, byte channelType, IChannelInfoListener iChannelInfoListener);
}
