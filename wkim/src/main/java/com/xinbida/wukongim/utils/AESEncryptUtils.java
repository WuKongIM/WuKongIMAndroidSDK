package com.xinbida.wukongim.utils;

import android.util.Base64;
import android.util.Log;

import com.xinbida.wukongim.WKIMApplication;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 2/26/21 12:11 PM
 */
public class AESEncryptUtils {

    /**
     * 加密算法
     */
    private static final String KEY_ALGORITHM = "AES";

    /**
     * AES 的 密钥长度，32 字节，范围：16 - 32 字节
     */
    public static final int SECRET_KEY_LENGTH = 32;

    /**
     * 字符编码
     */
    private static final Charset CHARSET_UTF8 = StandardCharsets.UTF_8;

    /**
     * 秘钥长度不足 16 个字节时，默认填充位数
     */
    private static final String DEFAULT_VALUE = "0";
    /**
     * 加解密算法/工作模式/填充方式
     */
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS7Padding";

    /**
     * aes加密
     *
     * @param sSrc 内容
     * @param sKey key
     * @param salt 安全码
     * @return
     * @throws Exception
     */
    public static byte[] aesEncrypt(String sSrc, String sKey, String salt) {

        Cipher cipher = null;
        byte[] encrypted = null;
        try {
            cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            byte[] raw = sKey.getBytes(CHARSET_UTF8);
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            //使用CBC模式，需要一个向量iv，可增加加密算法的强度
            IvParameterSpec iv = new IvParameterSpec(salt.getBytes(CHARSET_UTF8));
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);
            encrypted = cipher.doFinal(sSrc.getBytes());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
            Log.e("加密错误：", "-->");
        }
        if (encrypted == null) {
            Log.e("加密后的数据为空", "--->");
            encrypted = sSrc.getBytes();
        }
        return encrypted;
    }

    /**
     * 解密
     *
     * @param sSrc 内容
     * @param sKey 密钥
     * @param salt 安全码
     * @return 内容
     */
    public static String aesDecrypt(byte[] sSrc, String sKey, String salt) {
        byte[] raw = sKey.getBytes(CHARSET_UTF8);
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher;
        String content = "";
        try {
            cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            IvParameterSpec iv = new IvParameterSpec(salt.getBytes(CHARSET_UTF8));
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
            //先用base64解密
            byte[] original = cipher.doFinal(sSrc);
            content = new String(original, CHARSET_UTF8);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("解密错误：", "--->");
        }

        return content;
    }

    /**
     * 将 Base64 字符串 解码成 字节数组
     */
    public static byte[] base64Decode(String data) {
        return Base64.decode(data, Base64.NO_WRAP);
    }

    /**
     * 将 字节数组 转换成 Base64 编码
     */
    public static String base64Encode(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static String digest(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                int c = b & 0xff; //负数转换成正数
                String result = Integer.toHexString(c); //把十进制的数转换成十六进制的书
                if (result.length() < 2) {
                    sb.append(0); //让十六进制全部都是两位数
                }
                sb.append(result);
            }
            return sb.toString(); //返回加密后的密文
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }

    public static boolean checkRSASign(String content, String sign) {
        try {

            String publicKey = WKIMApplication.getInstance().getRSAPublicKey();
            byte[] keyByte = base64Decode(publicKey);
            String key = new String(keyByte);
            key = key.replace("-----BEGIN PUBLIC KEY-----", "");
            key = key.replace("-----END PUBLIC KEY-----", "");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(base64Decode(key));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicK = keyFactory.generatePublic(keySpec);
            Signature signature = Signature.getInstance("MD5withRSA");
            signature.initVerify(publicK);
            signature.update(content.getBytes());
            boolean result = signature.verify(base64Decode(sign));
            Log.e("校验结果", result + "");
            return result;
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | InvalidKeySpecException e) {
            e.printStackTrace();
            Log.e("校验异常", e.getLocalizedMessage());
            return false;
        }
    }
}
