package com.wukong.im.interfaces;


import com.wukong.im.entity.WKCMD;

/**
 * 2/3/21 2:23 PM
 * cmd监听
 */
public interface ICMDListener {
    void onMsg(WKCMD wkcmd);
}
