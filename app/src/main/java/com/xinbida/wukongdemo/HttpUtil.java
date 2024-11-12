package com.xinbida.wukongdemo;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKSyncChannelMsg;
import com.xinbida.wukongim.entity.WKSyncExtraMsg;
import com.xinbida.wukongim.entity.WKSyncRecent;
import com.xinbida.wukongim.utils.WKLoggerUtils;

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
import java.util.HashMap;
import java.util.Iterator;

public class HttpUtil {
    public String apiURL = "http://62.234.8.38:7090/v1";

    private static class HttpUtilTypeClass {
        private static final HttpUtil instance = new HttpUtil();
    }

    public static HttpUtil getInstance() {
        return HttpUtilTypeClass.instance;
    }

    public String getAvatar(String channelId, byte channelType) {
        if (channelType == WKChannelType.PERSONAL) {
            return apiURL + "/users/" + channelId + "/avatar";
        }
        return apiURL + "/groups/" + channelId + "/avatar";
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

    public void getHistoryMsg(String loginUID, String channelID, byte channelType, long startSeq, long endSeq, int limit, int pullMode, final IMsgResult iMsgResult) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("login_uid", loginUID);
            jsonObject.put("channel_id", channelID);
            jsonObject.put("channel_type", channelType);
            jsonObject.put("start_message_seq", startSeq);
            jsonObject.put("end_message_seq", endSeq);
            jsonObject.put("limit", limit);
            jsonObject.put("pull_mode", pullMode);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        post("/message/channel/sync", jsonObject, (code, data) -> {
            if (code == 200) {
                try {
                    WKSyncChannelMsg msg = new WKSyncChannelMsg();
                    msg.messages = new ArrayList<>();
                    JSONObject resultJson = new JSONObject(data);
                    JSONArray jsonArray = resultJson.optJSONArray("messages");
                    if (jsonArray != null) {
                        for (int i = 0, size = jsonArray.length(); i < size; i++) {
                            JSONObject msgJson = jsonArray.optJSONObject(i);
                            WKSyncRecent recent = getWKSyncRecent(msgJson);
                            msg.messages.add(recent);
                        }
                    }
                    iMsgResult.onResult(msg);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public WKSyncRecent getWKSyncRecent(JSONObject msgJson) {
        String from_uid = msgJson.optString("from_uid");
        String client_msg_no = msgJson.optString("client_msg_no");
        String channel_id = msgJson.optString("channel_id");
        byte channel_type = (byte) msgJson.optInt("channel_type");
        long timestamp = msgJson.optLong("timestamp");

        String content = msgJson.optString("payload");
//        byte[] b = Base64.decode(payload, Base64.DEFAULT);
//        String content = new String(b);
        WKSyncRecent recent = new WKSyncRecent();
        recent.from_uid = from_uid;
        recent.message_id = msgJson.optString("message_id");
        recent.message_seq = msgJson.optInt("message_seq");
        recent.client_msg_no = client_msg_no;
        recent.channel_id = channel_id;
        recent.channel_type = channel_type;
        recent.timestamp = timestamp;
        if (!TextUtils.isEmpty(content)) {
            HashMap<String, Object> hashMap = new HashMap<>();
            JSONObject jsonObject = getJSON(content);
            if (jsonObject != null) {
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    hashMap.put(key, jsonObject.opt(key));
                }
                recent.payload = hashMap;
            }
        }
        if (msgJson.has("message_extra")) {
            JSONObject extraJson = msgJson.optJSONObject("message_extra");
            if (extraJson == null) {
                return recent;
            }
            WKSyncExtraMsg extraMsg = new WKSyncExtraMsg();
            extraMsg.message_id_str = extraJson.optString("message_id_str");
            extraMsg.revoke = extraJson.optInt("revoke");
            extraMsg.revoker = extraJson.optString("revoker");
            extraMsg.readed = extraJson.optInt("readed");
            extraMsg.readed_count = extraJson.optInt("readed_count");
            extraMsg.is_mutual_deleted = extraJson.optInt("is_mutual_deleted");
            recent.message_extra = extraMsg;
        }
        return recent;
    }

    public JSONObject getJSON(String test) {
        try {
            return new JSONObject(test);
        } catch (JSONException ex) {
            return null;
        }
    }

    public interface IResult {
        void onResult(int code, String data);
    }

    public interface IMsgResult {
        void onResult(WKSyncChannelMsg msg);
    }

    public void getUserInfo(String uid) {
        new Thread(() -> get("/users/" + uid, (code, data) -> {
            if (code == 200 && !TextUtils.isEmpty(data)) {
                try {
                    JSONObject json = new JSONObject(data);
                    WKChannel channel = new WKChannel(uid, WKChannelType.PERSONAL);
                    channel.channelName = json.optString("name");
                    channel.avatar = json.optString("avatar");
                    WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

            }
        })).start();
    }

    public void getGroupInfo(String groupNO) {
        new Thread(() -> get("/groups/" + groupNO, (code, data) -> {
            if (code == 200 && !TextUtils.isEmpty(data)) {
                try {
                    JSONObject json = new JSONObject(data);
                    WKChannel channel = new WKChannel(groupNO, WKChannelType.GROUP);
                    channel.channelName = json.optString("name");
                    channel.avatar = json.optString("avatar");
                    WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

            }
        })).start();
    }

}
