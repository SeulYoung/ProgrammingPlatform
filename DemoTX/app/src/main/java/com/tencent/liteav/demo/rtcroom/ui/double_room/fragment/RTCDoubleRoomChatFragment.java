package com.tencent.liteav.demo.rtcroom.ui.double_room.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.tencent.liteav.demo.R;
import com.tencent.liteav.demo.rtcroom.IRTCRoomListener;
import com.tencent.liteav.demo.roomutil.commondef.PusherInfo;
import com.tencent.liteav.demo.roomutil.commondef.RoomInfo;
import com.tencent.liteav.demo.rtcroom.RTCRoom;
import com.tencent.liteav.demo.common.misc.TextChatMsg;
import com.tencent.liteav.demo.rtcroom.ui.double_room.RTCDoubleRoomActivityInterface;
import com.tencent.liteav.demo.common.misc.ChatMessageAdapter;
import com.tencent.liteav.demo.common.misc.HintDialog;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RTCDoubleRoomChatFragment extends Fragment implements IRTCRoomListener {

    private static final String TAG = RTCDoubleRoomChatFragment.class.getSimpleName();

    private Activity                            mActivity;
    private RTCDoubleRoomActivityInterface      mActivityInterface;

    private RoomInfo                            mRoomInfo;
    private List<RoomVideoView>                 mPlayerViews = new ArrayList<>();

    private ListView                            mChatListView;
    private ArrayList<TextChatMsg>              mChatMsgList;
    private ChatMessageAdapter                  mChatMsgAdapter;

    private int                                 mShowLogFlag    = 0;
    private int                                 mBeautyStyle    = TXLiveConstants.BEAUTY_STYLE_SMOOTH;
    private int                                 mBeautyLevel    = 5;
    private int                                 mWhiteningLevel = 3;
    private int                                 mRuddyLevel     = 2;
    private boolean                             mEnableBeauty   = true;
    private boolean                             mPusherMute     = false;

    
    public static RTCDoubleRoomChatFragment newInstance(RoomInfo config, String userID, boolean createRoom) {
        RTCDoubleRoomChatFragment fragment = new RTCDoubleRoomChatFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable("roomInfo", config);
        bundle.putString("userID", userID);
        bundle.putBoolean("createRoom", createRoom);
        fragment.setArguments(bundle);
        return fragment;
    }

    /***********************************************************************************************************************************************
     *
     *                                                      Fragment生命周期函数调用顺序
     *
     *     onAttach() --> onCreateView() --> onActivityCreated() --> onResume() --> onPause() --> onDestroyView() --> onDestroy() --> onDetach()
     *
     ***********************************************************************************************************************************************/

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = ((Activity) context);
        mActivityInterface = ((RTCDoubleRoomActivityInterface) context);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = ((Activity) activity);
        mActivityInterface = ((RTCDoubleRoomActivityInterface) activity);
    }
    
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rtc_double_room_chat, container, false);

        TXCloudVideoView views[] = new TXCloudVideoView[6];
        views[0] = ((TXCloudVideoView) view.findViewById(R.id.rtmproom_video_0));
        views[1] = ((TXCloudVideoView) view.findViewById(R.id.rtmproom_video_1));

        TextView nameViews[] = new TextView[6];
        nameViews[0] = ((TextView) view.findViewById(R.id.rtmproom_video_name_0));
        nameViews[1] = ((TextView) view.findViewById(R.id.rtmproom_video_name_1));

        for (int i = 0; i < 2; i++) {
            mPlayerViews.add(new RoomVideoView(views[i], nameViews[i]));
        }

        //切换摄像头
        (view.findViewById(R.id.rtmproom_camera_switcher_btn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mActivityInterface != null)
                    mActivityInterface.getRTCRoom().switchCamera();
            }
        });

        //美颜
        view.findViewById(R.id.rtmproom_beauty_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEnableBeauty = !mEnableBeauty;
                v.setBackgroundResource(mEnableBeauty ? R.drawable.beauty : R.drawable.beauty_dis);
                if (mEnableBeauty) {
                    mActivityInterface.getRTCRoom().setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }else {
                    mActivityInterface.getRTCRoom().setBeautyFilter(mBeautyStyle, 0, 0, 0);
                }
            }
        });

        //静音
        view.findViewById(R.id.rtmproom_mute_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPusherMute = !mPusherMute;
                mActivityInterface.getRTCRoom().setMute(mPusherMute);
                v.setBackgroundResource(mPusherMute ? R.drawable.mic_disable : R.drawable.mic_normal);
            }
        });

        //日志
        (view.findViewById(R.id.rtmproom_log_switcher_btn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchLog();
            }
        });

        final EditText chatEditText = ((EditText) view.findViewById(R.id.double_room_chat_input_et));
        chatEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND){
                    CharSequence text = v.getText();
                    if (text == null) return false;
                    chatEditText.setText("");
                    sendMessage(text.toString());
                    InputMethodManager inputMethodManager = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });

        chatEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    InputMethodManager inputMethodManager = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        });

        mChatMsgList    = new ArrayList<>();
        mChatMsgAdapter = new ChatMessageAdapter(mActivity, mChatMsgList);
        mChatListView   = ((ListView) view.findViewById(R.id.chat_list_view));
        mChatListView.setAdapter(mChatMsgAdapter);

        return view;
    }
    
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onActivityCreated() called ");
        super.onActivityCreated(savedInstanceState);

        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Bundle bundle = getArguments();
        this.mRoomInfo = bundle.getParcelable("roomInfo");
        String userID = bundle.getString("userID");
        String userName = mActivityInterface.getSelfUserName();
        boolean createRoom = bundle.getBoolean("createRoom");

        if (userID == null || ( !createRoom && mRoomInfo == null)) {
            return;
        }

        mActivityInterface.setTitle(mRoomInfo.roomInfo);

        RoomVideoView videoView = applyVideoView(userID, "我("+userName+")");
        if (videoView == null) {
            mActivityInterface.printGlobalLog("申请 UserID {%s} 返回view 为空", userID);
            return;
        }

        mActivityInterface.getRTCRoom().startLocalPreview(videoView.videoView);
        mActivityInterface.getRTCRoom().setPauseImage(BitmapFactory.decodeResource(getResources(), R.drawable.pause_publish));
        mActivityInterface.getRTCRoom().setBitrateRange(400, 800);
        mActivityInterface.getRTCRoom().setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);

        if (createRoom){
            mActivityInterface.getRTCRoom().createRoom("", mRoomInfo.roomInfo, new RTCRoom.CreateRoomCallback() {
                @Override
                public void onSuccess(String roomId) {
                    mRoomInfo.roomID = roomId;
                }

                @Override
                public void onError(int errCode, String e) {
                    errorGoBack("创建会话错误", errCode, e);
                }
            });
        }
        else {
            mActivityInterface.getRTCRoom().enterRoom(mRoomInfo.roomID, new RTCRoom.EnterRoomCallback() {
                @Override
                public void onError(int errCode, String errInfo) {
                    errorGoBack("进入会话错误", errCode, errInfo);
                }

                @Override
                public void onSuccess() {

                }
            });
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        mActivityInterface.getRTCRoom().switchToForeground();
    }

    @Override
    public void onPause() {
        super.onPause();
        mActivityInterface.getRTCRoom().switchToBackground();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPlayerViews.clear();
        mActivityInterface.getRTCRoom().stopLocalPreview();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        recycleVideoView();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mActivity = null;
        mActivityInterface = null;
    }

    public void onBackPressed() {
        if (mActivityInterface != null) {
            mActivityInterface.getRTCRoom().exitRoom(new RTCRoom.ExitRoomCallback() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "exitRoom Success");
                }

                @Override
                public void onError(int errCode, String e) {
                    Log.e(TAG, "exitRoom failed, errorCode = " + errCode + " errMessage = " + e);
                }
            });
        }

        recycleVideoView();
        backStack();
    }

    private void errorGoBack(String title, int errCode, String errInfo){
        mActivityInterface.getRTCRoom().exitRoom(null);
        new AlertDialog.Builder(mActivity)
                .setTitle(title)
                .setMessage(errInfo + "[" + errCode + "]")
                .setNegativeButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        recycleVideoView();
                        backStack();
                    }
                }).show();
    }

    private void backStack(){
        if (mActivity != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mActivity != null) {
                        FragmentManager fragmentManager = mActivity.getFragmentManager();
                        fragmentManager.popBackStack();
                        fragmentManager.beginTransaction().commit();
                    }
                }
            });
        }
    }

    private void switchLog(){
        mShowLogFlag++;
        mShowLogFlag = (mShowLogFlag % 3);
        switch (mShowLogFlag) {
            case 0: {
                for (RoomVideoView item : mPlayerViews) {
                    if (item.isUsed) {
                        item.videoView.showLog(false);
                    }
                }
                if (mActivityInterface != null) {
                    mActivityInterface.showGlobalLog(false);
                }
                break;
            }

            case 1:{
                for (RoomVideoView item : mPlayerViews) {
                    if (item.isUsed) {
                        item.videoView.showLog(false);
                    }
                }
                if (mActivityInterface != null) {
                    mActivityInterface.showGlobalLog(true);
                }
                break;
            }

            case 2:{
                for (RoomVideoView item : mPlayerViews) {
                    if (item.isUsed) {
                        item.videoView.showLog(true);
                    }
                }
                if (mActivityInterface != null) {
                    mActivityInterface.showGlobalLog(false);
                }
                break;
            }
        }
    }

    private void addMessageItem(final String userName, final String message, final TextChatMsg.Aligment aligment){
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
                mChatMsgList.add(new TextChatMsg(userName, TIME_FORMAT.format(new Date()), message, aligment));
                mChatMsgAdapter.notifyDataSetChanged();
                mChatListView.post(new Runnable() {
                    @Override
                    public void run() {
                        mChatListView.setSelection(mChatMsgList.size() - 1);
                    }
                });
            }
        });
    }

    private void sendMessage(final String message){
        mActivityInterface.getRTCRoom().sendRoomTextMsg(message, new RTCRoom.SendTextMessageCallback() {
            @Override
            public void onError(int errCode, String errInfo) {
                new AlertDialog.Builder(mActivity, R.style.RtmpRoomDialogTheme).setMessage(errInfo)
                        .setTitle("发送消息失败")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();
            }

            @Override
            public void onSuccess() {
                addMessageItem(mActivityInterface.getSelfUserName(), message, TextChatMsg.Aligment.RIGHT);
            }
        });
    }

    @Override
    public void onGetPusherList(List<PusherInfo> pusherInfoList) {
        //do nothing
    }

    @Override
    public void onPusherJoin(final PusherInfo pusher) {
        if (pusher == null || pusher.userID == null) {
            return;
        }

        RoomVideoView videoView = applyVideoView(pusher.userID, pusher.userName == null ? pusher.userID : pusher.userName);
        if (videoView != null)  {
            mActivityInterface.getRTCRoom().addRemoteView(videoView.videoView, pusher, new RTCRoom.RemoteViewPlayCallback() {
                @Override
                public void onPlayBegin() {

                }

                @Override
                public void onPlayError() {
                    onPusherQuit(pusher);
                }
            }); //开启远端视频渲染
        }
    }

    @Override
    public void onPusherQuit(PusherInfo pusher) {
        mActivityInterface.getRTCRoom().deleteRemoteView(pusher);//关闭远端视频渲染
        recycleVideoView(pusher.userID);
    }

    @Override
    public void onRecvRoomTextMsg(String roomid, String userid, String userName, String userAvatar, String msg) {
        addMessageItem(userName, msg, TextChatMsg.Aligment.LEFT);
    }

    @Override
    public void onRecvRoomCustomMsg(final String roomID, final String userID, final String userName, final String userAvatar, final String cmd, final String message) {
        //do nothing
    }

    @Override
    public void onRoomClosed(String roomId) {
        boolean createRoom = getArguments().getBoolean("createRoom");
        if (createRoom == false) {
            new HintDialog.Builder(mActivity)
                    .setTittle("系统消息")
                    .setContent(String.format("会话【%s】解散了", mRoomInfo != null ? mRoomInfo.roomInfo : "null"))
                    .setButtonText("返回")
                    .setDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            onBackPressed();
                        }
                    }).show();
        }
    }

    @Override
    public void onDebugLog(String line) {
        //do nothing
    }

    @Override
    public void onError(final int errorCode, final String errorMessage) {
        errorGoBack("会话错误", errorCode, errorMessage);
    }

    private class RoomVideoView {
        TXCloudVideoView  videoView;
        TextView          titleView;
        String            userID   = "";
        String            userName = "";
        boolean           isUsed   = false;

        public RoomVideoView(TXCloudVideoView view, TextView titleView) {
            this.videoView = view;
            this.videoView.setVisibility(View.GONE);
            this.titleView = titleView;
            this.titleView.setText("");
            this.isUsed = false;
        }

        private void setUsed(boolean used){
            videoView.setVisibility(used ? View.VISIBLE : View.GONE);
            titleView.setVisibility(used ? View.VISIBLE : View.GONE);
            titleView.setText(used ? userName : "");
            this.isUsed = used;
        }

    }

    public synchronized RoomVideoView applyVideoView(String id, String name){
        if (id == null) {
            return null;
        }

        for (RoomVideoView videoView : mPlayerViews) {
            if (!videoView.isUsed) {
                videoView.userName = name;
                videoView.userID = id;
                videoView.setUsed(true);
                return videoView;
            }
            else {
                if (videoView.userID != null && videoView.userID.equals(id)){
                    videoView.userName = name;
                    videoView.setUsed(true);
                    return videoView;
                }
            }
        }
        return null;
    }

    public synchronized void recycleVideoView(String id){
        for (RoomVideoView item : mPlayerViews) {
            if (item.userID != null && item.userID.equals(id)){
                item.userID = null;
                item.userName = "";
                item.setUsed(false);
            }
        }
    }

    public synchronized void recycleVideoView(){
        for (RoomVideoView item : mPlayerViews) {
            item.userID = null;
            item.userName = "";
            item.setUsed(false);
        }
    }
}
