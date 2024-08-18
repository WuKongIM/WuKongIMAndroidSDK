package com.xinbida.wukongdemo;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.xinbida.wukongim.WKIM;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_login_layout);
        onListener();
        TextView descTv = findViewById(R.id.descTv);
        String v = WKIM.getInstance().getVersion();
        String c = "悟空IM演示程序。当前SDK版本：【" + v + "】";
        descTv.setText(c);
    }

    private void onListener() {
        EditText urlET = findViewById(R.id.urlET);
        String url = urlET.getText().toString();
        if (!TextUtils.isEmpty(url)) {
            HttpUtil.getInstance().apiURL = url;
        }

        findViewById(R.id.loginBtn).setOnClickListener(v -> {
            String uid = ((EditText) findViewById(R.id.uidET)).getText().toString();
            String token = ((EditText) findViewById(R.id.tokenET)).getText().toString();
            if (!TextUtils.isEmpty(uid) && !TextUtils.isEmpty(token)) {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("uid", uid);
                    jsonObject.put("token", token);
                    jsonObject.put("device_flag", 0);
                    jsonObject.put("device_level", 1);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                new Thread(() -> HttpUtil.getInstance().post("/user/token", jsonObject, (code, data) -> {
                    if (code == 200) {
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        intent.putExtra("uid", uid);
                        intent.putExtra("token", token);
                        startActivity(intent);
                        finish();
                    } else {
                        runOnUiThread(() -> Toast.makeText(LoginActivity.this, "登录失败【" + code + "】", Toast.LENGTH_SHORT).show());
                    }
                })).start();
            }
        });
    }
}
