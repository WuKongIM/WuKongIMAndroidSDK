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
import java.util.List;

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

    public void delete(String url, JSONObject data, final IResult iResult) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(apiURL + url).openConnection();
            conn.setRequestMethod("DELETE");
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

    public void put(String url, JSONObject data, final IResult iResult) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(apiURL + url).openConnection();
            conn.setRequestMethod("PUT");
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
        Log.e("消息数据:", msgJson.toString());
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
        recent.is_deleted = msgJson.optInt("is_deleted");
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
            extraMsg.message_id = extraJson.optString("message_id_str");
            if (extraJson.has("revoke"))
                extraMsg.revoke = extraJson.optInt("revoke");
            if (extraJson.has("revoker"))
                extraMsg.revoker = extraJson.optString("revoker");
            if (extraJson.has("readed"))
                extraMsg.readed = extraJson.optInt("readed");
            if (extraJson.has("readed_count"))
                extraMsg.readed_count = extraJson.optInt("readed_count");
            if (extraJson.has("is_mutual_deleted"))
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

    public void clearChannelMsg(String channelId, byte channelType) {
        JSONObject json = new JSONObject();
        try {
            long msgSeq = WKIM.getInstance().getMsgManager().getMaxMessageSeqWithChannel(channelId, channelType);
            json.put("login_uid", Const.Companion.getUid());
            json.put("channel_id", channelId);
            json.put("channel_type", channelType);
            json.put("message_seq", msgSeq);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        new Thread(() -> post("/message/offset", json, (code, data) -> {
            if (code == 200) {
                WKIM.getInstance().getMsgManager().clearWithChannel(channelId, channelType);
            }
        })).start();
    }

    public void syncMsgExtra(String channelId, byte channelType) {
        JSONObject json = new JSONObject();
        try {
            long version = WKIM.getInstance().getMsgManager().getMsgExtraMaxVersionWithChannel(channelId, channelType);
            json.put("login_uid", Const.Companion.getUid());
            json.put("channel_id", channelId);
            json.put("channel_type", channelType);
            json.put("source", Const.Companion.getUid());
            json.put("extra_version", version);
            json.put("limit", 100);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        new Thread(() -> post("/message/extra/sync", json, (code, data) -> {
            if (code == 200 && !TextUtils.isEmpty(data)) {
                try {
                    JSONArray arr = new JSONArray(data);
                    List<WKSyncExtraMsg> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject extraJson = arr.optJSONObject(i);
                        WKSyncExtraMsg extraMsg = new WKSyncExtraMsg();
                        extraMsg.message_id_str = extraJson.optString("message_id_str");
                        extraMsg.message_id = extraJson.optString("message_id_str");
                        extraMsg.revoker = extraJson.optString("revoker");
                        extraMsg.readed = extraJson.optInt("readed");
                        extraMsg.readed_count = extraJson.optInt("readed_count");
                        extraMsg.is_mutual_deleted = extraJson.optInt("is_mutual_deleted");
                        extraMsg.extra_version = extraJson.optLong("extra_version");
                        extraMsg.revoke = extraJson.optInt("revoke");
                        list.add(extraMsg);
                    }
                    WKIM.getInstance().getMsgManager().saveRemoteExtraMsg(new WKChannel(channelId, channelType), list);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        })).start();
    }

    public void updateGroupName(String groupNo, String name) {
        JSONObject json = new JSONObject();
        try {
            json.put("login_uid", Const.Companion.getUid());
            json.put("name", name);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        new Thread(() -> put("/groups/" + groupNo, json, (code, data) -> {
            if (code == 200) {
                Log.e("修改成功", "-->");
            }
        })).start();
    }

    public void clearUnread(String channelId, byte channelType) {
        JSONObject json = new JSONObject();
        try {
            json.put("login_uid", Const.Companion.getUid());
            json.put("channel_id", channelId);
            json.put("channel_type", channelType);
            json.put("unread", 0);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        new Thread(() -> put("/conversation/clearUnread", json, (code, data) -> {
            if (code == 200) {
                Log.e("修改成功", "-->");
            }
        })).start();
    }

    public void revokeMsg(String channelId, byte channelType, String messageId, String clientMsgNo) {
        JSONObject json = new JSONObject();
        try {
            json.put("login_uid", Const.Companion.getUid());
            json.put("channel_id", channelId);
            json.put("channel_type", channelType);
            json.put("client_msg_no", clientMsgNo);
            json.put("message_id", messageId);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        new Thread(() -> post("/message/revoke", json, (code, data) -> {
            if (code == 200) {
                Log.e("撤回成功", "--->");
            }
        })).start();
    }

    public void deleteMsg(String channelId, byte channelType, int messageSeq, String messageId, String clientMsgNo) {
        JSONObject json = new JSONObject();
        try {
            json.put("login_uid", Const.Companion.getUid());
            json.put("channel_id", channelId);
            json.put("channel_type", channelType);
            json.put("message_seq", messageSeq);
            json.put("message_id", messageId);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        new Thread(() -> post("/message/delete", json, (code, data) -> {
            if (code == 200) {
                Log.e("删除消息成功", "-->");
                WKIM.getInstance().getMsgManager().deleteWithClientMsgNO(clientMsgNo);
            }
        })).start();
    }
}
