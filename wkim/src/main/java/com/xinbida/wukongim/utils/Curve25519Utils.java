package com.xinbida.wukongim.utils;

import android.text.TextUtils;
import android.util.Base64;

import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;


/**
 * 2/25/21 6:20 PM
 * 消息加密处理
 */
public class Curve25519Utils {
    private byte[] privateKey, publicKey;
    private byte[] serverKey;
    public String aesKey;
    public String salt;

    private Curve25519Utils() {
    }

    private static class Curve25519UtilsBinder {
        private final static Curve25519Utils util = new Curve25519Utils();
    }

    public static Curve25519Utils getInstance() {
        return Curve25519UtilsBinder.util;
    }

    public void initKey() {
        Curve25519KeyPair keyPair = Curve25519.getInstance(Curve25519.BEST).generateKeyPair();
        privateKey = keyPair.getPrivateKey();
        publicKey = keyPair.getPublicKey();
    }

    public String getPublicKey() {
        return Base64.encodeToString(publicKey, Base64.NO_WRAP);
    }

    /**
     * 设置服务端公钥和安全码
     *
     * @param serverKey 公钥
     * @param salt      安全码
     */
    public void setServerKeyAndSalt(String serverKey, String salt) {
        if (TextUtils.isEmpty(serverKey) || TextUtils.isEmpty(salt)) {
            this.serverKey = new byte[0];
            this.salt = "";
            return;
        }
        this.serverKey = AESEncryptUtils.base64Decode(serverKey);
        this.salt = salt;

        Curve25519 cipher = Curve25519.getInstance(Curve25519.BEST);
        byte[] sharedSecret = cipher.calculateAgreement(this.serverKey, privateKey);
        String key = AESEncryptUtils.digest(AESEncryptUtils.base64Encode(sharedSecret));
        if (!TextUtils.isEmpty(key) && key.length() > 16) {
            aesKey = key.substring(0, 16);
        }
    }

    public String encode(String content) {
        return content;
    }

    public String decode(String content) {
        return content;
    }
}
