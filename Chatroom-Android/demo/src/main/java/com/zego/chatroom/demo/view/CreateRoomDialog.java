package com.zego.chatroom.demo.view;

import android.app.Dialog;
import android.content.Context;
import android.support.annotation.NonNull;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import com.zego.chatroom.demo.R;
import com.zego.chatroom.demo.data.ZegoDataCenter;

/**
 * Created by zego on 2018/11/7.
 */

public class CreateRoomDialog extends Dialog implements View.OnClickListener {

    public EditText mEtRoomName;     // 房间名字 EditText
    public Button mBtCreateNow;  // 立即创建按钮

    public CreateRoomDialog(@NonNull Context context) {
        super(context, R.style.CommonDialog);
        initDialog(context);
    }

    private void initDialog(Context context) {
        initView(context);
        initConfig();
    }

    private void initView(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_create_room, null);
        setContentView(view);

        mEtRoomName = view.findViewById(R.id.et_room_name);
        mBtCreateNow = view.findViewById(R.id.bt_create_now);

        findViewById(R.id.iv_close).setOnClickListener(this);
    }

    private void initConfig() {
        setCancelable(true);
        setCanceledOnTouchOutside(true);
    }

    public void resetInput() {
        mEtRoomName.setText("");
    }

    @Override
    public void show() {
        super.show();

        //设置宽度全屏，要设置在show的后面
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.gravity = Gravity.BOTTOM;
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;

        getWindow().getDecorView().setPadding(0, 0, 0, 0);

        getWindow().setAttributes(layoutParams);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iv_close:
                dismiss();
                break;
        }
    }
}

