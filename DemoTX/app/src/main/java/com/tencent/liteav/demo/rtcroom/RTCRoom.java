package com.tencent.liteav.demo.rtcroom;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Bitmap;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.tencent.liteav.demo.roomutil.commondef.BaseRoom;
import com.tencent.liteav.demo.roomutil.commondef.LoginInfo;
import com.tencent.liteav.demo.roomutil.im.IMMessageMgr;
import com.tencent.liteav.demo.roomutil.commondef.PusherInfo;
import com.tencent.liteav.demo.roomutil.commondef.RoomInfo;
import com.tencent.liteav.demo.roomutil.http.HttpRequests;
import com.tencent.liteav.demo.roomutil.http.HttpResponse;

import com.tencent.rtmp.TXLiveBase;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.rtmp.TXLivePusher;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;

/**
 * Created by jac on 2017/11/4.
 * Copyright © 2013-2017 Tencent Cloud. All Rights Reserved.
 */
public class RTCRoom extends BaseRoom {
    private boolean              mBackground             = false;

    private RoomListenerCallback mRoomListenerCallback;

    private RoomStatisticInfo    mStatisticInfo = new RoomStatisticInfo();

    /**
     * RealTime ChatRoom 实时音视频通话房间
     */
    public RTCRoom(Context context) {
        super(context);
        mRoomListenerCallback = new RoomListenerCallback(null);
    }

    /**
     * 设置房间事件回调
     * @param listener
     */
    public void setRTCRoomListener(IRTCRoomListener listener) {
        mRoomListenerCallback.setRoomMemberEventListener(listener);
    }

    /**
     * RTCRoom 初始化Callback
     */
    public interface LoginCallback {
        void onError(int errCode, String errInfo);
        void onSuccess(String userId);
    }

    /**
     * 初始化RTCRoom 上下文
     * @param serverDomain      服务器域名地址
     * @param loginInfo          初始化信息
     * @param callback          初始化完成的回调
     */
    public void login(@NonNull String serverDomain, @NonNull final LoginInfo loginInfo, final LoginCallback callback) {

        mStatisticInfo.str_appid = String.valueOf(loginInfo.sdkAppID);
        mStatisticInfo.str_userid = loginInfo.userID;

        final MainCallback cb = new MainCallback<LoginCallback, String>(callback);

        super.login(serverDomain, loginInfo, new IMMessageMgr.Callback() {
            @Override
            public void onError(int code, String errInfo) {
                mRoomListenerCallback.printDebugLog("[RTCRoom] 初始化失败: %s(%d)", errInfo, code);
                cb.onError(code, errInfo);
            }

            @Override
            public void onSuccess(Object... args) {
                mRoomListenerCallback.printDebugLog("[RTCRoom] 初始化成功, userID {%s}, " + "sdkAppID {%s}", mSelfAccountInfo.userID, mSelfAccountInfo.sdkAppID);
                cb.onSuccess(mSelfAccountInfo.userID);
            }
        });
    }

    /**
     * 反初始化
     */
    public void logout() {
        mRoomListenerCallback.onDebugLog("[RTCRoom] unInit");
        super.logout();
    }

    /**
     * RTCRoom 获取房间列表Callback
     */
    public interface GetRoomListCallback{
        void onError(int errCode, String errInfo);
        void onSuccess(ArrayList<RoomInfo> roomInfoList);
    }

    /**
     * 获取房间列表，分页获取
     * @param index     获取的房间开始索引，从0开始计算
     * @param count     获取的房间个数
     * @param callback  拉取房间列表完成的回调，回调里返回获取的房间列表信息，如果个数小于cnt则表示已经拉取所有的房间列表
     */
    public void getRoomList(int index, int count,final GetRoomListCallback callback){
        mHttpRequest.getRoomList(index, count, new HttpRequests.OnResponseCallback<HttpResponse.RoomList>() {
            @Override
            public void onResponse(final int retcode, final @Nullable String retmsg, @Nullable HttpResponse.RoomList data) {
                if (retcode != HttpResponse.CODE_OK || data == null || data.rooms == null){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(retcode, retmsg);
                        }
                    });
                }
                else {
                    final ArrayList<RoomInfo> arrayList = new ArrayList<>(data.rooms.size());
                    arrayList.addAll(data.rooms);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mRoomList = arrayList;
                            callback.onSuccess(arrayList);
                        }
                    });
                }
            }
        });
    }

    /**
     * RTCRoom 创建房间Callback
     */
    public interface CreateRoomCallback {
        void onError(int errCode, String errInfo);
        void onSuccess(String name);
    }

    /**
     * 创建房间，由会议创建者调用
     * @param roomInfo  房间信息
     * @param cb        房间创建完成的回调，里面会携带roomID
     */
    public void createRoom(final String roomID, @NonNull final String roomInfo, final CreateRoomCallback cb) {
        //1. 在应用层调用startLocalPreview，启动本地预览

        final MainCallback callback = new MainCallback<CreateRoomCallback, String>(cb);

        //2. 请求CGI:create_room，获取roomID
        doCreateRoom(roomID, roomInfo, new BaseRoom.CreateRoomCallback() {
            @Override
            public void onError(int errCode, String errInfo) {
                callback.onError(errCode, errInfo);
            }

            @Override
            public void onSuccess(final String newRoomID) {

                //3. 请求CGI:get_push_url，异步获取到推流地址pushUrl
                mHttpRequest.getPushUrl(mSelfAccountInfo.userID, newRoomID, new HttpRequests.OnResponseCallback<HttpResponse.PushUrl>() {
                    @Override
                    public void onResponse(int retcode, @Nullable String retmsg, @Nullable HttpResponse.PushUrl data) {
                        if (retcode == HttpResponse.CODE_OK && data != null && data.pushURL != null) {
                            final String pushURL = data.pushURL;

                            //4.开始推流
                            startPushStream(pushURL, new BaseRoom.PusherStreamCallback() {
                                @Override
                                public void onError(int errCode, String errInfo) {
                                    callback.onError(errCode, errInfo);
                                }

                                @Override
                                public void onSuccess() {
                                    //推流过程中，可能会重复收到PUSH_EVT_PUSH_BEGIN事件，onSuccess可能会被回调多次，如果已经创建的房间，直接返回
                                    if (mCurrRoomID != null && mCurrRoomID.length() > 0) {
                                        return;
                                    }

                                    //5.请求CGI：add_pusher，加入房间
                                    addPusher(newRoomID, pushURL, new AddPusherCallback() {
                                        @Override
                                        public void onError(int errCode, String errInfo) {
                                            callback.onError(errCode, errInfo);
                                        }

                                        @Override
                                        public void onSuccess() {
                                            mCurrRoomID = newRoomID;

                                            //6.调用IM的joinGroup，加入群组
                                            jionGroup(newRoomID, new JoinGroupCallback() {
                                                @Override
                                                public void onError(int errCode, String errInfo) {
                                                    callback.onError(errCode, errInfo);
                                                }

                                                @Override
                                                public void onSuccess() {
                                                    mHeartBeatThread.startHeartbeat();
                                                    //启动心跳
                                                    callback.onSuccess(mCurrRoomID);
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                        }
                        else {
                            callback.onError(retcode, "获取推流地址失败");
                        }
                    }
                });
            }
        });
    }

    /**
     * RTCRoom 进入房间Callback
     */
    public interface EnterRoomCallback{
        void onError(int errCode, String errInfo);
        void onSuccess();
    }

    /**
     * 进入房间，由会议参与者调用
     * @param roomID    房间号
     * @param cb        进入房间完成的回调
     */
    public void enterRoom(@NonNull final String roomID, final EnterRoomCallback cb) {
        mStatisticInfo.clean();
        mStatisticInfo.str_roomid = roomID;
        mStatisticInfo.str_room_creator = getRoomCreator(roomID);
        mStatisticInfo.str_nickname = mSelfAccountInfo.userName;
        mStatisticInfo.int64_ts_enter_room = System.currentTimeMillis();

        mCurrRoomID = roomID;

        //1. 应用层调用startLocalPreview，启动本地预览

        final MainCallback<EnterRoomCallback, Object> callback = new MainCallback<EnterRoomCallback, Object>(cb);

        //2. 调用IM的joinGroup
        final long joinGroupTSBeg = System.currentTimeMillis();
        jionGroup(roomID, new JoinGroupCallback() {
            @Override
            public void onError(int code, String errInfo) {
                mStatisticInfo.int64_tc_join_group = code < 0 ? code : 0 - code;
                mStatisticInfo.reportStatisticInfo();
                callback.onError(code, errInfo);
            }

            @Override
            public void onSuccess() {
                mStatisticInfo.int64_tc_join_group = System.currentTimeMillis() - joinGroupTSBeg;

                //3. 请求CGI:get_pushers，异步获取房间里所有正在推流的成员
                final long updatePushersTSBeg = System.currentTimeMillis();
                updatePushers(false, new UpdatePushersCallback() {
                    @Override
                    public void onUpdatePushersComplete(int retcode, List<PusherInfo> newPushers, List<PusherInfo> delPushers, HashMap<String, PusherInfo> mergedPushers) {
                        if (retcode != 0) {
                            mStatisticInfo.int64_tc_get_pushers = retcode < 0 ? retcode : 0 - retcode;
                            mStatisticInfo.reportStatisticInfo();
                            mRoomListenerCallback.printDebugLog("[RTCRoom] getPusherList failed");
                        }
                        else {
                            mStatisticInfo.int64_tc_get_pushers = System.currentTimeMillis() - updatePushersTSBeg;
                            mStatisticInfo.setPlayStreamBeginTS(System.currentTimeMillis());

                            //4. 调用listener.onGetPusherList，把房间成员通知出去（应用层代码在收到这个通知后，调用addRemoteView播放每一个成员的流）
                            mRoomListenerCallback.printDebugLog("[RTCRoom][enterRoom][updatePushers] pusher(%d)", newPushers.size());
                            mRoomListenerCallback.onGetPusherList(newPushers);
                            mPushers = mergedPushers;
                        }
                    }
                });

                //5. 请求CGI:get_push_url，异步获取到推流地址pushUrl
                final long getPushUrlTSBeg = System.currentTimeMillis();
                mHttpRequest.getPushUrl(mSelfAccountInfo.userID, mCurrRoomID, new HttpRequests.OnResponseCallback<HttpResponse.PushUrl>() {
                    @Override
                    public void onResponse(int retcode, @Nullable String retmsg, final @Nullable HttpResponse.PushUrl data) {
                        if (retcode != HttpResponse.CODE_OK || data == null || data.pushURL == null) {
                            mStatisticInfo.int64_tc_get_pushurl = retcode < 0 ? retcode : 0 - retcode;
                            mStatisticInfo.reportStatisticInfo();
                            callback.onError(retcode, "获取推流地址失败");
                        }
                        else {
                            mStatisticInfo.int64_tc_get_pushurl = System.currentTimeMillis() - getPushUrlTSBeg;
                            mStatisticInfo.setStreamPushUrl(data.pushURL);

                            //6. 开始推流
                            final long pushStreamTSBeg = System.currentTimeMillis();
                            startPushStream(data.pushURL, new PusherStreamCallback() {
                                @Override
                                public void onError(int code, String info) {
                                    mStatisticInfo.reportStatisticInfo();
                                    callback.onError(code, info);
                                }

                                @Override
                                public void onSuccess() {
                                    mStatisticInfo.int64_tc_push_stream = System.currentTimeMillis() - pushStreamTSBeg;

                                    //7. 推流成功，请求CGI:add_pusher，把自己加入房间成员列表
                                    final long addPusherTSBeg = System.currentTimeMillis();
                                    addPusher(roomID, data.pushURL, new AddPusherCallback() {
                                        @Override
                                        public void onError(int code, String info) {
                                            mStatisticInfo.int64_tc_add_pusher = code < 0 ? code : 0 - code;
                                            mStatisticInfo.reportStatisticInfo();
                                            callback.onError(code, info);
                                        }

                                        @Override
                                        public void onSuccess() {
                                            mStatisticInfo.int64_tc_add_pusher = System.currentTimeMillis() - addPusherTSBeg;
                                            mStatisticInfo.updateAddPusherSuccessTS(System.currentTimeMillis());

                                            mHeartBeatThread.startHeartbeat();// 开启心跳
                                            callback.onSuccess();
                                        }
                                    });
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    /**
     * RTCRoom 离开房间Callback
     */
    public interface ExitRoomCallback{
        void onError(int errCode, String errInfo);
        void onSuccess();
    }

    /**
     * 离开房间
     * @param cb 离开房间完成的回调
     */
    public void exitRoom(final ExitRoomCallback cb) {
        final MainCallback callback = new MainCallback<ExitRoomCallback, Object>(cb);

        //1. 应用层结束播放所有的流

        //2. 结束心跳
        mHeartBeatThread.stopHeartbeat();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //3. 结束本地推流
                stopLocalPreview();

                //4. 关闭所有播放器，清理房间信息
                cleanPlayers();
            }
        });

        //5. 调用IM的quitGroup，退出群组
        mIMMessageMgr.quitGroup(mCurrRoomID, new IMMessageMgr.Callback() {
            @Override
            public void onError(int code, String errInfo) {
                //cb.onError(code, errInfo);
            }

            @Override
            public void onSuccess(Object... args) {
                //cb.onSuccess();
            }
        });

        //6. 退出房间：请求CGI:delete_pusher，把自己从房间成员列表里删除（后台会判断如果是房间创建者退出房间，则会直接解散房间）
        mHttpRequest.delPusher(mCurrRoomID, mSelfAccountInfo.userID, new HttpRequests.OnResponseCallback<HttpResponse>() {
            @Override
            public void onResponse(int retcode, @Nullable String retmsg, @Nullable HttpResponse data) {
                if (retcode == HttpResponse.CODE_OK || retcode == 5) {
                    mRoomListenerCallback.printDebugLog("[RTCRoom] UserID{%s} 退出房间 {%s}  成功", mSelfAccountInfo.userID, mCurrRoomID);
                    callback.onSuccess();
                }
                else {
                    mRoomListenerCallback.printDebugLog("[RTCRoom] UserID{%s} 退出房间 {%s}  失败", mSelfAccountInfo.userID, mCurrRoomID);
                    callback.onError(retcode, retmsg);
                }
            }
        });

        mCurrRoomID = "";
        mPushers.clear();
    }

    /**
     * 启动摄像头预览
     * @param videoView 摄像头预览组件
     */
    public synchronized void startLocalPreview(final @NonNull TXCloudVideoView videoView) {
        super.startLocalPreview(videoView);
    }

    /**
     * 停止摄像头预览
     */
    public synchronized void stopLocalPreview() {
        super.stopLocalPreview();
    }

    /**
     * 远端视频播放回调通知
     */
    public interface RemoteViewPlayCallback {
        void onPlayBegin();
        void onPlayError();
    }

    /**
     * 启动远端视频
     * @param videoView  视频预览组件
     * @param pusherInfo 对应视频的成员
     * @param callback   视频播放回调
     */
    public void addRemoteView(final @NonNull TXCloudVideoView videoView, final @NonNull PusherInfo pusherInfo, final RemoteViewPlayCallback callback) {
        super.addRemoteView(videoView, pusherInfo, new PlayCallback() {
            @Override
            public void onPlayBegin() {
                mStatisticInfo.updatePlayStreamSuccessTS(System.currentTimeMillis());
                if (callback != null) {
                    callback.onPlayBegin();
                }
            }

            @Override
            public void onPlayError() {
                mStatisticInfo.reportStatisticInfo();
                if (callback != null) {
                    callback.onPlayError();
                }
            }

            @Override
            public void onPlayEvent(int event) {

            }
        });
    }

    /**
     * 停止远端视频
     * @param pusherInfo 对应视频的成员
     */
    public void deleteRemoteView(final @NonNull PusherInfo pusherInfo) {
        super.deleteRemoteView(pusherInfo);
    }

    /**
     * RTCRoom 发送文本消息Callback
     */
    public interface SendTextMessageCallback{
        void onError(int errCode, String errInfo);
        void onSuccess();
    }

    /**
     * 发送文本消息
     * @param message
     * @param callback
     */
    public void sendRoomTextMsg(@NonNull String message, final SendTextMessageCallback callback){
        super.sendRoomTextMsg(message, new IMMessageMgr.Callback() {
            @Override
            public void onError(final int code, final String errInfo) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onError(code, errInfo);
                        }
                    }
                });
            }

            @Override
            public void onSuccess(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    }
                });
            }
        });
    }

    /**
     * RTCRoom 发送房间自定义消息消息Callback
     */
    public interface SendCustomMessageCallback{
        void onError(int errCode, String errInfo);
        void onSuccess();
    }

    /**
     * RTCRoom 发送自定义消息
     * @param cmd
     * @param message
     * @param callback
     */
    public void sendRoomCustomMsg(@NonNull String cmd, @NonNull String message, final SendCustomMessageCallback callback) {
        super.sendRoomCustomMsg(cmd, message, new IMMessageMgr.Callback() {
            @Override
            public void onError(final int code, final String errInfo) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onError(code, errInfo);
                        }
                    }
                });
            }

            @Override
            public void onSuccess(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    }
                });
            }
        });
    }

    /**
     * 从前台切换到后台，关闭采集摄像头数据，推送默认图片
     */
    public void switchToBackground(){
        super.switchToBackground();
        mBackground = true;
    }

    /**
     * 由后台切换到前台，开启摄像头数据采集
     */
    public void switchToForeground(){
        super.switchToForeground();
        mBackground = false;
        if (mTXLivePusher != null && mTXLivePusher.isPushing()) {
            onPusherChanged();
        }
    }

    /**
     * 切换摄像头
     */
    public void switchCamera() {
        super.switchCamera();
    }

    /**
     * 静音设置
     * @param isMute 静音变量, true 表示静音， 否则 false
     */
    public void setMute(boolean isMute) {
        super.setMute(isMute);
    }


    /**
     * 设置美颜效果.
     * @param style          美颜风格.三种美颜风格：0 ：光滑  1：自然  2：朦胧
     * @param beautyLevel    美颜等级.美颜等级即 beautyLevel 取值为0-9.取值为0时代表关闭美颜效果.默认值:0,即关闭美颜效果.
     * @param whiteningLevel 美白等级.美白等级即 whiteningLevel 取值为0-9.取值为0时代表关闭美白效果.默认值:0,即关闭美白效果.
     * @param ruddyLevel     红润等级.美白等级即 ruddyLevel 取值为0-9.取值为0时代表关闭美白效果.默认值:0,即关闭美白效果.
     * @return               是否成功设置美白和美颜效果. true:设置成功. false:设置失败.
     */
    public boolean setBeautyFilter(int style, int beautyLevel, int whiteningLevel, int ruddyLevel) {
        return super.setBeautyFilter(style, beautyLevel, whiteningLevel, ruddyLevel);
    }

    /**
     * 调整摄像头焦距
     * @param  value 焦距，取值 0~getMaxZoom();
     * @return  true : 成功 false : 失败
     */
    public boolean setZoom(int value) {
        return super.setZoom(value);
    }

    /**
     * 设置播放端水平镜像与否(tips：推流端前置摄像头默认看到的是镜像画面，后置摄像头默认看到的是非镜像画面)
     * @param enable true:播放端看到的是镜像画面,false:播放端看到的是镜像画面
     */
    public boolean setMirror(boolean enable) {
        return super.setMirror(enable);
    }

    /**
     * 调整曝光
     * @param value 曝光比例，表示该手机支持最大曝光调整值的比例，取值范围从-1到1。
     *              负数表示调低曝光，-1是最小值，对应getMinExposureCompensation。
     *              正数表示调高曝光，1是最大值，对应getMaxExposureCompensation。
     *              0表示不调整曝光
     */
    public void setExposureCompensation(float value) {
        super.setExposureCompensation(value);
    }

    /**
     * 设置麦克风的音量大小.
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     * @param x: 音量大小,1为正常音量,建议值为0~2,如果需要调大音量可以设置更大的值.
     * @return 是否成功设置麦克风的音量大小. true:设置麦克风的音量成功. false:设置麦克风的音量失败.
     */
    public boolean setMicVolume(float x) {
        return super.setMicVolume(x);
    }

    /**
     * 设置背景音乐的音量大小.
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     * @param x 音量大小,1为正常音量,建议值为0~2,如果需要调大背景音量可以设置更大的值.
     * @return  是否成功设置背景音乐的音量大小. true:设置背景音的音量成功. false:设置背景音的音量失败.
     */
    public boolean setBGMVolume(float x) {
        return super.setBGMVolume(x);
    }

    public void setBGMNofify(TXLivePusher.OnBGMNotify notify){
        super.setBGMNofify(notify);
    }

    /**
     * 获取音乐文件时长.
     *
     * @param path
     *          音乐文件路径
     *          path == null 获取当前播放歌曲时长
     *          path != null 获取path路径歌曲时长
     * @return
     *        音乐文件时长,单位ms.
     */
    public int getMusicDuration(String path) {
        return super.getMusicDuration(path);
    }

    /**
     * 播放背景音乐.
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     *
     * @param path
     *          背景音乐文件路径.
     * @return
     *      是否成功播放背景音乐. true:播放成功. false:播放失败.
     */
    public boolean playBGM(String path) {
        if (mTXLivePusher != null) {
            return mTXLivePusher.playBGM(path);
        }
        return false;
    }

    /**
     * 停止播放背景音乐.
     *
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     *
     * @return
     *      是否成功停止播放背景音乐. true:停止播放成功. false:停止播放失败.
     */
    public boolean stopBGM() {
        return super.stopBGM();
    }

    /**
     * 暂停播放背景音乐.
     *
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     *
     * @return
     *      是否成功暂停播放背景音乐. true:暂停播放成功. false:暂停播放失败.
     */
    public boolean pauseBGM() {
        return super.pauseBGM();
    }

    /**
     * 恢复播放背景音乐.
     *
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     *
     * @return
     *      是否成功恢复播放背景音乐. true:恢复播放成功. false:恢复播放失败.
     */
    public boolean resumeBGM() {
        return super.resumeBGM();
    }
    

    /**
     * setFilterImage 设置指定素材滤镜特效
     * @param bmp: 指定素材，即颜色查找表图片。注意：一定要用png图片格式！！！
     *           demo用到的滤镜查找表图片位于RTMPAndroidDemo/app/src/main/res/drawable-xxhdpi/目录下。
     */
    public void setFilter(Bitmap bmp) {
        super.setFilter(bmp);
    }

    /**
     * 设置动效贴图文件位置
     * @param specialValue
     */
    public void setMotionTmpl(String specialValue) {
        super.setMotionTmpl(specialValue);
    }

    /**
     * 设置绿幕文件:目前图片支持jpg/png，视频支持mp4/3gp等Android系统支持的格式
     * API要求18
     * @param file ：绿幕文件位置，支持两种方式：
     *             1.资源文件放在assets目录，path直接取文件名
     *             2.path取文件绝对路径
     * @return false:调用失败
     *         true:调用成功
     */
    public boolean setGreenScreenFile(String file) {
        return super.setGreenScreenFile(file);
    }

    /**
     * 设置大眼效果.
     * @param level
     *          大眼等级取值为0-9.取值为0时代表关闭美颜效果.默认值:0
     */
    public void setEyeScaleLevel(int level) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setEyeScaleLevel(level);
        }
    }

    /**
     * 设置瘦脸效果.
     * @param level
     *          瘦脸等级取值为0-9.取值为0时代表关闭美颜效果.默认值:0
     */
    public void setFaceSlimLevel(int level) {
        super.setFaceSlimLevel(level);
    }

    /**
     * V 脸
     * @param level
     */
    public void setFaceVLevel(int level) {
        super.setFaceVLevel(level);
    }

    /**
     * setSpecialRatio 设置滤镜效果程度
     * @param ratio: 从0到1，越大滤镜效果越明显，默认取值0.5
     */
    public void setSpecialRatio(float ratio) {
        super.setSpecialRatio(ratio);
    }

    /**
     * 缩脸
     * @param level
     */
    public void setFaceShortLevel(int level) {
        super.setFaceShortLevel(level);
    }

    /**
     * 下巴
     * @param scale
     */
    public void setChinLevel(int scale) {
        super.setChinLevel(scale);
    }

    /**
     * 小鼻
     * @param scale
     */
    public void setNoseSlimLevel(int scale) {
        super.setNoseSlimLevel(scale);
    }

    /**
     * 混响
     * @param reverbType
     */
    public void setReverb(int reverbType) {
        super.setReverb(reverbType);
    }

    /**
     * 设置从前台切换到后台，推送的图片
     * @param bitmap
     */
    public void setPauseImage(final @Nullable Bitmap bitmap) {
        super.setPauseImage(bitmap);
    }

    /**
     * 从前台切换到后台，关闭采集摄像头数据
     * @param id 设置默认显示图片的资源文件
     */
    public void setPauseImage(final @IdRes int id){
        super.setPauseImage(id);
    }

    /**
     * 设置视频的码率区间
     * @param minBitrate
     * @param maxBitrate
     */
    public void setBitrateRange(int minBitrate, int maxBitrate) {
        if (mTXLivePusher != null) {
            TXLivePushConfig config = mTXLivePusher.getConfig();
            config.setMaxVideoBitrate(maxBitrate);
            config.setMinVideoBitrate(minBitrate);
            mTXLivePusher.setConfig(config);
        }
    }

    @Override
    public void onConnected() {
        mRoomListenerCallback.printDebugLog("[IM] online");
    }

    @Override
    public void onDisconnected() {
        mRoomListenerCallback.printDebugLog("[IM] offline");
    }

    @Override
    public void onPusherChanged() {
        if (mBackground == false) {
            if (mCurrRoomID != null && mCurrRoomID.length() > 0) {
                mRoomListenerCallback.printDebugLog("[RTCRoom] updatePushers called");
                updatePushers(false, new UpdatePushersCallback() {
                    @Override
                    public void onUpdatePushersComplete(int retcode, List<PusherInfo> newPushers, List<PusherInfo> delPushers, HashMap<String, PusherInfo> mergedPushers) {
                        if (mHeartBeatThread != null && mHeartBeatThread.running()) {
                            if (retcode == 0) {
                                mRoomListenerCallback.printDebugLog("[RTCRoom][updatePushers] new(%d), remove(%d)", newPushers.size(), delPushers.size());

                                for (PusherInfo member : delPushers) {
                                    mRoomListenerCallback.onPusherQuit(member);
                                }

                                for (PusherInfo member : newPushers) {
                                    mRoomListenerCallback.onPusherJoin(member);
                                }

                                mPushers.clear();
                                mPushers = mergedPushers;
                            } else {
                                mRoomListenerCallback.printDebugLog("[RTCRoom] updatePushers failed");
                            }
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onGroupDestroyed(final String groupID) {
        mRoomListenerCallback.onDebugLog("[RTCRoom] onGroupDestroyed called ");
        if (mCurrRoomID != null && mCurrRoomID.equalsIgnoreCase(groupID)) {
            mRoomListenerCallback.onRoomClosed(mCurrRoomID);
        }
    }

    @Override
    public void onGroupTextMessage(final String groupID, final String senderID, final String userName, final String headPic, final String message) {
        PusherInfo pusherInfo = null;
        if (mPushers.containsKey(senderID)) {
            pusherInfo = mPushers.get(senderID);
        }
        if (pusherInfo != null) {
            mRoomListenerCallback.onRecvRoomTextMsg(groupID, pusherInfo.userID, pusherInfo.userName, pusherInfo.userAvatar, message);
        }
    }

    @Override
    public void onGroupCustomMessage(String groupID, String senderID, String message) {
        CustomMessage customMessage =  new Gson().fromJson(message, CustomMessage.class);
        if (mRoomListenerCallback != null) {
            mRoomListenerCallback.onRecvRoomCustomMsg(groupID, senderID, customMessage.userName, customMessage.userAvatar, customMessage.cmd, customMessage.msg);
        }
    }

    @Override
    public void onC2CCustomMessage(String sendId, String cmd, String message) {
    }

    @Override
    public void onDebugLog(final String log) {
        mRoomListenerCallback.onDebugLog(log);
    }

    protected void invokeDebugLog(String log){
        mRoomListenerCallback.onDebugLog(log);
    }

    protected void invokeError(int errorCode, String errorMessage) {
        mRoomListenerCallback.onError(errorCode, errorMessage);
    }

    protected void initLivePusher() {
        super.initLivePusher();
        if (mTXLivePusher != null) {
            mTXLivePusher.setVideoQuality(TXLiveConstants.VIDEO_QUALITY_REALTIEM_VIDEOCHAT, true, true);
            TXLivePushConfig config = mTXLivePusher.getConfig();
            config.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_480_640);
            config.setAudioSampleRate(48000);
            mTXLivePusher.setConfig(config);
        }
    }

    private class RoomListenerCallback implements IRTCRoomListener {
        private final Handler    handler;
        private IRTCRoomListener roomMemberEventListener;

        public RoomListenerCallback(IRTCRoomListener roomMemberEventListener) {
            this.handler = new Handler(Looper.getMainLooper());
            this.roomMemberEventListener = roomMemberEventListener;
        }

        public void setRoomMemberEventListener(IRTCRoomListener roomMemberEventListener) {
            this.roomMemberEventListener = roomMemberEventListener;
        }

        @Override
        public void onPusherJoin(final PusherInfo member) {
            printDebugLog("[RTCRoom] onPusherJoin, UserID {%s} PlayUrl {%s}", member.userID, member.accelerateURL);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (roomMemberEventListener != null) {
                        roomMemberEventListener.onPusherJoin(member);
                    }
                }
            });
        }

        @Override
        public void onPusherQuit(final PusherInfo member) {
            printDebugLog("[RTCRoom] onPusherQuit, UserID {%s} PlayUrl {%s}", member.userID, member.accelerateURL);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if(roomMemberEventListener != null) {
                        roomMemberEventListener.onPusherQuit(member);
                    }
                }
            });
        }

        @Override
        public void onRoomClosed(final String roomId) {
            printDebugLog("[RTCRoom] onRoomClosed, RoomId {%s}", roomId);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if(roomMemberEventListener != null) {
                        roomMemberEventListener.onRoomClosed(roomId);
                    }
                }
            });
        }

        @Override
        public void onDebugLog(final String line) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if(roomMemberEventListener != null) {
                        roomMemberEventListener.onDebugLog(line);
                    }
                }
            });
        }

        @Override
        public void onGetPusherList(final List<PusherInfo> pusherInfoList) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if(roomMemberEventListener != null) {
                        roomMemberEventListener.onGetPusherList(pusherInfoList);
                    }
                }
            });
        }

        @Override
        public void onRecvRoomTextMsg(final String roomId, final String userId, final String userName, final String headPic, final String message) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if(roomMemberEventListener != null) {
                        roomMemberEventListener.onRecvRoomTextMsg(roomId, userId, userName, headPic, message);
                    }
                }
            });
        }

        @Override
        public void onRecvRoomCustomMsg(final String roomID, final String userID, final String userName, final String headPic, final  String cmd, final  String message) {
            if(roomMemberEventListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        roomMemberEventListener.onRecvRoomCustomMsg(roomID, userID, userName, headPic, cmd, message);
                    }
                });
        }

        @Override
        public void onError(final int errorCode, final String errorMessage) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if(roomMemberEventListener != null) {
                        roomMemberEventListener.onError(errorCode, errorMessage);
                    }
                }
            });
        }

        void printDebugLog(String format, Object ...args){
            String line = String.format(format, args);
            onDebugLog(line);
        }
    }

    private class RoomStatisticInfo {
        public String str_appid                 = "";
        public String str_platform              = "android";
        public String str_userid                = "";
        public String str_roomid                = "";
        public String str_room_creator          = "";
        public String str_streamid              = "";
        public long   int64_ts_enter_room       = -1;
        public long   int64_tc_join_group       = -1;
        public long   int64_tc_get_pushers      = -1;
        public long   int64_tc_play_stream      = -1;
        public long   int64_tc_get_pushurl      = -1;
        public long   int64_tc_push_stream      = -1;
        public long   int64_tc_add_pusher       = -1;
        public long   int64_tc_enter_room       = -1;
        public String str_appversion            = TXLiveBase.getSDKVersionStr();
        public String str_sdkversion            = TXLiveBase.getSDKVersionStr();
        public String str_common_version        = "";   //公共库版本号，微信专用
        public String str_nickname              = "";
        public String str_device                = Build.MODEL;
        public String str_device_type           = "";   //设备及OS版本号，微信专用
        public String str_play_info             = "";
        public String str_push_info             = "";
        public long   int32_report_type         = 0;    //0：RTCRoom     1：RoomService

        private long  int64_ts_play_stream      = -1;

        public void clean() {
            str_roomid                = "";
            str_room_creator          = "";
            str_streamid              = "";
            int64_ts_enter_room       = -1;
            int64_tc_join_group       = -1;
            int64_tc_get_pushers      = -1;
            int64_tc_play_stream      = -1;
            int64_tc_get_pushurl      = -1;
            int64_tc_push_stream      = -1;
            int64_tc_add_pusher       = -1;
            int64_tc_enter_room       = -1;

            int64_ts_play_stream      = -1;
        }

        public void setStreamPushUrl(String strStreamUrl) {
            if (strStreamUrl == null || strStreamUrl.length() == 0) {
                return;
            }

            //推流地址格式：rtmp://8888.livepush.myqcloud.com/path/8888_test_12345_test?txSecret=aaaa&txTime=bbbb
            //拉流地址格式：rtmp://8888.liveplay.myqcloud.com/path/8888_test_12345_test
            //            http://8888.liveplay.myqcloud.com/path/8888_test_12345_test.flv
            //            http://8888.liveplay.myqcloud.com/path/8888_test_12345_test.m3u8


            String subString = strStreamUrl;

            {
                //1 截取第一个 ？之前的子串
                int index = subString.indexOf("?");
                if (index != -1) {
                    subString = subString.substring(0, index);
                }
                if (subString == null || subString.length() == 0) {
                    return;
                }
            }

            {
                //2 截取最后一个 / 之后的子串
                int index = subString.lastIndexOf("/");
                if (index != -1) {
                    subString = subString.substring(index + 1);
                }

                if (subString == null || subString.length() == 0) {
                    return;
                }
            }

            {
                //3 截取第一个 . 之前的子串
                int index = subString.indexOf(".");
                if (index != -1) {
                    subString = subString.substring(0, index);
                }
                if (subString == null || subString.length() == 0) {
                    return;
                }
            }

            str_streamid = subString;
        }

        public void setPlayStreamBeginTS(long ts) {
            int64_ts_play_stream = ts;
        }

        public void updatePlayStreamSuccessTS(long ts) {
            if (int64_tc_play_stream == -1) {
                if (int64_ts_play_stream != -1) {
                    int64_tc_play_stream = ts - int64_ts_play_stream;
                }

                int64_tc_enter_room = ts - int64_ts_enter_room;

                if (int64_tc_add_pusher != -1 && int64_tc_play_stream != -1) { //加入房间成功
                    reportStatisticInfo();
                }
            }
        }

        public void updateAddPusherSuccessTS(long ts) {
            int64_tc_enter_room = ts - int64_ts_enter_room;

            if (int64_tc_add_pusher != -1 && int64_tc_play_stream != -1) { //加入房间成功
                reportStatisticInfo();
            }
        }

        public void reportStatisticInfo() {
            if (TextUtils.isEmpty(str_appid) || TextUtils.isEmpty(str_userid) || TextUtils.isEmpty(str_roomid)) {
                return;
            }

            if (int64_tc_add_pusher == -1 || int64_tc_play_stream == -1) { //加入房间成功
                int64_tc_enter_room = -1;
            }

            try {
                String strJson = new Gson().toJson(this, RoomStatisticInfo.class);
                JSONObject jsonObj = new JSONObject(strJson);

                String strLog = "";
                Iterator<String> sIterator = jsonObj.keys();
                while(sIterator.hasNext()){
                    // 获得key
                    String key = sIterator.next();
                    // 根据key获得value, value也可以是JSONObject,JSONArray,使用对应的参数接收即可
                    String value = jsonObj.getString(key);
                    strLog = strLog + key + " \t\t\t= " + value + "\n";
                }
                Log.e("HTTP_REPORT", strLog);

                mHttpRequest.report("1", jsonObj, new HttpRequests.OnResponseCallback<HttpResponse>() {
                    @Override
                    public void onResponse(int retcode, @Nullable String retmsg, @Nullable HttpResponse data) {
                        Log.e("HTTP_REPORT", "onResponse retcode = " + retcode + " retmsg = " + retmsg);
                    }
                });
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            clean();
        }
    }
}
