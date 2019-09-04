package com.zego.chatroom.demo;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.zego.chatroom.demo.adapter.ChatroomListAdapter;
import com.zego.chatroom.demo.bean.ChatroomInfo;
import com.zego.chatroom.demo.data.ZegoDataCenter;
import com.zego.chatroom.demo.utils.ChatroomInfoHelper;
import com.zego.chatroom.demo.utils.UiUtils;
import com.zego.chatroom.demo.view.CreateRoomDialog;
import com.zego.chatroom.demo.view.PaddingDecoration;
import com.zego.chatroom.manager.log.ZLog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatroomListActivity extends BaseActivity implements SwipeRefreshLayout.OnRefreshListener,
        ChatroomListAdapter.OnChatroomClickListener, View.OnClickListener {

    private final static String TAG = ChatroomListActivity.class.getSimpleName();

    private final static String BODY_KEY = "body";
    private final static String REQUEST_KEY = "req";
    private final static String REQUEST_CHATROOM_LIST = "room_list";
    private final static String BODY_ERROR = "error";

    private final static String RESPONCE_KEY_DATA = "data";

    private final static int PERMISSIONS_REQUEST_CODE = 101;

    private final static int MESSAGE_GET_CHATROOM_LIST = 0x10;

    private String mAccessToken;

    private Handler mUiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_CHATROOM_LIST:
                    Map<String, String> map = (Map<String, String>) msg.obj;
                    httpReturn(map.get(BODY_KEY), map.get(REQUEST_KEY));
                    break;
            }
        }
    };

    // 下拉刷新View
    private SwipeRefreshLayout mSwipeLayout;

    // 业务排队入口 RecyclerView
    private RecyclerView mRecyclerView;

    // 提示 layout
    private ViewGroup mTipLayout;
    // 提示 Title TextView
    private TextView mTipTitleTv;
    // 提示 desc TextView
    private TextView mTipDescTv;
    // 创建房间 Dialog
    private CreateRoomDialog mCreateRoomDialog;

    private ChatroomListAdapter mChatroomListAdapter;

    private String access_token;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatroom_list);

        initView();

        //获得access_token信息
        sendHTTPPostData();

    }

    private String getToken(){
        long current_time = System.currentTimeMillis(); //获取当前unix时间戳
        long expired_time = current_time+7200; //过期unix时间戳，单位：秒

        String appid = "3939196392";
        String serverSecret ="c9f23f966d923d3e28fe27ad1fe6100f";
        String nonce = "c9f23f966f923d4e28fe27ad1fe6100f";

        // 待加密信息
        String originString = appid + serverSecret + nonce + Long.toString(expired_time);
        String hashString = getMD5(originString);

        //定义一个tokeninfo json
        LinkedHashMap  hashMap = new LinkedHashMap();
        hashMap.put("ver",1);
        hashMap.put("hash",hashString);
        hashMap.put("nonce",nonce);
        hashMap.put("expired",expired_time);
        String  tokeninfo= JSON.toJSONString(hashMap);
        final Base64.Encoder encoder = Base64.getEncoder();   //加密
        String encodedText = "";
        try {
            final byte[] textByte = tokeninfo.getBytes("UTF-8");
            encodedText = encoder.encodeToString(textByte);
        }catch (UnsupportedEncodingException e) {
        };

        return encodedText;
    }

    private void sendHTTPPostData(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                String urlpath = "https://test2-liveroom-api.zego.im/cgi/token";
                JSONObject obj = new JSONObject();
                obj.put("version", 1);
                obj.put("seq", 1);
                obj.put("app_id", 3939196392l);
                obj.put("biz_type", 0);
                obj.put("token",getToken());
                HttpURLConnection connection = null;
                try {
                    URL url=new URL(urlpath);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Accept", "application/json");
                    OutputStreamWriter streamWriter = new OutputStreamWriter(connection.getOutputStream());
                    streamWriter.write(obj.toString());
                    streamWriter.flush();
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
                        JSONObject data = rep.getJSONObject("data");
                        mAccessToken = data.getString("access_token");
                    } else {
                        Log.e(TAG, connection.getResponseMessage());
                    }
                } catch (Exception exception){
                    Log.e(TAG, exception.toString());
                } finally {
                    if (connection != null){
                        connection.disconnect();
                    }
                }
            }
        }).start();
    };

    //生成MD5
    public static String getMD5(String message) {
        String md5 = "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");  // 创建一个md5算法对象
            byte[] messageByte = message.getBytes("UTF-8");
            byte[] md5Byte = md.digest(messageByte);              // 获得MD5字节数组,16*8=128位
            md5 = bytesToHex(md5Byte);                            // 转换为16进制字符串
        } catch (Exception e) {
            System.out.println("erro md5 creat!!!!");
            e.printStackTrace();
        }
        return md5;
    }

    // 二进制转十六进制
    public static String bytesToHex(byte[] bytes) {
        StringBuffer hexStr = new StringBuffer();
        int num;
        for (int i = 0; i < bytes.length; i++) {
            num = bytes[i];
            if(num < 0) {
                num += 256;
            }
            if(num < 16){
                hexStr.append("0");
            }
            hexStr.append(Integer.toHexString(num));
        }
        return hexStr.toString();
    }


    @Override
    protected void onStart() {
        super.onStart();
        mSwipeLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, 1000);
    }

    /**
     * 初始化 View
     */
    private void initView() {
        mTipLayout = findViewById(R.id.tip_layout);
        mTipTitleTv = findViewById(R.id.tip_title_tv);
        mTipDescTv = findViewById(R.id.tip_desc_tv);
        mSwipeLayout = findViewById(R.id.swipe);
        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        // 初始化QueueAdapter
        mChatroomListAdapter = new ChatroomListAdapter();
        mChatroomListAdapter.setOnChatroomClickListener(this);
        mRecyclerView.setAdapter(mChatroomListAdapter);
        mRecyclerView.addItemDecoration(new PaddingDecoration(UiUtils.dp2px(25)));

        // 初始化 SwipeLayout 下拉刷新回调
        mSwipeLayout.setOnRefreshListener(this);

        // 初始化点击事件
        findViewById(R.id.bt_create_room).setOnClickListener(this);
    }

    @Override
    public void onRefresh() {
        fetchChatroomList();
    }

    @Override
    public void onChatroomClick(ChatroomInfo chatroomInfo) {
        if (chatroomInfo == null || TextUtils.isEmpty(chatroomInfo.room_name)) {
            Toast.makeText(this, "房间错误，进入房间失败！", Toast.LENGTH_SHORT).show();
            // 有错误的房间，刷新一下。
            refresh();
            return;
        }
        if (checkOrRequestPermission(PERMISSIONS_REQUEST_CODE)) {
            joinRoom(chatroomInfo);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_create_room:
                showCreateRoomDialog();
                break;
            case R.id.bt_create_now:
                if (checkOrRequestPermission(PERMISSIONS_REQUEST_CODE)) {
                    createRoom();
                    mCreateRoomDialog.dismiss();
                }
                break;
            default:
                break;
        }
    }

    private void createRoom() {

        String roomID = ChatroomInfoHelper.getRoomID();
        String roomName = mCreateRoomDialog.mEtRoomName.getText().toString();
        String ownerID = ZegoDataCenter.ZEGO_USER.userID;
        String ownerName = ZegoDataCenter.ZEGO_USER.userName;
        int audioBitrate = ChatroomInfoHelper.getAudioBitrateFromString("");
        int audioChannelCount = ChatroomInfoHelper.getAudioChannelCountFromString("");
        int latencyMode = ChatroomInfoHelper.getLatencyModeFromString("");

        mCreateRoomDialog.resetInput();

        startChatroomActivity(roomID, roomName, ownerID, ownerName, audioBitrate, audioChannelCount, latencyMode);
    }

    private void joinRoom(ChatroomInfo info) {
        String roomID = info.room_id;
        String roomName = info.room_name;
        String ownerID = info.anchor_id_name;
        String ownerName = info.anchor_nick_name;
        int audioBitrate = ChatroomInfoHelper.getBitrateFromRoomName(info.room_name);
        int audioChannelCount = ChatroomInfoHelper.getAudioChannelCountFromRoomName(info.room_name);
        int latencyMode = ChatroomInfoHelper.getLatencyModeFromRoomName(info.room_name);
        startChatroomActivity(roomID, roomName, ownerID, ownerName, audioBitrate, audioChannelCount, latencyMode);
    }

    private void startChatroomActivity(String roomID, String roomName, String ownerID, String ownerName, int audioBitrate, int audioChannelCount, int latencyMode) {
        Intent intent = new Intent(this, ChatroomActivity.class);

        intent.putExtra(ChatroomActivity.EXTRA_KEY_OWNER_ID, ownerID);
        intent.putExtra(ChatroomActivity.EXTRA_KEY_OWNER_NAME, ownerName);
        intent.putExtra(ChatroomActivity.EXTRA_KEY_ROOM_ID, roomID);
        intent.putExtra(ChatroomActivity.EXTRA_KEY_ROOM_NAME, ChatroomInfoHelper.getRoomName(roomName, audioBitrate, audioChannelCount, latencyMode));
        intent.putExtra(ChatroomActivity.EXTRA_KEY_AUDIO_BITRATE, audioBitrate);
        intent.putExtra(ChatroomActivity.EXTRA_KEY_AUDIO_CHANNEL_COUNT, audioChannelCount);
        intent.putExtra(ChatroomActivity.EXTRA_KEY_LATENCY_MODE, latencyMode);

        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE: {
                boolean allPermissionGranted = true;
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        allPermissionGranted = false;
                        Toast.makeText(this, String.format("获取%s权限失败 ", permissions[i]), Toast.LENGTH_LONG).show();
                    }
                }
                if (!allPermissionGranted) {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + this.getPackageName()));
                    startActivity(intent);
                }
                break;
            }
        }
    }

    // ---------------- 创建房间Dialog ---------------- //
    private void showCreateRoomDialog() {
        if (mCreateRoomDialog == null) {
            initCreateRoomDialog();
        }

        mCreateRoomDialog.mEtRoomName.setText("");
        mCreateRoomDialog.show();
    }

    private void initCreateRoomDialog() {
        mCreateRoomDialog = new CreateRoomDialog(this);
        mCreateRoomDialog.mBtCreateNow.setOnClickListener(this);
    }


    // ---------------- 获取房间列表 ---------------- //


    protected void httpUrl(final String url, final String req) {
        StringRequest request = new StringRequest(url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String body) {
                        Map<String, String> map = new HashMap<>();
                        map.put(BODY_KEY, body);
                        map.put(REQUEST_KEY, req);
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MESSAGE_GET_CHATROOM_LIST, map));
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                ZLog.d(TAG, "onErrorResponse error: " + error.getMessage());
                Map<String, String> map = new HashMap<>();
                map.put(BODY_KEY, BODY_ERROR);
                map.put(REQUEST_KEY, req);
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MESSAGE_GET_CHATROOM_LIST, map));
            }
        });

        request.setRetryPolicy(new RetryPolicy() {
            @Override
            public int getCurrentTimeout() {
                return 5000;
            }

            @Override
            public int getCurrentRetryCount() {
                return 0;
            }

            @Override
            public void retry(VolleyError error) {

            }
        });
        getRequestQueue().add(request);
    }

    private void refresh() {
        mSwipeLayout.setRefreshing(true);
        fetchChatroomList();
    }

    private void fetchChatroomList() {
        String url = String.format(Locale.ENGLISH, ZegoDataCenter.getRoomListUrl(), ZegoDataCenter.APP_ID, ZegoDataCenter.APP_ID);
        httpUrl(url, REQUEST_CHATROOM_LIST);
    }

    private void httpReturn(String body, String req) {
        ZLog.d(TAG, "httpReturn body: " + body + " req: " + req);
        if (body != null && !BODY_ERROR.equals(body) && REQUEST_CHATROOM_LIST.equals(req)) {
            try {
                JSONArray jsonArray = JSON.parseObject(body).getJSONObject(RESPONCE_KEY_DATA).getJSONArray(REQUEST_CHATROOM_LIST);
                List<ChatroomInfo> roomListValue = JSON.parseArray(jsonArray.toJSONString(), ChatroomInfo.class);
                List<ChatroomInfo> chatroomList = new ArrayList<>();
                for (ChatroomInfo room : roomListValue) {
                    if (room.room_id.startsWith(ChatroomInfoHelper.CHATROOM_PREFIX)) {
                        chatroomList.add(room);
                    }
                }
                mChatroomListAdapter.setChatrooms(chatroomList);
                if (chatroomList.size() == 0) {
                    showNoChatroom();
                } else {
                    mTipLayout.setVisibility(View.GONE);
                    mRecyclerView.setVisibility(View.VISIBLE);
                }

            } catch (Exception e) {
                ZLog.w(TAG, "-->:: httpReturn error e: " + e.getMessage());
                showNoChatroom();
            }
        } else {
            showErrorTip();
        }
        // 获取到结果，停止刷新
        mSwipeLayout.setRefreshing(false);
    }

    private void showNoChatroom() {
        mRecyclerView.setVisibility(View.GONE);
        mTipLayout.setVisibility(View.VISIBLE);
        mTipTitleTv.setText("暂无房间");
        mTipDescTv.setText("您可以尝试下拉刷新，拉取最新信息\n也可以点击下方按钮创建房间");
    }

    private void showErrorTip() {
        mRecyclerView.setVisibility(View.GONE);
        mTipLayout.setVisibility(View.VISIBLE);
        mTipTitleTv.setText("拉取信息异常");
        mTipDescTv.setText("您可以尝试下拉刷新，重新拉取房间列表信息");
    }
}
