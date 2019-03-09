package com.tencent.liteav.demo.webrtc;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.gson.Gson;
import com.tencent.liteav.demo.R;
import com.tencent.rtmp.ITXLivePlayListener;
import com.tencent.rtmp.ITXLivePushListener;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePlayer;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.TXLivePusher;
import com.tencent.rtmp.ui.TXCloudVideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.logging.HttpLoggingInterceptor;

import static android.graphics.BitmapFactory.decodeResource;

/**
 * Created by carolsuo on 2018/2/27.
 */

public class WebRTCActivity extends Activity implements ITXLivePushListener {
    private static final String TAG = WebRTCActivity.class.getSimpleName();

    private TXLivePushConfig mLivePushConfig;
    private TXLivePusher mLivePusher;
    private TXCloudVideoView mCaptureView;
    private boolean mVideoPublish = false;

    private boolean mPusherMute = false;
    private Vector<WebRTCVideoView> mPlayersList = new Vector<>();
    private HashMap<String, String> mOldWebRTCUserList = new HashMap<String, String>();

    private Handler mHandler = new Handler();

    private EditText mEtRoomId;
    private EditText mEtUserId;
    private EditText mEtUserPwd;

    private boolean mBackgroundRunning = false;
    private boolean envSwitch = true;
    private boolean showVideoViewLog = false;

    private class WebRTCVideoView {
        boolean isUsed  = false;
        String  account = "";
        String  playUrl = "";
        TXCloudVideoView view;
        TXLivePlayer player;

        public WebRTCVideoView(TXCloudVideoView view, TXLivePlayer player) {
            this.view = view;
            this.view.setVisibility(View.GONE);
            this.player = player;
            this.player.setPlayerView(this.view);
            this.player.enableHardwareDecode(true);
            this.player.setPlayListener(new ITXLivePlayListener() {
                @Override
                public void onPlayEvent(int event, Bundle param) {
                    if (event == TXLiveConstants.PLAY_EVT_PLAY_END || event == TXLiveConstants.PLAY_ERR_NET_DISCONNECT){
                        stopPlay();
                        releaseVideoView();
                    }
                }

                @Override
                public void onNetStatus(Bundle status) {

                }
            });
        }

        public void applyVideoView(String account, String playUrl) {
            this.isUsed = true;
            this.account = account;
            this.playUrl = playUrl;
        }

        public void releaseVideoView() {
            mOldWebRTCUserList.remove(this.account);
            this.isUsed = false;
            this.account = "";
            this.playUrl = "";
        }

        public void startPlay() {
            this.view.setVisibility(View.VISIBLE);
            if (this.playUrl != null && this.playUrl.length() > 0) {
                this.player.startPlay(playUrl, TXLivePlayer.PLAY_TYPE_LIVE_RTMP_ACC);
            }
        }

        public void stopPlay() {
            this.player.stopPlay(true);
        }

        public String getAccount() {
            return account;
        }

        public void showLog(boolean show) {
            if (this.player != null && this.player.isPlaying()) {
                this.view.showLog(show);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLivePusher     = new TXLivePusher(getApplicationContext());
        mLivePushConfig = new TXLivePushConfig();
        mLivePushConfig.setVideoEncodeGop(5);
        mLivePushConfig.setPauseImg(300,5);
        Bitmap bitmap = decodeResource(getResources(),R.drawable.pause_publish);
        mLivePushConfig.setPauseImg(bitmap);
        mLivePushConfig.setPauseFlag(TXLiveConstants.PAUSE_FLAG_PAUSE_VIDEO | TXLiveConstants.PAUSE_FLAG_PAUSE_AUDIO);
        mLivePusher.setConfig(mLivePushConfig);

        initView();

        checkPublishPermission();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onResume() {
        super.onResume();

        mBackgroundRunning = false;

        if (mVideoPublish) {
            // 推流：继续
            mLivePusher.resumePusher();

            // 播放器：开始播放
            for (WebRTCVideoView item: mPlayersList) {
                item.startPlay();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mBackgroundRunning = true;

        if (mVideoPublish) {
            // 推流：暂停
            mLivePusher.pausePusher();

            // 播放器：停止播放
            for (WebRTCVideoView item: mPlayersList) {
                item.stopPlay();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopPublishRtmp();

        mOldWebRTCUserList.clear();
        mPlayersList.clear();
        if (mCaptureView != null) {
            mCaptureView.onDestroy();
            mCaptureView = null;
        }

        mLivePusher = null;
    }

    private void initView() {
        setContentView(R.layout.activity_webrtc);

        mCaptureView = (TXCloudVideoView) findViewById(R.id.webrtc_video_0);

        mPlayersList.add(new WebRTCVideoView((TXCloudVideoView) findViewById(R.id.webrtc_video_1), new TXLivePlayer(getApplicationContext())));
        mPlayersList.add(new WebRTCVideoView((TXCloudVideoView) findViewById(R.id.webrtc_video_2), new TXLivePlayer(getApplicationContext())));
        mPlayersList.add(new WebRTCVideoView((TXCloudVideoView) findViewById(R.id.webrtc_video_3), new TXLivePlayer(getApplicationContext())));

        mEtRoomId = (EditText) findViewById(R.id.webrtc_roomid);
        mEtUserId = (EditText) findViewById(R.id.webrtc_user_id);
        mEtUserPwd = (EditText) findViewById(R.id.webrtc_user_pwd);
        LinearLayout backLL = (LinearLayout)findViewById(R.id.back_ll);
        backLL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mVideoPublish) {
                    stopPublishRtmp();
                }
                finish();
            }
        });

        findViewById(R.id.webrtc_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mVideoPublish) {
                    stopPublishRtmp();
                } else {
                    startPublishRtmp();
                }
            }
        });

        findViewById(R.id.webrtc_log_switcher_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVideoViewLog = !showVideoViewLog;
                mCaptureView.showLog(showVideoViewLog);
                for (WebRTCVideoView item : mPlayersList) {
                    if (item.isUsed) {
                        item.showLog(showVideoViewLog);
                    }
                }
            }
        });

        findViewById(R.id.webrtc_camera_switcher_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLivePusher.switchCamera();
            }
        });

        findViewById(R.id.webrtc_mute_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPusherMute = !mPusherMute;
                mLivePusher.setMute(mPusherMute);
                v.setBackgroundResource(mPusherMute ? R.drawable.mic_disable : R.drawable.mic_normal);
            }
        });

        findViewById(R.id.webrtc_env_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                envSwitch = !envSwitch;
                v.setBackgroundResource(envSwitch ? R.drawable.env_formal : R.drawable.env_test);
            }
        });

        findViewById(R.id.webrtc_link_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://cloud.tencent.com/document/product/454/16914"));
                startActivity(intent);
            }
        });
    }

    private boolean checkPublishPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> permissions = new ArrayList<>();
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)) {
                permissions.add(Manifest.permission.READ_PHONE_STATE);
            }
            if (permissions.size() != 0) {
                ActivityCompat.requestPermissions(this,
                        permissions.toArray(new String[0]),
                        100);
                return false;
            }
        }

        return true;
    }

    private boolean startPublishRtmp() {
        String roomid = mEtRoomId.getText().toString();
        String userid = mEtUserId.getText().toString();
        String userpwd = mEtUserPwd.getText().toString();
        if (roomid != null) {
            roomid = roomid.trim();
        }
        if (userid != null) {
            userid = userid.trim();
        }
        if (userpwd != null) {
            userpwd = userpwd.trim();
        }
        if (TextUtils.isEmpty(roomid)) {
            roomid = "104629";
        }
        if (TextUtils.isEmpty(userid)) {
            userid = "webrtc88";
        }
        if (TextUtils.isEmpty(userpwd)) {
            userpwd = "12345678";
        }
        final String pushUrl = "room://cloud.tencent.com?sdkappid=1400037025&roomid=" + roomid + "&userid=" + userid;
        getRoomSig(userid, userpwd, Long.valueOf(roomid), 1400037025, 5, new IGetRoomSigCompletion() {
            @Override
            public void onGetRoomSig(final boolean success, final String roomSig) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success && roomSig != null && roomSig.length() > 0) {
                            try {
                                String finalPushUrl = pushUrl + "&roomsig=" + URLEncoder.encode(roomSig, "UTF-8");
                                mVideoPublish = startPublishRtmp(finalPushUrl);
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        else {
                            Toast.makeText(getApplicationContext(), "获取RoomSig失败: " + (roomSig != null ? roomSig : "未知错误"), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });

        return true;
    }

    private  boolean startPublishRtmp(String inputUrl) {
        String rtmpUrl = "";
        if (!TextUtils.isEmpty(inputUrl)) {
            String url[] = inputUrl.split("###");
            if (url.length > 0) {
                rtmpUrl = url[0];
            }
        }

        if (TextUtils.isEmpty(rtmpUrl) || (!rtmpUrl.trim().toLowerCase().startsWith("room://"))) {
            Toast.makeText(getApplicationContext(), "推流地址不合法，不是WebRTC地址!", Toast.LENGTH_SHORT).show();
            return false;
        }
        mCaptureView.setVisibility(View.VISIBLE);
        mLivePusher.setPushListener(this);
        mLivePushConfig.setBeautyFilter(5, 3, 2);
        mLivePusher.setConfig(mLivePushConfig);
        mLivePusher.setVideoQuality(TXLiveConstants.VIDEO_QUALITY_REALTIEM_VIDEOCHAT, true, true);
        mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_480_640);
        mLivePushConfig.setAudioSampleRate(48000);
        mLivePushConfig.setMinVideoBitrate(200);
        mLivePushConfig.setMaxVideoBitrate(400);
        mLivePusher.setConfig(mLivePushConfig);
        mLivePusher.startCameraPreview(mCaptureView);
        mLivePusher.startPusher(rtmpUrl.trim());

        findViewById(R.id.webrtc_start).setBackgroundResource(R.drawable.play_pause);

        return true;
    }

    private void stopPublishRtmp() {
        mVideoPublish = false;

        findViewById(R.id.webrtc_start).setBackgroundResource(R.drawable.play_start);

        //结束推流
        mLivePusher.stopBGM();
        mLivePusher.stopCameraPreview(true);
        mLivePusher.setPushListener(null);
        mLivePusher.stopPusher();
        mCaptureView.setVisibility(View.GONE);
        mOldWebRTCUserList.clear();

        //关闭播放器
        for (WebRTCVideoView item : mPlayersList) {
            item.stopPlay();
            item.releaseVideoView();
        }
    }

    @Override
    public void onPushEvent(int event, Bundle param) {
        String msg = param.getString(TXLiveConstants.EVT_DESCRIPTION);
        String pushEventLog = "receive event: " + event + ", " + msg;
        Log.d(TAG, pushEventLog);
        if (event < 0) {
            Toast.makeText(getApplicationContext(), param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
            if(event == TXLiveConstants.PUSH_ERR_OPEN_CAMERA_FAIL || event == TXLiveConstants.PUSH_ERR_OPEN_MIC_FAIL){
                stopPublishRtmp();
            }
        }

        if (event == TXLiveConstants.PUSH_ERR_NET_DISCONNECT) {
            stopPublishRtmp();
        }
        else if (event == TXLiveConstants.PUSH_WARNING_HW_ACCELERATION_FAIL) {
            Toast.makeText(getApplicationContext(), param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
            mLivePushConfig.setHardwareAcceleration(TXLiveConstants.ENCODE_VIDEO_SOFTWARE);
            mLivePusher.setConfig(mLivePushConfig);
        }
        else if (event == TXLiveConstants.PUSH_ERR_SCREEN_CAPTURE_UNSURPORT) {
            stopPublishRtmp();
        }
        else if (event == TXLiveConstants.PUSH_ERR_SCREEN_CAPTURE_START_FAILED) {
            stopPublishRtmp();
        } else if (event == TXLiveConstants.PUSH_EVT_CHANGE_RESOLUTION) {
            Log.d(TAG, "change resolution to " + param.getInt(TXLiveConstants.EVT_PARAM2) + ", bitrate to" + param.getInt(TXLiveConstants.EVT_PARAM1));
        } else if (event == TXLiveConstants.PUSH_EVT_CHANGE_BITRATE) {
            Log.d(TAG, "change bitrate to" + param.getInt(TXLiveConstants.EVT_PARAM1));
        } else if (event == TXLiveConstants.PUSH_EVT_ROOM_USERLIST) {
            onWebRTCUserLishPush(msg);
        } else if (event == TXLiveConstants.PUSH_EVT_ROOM_IN) {
            // 已经在webrtc房间里面，进房成功后通知
        } else if (event == TXLiveConstants.PUSH_EVT_ROOM_OUT) {
            // 不在webrtc房间里面，进房失败或者中途退出房间时通知
            stopPublishRtmp();
        } else if (event == TXLiveConstants.PUSH_EVT_ROOM_NEED_REENTER) {
            // 需要重新进入房间，原因是网络发生切换，需要重新拉取最优的服务器地址
            stopPublishRtmp();
            startPublishRtmp();
        }
    }

    private void onWebRTCUserLishPush(String msg) {
        Log.e(TAG, msg);

        if (TextUtils.isEmpty(msg)) {
            return;
        }

        try {
            JSONObject jsonObject = new JSONObject(msg);
            JSONArray userlist = jsonObject.getJSONArray("userlist");

            if (userlist == null){
                return;
            }

            HashMap<String, String> newAccountsList = new HashMap<String, String>();
            for (int i = 0; i< userlist.length(); i++){
                JSONObject obj = (JSONObject)userlist.opt(i);
                String account = obj.getString("userid");
                newAccountsList.put(account, obj.getString("playurl"));
            }

            Vector<String> vecFailed = new Vector<>();
            for (Map.Entry<String, String> entry : newAccountsList.entrySet()) {
                if (mOldWebRTCUserList.containsKey(entry.getKey())) {
                    mOldWebRTCUserList.remove(entry.getKey());
                } else {
                    boolean success = onWebRTCUserJoin(entry.getKey(), entry.getValue());
                    if (!success) {
                        vecFailed.add(entry.getKey());
                    }
                }
            }

            for (String key : vecFailed) {
                newAccountsList.remove(key);
            }

            for (Map.Entry<String, String> entry : mOldWebRTCUserList.entrySet()) {
                onWebRTCUserQuit(entry.getKey());
            }

            mOldWebRTCUserList = newAccountsList;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean onWebRTCUserJoin(String account, String playUrl) {
        Log.e(TAG, "onWebRTCUserJoin account = " + account + " playUrl = " + playUrl);

        for (WebRTCVideoView item: mPlayersList) {
            if (!item.isUsed) {
                item.applyVideoView(account, playUrl);
                if (mBackgroundRunning == false) {
                    item.startPlay();
                }
                return true;
            }
        }

        Log.e(TAG, "onWebRTCUserJoin account = " + account + " FAILED!!!");

        return false;
    }


    private void onWebRTCUserQuit(String account) {
        Log.e(TAG, "onWebRTCUserQuit account = " + account);

        for (WebRTCVideoView item: mPlayersList) {
            if (account.equalsIgnoreCase(item.getAccount())) {
                item.stopPlay();
                item.releaseVideoView();
                return;
            }
        }
    }

    @Override
    public void onNetStatus(Bundle status) {

    }

    private class HttpInterceptorLog implements HttpLoggingInterceptor.Logger{
        @Override
        public void log(String message) {
            Log.i("HttpRequest", message+"\n");
        }
    }

    public class LoginAppServerRequestBody {
        String identifier;
        String pwd;
        long   appid;
        long   roomnum;
        long   privMap;
    }

    public class LoginAppServerResponseBody {
        public class UserData {
            String userSig;
            String privMapEncrypt;
        }
        public long errorCode;
        public String errorInfo;
        public UserData data;
    }

    public interface ILoginAppServerCompletion {
        void onLoginAppServer(boolean success, String userSig, String privMapEncrypt);
    }

    public void loginAppServer(final String userID, final String userPWD, final long roomID, final long sdkAppID, final ILoginAppServerCompletion completion) {

        final LoginAppServerRequestBody reqBody = new LoginAppServerRequestBody();
        reqBody.identifier = userID;
        reqBody.pwd = userPWD;
        reqBody.appid = sdkAppID;
        reqBody.roomnum = roomID;
        reqBody.privMap = 255;

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new HttpLoggingInterceptor(new HttpInterceptorLog()).setLevel(HttpLoggingInterceptor.Level.BODY))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();

        final MediaType MEDIA_JSON = MediaType.parse("application/json; charset=utf-8");

        final Request request = new Request.Builder()
                .url("https://sxb.qcloud.com/sxb_dev/?svc=account&cmd=authPrivMap")
                .post(RequestBody.create(MEDIA_JSON, new Gson().toJson(reqBody)))
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                if (completion != null) {
                    completion.onLoginAppServer(false, null, null);
                }
            }

            @Override
            public void onResponse(final Call call, okhttp3.Response response) throws IOException {
                try {
                    LoginAppServerResponseBody rspBody = new Gson().fromJson(response.body().string(), LoginAppServerResponseBody.class);
                    if (rspBody.errorCode == 0 && rspBody.data != null && rspBody.data.userSig != null && rspBody.data.userSig.length() > 0
                            && rspBody.data.privMapEncrypt != null && rspBody.data.privMapEncrypt.length() > 0) {
                        if (completion != null) {
                            completion.onLoginAppServer(true, rspBody.data.userSig, rspBody.data.privMapEncrypt);
                            return;
                        }
                    }
                    if (completion != null) {
                        completion.onLoginAppServer(false, null, null);
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                    if (completion != null) {
                        completion.onLoginAppServer(false, null, null);
                    }
                }
            }
        });
    }

    public class RoomSigRequestBody {
        public class RequestHead {
            public long Cmd     = 1;
            public long SeqNo   = 1;
            public long BusType = 7;
            public long GroupId;
        }
        public class RequestBody {
            public String PrivMapEncrypt = null;
            public long TerminalType = 1;
            public long FromType     = 3;
            public long SdkVersion   = 26280566;
        }
        public RequestHead ReqHead = new RequestHead();
        public RequestBody ReqBody = new RequestBody();
    }

    public class RoomSigResponseBody {
        public class ResponseHead {
            public long ErrorCode;
            public String ErrorInfo;
            public long Cmd;
            public long SeqNo;
            public long BusType;
            public long GroupId;
            public String ActionStatus;
        }
        public ResponseHead RspHead;
        public Object       RspBody;
    }

    public interface IRequestRoomSigCompletion {
        void onRequestRoomSig(boolean success, String roomSig);
    }

    public void requestRoomSig(final String userID, final String userSig, final String privMapEncrypt, final long roomID, final long sdkAppID, final IRequestRoomSigCompletion completion) {

        final RoomSigRequestBody reqBody = new RoomSigRequestBody();
        reqBody.ReqHead.GroupId = roomID;
        reqBody.ReqBody.PrivMapEncrypt = privMapEncrypt;

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new HttpLoggingInterceptor(new HttpInterceptorLog()).setLevel(HttpLoggingInterceptor.Level.BODY))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();

        final MediaType MEDIA_JSON = MediaType.parse("application/json; charset=utf-8");

        String url = "https://official.opensso.tencent-cloud.com/v4/openim/jsonvideoapp?sdkappid=" + sdkAppID + "&identifier=" + userID + "&usersig=" + userSig + "&random=9999&contenttype=json";

        if (envSwitch) {
            // 正式环境
            url = "https://official.opensso.tencent-cloud.com/v4/openim/jsonvideoapp?sdkappid=" + sdkAppID + "&identifier=" + userID + "&usersig=" + userSig + "&random=9999&contenttype=json";
        } else {
            // 测试环境
            url = "https://test.opensso.tencent-cloud.com/v4/openim/jsonvideoapp?sdkappid=" + sdkAppID + "&identifier=" + userID + "&usersig=" + userSig + "&random=9999&contenttype=json";
        }

        final Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MEDIA_JSON, new Gson().toJson(reqBody)))
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                if (completion != null) {
                    completion.onRequestRoomSig(false, null);
                }
            }

            @Override
            public void onResponse(final Call call, okhttp3.Response response) throws IOException {
                try {

                    if (completion != null) {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        JSONObject rspHead = jsonObject.optJSONObject("RspHead");
                        int errorCode = 0;
                        String errorMsg = "";
                        if (rspHead != null) {
                            errorCode = rspHead.optInt("ErrorCode");
                            errorMsg  = rspHead.optString("ErrorInfo");
                        }
                        if (errorCode != 0) {
                            completion.onRequestRoomSig(false, errorMsg);
                        }
                        else {
                            JSONObject roomSig = jsonObject.optJSONObject("RspBody");
                            completion.onRequestRoomSig(true, roomSig.toString());
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                    if (completion != null) {
                        completion.onRequestRoomSig(false, null);
                    }
                }
            }
        });
    }

    public interface IGetRoomSigCompletion {
        void onGetRoomSig(boolean success, String roomSig);
    }

    public void internalGetRoomSig(final String userID, final String userPWD, final long roomID, final long sdkAppID, final IGetRoomSigCompletion completion) {
        loginAppServer(userID, userPWD, roomID, sdkAppID, new ILoginAppServerCompletion() {
            @Override
            public void onLoginAppServer(boolean success, String userSig, String privMapEncrypt) {
                if (success && userSig != null && userSig.length() > 0 && privMapEncrypt != null && privMapEncrypt.length() > 0) {
                    requestRoomSig(userID, userSig, privMapEncrypt, roomID, sdkAppID, new IRequestRoomSigCompletion() {
                        @Override
                        public void onRequestRoomSig(boolean success, String roomSig) {
                            if (success && roomSig != null && roomSig.length() > 0) {
                                if (completion != null) {
                                    completion.onGetRoomSig(true, roomSig);
                                }
                            }
                            else {
                                if (completion != null) {
                                    completion.onGetRoomSig(false, roomSig);
                                }
                            }
                        }
                    });
                }
                else {
                    if (completion != null) {
                        completion.onGetRoomSig(false, null);
                    }
                }
            }
        });
    }

    public void getRoomSig(final String userID, final String userPWD, final long roomID, final long sdkAppID, final int retryCount, final IGetRoomSigCompletion completion) {
        if (retryCount > 0) {
            internalGetRoomSig(userID, userPWD, roomID, sdkAppID, new IGetRoomSigCompletion() {
                @Override
                public void onGetRoomSig(boolean success, String roomSig) {
                    if (success) {
                        if (completion != null) {
                            completion.onGetRoomSig(true, roomSig);
                        }
                    }
                    else {
                        int retryIndex = retryCount - 1;
                        if (retryIndex > 0) {
                            try {
                                Thread.sleep(1000);
                                getRoomSig(userID, userPWD, roomID, sdkAppID, retryIndex, completion);
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        else {
                            if (completion != null) {
                                completion.onGetRoomSig(false, roomSig);
                            }
                        }
                    }
                }
            });
        }
    }
}
