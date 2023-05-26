package com.wukong.im.interfaces;


import com.wukong.im.entity.WKMsg;

/**
 * 2020-12-04 17:33
 * 存库之前拦截器
 */
public interface IMessageStoreBeforeIntercept {
    boolean isSaveMsg(WKMsg msg);
}
