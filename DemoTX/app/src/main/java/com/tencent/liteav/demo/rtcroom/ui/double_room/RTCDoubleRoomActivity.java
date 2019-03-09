package com.tencent.liteav.demo.rtcroom.ui.double_room;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.tencent.liteav.demo.R;
import com.tencent.liteav.demo.roomutil.commondef.BaseRoom;
import com.tencent.liteav.demo.rtcroom.IRTCRoomListener;
import com.tencent.liteav.demo.roomutil.commondef.PusherInfo;
import com.tencent.liteav.demo.roomutil.commondef.LoginInfo;
import com.tencent.liteav.demo.rtcroom.RTCRoom;
import com.tencent.liteav.demo.common.misc.CommonAppCompatActivity;
import com.tencent.liteav.demo.common.misc.NameGenerator;
import com.tencent.liteav.demo.rtcroom.ui.double_room.fragment.RTCDoubleRoomChatFragment;
import com.tencent.liteav.demo.rtcroom.ui.double_room.fragment.RTCDoubleRoomListFragment;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;

public class RTCDoubleRoomActivity extends CommonAppCompatActivity implements RTCDoubleRoomActivityInterface {

    private static final String TAG = RTCDoubleRoomActivity.class.getSimpleName();

    public final Handler    uiHandler = new Handler();

    private RTCRoom         RTCRoom;
    private String          userId;
    private String          userName;
    private String          userAvatar = "avatar";
    private TextView        titleTextView;
    private TextView        globalLogTextview;
    private ScrollView      globalLogTextviewContainer;
    private Runnable        retryInitRoomRunnable;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_rtc_double_room);

        findViewById(R.id.rtc_double_room_back_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        titleTextView = ((TextView) findViewById(R.id.rtc_double_room_title_textview));
        titleTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (retryInitRoomRunnable != null) {
                    synchronized (RTCDoubleRoomActivity.this) {
                        retryInitRoomRunnable.run();
                        retryInitRoomRunnable = null;
                    }
                }
            }
        });

        globalLogTextview = ((TextView) findViewById(R.id.rtc_double_room_global_log_textview));
        globalLogTextview.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                 new AlertDialog.Builder(RTCDoubleRoomActivity.this, R.style.RtmpRoomDialogTheme)
                        .setTitle("Global Log")
                        .setMessage("清除Log")
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).setPositiveButton("清除", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                globalLogTextview.setText("");
                                dialog.dismiss();
                            }
                        }).show();

                return true;
            }
        });

        findViewById(R.id.webrtc_link_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://cloud.tencent.com/document/product/454/14617"));
                startActivity(intent);
            }
        });
        globalLogTextviewContainer = ((ScrollView) findViewById(R.id.rtc_double_room_global_log_container));

        RTCRoom = new RTCRoom(this.getApplicationContext());
        RTCRoom.setRTCRoomListener(new MemberEventListener());

        initializeRTCRoom();
    }
       @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RTCRoom.setRTCRoomListener(null);
        RTCRoom.logout();
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
        if (fragment instanceof RTCDoubleRoomChatFragment){
            ((RTCDoubleRoomChatFragment) fragment).onBackPressed();
        }
        else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onPermissionDisable() {
        new AlertDialog.Builder(this, R.style.RtmpRoomDialogTheme)
                .setMessage("需要录音和摄像头权限，请到【设置】【应用】打开")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
    }

    private class LoginInfoResponse {
        public int    code;
        public String message;
        public long   sdkAppID;
        public String accType;
        public String userID;
        public String userSig;
    }

    private class HttpInterceptorLog implements HttpLoggingInterceptor.Logger {
        @Override
        public void log(String message) {
            Log.i("HttpRequest", message+"\n");
        }
    }

    private void initializeRTCRoom() {
        setTitle("连接中...");

        SharedPreferences sp = getSharedPreferences("com.tencent.demo", Context.MODE_PRIVATE);
        String userIdFromSp = sp.getString("userID", "");
        String loginInfoCgi = BaseRoom.ROOM_SERVICE_DOMAIN+"utils/get_login_info_debug";
        if (!TextUtils.isEmpty(userIdFromSp)) {
            loginInfoCgi = loginInfoCgi + "?userID=" + userIdFromSp;
        }
        String userNameFromSp = sp.getString("userName", "");
        if (!TextUtils.isEmpty(userNameFromSp)) {
            userName = userNameFromSp;
        }
        else {
            userName = NameGenerator.getRandomName();
            sp.edit().putString("userName", userName).commit();
        }
        final Request request = new Request.Builder()
                .url(loginInfoCgi)
                .build();

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new HttpLoggingInterceptor(new HttpInterceptorLog()).setLevel(HttpLoggingInterceptor.Level.BODY))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setTitle("获取登录信息失败，点击重试");
                        printGlobalLog(String.format("[Activity]获取登录信息失败{%s}", e.getMessage()));
                        retryInitRoomRunnable = new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(RTCDoubleRoomActivity.this, "重试...", Toast.LENGTH_SHORT).show();
                                initializeRTCRoom();
                            }
                        };
                    }
                });
            }

            @Override
            public void onResponse(final Call call, final okhttp3.Response response) throws IOException {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            LoginInfoResponse resp = new Gson().fromJson(response.body().string(), LoginInfoResponse.class);
                            if (resp.code != 0){
                                setTitle("获取登录信息失败");
                                printGlobalLog(String.format("[Activity]获取登录信息失败：{%s}", resp.message));
                                retryInitRoomRunnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(RTCDoubleRoomActivity.this, "重试...", Toast.LENGTH_SHORT).show();
                                        initializeRTCRoom();
                                    }
                                };
                            }
                            else {
                                SharedPreferences sp = getSharedPreferences("com.tencent.demo", Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = sp.edit();
                                editor.putString("userID", resp.userID);
                                editor.commit();
                                internalInitializeRTCRoom(resp);
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }

    private void internalInitializeRTCRoom(LoginInfoResponse resp) {
        LoginInfo loginInfo       = new LoginInfo();
        loginInfo.sdkAppID       = resp.sdkAppID;
        loginInfo.userID         = resp.userID;
        loginInfo.userSig        = resp.userSig;
        loginInfo.accType        = resp.accType;
        loginInfo.userName       = userName;
        loginInfo.userAvatar     = userAvatar;

        RTCRoom.login(BaseRoom.ROOM_SERVICE_DOMAIN+"double_room", loginInfo, new com.tencent.liteav.demo.rtcroom.RTCRoom.LoginCallback() {
            @Override
            public void onError(int errCode, String errInfo) {
                setTitle(errInfo);
                printGlobalLog(String.format("[Activity]RTCRoom初始化失败：{%s}", errInfo));
                retryInitRoomRunnable = new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(RTCDoubleRoomActivity.this, "重试...", Toast.LENGTH_SHORT).show();
                        initializeRTCRoom();
                    }
                };
            }

            @Override
            public void onSuccess(String userId) {
                setTitle("双人音视频");
                RTCDoubleRoomActivity.this.userId = userId;
                printGlobalLog("[Activity]初始化成功,userID{%s}", userId);

                Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
                if (!(fragment instanceof RTCDoubleRoomChatFragment)) {
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    fragment = RTCDoubleRoomListFragment.newInstance(userId);
                    ft.replace(R.id.rtmproom_fragment_container, fragment);
                    ft.commit();
                }
            }
        });
    }

    @Override
    public void onPermissionGranted() {

    }

    @Override
    public RTCRoom getRTCRoom() {
        return RTCRoom;
    }

    @Override
    public String getSelfUserID() {
        return userId;
    }

    @Override
    public String getSelfUserName() {
        return userName;
    }

    @Override
    public void showGlobalLog(final boolean enable) {
        if (uiHandler != null) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    globalLogTextviewContainer.setVisibility(enable ? View.VISIBLE : View.GONE);
                }
            });
        }
    }

    @Override
    public void printGlobalLog(final String format, final Object ...args){
        if (uiHandler != null) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    SimpleDateFormat dataFormat = new SimpleDateFormat("HH:mm:ss");
                    String line = String.format("[%s] %s\n", dataFormat.format(new Date()), String.format(format, args));

                    globalLogTextview.append(line);
                    if (globalLogTextviewContainer.getVisibility() != View.GONE){
                        globalLogTextviewContainer.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                }
            });
        }
    }

    @Override
    public void setTitle(final String s) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                String ss = NameGenerator.replaceNonPrintChar(s, 20, "...", false);
                titleTextView.setLinksClickable(false);
                titleTextView.setText(ss);
            }
        });
    }

    private final class MemberEventListener implements IRTCRoomListener {

        @Override
        public void onPusherJoin(PusherInfo pusher) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof RTCDoubleRoomChatFragment && fragment.isVisible()){
                ((RTCDoubleRoomChatFragment) fragment).onPusherJoin(pusher);
            }
        }

        @Override
        public void onPusherQuit(PusherInfo member) {

            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof RTCDoubleRoomChatFragment && fragment.isVisible()){
                ((RTCDoubleRoomChatFragment) fragment).onPusherQuit(member);
            }
        }

        @Override
        public void onRoomClosed(String roomId) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof RTCDoubleRoomChatFragment && fragment.isVisible()){
                ((RTCDoubleRoomChatFragment) fragment).onRoomClosed(roomId);
            }
        }

        @Override
        public void onDebugLog(String line) {
            printGlobalLog(line);
        }

        @Override
        public void onGetPusherList(List<PusherInfo> pusherInfoList) {
            for (PusherInfo pusherInfo : pusherInfoList) {
                onPusherJoin(pusherInfo);
            }
        }

        @Override
        public void onRecvRoomTextMsg(String roomid, String userid, String userName, String userAvatar, String msg) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof RTCDoubleRoomChatFragment && fragment.isVisible()){
                ((RTCDoubleRoomChatFragment) fragment).onRecvRoomTextMsg(roomid, userid, userName, userAvatar, msg);
            }
        }

        @Override
        public void onRecvRoomCustomMsg(final String roomID, final String userID, final String userName, final String userAvatar, final String cmd, final String message) {
            //do nothing
        }

        @Override
        public void onError(final int errorCode, final String errorMessage) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof RTCDoubleRoomChatFragment && fragment.isVisible()){
                ((RTCDoubleRoomChatFragment) fragment).onError(errorCode, errorMessage);
            }
        }
    }
}
