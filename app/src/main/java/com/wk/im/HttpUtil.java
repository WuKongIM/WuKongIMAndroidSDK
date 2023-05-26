package com.wk.im;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.wukong.im.WKIM;
import com.wukong.im.entity.WKMsg;
import com.wukong.im.message.type.WKSendMsgResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HttpUtil {
    public final String apiURL = "https://api.githubim.com";

    private static class HttpUtilTypeClass {
        private static final HttpUtil instance = new HttpUtil();
    }

    public static HttpUtil getInstance() {
        return HttpUtilTypeClass.instance;
    }

    public void post(String url, JSONObject data, final IResult iResult) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(apiURL + url).openConnection();
            conn.setRequestMethod("POST");
            conn.setReadTimeout(5000);
            conn.setConnectTimeout(5000);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Connection", "keep-Alive");
            conn.setRequestProperty("Content-Type", "application/json");
            OutputStream outputStream = conn.getOutputStream();
            outputStream.write(data.toString().getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            Log.e("返回状态", conn.getResponseCode() + "");
            if (conn.getResponseCode() == 200) {
                InputStream inputStream = conn.getInputStream();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                int length;
                byte[] bytes = new byte[1024];
                while ((length = inputStream.read(bytes)) != -1) {
                    byteArrayOutputStream.write(bytes, 0, length);
                }
                byteArrayOutputStream.close();
                inputStream.close();
                String data1 = byteArrayOutputStream.toString();
                iResult.onResult(200, data1);
            } else {
                iResult.onResult(conn.getResponseCode(), "");
            }
        } catch (Throwable e) {
            e.printStackTrace();
            iResult.onResult(500, "");
        }
    }

    public void get(String url1, final IResult iResult) {
        try {
            URL url = new URL(apiURL + url1);
            //获取访问对象
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            //设置请求参数
            connection.setDoOutput(false);
            connection.setDoInput(true);
            //设置请求方式
            connection.setRequestMethod("GET");
            //设置缓存
            connection.setUseCaches(true);
            //设置重定向
            connection.setInstanceFollowRedirects(true);
            //设置超时时间
            connection.setConnectTimeout(5000);
            //连接
            connection.connect();
            //获取响应码
            int code = connection.getResponseCode();
            if (code == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String result = br.readLine();
                iResult.onResult(200, result);
            } else {
                iResult.onResult(code, "");
            }
        } catch (IOException e) {
            e.printStackTrace();
            iResult.onResult(500, "");
        }
    }

    public void getHistoryMsg(String loginUID, String channelID, byte channelType, final IMsgResult iMsgResult) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("login_uid", loginUID);
            jsonObject.put("channel_id", channelID);
            jsonObject.put("channel_type", channelType);
            jsonObject.put("start_message_seq", 0);
            jsonObject.put("end_message_seq", 0);
            jsonObject.put("limit", 50);
            jsonObject.put("pull_mode", 1);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        post("/channel/messagesync", jsonObject, (code, data) -> {
            if (code == 200) {
                try {
                    List<UIMessageEntity> list = new ArrayList<>();
                    JSONObject resultJson = new JSONObject(data);
                    JSONArray jsonArray = resultJson.optJSONArray("messages");
                    if (jsonArray != null) {
                        for (int i = 0, size = jsonArray.length(); i < size; i++) {
                            JSONObject msgJson = jsonArray.optJSONObject(i);
                            String from_uid = msgJson.optString("from_uid");
                            String client_msg_no = msgJson.optString("client_msg_no");
                            String channel_id = msgJson.optString("channel_id");
                            byte channel_type = (byte) msgJson.optInt("channel_type");
                            long timestamp = msgJson.optLong("timestamp");
                            String payload = msgJson.optString("payload");
                            byte[] b = Base64.decode(payload, Base64.DEFAULT);
                            String content = new String(b);
                            WKMsg wkMsg = new WKMsg();
                            wkMsg.clientMsgNO = client_msg_no;
                            wkMsg.fromUID = from_uid;
                            wkMsg.channelID = channel_id;
                            wkMsg.channelType = channel_type;
                            wkMsg.timestamp = timestamp;
                            wkMsg.content = content;
                            wkMsg.status = WKSendMsgResult.send_success;
                            wkMsg.baseContentMsgModel = WKIM.getInstance().getMsgManager().getMsgContentModel(content);
                            int itemType = 0;
                            if (!TextUtils.isEmpty(from_uid) && from_uid.equals(loginUID)) {
                                itemType = 1;
                            }
                            UIMessageEntity uiMessageEntity = new UIMessageEntity(wkMsg, itemType);
                            list.add(uiMessageEntity);
                        }
                    }
                    iMsgResult.onResult(list);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public interface IResult {
        void onResult(int code, String data);
    }

    public interface IMsgResult {
        void onResult(List<UIMessageEntity> list);
    }
}
