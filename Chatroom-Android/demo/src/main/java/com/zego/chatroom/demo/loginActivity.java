package com.zego.chatroom.demo;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.zego.chatroom.demo.utils.ChatroomInfoHelper;

public class loginActivity extends AppCompatActivity implements View.OnClickListener{

    private TextView mUserName;
    private TextView mPasswd;
    private Button mLoginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mUserName=findViewById(R.id.account_input);
        mPasswd = findViewById(R.id.password_input);
        mLoginButton = findViewById(R.id.btn_login);
        mLoginButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_login:
                String userName = mUserName.getText().toString();
                if(TextUtils.isEmpty(userName)) {
                    Toast.makeText(loginActivity.this, "用户名不能为空!", Toast.LENGTH_SHORT).show();
                    return;
                }
                String passWord = mPasswd.getText().toString();
                if(TextUtils.isEmpty(passWord)) {
                    Toast.makeText(loginActivity.this, "密码不能为空!", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(this, ChatroomListActivity.class);
                intent.putExtra(ChatroomListActivity.EXTRA_KEY_USERNAME,userName);
                startActivity(intent);
                break;
            default:
                break;
        }
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
