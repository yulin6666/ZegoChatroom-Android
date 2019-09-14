package com.zego.chatroom.demo;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONObject;
import com.zego.chatroom.demo.utils.ChatroomInfoHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class loginActivity extends AppCompatActivity implements View.OnClickListener{

    private TextView mUserNameView;
    private TextView mPasswd;
    private Button mLoginButton;

    public static String mUserName;
    public static String mRole;

    private Handler loginHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1://登录成功
                    Intent intent = new Intent(loginActivity.this, ChatroomListActivity.class);
                    intent.putExtra(ChatroomListActivity.EXTRA_KEY_USERNAME, loginActivity.mUserName);
                    intent.putExtra(ChatroomListActivity.EXTRA_KEY_USERROLE, loginActivity.mRole);
                    startActivity(intent);
                    break;
                default:
                    Toast.makeText(loginActivity.this, "用户名密码错误!", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mUserNameView=findViewById(R.id.account_input);
        mPasswd = findViewById(R.id.password_input);
        mLoginButton = findViewById(R.id.btn_login);
        mLoginButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_login:
                mUserName = mUserNameView.getText().toString();
                if(TextUtils.isEmpty(mUserName)) {
                    Toast.makeText(loginActivity.this, "用户名不能为空!", Toast.LENGTH_SHORT).show();
                    return;
                }
                String passWord = mPasswd.getText().toString();
                if(TextUtils.isEmpty(passWord)) {
                    Toast.makeText(loginActivity.this, "密码不能为空!", Toast.LENGTH_SHORT).show();
                    return;
                }

                check(mUserName,passWord);


                break;
            default:
                break;
        }
    }

    private void check(final String userName, final String Password){
        new Thread(new Runnable() {
            @Override
            public void run() {
                String urlpath = "http://114.247.187.137/api/userLogin?username="+userName+"&password="+Password;
                HttpURLConnection connection = null;
                try {
                    URL url=new URL(urlpath);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Accept", "application/json");

                    StringBuilder stringBuilder = new StringBuilder();
                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK){
                        InputStreamReader streamReader = new InputStreamReader(connection.getInputStream());
                        BufferedReader bufferedReader = new BufferedReader(streamReader);
                        String response = null;
                        while ((response = bufferedReader.readLine()) != null) {
                            stringBuilder.append(response);
                        }
                        bufferedReader.close();

                        String result = stringBuilder.toString();
                        JSONObject rep = JSONObject.parseObject(result);
                        int ret = rep.getBoolean("result")? 1 : 0;
                        JSONObject userInfoBean = rep.getJSONObject("userInfoBean");
                        mRole = userInfoBean.getString("role");
                        loginHandler.sendEmptyMessage(ret);
                    } else {
                        Log.e("", connection.getResponseMessage());
                    }
                } catch (Exception exception){
                    Log.e("", exception.toString());
                } finally {
                    if (connection != null){
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    public boolean onTouchEvent(MotionEvent event) {

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {

            if (loginActivity.this.getCurrentFocus().getWindowToken() != null) {

                imm.hideSoftInputFromWindow(loginActivity.this.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

            }

        }

        return super.onTouchEvent(event);

    }
}
