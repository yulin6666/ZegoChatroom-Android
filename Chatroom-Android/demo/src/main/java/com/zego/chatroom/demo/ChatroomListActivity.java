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
import android.widget.Button;
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
import com.zego.chatroom.demo.bean.virtualChatroomInfo;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
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

    private final static int MESSAGE_GET_VIRTUAL_ROOM_LIST = 0x11;

    private TextView mAppName;

    private String mUserName;

    private String mUserRole;

    private List<ChatroomInfo> mchatroomList;

    private ChatroomInfo mCurrentChatRoomInfo;
    /**
     * Intent extra info
     */
    final static String EXTRA_KEY_USERNAME = "user_name";
    final static String EXTRA_KEY_USERROLE = "user_role";

    private Handler mUiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_CHATROOM_LIST:
                    Map<String, String> map = (Map<String, String>) msg.obj;
                    httpReturn(map.get(BODY_KEY), map.get(REQUEST_KEY));
                    break;
                case MESSAGE_GET_VIRTUAL_ROOM_LIST:
                    Map<String, String> vmap = (Map<String, String>) msg.obj;
                    virtualHttpReturn(vmap.get(BODY_KEY));
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatroom_list);

        initView();

        mUserName = getIntent().getStringExtra(EXTRA_KEY_USERNAME);
        mAppName = findViewById(R.id.tv_app_name);
        mUserRole = getIntent().getStringExtra(EXTRA_KEY_USERROLE);
        mAppName.setText("用户名:"+mUserName+",职位:"+mUserRole);

        ZegoDataCenter.ZEGO_USER.userName = mUserName;
        ZegoDataCenter.ZEGO_USER.userID = mUserName;

        mchatroomList = new ArrayList<>();
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
        Button create_button = findViewById(R.id.bt_create_room);
        create_button.setVisibility(View.GONE);
        create_button.setOnClickListener(this);
    }

    @Override
    public void onRefresh() {
        fetchChatroomList();
    }

    @Override
    public void onChatroomClick(ChatroomInfo chatroomInfo) {
        if (chatroomInfo == null || TextUtils.isEmpty(chatroomInfo.room_name)) {
            Toast.makeText(this, "组名错误，进入组失败！", Toast.LENGTH_SHORT).show();
            // 有错误的房间，刷新一下。
            refresh();
            return;
        }

        //保存现有房间
        mCurrentChatRoomInfo = chatroomInfo;

        //获得真实的列表
        String url = String.format(Locale.ENGLISH, ZegoDataCenter.getRoomListUrl(), ZegoDataCenter.APP_ID, ZegoDataCenter.APP_ID);
        httpUrl(url, REQUEST_CHATROOM_LIST);
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

        String roomID = mCreateRoomDialog.mEtRoomName.getText().toString();
        String roomName = mCreateRoomDialog.mEtRoomName.getText().toString();
        String ownerID = ZegoDataCenter.ZEGO_USER.userID;
        String ownerName =  ZegoDataCenter.ZEGO_USER.userName;
        int audioBitrate = ChatroomInfoHelper.getAudioBitrateFromString("");
        int audioChannelCount = ChatroomInfoHelper.getAudioChannelCountFromString("");
        int latencyMode = ChatroomInfoHelper.getLatencyModeFromString("");

        mCreateRoomDialog.resetInput();

        startChatroomActivity(roomID, roomName, ownerID, ownerName,mUserRole, audioBitrate, audioChannelCount, latencyMode);
    }

    private void createNewRoom(ChatroomInfo info) {

        String roomID = info.room_id;
        String roomName = info.room_name;
        String ownerID = ZegoDataCenter.ZEGO_USER.userID;
        String ownerName =  ZegoDataCenter.ZEGO_USER.userName;
        int audioBitrate = ChatroomInfoHelper.getAudioBitrateFromString("");
        int audioChannelCount = ChatroomInfoHelper.getAudioChannelCountFromString("");
        int latencyMode = ChatroomInfoHelper.getLatencyModeFromString("");

        startChatroomActivity(roomID, roomName, ownerID, ownerName,mUserRole, audioBitrate, audioChannelCount, latencyMode);
    }

    private void joinRoom(ChatroomInfo info) {
        String roomID = info.room_id;
        String roomName = info.room_name;
        String ownerID = info.anchor_id_name;
        String ownerName = info.anchor_nick_name;
        int audioBitrate = 64000;
        int audioChannelCount = 2;
        int latencyMode = 4;
        startChatroomActivity(roomID, roomName, ownerID, ownerName,mUserRole, audioBitrate, audioChannelCount, latencyMode);
    }

    private void startChatroomActivity(String roomID, String roomName, String ownerID, String ownerName, String userRole,int audioBitrate, int audioChannelCount, int latencyMode) {
        Intent intent = new Intent(this, ChatroomActivity.class);

        intent.putExtra(ChatroomActivity.EXTRA_KEY_OWNER_ID, ownerID);
        intent.putExtra(ChatroomActivity.EXTRA_KEY_OWNER_NAME, ownerName);
        intent.putExtra(ChatroomActivity.EXTRA_KEY_ROOM_ID, roomID);
        intent.putExtra(ChatroomActivity.EXTRA_KEY_ROOM_NAME, ownerName);
        intent.putExtra(ChatroomActivity.EXTRA_KEY_AUDIO_BITRATE, audioBitrate);
        intent.putExtra(ChatroomActivity.EXTRA_KEY_AUDIO_CHANNEL_COUNT, audioChannelCount);
        intent.putExtra(ChatroomActivity.EXTRA_KEY_UESR_ROLE, userRole);

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
    protected void getVirtualRoom(final String url) {
        StringRequest request = new StringRequest(url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String body) {
                        Map<String, String> map = new HashMap<>();
                        map.put(BODY_KEY, body);
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MESSAGE_GET_VIRTUAL_ROOM_LIST, map));
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                ZLog.d(TAG, "onErrorResponse error: " + error.getMessage());
                Map<String, String> map = new HashMap<>();
                map.put(BODY_KEY, BODY_ERROR);
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MESSAGE_GET_VIRTUAL_ROOM_LIST, map));
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
        //获得虚拟的列表
        String vUrl = "http://138.128.223.140/api/roomList";
        getVirtualRoom(vUrl);
    }

    private void httpReturn(String body, String req) {
        ZLog.d(TAG, "httpReturn body: " + body + " req: " + req);
        if (body != null && !BODY_ERROR.equals(body) && REQUEST_CHATROOM_LIST.equals(req)) {
            try {
                JSONArray jsonArray = JSON.parseObject(body).getJSONObject(RESPONCE_KEY_DATA).getJSONArray(REQUEST_CHATROOM_LIST);
                List<ChatroomInfo> roomListValue = JSON.parseArray(jsonArray.toJSONString(), ChatroomInfo.class);
                mchatroomList.clear();
                for (ChatroomInfo room : roomListValue) {
                    mchatroomList.add(room);
                }
                //判断是创建还是加入房间
                if(mchatroomList.contains(mCurrentChatRoomInfo)){
                    Toast.makeText(this, "加入房间！", Toast.LENGTH_SHORT).show();
                    joinRoom(mCurrentChatRoomInfo);
                }else{
                    Toast.makeText(this, "创建房间！", Toast.LENGTH_SHORT).show();
                    createNewRoom(mCurrentChatRoomInfo);
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

    private void virtualHttpReturn(String body) {
        ZLog.d(TAG, "httpReturn body: " + body);
        if (body != null && !BODY_ERROR.equals(body)) {
            try {
                List<virtualChatroomInfo> roomListValue = JSON.parseArray(body, virtualChatroomInfo.class);
                List<ChatroomInfo> chatroomList = new ArrayList<>();
                for (virtualChatroomInfo vRoom : roomListValue) {
                    ChatroomInfo room = new ChatroomInfo();
                    room.room_id = vRoom.roomid;
                    room.room_name = vRoom.roomname;
                    room.anchor_id_name = vRoom.joinAuthorityRequest;
                    if(canAccess(vRoom.joinAuthorityRequest)){
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
        mTipTitleTv.setText("暂无组");
        mTipDescTv.setText("您可以尝试下拉刷新，拉取最新信息\n也可以点击下方按钮创建组");
    }

    private void showErrorTip() {
        mRecyclerView.setVisibility(View.GONE);
        mTipLayout.setVisibility(View.VISIBLE);
        mTipTitleTv.setText("拉取信息异常");
        mTipDescTv.setText("您可以尝试下拉刷新，重新拉取组列表信息");
    }

    private boolean canAccess(String roomAuthority){
        if(mUserRole.equals("部长")){
            //部长加入所有
            return true;
        }else if(mUserRole.equals("副部长")) {
            //部长的加不了
            if(!roomAuthority.equals("部长")){
                return true;
            }
        }else if(mUserRole.equals("组长")){
            //副部长和部长的加不了
            if(!roomAuthority.equals("部长") && !roomAuthority.equals("副部长")){
                return true;
            }
        }else if(mUserRole.equals("组员")){
            //只能加入组员的
            if(roomAuthority.equals("组员")){
                return true;
            }
        }
        return false;
    }
}
