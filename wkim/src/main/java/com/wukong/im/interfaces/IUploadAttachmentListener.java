package com.wukong.im.interfaces;


import com.wukong.im.entity.WKMsg;

/**
 * 2020-08-02 00:29
 * 上传聊天附件
 */
public interface IUploadAttachmentListener {
    void onUploadAttachmentListener(WKMsg msg, IUploadAttacResultListener attacResultListener);
}
